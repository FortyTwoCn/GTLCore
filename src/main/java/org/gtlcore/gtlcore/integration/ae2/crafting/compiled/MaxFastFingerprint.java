package org.gtlcore.gtlcore.integration.ae2.crafting.compiled;

import net.minecraft.core.HolderLookup;
import java.lang.ref.WeakReference;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

public final class MaxFastFingerprint {

    public static final String UNAVAILABLE = "unavailable";
    public static final String SCHEMA = "sha256_binary_v2";

    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private static final Cache<IPatternDetails, CachedPattern> PATTERN_FINGERPRINT_CACHE = CacheBuilder.newBuilder()
            .weakKeys()
            .build();

    private record CachedPattern(WeakReference<HolderLookup.Provider> registries, String value) {}

    private MaxFastFingerprint() {}

    public static Result ofKeyCounter(KeyCounter counter, HolderLookup.Provider registries) {
        long startedNanos = System.nanoTime();
        try {
            List<String> entries = new ArrayList<>(counter.size());
            for (var entry : counter) {
                entries.add(key(entry.getKey(), registries) + '=' + entry.getLongValue());
            }
            entries.sort(Comparator.naturalOrder());
            return new Result(hashEntries(entries), entries.size(), System.nanoTime() - startedNanos);
        } catch (Throwable ignored) {
            return unavailable(startedNanos);
        }
    }

    public static PlanResult ofPlan(ICraftingPlan plan, Map<IPatternDetails, String> patternFingerprints, HolderLookup.Provider registries) {
        long startedNanos = System.nanoTime();
        try {
            List<String> entries = new ArrayList<>();
            entries.add("final=" + stack(plan.finalOutput(), registries));
            entries.add("bytes=" + plan.bytes());
            entries.add("simulation=" + plan.simulation());
            entries.add("multiple_paths=" + plan.multiplePaths());
            appendCounter(entries, "used", plan.usedItems(), registries);
            appendCounter(entries, "emitted", plan.emittedItems(), registries);
            appendCounter(entries, "missing", plan.missingItems(), registries);

            List<String> patterns = new ArrayList<>(plan.patternTimes().size());
            int patternCacheHits = 0;
            int patternCacheMisses = 0;
            for (Map.Entry<IPatternDetails, Long> entry : plan.patternTimes().entrySet()) {
                String patternFingerprint = patternFingerprints.get(entry.getKey());
                if (patternFingerprint == null) {
                    patternFingerprint = pattern(entry.getKey(), registries);
                    patternCacheMisses++;
                } else {
                    patternCacheHits++;
                }
                patterns.add(patternFingerprint + "@times=" + entry.getValue());
            }
            patterns.sort(Comparator.naturalOrder());
            for (String pattern : patterns) {
                entries.add("pattern=" + pattern);
            }
            return new PlanResult(
                    new Result(hashEntries(entries), entries.size(), System.nanoTime() - startedNanos),
                    patternCacheHits,
                    patternCacheMisses);
        } catch (Throwable ignored) {
            return new PlanResult(unavailable(startedNanos), 0, 0);
        }
    }

    public static Result ofStructureEntries(SortedSet<String> structureEntries) {
        long startedNanos = System.nanoTime();
        try {
            return new Result(
                    hashEntries(structureEntries),
                    structureEntries.size(),
                    System.nanoTime() - startedNanos);
        } catch (Throwable ignored) {
            return unavailable(startedNanos);
        }
    }

    public static AnalyzedProgramResult analyzedProgram(
                                                        AEKey key, boolean emitter, Collection<IPatternDetails> candidates,
                                                        IdentityHashMap<IPatternDetails, String> patternFingerprints, HolderLookup.Provider registries) {
        try {
            MessageDigest digest = newDigest();
            updateString(digest, SCHEMA);
            updateKey(digest, key, registries);
            if (emitter) {
                updateInt(digest, 1);
            } else if (candidates.isEmpty()) {
                updateInt(digest, 2);
            } else {
                updateInt(digest, 3);
                updateInt(digest, candidates.size());
                int patternCacheHits = 0;
                int patternCacheMisses = 0;
                for (IPatternDetails candidate : candidates) {
                    CachedPattern cached = PATTERN_FINGERPRINT_CACHE.getIfPresent(candidate);
                    String patternFingerprint = cached != null && cached.registries().get() == registries ? cached.value() : null;
                    if (patternFingerprint == null) {
                        patternFingerprint = pattern(candidate, registries);
                        PATTERN_FINGERPRINT_CACHE.put(candidate, new CachedPattern(new WeakReference<>(registries), patternFingerprint));
                        patternCacheMisses++;
                    } else {
                        patternCacheHits++;
                    }
                    patternFingerprints.put(candidate, patternFingerprint);
                    updateString(digest, patternFingerprint);
                }
                return new AnalyzedProgramResult(
                        HEX_FORMAT.formatHex(digest.digest()),
                        false,
                        patternCacheHits,
                        patternCacheMisses);
            }
            return new AnalyzedProgramResult(HEX_FORMAT.formatHex(digest.digest()), false, 0, 0);
        } catch (Throwable ignored) {
            return new AnalyzedProgramResult(UNAVAILABLE, true, 0, 0);
        }
    }

    private static void appendCounter(List<String> target, String name, KeyCounter counter, HolderLookup.Provider registries) {
        List<String> entries = new ArrayList<>(counter.size());
        for (var entry : counter) {
            entries.add(key(entry.getKey(), registries) + '=' + entry.getLongValue());
        }
        entries.sort(Comparator.naturalOrder());
        for (String entry : entries) {
            target.add(name + '=' + entry);
        }
    }

    private static String pattern(IPatternDetails details, HolderLookup.Provider registries) {
        MessageDigest digest = newDigest();
        updateString(digest, SCHEMA);
        updateString(digest, details.getClass().getName());
        updateKey(digest, details.getDefinition(), registries);
        IPatternDetails.IInput[] inputs = details.getInputs();
        updateInt(digest, inputs.length);
        for (IPatternDetails.IInput input : inputs) {
            updateString(digest, input.getClass().getName());
            updateLong(digest, input.getMultiplier());
            GenericStack[] possibleInputs = input.getPossibleInputs();
            updateInt(digest, possibleInputs.length);
            for (GenericStack possibleInput : possibleInputs) {
                updateStack(digest, possibleInput, registries);
                AEKey remainingKey = input.getRemainingKey(possibleInput.what());
                updateBoolean(digest, remainingKey != null);
                if (remainingKey != null) {
                    updateKey(digest, remainingKey, registries);
                }
            }
        }
        List<GenericStack> outputs = details.getOutputs();
        updateInt(digest, outputs.size());
        for (GenericStack output : outputs) {
            updateStack(digest, output, registries);
        }
        return HEX_FORMAT.formatHex(digest.digest());
    }

    private static String stack(GenericStack stack, HolderLookup.Provider registries) {
        return key(stack.what(), registries) + '@' + stack.amount();
    }

    private static String key(AEKey key, HolderLookup.Provider registries) {
        return key.toTagGeneric(registries).toString();
    }

    private static void updateStack(MessageDigest digest, GenericStack stack, HolderLookup.Provider registries) {
        updateKey(digest, stack.what(), registries);
        updateLong(digest, stack.amount());
    }

    private static void updateKey(MessageDigest digest, AEKey key, HolderLookup.Provider registries) {
        updateString(digest, key.toTagGeneric(registries).toString());
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateBoolean(MessageDigest digest, boolean value) {
        digest.update(value ? (byte) 1 : (byte) 0);
    }

    private static void updateLong(MessageDigest digest, long value) {
        digest.update((byte) (value >>> 56));
        digest.update((byte) (value >>> 48));
        digest.update((byte) (value >>> 40));
        digest.update((byte) (value >>> 32));
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static String hashEntries(Collection<String> entries) {
        MessageDigest digest = newDigest();
        for (String entry : entries) {
            byte[] bytes = entry.getBytes(StandardCharsets.UTF_8);
            updateInt(digest, bytes.length);
            digest.update(bytes);
        }
        return HEX_FORMAT.formatHex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(DIGEST_ALGORITHM);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(DIGEST_ALGORITHM + " is unavailable", exception);
        }
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static Result unavailable(long startedNanos) {
        return new Result(UNAVAILABLE, 0, System.nanoTime() - startedNanos);
    }

    public record Result(String value, int entries, long nanos) {}

    public record PlanResult(Result fingerprint, int patternCacheHits, int patternCacheMisses) {}

    public record AnalyzedProgramResult(String value, boolean unavailable,
                                        int patternCacheHits, int patternCacheMisses) {}
}
