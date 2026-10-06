package org.gtlcore.gtlcore.mixin.extendedae;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.EncodedPatternItem;
import appeng.menu.SlotSemantics;
import com.glodblock.github.extendedae.common.me.itemhost.HostPatternModifier;
import com.glodblock.github.extendedae.container.ContainerPatternModifier;
import com.glodblock.github.extendedae.util.Ae2Reflect;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import org.gtlcore.gtlcore.integration.ae2.pattern.NativePatternModifierAmounts;

@Mixin(ContainerPatternModifier.class)
public abstract class ContainerPatternModifierMixin {

    @Unique
    private static final int GTLCORE$SCOPE_ALL = 0;
    @Unique
    private static final int GTLCORE$SCOPE_INPUTS = 1;
    @Unique
    private static final int GTLCORE$SCOPE_OUTPUTS = 2;

    @ModifyConstant(method = "checkModify", remap = false, constant = @Constant(longValue = 999999L))
    private long modifyContainer(long constant) {
        return Integer.MAX_VALUE;
    }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lcom/glodblock/github/extendedae/common/me/itemhost/HostPatternModifier;)V", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$registerActions(int id, Inventory playerInventory, HostPatternModifier host,
                                         CallbackInfo ci) {
        var self = (ContainerPatternModifier) (Object) this;
        self.getActionMap().put("modify", o -> {
            if (o.getParaAmount() != 2 && o.getParaAmount() != 3) return;
            Object scale = o.getSoft(0), divide = o.getSoft(1);
            Object scope = o.getParaAmount() == 3 ? o.getSoft(2) : GTLCORE$SCOPE_ALL;
            if (scale instanceof Integer value && divide instanceof Boolean div && scope instanceof Integer area)
                gtlcore$modify(value, div, area);
        });
        self.getActionMap().put("gtlReplace", o -> {
            if (o.getParaAmount() == 1 && o.getSoft(0) instanceof Boolean force) gtlcore$replace(force);
        });
        self.getActionMap().put("swapReplace", o -> gtlcore$swapReplace());
    }

    @Inject(method = "setPage", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$onSetPage(int page, CallbackInfo ci) {
        // 修复上游 bug：切换页面后槽位内容不重新同步，导致样板与标记不显示
        var self = (ContainerPatternModifier) (Object) this;
        if (!self.getPlayer().level().isClientSide) {
            self.sendAllDataToRemote();
        }
    }

    @Unique
    private void gtlcore$swapReplace() {
        var self = (ContainerPatternModifier) (Object) this;
        var target = self.replaceTarget.getItem();
        var with = self.replaceWith.getItem();
        self.replaceTarget.set(with);
        self.replaceWith.set(target);
    }

    /**
     * @author GTLCore
     * @reason 统一走带强制模式参数的新替换逻辑，旧动作默认关闭强制模式
     */
    @Overwrite(remap = false)
    public void replace() {
        gtlcore$replace(false);
    }

    @Unique
    private void gtlcore$modify(int scale, boolean div, int scope) {
        if (scale <= 0 || scope < GTLCORE$SCOPE_ALL || scope > GTLCORE$SCOPE_OUTPUTS) {
            return;
        }
        var self = (ContainerPatternModifier) (Object) this;
        for (var slot : self.getSlots(SlotSemantics.ENCODED_PATTERN)) {
            var stack = slot.getItem();
            if (stack.getItem() instanceof EncodedPatternItem pattern) {
                var detail = pattern.decode(AEItemKey.of(stack), self.getPlayer().level());
                if (detail instanceof AEProcessingPattern process) {
                    try {
                        var input = process.getSparseInputs().toArray(GenericStack[]::new);
                        var output = process.getOutputs().toArray(GenericStack[]::new);
                        boolean modifyInput = scope != GTLCORE$SCOPE_OUTPUTS;
                        boolean modifyOutput = scope != GTLCORE$SCOPE_INPUTS;
                        if ((!modifyInput || gtlcore$checkModify(input, scale, div)) &&
                                (!modifyOutput || gtlcore$checkModify(output, scale, div))) {
                            var mulInput = modifyInput ? gtlcore$modifyStacks(input, scale, div) : input;
                            var mulOutput = modifyOutput ? gtlcore$modifyStacks(output, scale, div) : output;
                            slot.set(gtlcore$encodeProcessingPreservingMeta(stack, gtlcore$compact(mulInput),
                                    gtlcore$compact(mulOutput)));
                        }
                    } catch (IllegalArgumentException | ArithmeticException invalid) {
                        // Keep the original pattern when a merged or scaled quantity is invalid.
                    }
                }
            }
        }
    }

    @Unique
    private boolean gtlcore$checkModify(GenericStack[] stacks, int scale, boolean div) {
        return NativePatternModifierAmounts.canScale(stacks, scale, div);
    }

    /**
     * 压缩稀疏数组（去除 null 槽位）并合并同 key 条目（数量求和、保持首次出现顺序）：
     * 编码终端生成的样板 in/out 列表被填充至网格大小（81/27），
     * 直接追加或替换产生的重复条目会导致超出 AE2 的 MAX_INPUT_SLOTS 上限而被判为无效样板。
     */
    @Unique
    private GenericStack[] gtlcore$compact(GenericStack[] stacks) {
        return NativePatternModifierAmounts.compact(stacks);
    }

    @Unique
    private ItemStack gtlcore$encodeProcessingPreservingMeta(ItemStack original, GenericStack[] inputs,
                                                             GenericStack[] outputs) {
        var newPattern = PatternDetailsHelper.encodeProcessingPattern(Arrays.asList(inputs), Arrays.asList(outputs));
        gtlcore$copyNonStructuralTags(original, newPattern);
        return newPattern;
    }

    /** 重编码后保留名称、自定义组件和 custom_data 元数据。 */
    @Unique
    private void gtlcore$copyNonStructuralTags(ItemStack original, ItemStack newPattern) {
        NativePatternModifierAmounts.copyMetadata(original, newPattern);
    }

    @Unique
    private GenericStack[] gtlcore$modifyStacks(GenericStack[] stacks, int scale, boolean div) {
        var des = new GenericStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i] != null) {
                long amt = div ? stacks[i].amount() / scale : stacks[i].amount() * scale;
                des[i] = new GenericStack(stacks[i].what(), amt);
            }
        }
        return des;
    }

    @Unique
    private boolean gtlcore$insertLimitExceeded;

    @Unique
    private void gtlcore$replace(boolean force) {
        var self = (ContainerPatternModifier) (Object) this;
        gtlcore$insertLimitExceeded = false;
        var targetStack = GenericStack.fromItemStack(self.replaceTarget.getItem());
        var withStack = GenericStack.fromItemStack(self.replaceWith.getItem());
        if (targetStack == null && withStack == null) {
            return;
        }
        AEKey targetKey = targetStack == null ? null : targetStack.what();
        AEKey withKey = withStack == null ? null : withStack.what();
        long targetAmt = targetStack == null ? 0 : targetStack.amount();
        long withAmt = withStack == null ? 0 : withStack.amount();
        boolean deleteMode = withKey == null || withAmt <= 0;
        boolean insertMode = targetKey == null || targetAmt <= 0;
        if (targetKey == null) {
            // 左槽留空：仅强制模式下允许纯插入
            if (!force || deleteMode) {
                return;
            }
        } else if ((!force && (deleteMode || insertMode)) || (deleteMode && insertMode)) {
            return;
        }
        for (var slot : self.getSlots(SlotSemantics.ENCODED_PATTERN)) {
            var stack = slot.getItem();
            if (stack.getItem() instanceof EncodedPatternItem pattern) {
                var detail = pattern.decode(AEItemKey.of(stack), self.getPlayer().level());
                if (detail instanceof AEProcessingPattern process) {
                    try {
                        // 压缩并合并同 key 条目：去除占位槽，替换/插入产生的重复条目也会合并
                        var replaceInput = gtlcore$compact(gtlcore$replace(process.getSparseInputs().toArray(GenericStack[]::new), targetKey, withKey,
                                targetAmt, withAmt, deleteMode));
                        var replaceOutput = gtlcore$compact(gtlcore$replace(process.getOutputs().toArray(GenericStack[]::new), targetKey, withKey,
                                targetAmt, withAmt, deleteMode));
                        if (insertMode) {
                            // 已有同 key 条目则就地合并；新增条目受 AE2 输入槽上限约束，超出则跳过该样板
                            boolean merged = false;
                            for (int i = 0; i < replaceInput.length; i++) {
                                if (replaceInput[i].what().equals(withKey)) {
                                    replaceInput[i] = new GenericStack(withKey, Math.addExact(replaceInput[i].amount(), withAmt));
                                    merged = true;
                                    break;
                                }
                            }
                            if (!merged) {
                                if (replaceInput.length >= AEProcessingPattern.MAX_INPUT_SLOTS) {
                                    gtlcore$insertLimitExceeded = true;
                                    continue;
                                }
                                replaceInput = Arrays.copyOf(replaceInput, replaceInput.length + 1);
                                replaceInput[replaceInput.length - 1] = new GenericStack(withKey, withAmt);
                            }
                        }
                        slot.set(gtlcore$encodeProcessingPreservingMeta(stack, replaceInput, replaceOutput));
                    } catch (Exception e) {
                        // It is an invalid change
                    }
                } else if (!insertMode && detail instanceof AECraftingPattern craft &&
                        targetKey instanceof AEItemKey && (withKey == null || withKey instanceof AEItemKey)) {
                            try {
                                    var replaceInput = gtlcore$replace(craft.getSparseInputs().toArray(GenericStack[]::new), targetKey, withKey, targetAmt, withAmt,
                                            deleteMode);
                                    var newPattern = PatternDetailsHelper.encodeCraftingPattern(
                                            Ae2Reflect.getCraftRecipe(craft),
                                            gtlcore$itemize(replaceInput),
                                            gtlcore$itemize(craft.getPrimaryOutput()),
                                            craft.canSubstitute,
                                            craft.canSubstituteFluids);
                                    // noinspection DataFlowIssue
                                    var check = new AECraftingPattern(AEItemKey.of(newPattern), self.getPlayer().level());
                                    // noinspection ConstantValue
                                    if (check != null) {
                                        gtlcore$copyNonStructuralTags(stack, newPattern);
                                        slot.set(newPattern);
                                    }
                                } catch (Exception e) {
                                    // It is an invalid change
                                }
                            }
            }
        }
        if (gtlcore$insertLimitExceeded) {
            self.getPlayer().displayClientMessage(
                        Component.translatable("message.gtlcore.pattern_modifier.insert_limit_exceeded"), true);
        }
    }

    @Unique
    private GenericStack[] gtlcore$replace(GenericStack[] stacks, @Nullable AEKey target, @Nullable AEKey with,
                                               long targetAmt, long withAmt, boolean deleteMode) {
        var des = new GenericStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i] != null) {
                if (targetAmt > 0 && stacks[i].what().equals(target)) {
                        if (deleteMode) {
                            des[i] = null;
                        } else {
                            long newAmt = NativePatternModifierAmounts.replacement(stacks[i].amount(), withAmt, targetAmt);
                            des[i] = new GenericStack(with, newAmt);
                        }
                } else {
                        des[i] = new GenericStack(stacks[i].what(), stacks[i].amount());
                }
            }
        }
        return des;
    }

    @Unique
    private ItemStack[] gtlcore$itemize(GenericStack[] stacks) {
        var items = new ItemStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            items[i] = gtlcore$itemize(stacks[i]);
        }
        return items;
    }

    @Unique
    private ItemStack gtlcore$itemize(@Nullable GenericStack stack) {
        if (stack != null && stack.what() instanceof AEItemKey what) {
            return what.toStack((int) stack.amount());
        } else {
            return ItemStack.EMPTY;
        }
    }
}
