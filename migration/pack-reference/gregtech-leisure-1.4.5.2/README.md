# GregTech Leisure runtime content reference

Source: [CurseForge file 8903963](https://www.curseforge.com/minecraft/modpacks/gregtech-leisure/files/8903963), GregTech Leisure 1.4.5.2 by maple197. The included LICENSE is CC BY-NC-SA 4.0. Native adaptations retain this license; changes are marked in each script.

This archive bundles Core 1.2.3.1-fix9, while the migration source is skyblock Core 1.2.3.2-fix3. It supplies real pack-owned registration definitions and textures, including force field glass, coils and research items. It does not establish exact pack-version parity.

`original/` preserves the downloaded registration scripts. `native/` adapts their tags and armor material/item components to KubeJS 2101 and Minecraft 1.21.1. These scripts are not packaged into the Core JAR. The complete pack server/client scripts, world data and optional mods still need a separate integration pass; a smoke run using this content slice must not be reported as a complete pack migration.

Prepare a native development profile from the original archive with the checked script:

```sh
python3 migration/pack-reference/prepare_native_profile.py \
  --archive /path/to/GregTech-Leisure-1.4.5.2.zip \
  --game-directory ../GTLAdditions/migration/full-client \
  --include-configs
```

The script verifies the archive SHA-256 and the preserved startup scripts, then installs 1,037 original assets and the two native registration scripts. It adapts known changed AE2, Avaritia and ExtendedAE resource paths and NeoForge model metadata while retaining the original textures. `--include-configs` additionally copies the original GTCEu and GTLCore configurations. Existing files must match either the original archive or this native adaptation; other local edits are left intact and cause preparation to stop. Every copied file and its original/native checksum is recorded in the profile manifest.

For the client validation fixture, `--seed-world /path/to/native-world` copies an existing **1.21.1** world to the expected validation name. This is a new-version save/reload fixture. Old 1.20.1 save compatibility is outside the requested scope.
