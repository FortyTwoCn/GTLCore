#!/usr/bin/env python3
"""Inventory source/resource compatibility without claiming runtime registration coverage."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess

DIRECTORIES = {
    "recipes": "recipe", "loot_tables": "loot_table", "advancements": "advancement",
    "predicates": "predicate", "item_modifiers": "item_modifier", "structures": "structure",
}
TAG_DIRECTORIES = {
    "items": "item", "blocks": "block", "fluids": "fluid",
    "entity_types": "entity_type", "game_events": "game_event",
}
UI_LABELS_PATH = Path(__file__).with_name("native-ui-labels.json")
NATIVE_UI_LABELS = json.loads(UI_LABELS_PATH.read_text())

TEXTURE_PROFILE_PATH = Path(__file__).with_name("native-textures.json")
NATIVE_TEXTURES = json.loads(TEXTURE_PROFILE_PATH.read_text())["textures"]


def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args])


def normalize_resource(path, content):
    if path in NATIVE_TEXTURES:
        from PIL import Image
        expected = NATIVE_TEXTURES[path]
        file_hash = hashlib.sha256(content).hexdigest()
        if file_hash in {expected["original_sha256"], expected["png_sha256"]}:
            with Image.open(io.BytesIO(content)) as image:
                pixels = hashlib.sha256(image.convert("RGBA").tobytes()).hexdigest()
                if image.size != (expected["width"], expected["height"]) or pixels != expected["decoded_rgba_sha256"]:
                    raise ValueError("Lossless texture conversion changed original pixels: " + path)
            return path, pixels
    # This recorded shader signature changes with Minecraft's native fog helper.
    if path == "src/main/resources/assets/gtlcore/shaders/core/harmony_animation.vsh":
        shader = re.sub(rb"//[^\n]*", b"", content)
        shader = shader.replace(b"fog_distance(ModelViewMat, pos, FogShape)", b"fog_distance(pos, FogShape)")
        content = re.sub(rb"\s+", b"", shader)
    parts = path.split("/")
    if "data" in parts:
        start = parts.index("data")
        if len(parts) > start + 2:
            parts[start + 2] = DIRECTORIES.get(parts[start + 2], parts[start + 2])
        if len(parts) > start + 3 and parts[start + 2] == "tags":
            parts[start + 3] = TAG_DIRECTORIES.get(parts[start + 3], parts[start + 3])
    if path.endswith(".json"):
        value = json.loads(content)
        # Exact native dependency resource moves; all other IDs still affect the digest.
        def native_aliases(node):
            if isinstance(node, dict):
                return {("forge_data" if key == "neoforge_data" else key): native_aliases(item) for key, item in node.items()}
            if isinstance(node, list):
                return [native_aliases(item) for item in node]
            if isinstance(node, str):
                textures = {"avaritia:item/resource/infinity/infinity_ingot": "avaritia:item/resource/infinity_ingot",
                            "avaritia:item/resource/neutron/neutron_ingot": "avaritia:item/resource/neutron_ingot",
                            "avaritia:item/resource/neutron/neutron_nugget": "avaritia:item/resource/neutron_nugget"}
                if node in textures: return textures[node]
                if node in {"extendedae:assembler_matrix_frame", "extendedae:assembler_matrix_glass", "extendedae:assembler_matrix_wall"}:
                    return node.replace("extendedae:", "expatternprovider:", 1)
            return node
        value = native_aliases(value)
        if isinstance(value, dict) and re.search(r"/assets/[^/]+/lang/(en_us|zh_cn)\.json$", path):
            # Added native recipe labels must be exact aliases of the existing translation.
            for key in list(value):
                match = re.fullmatch(r"recipe_(?:type|category)\.([a-z0-9_.-]+)\.([a-z0-9_./-]+)", key)
                if match and value.get(match[1] + "." + match[2]) == value[key]:
                    del value[key]
            # These two Additions types had no legacy labels; verify their exact new text.
            locale = Path(path).stem
            new_labels = {
                "recipe_type.gtceu.universe_sandbox": {"en_us": "Universe Sandbox", "zh_cn": "宇宙沙盒"},
                "recipe_type.gtceu.transmutation_block_conversion": {"en_us": "Transmutation Block Conversion", "zh_cn": "转化方块转换"},
            }
            new_labels.update({key.replace("recipe_type.", "recipe_category.", 1): labels for key, labels in list(new_labels.items())})
            if path.startswith("src/main/resources/assets/gtceu/lang/"):
                for key, labels in new_labels.items():
                    if value.get(key) == labels[locale]:
                        del value[key]
            if path.startswith("src/main/resources/assets/gtlcore/lang/"):
                # Only these individually recorded native UI additions are excluded.
                # Existing translations and unexpected new/changed text still affect the digest.
                for key, labels in NATIVE_UI_LABELS.items():
                    if value.get(key) == labels[locale]:
                        del value[key]
        # NeoForge 21.1.248 registers the same OBJ format under neoforge:obj.
        if isinstance(value, dict) and value.get("loader") == "neoforge:obj":
            value["loader"] = "forge:obj"
        if isinstance(value, dict) and value.get("type") in {
            "minecraft:crafting_shaped", "minecraft:crafting_shapeless"
        } and isinstance(value.get("result"), dict) and "id" in value["result"]:
            value["result"]["item"] = value["result"].pop("id")
        content = json.dumps(value, sort_keys=True, ensure_ascii=False).encode()
    return "/".join(parts), hashlib.sha256(content).hexdigest()


def inventory(files):
    resources = {}
    literals = set()
    for path, content in files:
        if any(path.startswith(prefix) for prefix in (
            "src/main/resources/assets/", "src/main/resources/data/",
            "src/generated/resources/assets/", "src/generated/resources/data/",
        )):
            key, digest = normalize_resource(path, content)
            if key in resources:
                raise ValueError("Resource collision: " + key)
            resources[key] = digest
        if path.startswith("src/main/java/") and path.endswith((".java", ".kt")):
            text = content.decode("utf-8-sig")
            literals.update(re.findall(r'\b(?:register|registerAlias|recipeType|multiblock|machine|block|item|cover)\(\s*"([^"]+)"', text))
            literals.update(re.findall(r'\bNativeMachineBuilders\.machine\(\s*[^,]+,\s*"([^"]+)"', text))
    return {"resources": resources, "registration_literals": sorted(literals)}


def source_files(root):
    for base in ("src/main/java", "src/main/resources", "src/generated/resources"):
        for path in (root / base).rglob("*"):
            if path.is_file():
                yield path.relative_to(root).as_posix(), path.read_bytes()


def baseline_inventory(root, revision):
    paths = git(root, "ls-tree", "-r", "--name-only", revision, "src/main/java", "src/main/resources", "src/generated/resources").decode().splitlines()
    # One cat-file process avoids thousands of shell/network calls and never reads credentials.
    batch = subprocess.run(
        ["git", "-C", str(root), "cat-file", "--batch"],
        input="".join(f"{revision}:{path}\n" for path in paths).encode(),
        stdout=subprocess.PIPE, check=True,
    ).stdout
    offset = 0
    files = []
    for path in paths:
        end = batch.index(b"\n", offset)
        size = int(batch[offset:end].split()[-1])
        offset = end + 1
        files.append((path, batch[offset:offset + size]))
        offset += size + 1
    return inventory(files)


def source_audit(root, reference):
    target_sources = reference / "src/main/java"
    legacy = {}
    missing = {}
    item_nbt = {}
    ldlib = []
    for path, content in source_files(root):
        if not path.endswith((".java", ".kt")):
            continue
        text = content.decode("utf-8-sig")
        if "com.lowdragmc.lowdraglib" in text:
            ldlib.append(path)
        for number, line in enumerate(text.splitlines(), 1):
            if "net.minecraftforge" in line or "net/minecraftforge" in line:
                legacy.setdefault(path, []).append(number)
            if re.search(r'\.(?:getOrCreateTag|getTag|setTag)\(', line):
                item_nbt.setdefault(path, []).append(number)
        for imported in re.findall(r'^import (?:static )?(com\.gregtechceu\.gtceu\.[\w.*]+)', text, re.M):
            parts = imported.split(".")
            if parts[-1] == "*":
                available = ((target_sources / "/".join(parts[:-1])).is_dir()
                             or any((target_sources / ("/".join(parts[:i]) + ".java")).is_file()
                                    for i in range(len(parts) - 1, 3, -1)))
            else:
                available = any((target_sources / ("/".join(parts[:i]) + ".java")).is_file()
                                for i in range(len(parts), 3, -1))
            if not available:
                missing.setdefault(imported, []).append(path)
    return {"legacy_forge_references": legacy, "missing_gtceu_imports": missing,
            "ldlib_consumers_requiring_review": ldlib, "item_nbt_calls_requiring_review": item_nbt}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--reference", type=Path, default=Path(__file__).resolve().parents[2] / "GregTech-1.21")
    parser.add_argument("--baseline", required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    previous = baseline_inventory(args.root, args.baseline)
    current = inventory(source_files(args.root))
    missing_resources = sorted(set(previous["resources"]) - set(current["resources"]))
    changed_resources = sorted(path for path in previous["resources"] if path in current["resources"]
                               and previous["resources"][path] != current["resources"][path])
    recorded_path = args.root / 'migration/native-resource-transforms.json'
    recorded = json.loads(recorded_path.read_text()) if recorded_path.is_file() else {}
    if recorded and recorded['baseline_revision'] != args.baseline:
        raise ValueError('Recorded native resource transforms belong to a different source baseline')
    reviewed = recorded.get('resources', {})
    accepted = [path for path in changed_resources if path in reviewed
                and reviewed[path]['original_normalized_sha256'] == previous['resources'][path]
                and reviewed[path]['native_normalized_sha256'] == current['resources'][path]]
    unexpected_resources = sorted(set(changed_resources) - set(accepted))
    missing_literals = sorted(set(previous["registration_literals"]) - set(current["registration_literals"]))
    report = {"baseline_revision": args.baseline,
              "gtceu_revision": git(args.reference, "rev-parse", "HEAD").decode().strip(),
              "resource_count": len(current["resources"]),
              "baseline_registration_literal_count": len(previous["registration_literals"]),
              "missing_resources": missing_resources, "changed_resources": changed_resources,
              "reviewed_native_resource_changes": accepted, "unexpected_resource_changes": unexpected_resources,
              "missing_registration_literals": missing_literals,
              "allowed_resource_transforms": ["1.21 singular data paths", "crafting result item to id", "forge:obj to neoforge:obj", "verified native recipe translation aliases and two missing Additions labels", "individually recorded native UI and Jade configuration translations"],
              "limitations": "Static literals do not establish runtime registry IDs, recipe counts, machine behavior or world compatibility.",
              **source_audit(args.root, args.reference)}
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({key: (len(value) if isinstance(value, (dict, list)) else value)
                      for key, value in report.items() if key != "limitations"}, ensure_ascii=False, indent=2))
    # Preserve-content failures are separate from unresolved API migration work.
    raise SystemExit(1 if missing_resources or unexpected_resources or missing_literals else 0)


if __name__ == "__main__":
    main()
