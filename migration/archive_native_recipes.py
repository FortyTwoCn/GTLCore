#!/usr/bin/env python3
"""Archive a successful scoped recipe/world run, its exact inputs, and independent client evidence."""
from pathlib import Path
import argparse, datetime, fnmatch, hashlib, json, re, shutil, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / 'migration/recipe-tests'
DEST = ROOT / 'migration/validation/native-recipes'

def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def inputs():
    build = (PROJECT / 'build.gradle').read_text()
    patterns = re.findall(r"include '([^']+)'", build)
    candidates = set()
    for base in [ROOT / 'src/main/java', ROOT.parent / 'GTLAdditions/src/main/java', PROJECT / 'src/main/java']:
        for path in base.rglob('*'):
            if path.is_file() and any(fnmatch.fnmatch(path.relative_to(base).as_posix(), pattern) for pattern in patterns): candidates.add(path)
    for base in [PROJECT / 'src/main/resources', PROJECT / 'src/test', ROOT / 'migration/dependencies/gtceu', ROOT / 'migration/dependencies/gtmthings', ROOT / 'migration/dependencies/ae2wtlib', ROOT / 'migration/dependencies/ftbultimine', ROOT / 'migration/dependencies/ftbranks']:
        candidates.update(path for path in base.rglob('*') if path.is_file() and 'build' not in path.relative_to(base).parts and '.gradle' not in path.relative_to(base).parts)
    candidates.add(ROOT / 'migration/dependencies/ftb-native.gradle')
    for name in ['FTB-Ultimine-1.21', 'FTB-Ranks-1.21']:
        native = ROOT.parent / name
        for relative in ['common/src', 'neoforge/src']:
            candidates.update(path for path in (native / relative).rglob('*') if path.is_file())
        candidates.add(native / 'gradle.properties')
    for base in [ROOT / 'src/main/resources/assets/gtceu/lang', ROOT / 'src/main/resources/assets/gtlcore/lang',
                 ROOT.parent / 'GTLAdditions/src/main/resources/assets/gtceu/lang', ROOT.parent / 'GTLAdditions/src/main/resources/assets/gtladditions/lang']:
        candidates.update(base.glob('*.json'))
    candidates.update((ROOT / 'src/main/resources/data/gtceu/gtceu/bedrock_fluid').glob('*.json'))
    for relative in ['build.gradle', 'settings.gradle', 'gradle.properties', 'gradle/forge.versions.toml', 'gradle/libs.versions.toml',
                     'gradle/repositories.gradle', 'migration/recipe-tests/build.gradle', 'migration/recipe-tests/settings.gradle', 'migration/recipe-tests/gradle.properties',
                     'migration/recipe-types.json', 'migration/structure-templates.json', 'migration/native-ae-refresh-api.json',
                     'migration/native-ui-labels.json', 'migration/bedrock-fluids.json', 'migration/native-hpca-api.json',
                     'migration/native-avaritia-api.json', 'gradle/native-avaritia-validation.gradle',
                     'migration/native-adastra-api.json', 'gradle/native-common-storage-api.gradle',
                     'src/main/resources/assets/ae2/screens/gtlcore_set_replace_amount.json',
                     'src/main/resources/assets/gtlcore/textures/guis/states.png',
                     'migration/native-textures.json', 'migration/generate_native_animation_assets.py',
                     'src/main/java/org/gtlcore/gtlcore/common/data/material/MaterialBuilder.java',
                     'src/main/java/org/gtlcore/gtlcore/GTLGTAddon.java',
                     'migration/native-recipe-search-api.json', 'migration/native-maintenance-api.json', 'src/main/java/org/gtlcore/gtlcore/common/CommonProxy.java',
                     'migration/archive_native_recipes.py']:
        candidates.add(ROOT / relative)
    for relative in ['src/main/resources/assets/gtlcore/models/item/virtual_ingredient.json',
                     'src/main/resources/assets/gtlcore/textures/item/virtual_ingredient.png',
                     'src/main/resources/META-INF/licenses/wildcard-pattern-MIT.txt']:
        candidates.add(ROOT / relative)
    candidates.add(ROOT.parent / 'GTLAdditions/migration/recipe-types.json')
    candidates.add(ROOT.parent / 'GTLAdditions/migration/native-textures.json')
    for relative in ['assets/gtlcore/models/obj', 'assets/gtlcore/textures/block/obj']:
        candidates.update(path for path in (ROOT / 'src/main/resources' / relative).rglob('*') if path.is_file())
    candidates.update((ROOT / 'src/main/resources/assets/gtlcore/shaders').rglob('harmony_animation.*'))
    candidates.update((ROOT / 'src/main/resources/assets/gtlcore/textures/block/casings').glob('dimensionally_transcendent*'))
    candidates.add(ROOT / 'src/main/resources/assets/gtlcore/textures/block/space_elevator_mechanical_casing.png')
    for kind in ['blockstates', 'models/block/machine', 'models/item']:
        for name in ['annihilate_generator', 'eye_of_harmony', 'space_elevator']:
            candidates.add(ROOT / 'src/main/resources/assets/gtceu' / kind / (name + '.json'))
    for relative in ['assets/gtladditions/textures/block/obj/heartofuniverse.png', 'assets/gtladditions/textures/block/multiblock/forge_of_antichrist/space_layer.png', 'assets/gtladditions/textures/block/multiblock/forge_of_antichrist/star_layer_0.png']:
        candidates.add(ROOT.parent / 'GTLAdditions/src/main/resources' / relative)
    for relative in ['assets/gtceu/models/item', 'assets/gtceu/textures/item']:
        candidates.update(path for path in (ROOT / 'src/main/resources' / relative).rglob('*') if path.is_file())
    candidates.update((ROOT / 'src/main/resources/assets/gtlcore/textures/gui/preview').glob('*.png'))
    candidates.update((ROOT / 'src/main/resources/assets/gtlcore/textures/gui/wireless').glob('*.png'))
    candidates.add(ROOT.parent / 'GTLAdditions/src/main/resources/assets/gtladditions/textures/gui/matter_exotic_catalyst.png')
    return {path.relative_to(ROOT.parent).as_posix(): digest(path) for path in sorted(candidates) if path.is_file()}

def archive_item_menus_client(gradle_log, source):
    marker = 'GTL_NATIVE_ITEM_MENUS=PASS menus=6 offhand=true page_c2s=true rotation_c2s=true stock_s2c=true'
    client = gradle_log.read_text()
    minecraft_log = PROJECT / 'run/client/logs/latest.log'
    if 'BUILD SUCCESSFUL' not in client or marker not in client or marker not in minecraft_log.read_text():
        raise SystemExit('Current native item menu client run was not confirmed')
    target = DEST / 'item-menus-client'; target.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(gradle_log, target / 'gradle-client.log')
    shutil.copyfile(minecraft_log, target / 'minecraft-client.log')
    for name in ['dual-stock', 'structure-writer', 'pattern-generator', 'ultimate-terminal', 'pattern-box', 'pattern-modifier']:
        filename = 'native-menu-' + name + '.png'
        shutil.copyfile(PROJECT / 'run/client/screenshots' / filename, target / filename)
    (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')

def archive_wildcard_client(gradle_log, source):
    marker = 'GTL_NATIVE_WILDCARD_CLIENT=PASS menus=2 long_components=true rotation_s2c=true readonly_preview=true visibility_c2s_s2c=true rename_c2s_s2c=true pages=4 last_config=true stock_reload=true interval_c2s=true bounded=true fluid_tooltip=true'
    client = gradle_log.read_text()
    minecraft_log = PROJECT / 'run/client/logs/latest.log'
    if 'BUILD SUCCESSFUL' not in client or marker not in client or marker not in minecraft_log.read_text():
        raise SystemExit('Current native wildcard client run was not confirmed')
    target = DEST / 'wildcard-client'; target.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(gradle_log, target / 'gradle-client.log')
    shutil.copyfile(minecraft_log, target / 'minecraft-client.log')
    for name in ['buffer', 'fluid-tooltip', 'rename', 'stocking', 'stock-last-page', 'refresh']:
        filename = 'native-wildcard-' + name + '.png'
        shutil.copyfile(PROJECT / 'run/client/screenshots' / filename, target / filename)
    (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')

def archive_gt_output_client(gradle_log, source):
    marker = 'GTL_NATIVE_GT_OUTPUT_CLIENT=PASS menus=3 keys_per_list=12 long_components_s2c=true readonly=true scroll_last=true exact_hover=true return_click_c2s_s2c=true interval_c2s_s2c=true bounded=true'
    minecraft_log = PROJECT / 'run/client/logs/latest.log'
    if 'BUILD SUCCESSFUL' not in gradle_log.read_text() or marker not in gradle_log.read_text() or marker not in minecraft_log.read_text():
        raise SystemExit('Current native GT output client run was not confirmed')
    target = DEST / 'gt-output-client'; target.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(gradle_log, target / 'gradle-client.log')
    shutil.copyfile(minecraft_log, target / 'minecraft-client.log')
    for machine in ['item', 'fluid', 'dual']:
        for stage in ['main', 'last', 'refresh']:
            filename = 'native-gt-output-' + machine + '-' + stage + '.png'
            shutil.copyfile(PROJECT / 'run/client/screenshots' / filename, target / filename)
    (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')

def archive_super_pattern_client(gradle_log, source):
    marker = 'GTL_NATIVE_SUPER_PATTERN_CLIENT=PASS menus=3 default_slots=162 max_slots=1800 min_slots=1 foa_c2s_s2c=true page_c2s_s2c=true scroll_both_axes=true last_middle_popup=true component_pattern=true output_preview=true bounded=true'
    minecraft_log = PROJECT / 'run/client/logs/latest.log'
    if 'BUILD SUCCESSFUL' not in gradle_log.read_text() or marker not in gradle_log.read_text() or marker not in minecraft_log.read_text():
        raise SystemExit('Current native super pattern client run was not confirmed')
    target = DEST / 'super-pattern-client'; target.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(gradle_log, target / 'gradle-client.log')
    shutil.copyfile(minecraft_log, target / 'minecraft-client.log')
    for variant in ['default', 'wide', 'tiny']:
        stages = ['main', 'last'] + (['catalysts'] if variant != 'tiny' else []) + (['foa'] if variant == 'default' else [])
        for stage in stages:
            filename = 'native-super-pattern-' + variant + '-' + stage + '.png'
            shutil.copyfile(PROJECT / 'run/client/screenshots' / filename, target / filename)
    (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')

def archive_fluid_rig_client(gradle_log, source):
    marker = 'GTL_NATIVE_FLUID_RIG_CLIENT=PASS menus=3 long_display_s2c=true readonly=true physical_power_c2s_s2c=true one_paid_cycle=true controller_reload=true bounded=true title_bounds=true'
    minecraft_log = PROJECT / 'run/client/logs/latest.log'
    if 'BUILD SUCCESSFUL' not in gradle_log.read_text() or marker not in gradle_log.read_text() or marker not in minecraft_log.read_text():
        raise SystemExit('Current native fluid rig client run was not confirmed')
    target = DEST / 'fluid-rig-client'; target.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(gradle_log, target / 'gradle-client.log')
    shutil.copyfile(minecraft_log, target / 'minecraft-client.log')
    for variant in ['normal', 'overclocked', 'long']:
        for stage in ['ready', 'completed', 'reloaded']:
            filename = 'native-fluid-rig-' + variant + '-' + stage + '.png'
            shutil.copyfile(PROJECT / 'run/client/screenshots' / filename, target / filename)
    (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')

def archive_advanced_fluid_rig_client(gradle_log, source):
    marker = 'GTL_NATIVE_ADVANCED_FLUID_RIG_CLIENT=PASS readonly_thermal_long_s2c=true physical_fast_c2s_s2c=true physical_component_head=true one_paid_cycle=true controller_reload=true scroll=true wrapped=true bounded=true title_bounds=true'
    minecraft_log = PROJECT / 'run/client/logs/latest.log'
    if 'BUILD SUCCESSFUL' not in gradle_log.read_text() or marker not in gradle_log.read_text() or marker not in minecraft_log.read_text():
        raise SystemExit('Current native advanced fluid rig client run was not confirmed')
    target = DEST / 'advanced-fluid-rig-client'; target.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(gradle_log, target / 'gradle-client.log')
    shutil.copyfile(minecraft_log, target / 'minecraft-client.log')
    for stage in ['ready', 'fast', 'head-removed', 'head-restored', 'completed', 'fluids', 'reloaded']:
        filename = 'native-advanced-fluid-rig-' + stage + '.png'
        shutil.copyfile(PROJECT / 'run/client/screenshots' / filename, target / filename)
    (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')

def archive_ae_widgets_client(gradle_log, source):
    marker = 'GTL_NATIVE_AE_WIDGETS_CLIENT=PASS menus=2 long_components_s2c=true amount_popup_c2s_s2c=true scroll_c2s_s2c=true jei_item_fluid=true stock_hover=true native_stack_display=true fluid_units=true bounded=true'
    minecraft_log = PROJECT / 'run/client/logs/latest.log'
    if 'BUILD SUCCESSFUL' not in gradle_log.read_text() or marker not in gradle_log.read_text() or marker not in minecraft_log.read_text():
        raise SystemExit('Current native AE input widget client run was not confirmed')
    target = DEST / 'ae-widgets-client'; target.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(gradle_log, target / 'gradle-client.log')
    shutil.copyfile(minecraft_log, target / 'minecraft-client.log')
    for name in ['item-config', 'item-stock', 'fluid-config', 'fluid-stock']:
        filename = 'native-ae-input-' + name + '.png'
        shutil.copyfile(PROJECT / 'run/client/screenshots' / filename, target / filename)
    shutil.copyfile(PROJECT / 'run/client/screenshots/native-ae-stack-display.png', target / 'native-ae-stack-display.png')
    (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')

def archive_extendedae_client(gradle_log, source):
    marker = 'GTL_NATIVE_EXTENDEDAE_CLIENT=PASS original_host=true scope_c2s_s2c=true page_c2s_s2c=true middle_click_popup=true typed_long_c2s_s2c=true replace_c2s_s2c=true metadata=true bounded=true'
    minecraft_log = PROJECT / 'run/client/logs/latest.log'
    if 'BUILD SUCCESSFUL' not in gradle_log.read_text() or marker not in gradle_log.read_text() or marker not in minecraft_log.read_text():
        raise SystemExit('Current native ExtendedAE physical client run was not confirmed')
    target = DEST / 'extendedae-client'; target.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(gradle_log, target / 'gradle-client.log')
    shutil.copyfile(minecraft_log, target / 'minecraft-client.log')
    for name in ['scoped', 'replace', 'amount', 'replaced']:
        filename = 'native-extendedae-' + name + '.png'
        shutil.copyfile(PROJECT / 'run/client/screenshots' / filename, target / filename)
    (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')

def archive_prospector_client(gradle_log, source):
    marker = 'GTL_NATIVE_PROSPECTOR_CLIENT=PASS original_item_menu=true native_map=true negative_coordinates=true fixed_opening_anchor=true right_waypoints=true clear_preserves_manual=true teleport_c2s_s2c=true bounded=true'
    minecraft_log = PROJECT / 'run/client/logs/latest.log'
    if 'BUILD SUCCESSFUL' not in gradle_log.read_text() or marker not in gradle_log.read_text() or marker not in minecraft_log.read_text():
        raise SystemExit('Current native prospector physical client run was not confirmed')
    target = DEST / 'prospector-client'; target.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(gradle_log, target / 'gradle-client.log')
    shutil.copyfile(minecraft_log, target / 'minecraft-client.log')
    for name in ['waypoint', 'moved', 'cleared', 'teleported']:
        filename = 'native-prospector-' + name + '.png'
        shutil.copyfile(PROJECT / 'run/client/screenshots' / filename, target / filename)
    (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')

def archive_energy_client(gradle_log, source):
    marker = 'GTL_NATIVE_ENERGY_CLIENT=PASS original_menus=3 dropdown_c2s_s2c=true exact_odd_long=true typed_max_long=true zero_amps=true plus_c2s_s2c=true creative_reload=true monitor_scope_c2s_s2c=true native_rich_rows=true physical_scroll=true native_highlight=true bounded=true'
    minecraft_log = PROJECT / 'run/client/logs/latest.log'
    if 'BUILD SUCCESSFUL' not in gradle_log.read_text() or marker not in gradle_log.read_text() or marker not in minecraft_log.read_text():
        raise SystemExit('Current native energy physical client run was not confirmed')
    target = DEST / 'energy-client'; target.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(gradle_log, target / 'gradle-client.log')
    shutil.copyfile(minecraft_log, target / 'minecraft-client.log')
    filenames = ['native-creative-' + kind + '-' + phase + '.png'
                 for kind in ['energy', 'laser'] for phase in ['tiers', 'odd', 'maximum', 'reload']]
    filenames += ['native-wireless-monitor-' + phase + '.png' for phase in ['team', 'all', 'highlight']]
    for filename in filenames:
        shutil.copyfile(PROJECT / 'run/client/screenshots' / filename, target / filename)
    (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--gradle-log', type=Path, required=True)
    parser.add_argument('--machine-client-gradle-log', type=Path)
    parser.add_argument('--renderer-client-gradle-log', type=Path)
    parser.add_argument('--item-menus-client-gradle-log', type=Path)
    parser.add_argument('--tag-stock-client-gradle-log', type=Path)
    parser.add_argument('--chamber-configurator-client-gradle-log', type=Path)
    parser.add_argument('--avaritia-client-gradle-log', type=Path)
    parser.add_argument('--dynamic-render-client-gradle-log', type=Path)
    parser.add_argument('--dynamic-model-data-gradle-log', type=Path)
    parser.add_argument('--maintenance-client-gradle-log', type=Path)
    parser.add_argument('--preview-client-gradle-log', type=Path)
    parser.add_argument('--recipe-mode-client-gradle-log', type=Path)
    parser.add_argument('--configurator-columns-client-gradle-log', type=Path)
    parser.add_argument('--performance-client-gradle-log', type=Path)
    parser.add_argument('--wildcard-client-gradle-log', type=Path)
    parser.add_argument('--gt-output-client-gradle-log', type=Path)
    parser.add_argument('--super-pattern-client-gradle-log', type=Path)
    parser.add_argument('--fluid-rig-client-gradle-log', type=Path)
    parser.add_argument('--advanced-fluid-rig-client-gradle-log', type=Path)
    parser.add_argument('--ae-widgets-client-gradle-log', type=Path)
    parser.add_argument('--extendedae-client-gradle-log', type=Path)
    parser.add_argument('--prospector-client-gradle-log', type=Path)
    parser.add_argument('--energy-client-gradle-log', type=Path)
    args = parser.parse_args()
    log = args.gradle_log.read_text()
    expected = sum(path.read_text().count('@GameTest(') for path in
                   (PROJECT / 'src/main/java/org/gtlcore/gtlcore/migration').glob('*GameTests.java'))
    if 'BUILD SUCCESSFUL' not in log or f'All {expected} required tests passed' not in log: raise SystemExit('World Gradle log does not confirm successful current GameTests')
    DEST.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(args.gradle_log, DEST / 'gradle-verification.log')
    shutil.copyfile(PROJECT / 'run/gametest/logs/latest.log', DEST / 'gametest-server.log')
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    suites = list((PROJECT / 'build/test-results/test').glob('TEST-*.xml'))
    for path in suites:
        suite = ET.parse(path).getroot()
        for key in totals: totals[key] += int(suite.attrib[key])
        shutil.copyfile(path, DEST / path.name)
    if not suites or totals['failures'] or totals['errors'] or totals['skipped']: raise SystemExit('Unit tests are absent, failed or skipped')
    expected_units = sum(len(re.findall(r'@Test\b', path.read_text())) for path in
                         (PROJECT / 'src/test').rglob('*') if path.suffix in {'.java', '.kt'})
    if totals['tests'] != expected_units:
        raise SystemExit(f'Archive requires the complete unit suite: {totals["tests"]} reported, {expected_units} expected; remove --tests filters')
    worldgen_marker = 'GTL_NATIVE_WORLDGEN_DATA=PASS deposits=30 production_provider=true unchanged_settings=true'
    if worldgen_marker in log:
        generated = PROJECT / 'build/generated-worldgen/data/gtceu/gtceu/bedrock_fluid'
        shipped = ROOT / 'src/main/resources/data/gtceu/gtceu/bedrock_fluid'
        files = sorted(generated.glob('*.json'))
        if len(files) != 30 or {p.name for p in files} != {p.name for p in shipped.glob('*.json')}:
            raise SystemExit('Generated worldgen files do not match all 30 production resources')
        target = DEST / 'worldgen-data'; target.mkdir(parents=True, exist_ok=True)
        for path in files:
            if json.loads(path.read_text()) != json.loads((shipped / path.name).read_text()):
                raise SystemExit('Generated worldgen settings changed: ' + path.name)
            shutil.copyfile(path, target / path.name)
        shutil.copyfile(PROJECT / 'run/data/logs/latest.log', target / 'datagen.log')
    source = inputs()
    if args.energy_client_gradle_log:
        archive_energy_client(args.energy_client_gradle_log, source)
    if args.prospector_client_gradle_log:
        archive_prospector_client(args.prospector_client_gradle_log, source)
    if args.extendedae_client_gradle_log:
        archive_extendedae_client(args.extendedae_client_gradle_log, source)
    if args.ae_widgets_client_gradle_log:
        archive_ae_widgets_client(args.ae_widgets_client_gradle_log, source)
    if args.dynamic_render_client_gradle_log:
        marker = 'GTL_NATIVE_DYNAMIC_RENDER_CLIENT=PASS renderers=3 obj_models=6 native_models=true codecs=true shader=true mesh_upload=true admission=true resource_reload=true fixture_states=true textures=true'
        client = args.dynamic_render_client_gradle_log.read_text()
        minecraft_log = PROJECT / 'run/client/logs/latest.log'
        if 'BUILD SUCCESSFUL' not in client or marker not in client or marker not in minecraft_log.read_text():
            raise SystemExit('Current native dynamic renderer client validation was not confirmed')
        if not args.dynamic_model_data_gradle_log or 'BUILD SUCCESSFUL' not in args.dynamic_model_data_gradle_log.read_text():
            raise SystemExit('Native model provider generation log is required')
        target = DEST / 'dynamic-render-client'; target.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(args.dynamic_render_client_gradle_log, target / 'gradle-client.log')
        shutil.copyfile(args.dynamic_model_data_gradle_log, target / 'gradle-model-data.log')
        shutil.copyfile(minecraft_log, target / 'minecraft-client.log')
        for filename in ['native-machine-animations.png', 'native-machine-animations-reloaded.png']:
            shutil.copyfile(PROJECT / 'run/client/screenshots' / filename, target / filename)
        for kind in ['blockstates', 'models/block/machine', 'models/item']:
            for name in ['annihilate', 'harmony', 'elevator']:
                filename = 'native_' + name + '_render.json'
                destination = target / 'native-generated' / kind / filename
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(PROJECT / 'build/generated-machine-models/assets/gtlcore_recipe_test' / kind / filename, destination)
        (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')
    if args.avaritia_client_gradle_log:
        marker = 'GTL_NATIVE_AVARITIA_CLIENT=PASS models=50 cosmic=11 halo=39 native_loaders=true native_render=true source_json_unchanged=true bounded=true'
        client = args.avaritia_client_gradle_log.read_text()
        minecraft_log = PROJECT / 'run/client/logs/latest.log'
        if 'BUILD SUCCESSFUL' not in client or marker not in client or marker not in minecraft_log.read_text():
            raise SystemExit('Current native Avaritia client validation was not confirmed')
        target = DEST / 'avaritia-client'; target.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(args.avaritia_client_gradle_log, target / 'gradle-client.log')
        shutil.copyfile(minecraft_log, target / 'minecraft-client.log')
        shutil.copyfile(PROJECT / 'run/client/screenshots/native-avaritia-models.png', target / 'native-avaritia-models.png')
        shutil.copyfile(ROOT / 'migration/native-avaritia-api.json', target / 'native-avaritia-api.json')
        (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')
    if args.advanced_fluid_rig_client_gradle_log:
        archive_advanced_fluid_rig_client(args.advanced_fluid_rig_client_gradle_log, source)
    if args.fluid_rig_client_gradle_log:
        archive_fluid_rig_client(args.fluid_rig_client_gradle_log, source)
    if args.super_pattern_client_gradle_log:
        archive_super_pattern_client(args.super_pattern_client_gradle_log, source)
    if args.gt_output_client_gradle_log:
        archive_gt_output_client(args.gt_output_client_gradle_log, source)
    if args.preview_client_gradle_log:
        marker = 'GTL_NATIVE_PREVIEW_CLIENT=PASS native_gt_definitions=2 page_click=true fullscreen_click=true pan=true restore=true meshdata=true jei_layout=true jei_live_materials=true world_ghost=true world_layer=true expiration=true custom_world_shell=true jei_registration=true resource_reload=true'
        client = args.preview_client_gradle_log.read_text()
        minecraft_log = PROJECT / 'run/client/logs/latest.log'
        if 'BUILD SUCCESSFUL' not in client or marker not in client or marker not in minecraft_log.read_text():
            raise SystemExit('Current native preview client validation was not confirmed')
        target = DEST / 'preview-client'; target.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(args.preview_client_gradle_log, target / 'gradle-client.log')
        shutil.copyfile(minecraft_log, target / 'minecraft-client.log')
        for name in ['ebf', 'tower', 'fullscreen', 'jei', 'world', 'world-custom', 'reloaded']:
            filename = 'native-preview-' + name + '.png'
            shutil.copyfile(PROJECT / 'run/client/screenshots' / filename, target / filename)
        (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')
    if args.recipe_mode_client_gradle_log:
        marker = 'GTL_NATIVE_RECIPE_MODE_CLIENT=PASS types=7 popup=true scroll=true selection_c2s=true highlight_s2c=true saved_recreation=true'
        client = args.recipe_mode_client_gradle_log.read_text()
        minecraft_log = PROJECT / 'run/client/logs/latest.log'
        if 'BUILD SUCCESSFUL' not in client or marker not in client or marker not in minecraft_log.read_text():
            raise SystemExit('Current native recipe mode client validation was not confirmed')
        target = DEST / 'recipe-mode-client'; target.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(args.recipe_mode_client_gradle_log, target / 'gradle-client.log')
        shutil.copyfile(minecraft_log, target / 'minecraft-client.log')
        for name in ['recipe-modes', 'recipe-modes-scrolled', 'recipe-modes-restored']:
            filename = 'native-menu-' + name + '.png'
            shutil.copyfile(PROJECT / 'run/client/screenshots' / filename, target / filename)
        (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')
    if args.configurator_columns_client_gradle_log:
        marker = 'GTL_NATIVE_CONFIGURATOR_COLUMNS_CLIENT=PASS controls=9 columns=3 first_popup=true second_c2s_s2c=true third_popup=true rename_c2s_s2c=true bounded=true slots=108 scroll=true last_row_popup=true page_c2s_s2c=true last_slot_popup=true output_preview=true'
        client = args.configurator_columns_client_gradle_log.read_text()
        minecraft_log = PROJECT / 'run/client/logs/latest.log'
        if 'BUILD SUCCESSFUL' not in client or marker not in client or marker not in minecraft_log.read_text():
            raise SystemExit('Current native configurator column client validation was not confirmed')
        target = DEST / 'configurator-columns-client'; target.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(args.configurator_columns_client_gradle_log, target / 'gradle-client.log')
        shutil.copyfile(minecraft_log, target / 'minecraft-client.log')
        for name in ['native-ae-columns', 'native-ae-columns-items', 'native-ae-columns-rename', 'native-ae-columns-scrolled', 'native-ae-columns-catalysts', 'native-ae-columns-large', 'native-ae-columns-last-catalysts']:
            shutil.copyfile(PROJECT / 'run/client/screenshots' / (name + '.png'), target / (name + '.png'))
        (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')
    if args.performance_client_gradle_log:
        marker = 'GTL_NATIVE_PERFORMANCE_CLIENT=PASS rows=12 equal_timings=true native_s2c=true refresh=true dispose=true scroll=true highlight_dimension=true highlight_15s=true bounded=true'
        client = args.performance_client_gradle_log.read_text()
        minecraft_log = PROJECT / 'run/client/logs/latest.log'
        if 'BUILD SUCCESSFUL' not in client or marker not in client or marker not in minecraft_log.read_text():
            raise SystemExit('Current native performance client validation was not confirmed')
        target = DEST / 'performance-client'; target.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(args.performance_client_gradle_log, target / 'gradle-client.log')
        shutil.copyfile(minecraft_log, target / 'minecraft-client.log')
        for name in ['native-performance-monitor', 'native-performance-monitor-scrolled', 'native-performance-monitor-updated']:
            shutil.copyfile(PROJECT / 'run/client/screenshots' / (name + '.png'), target / (name + '.png'))
        (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')
    if args.wildcard_client_gradle_log: archive_wildcard_client(args.wildcard_client_gradle_log, source)
    if args.item_menus_client_gradle_log: archive_item_menus_client(args.item_menus_client_gradle_log, source)
    if args.chamber_configurator_client_gradle_log:
        marker = 'GTL_NATIVE_CHAMBER_CONFIGURATOR_CLIENT=PASS panels=3 native_inputs=true fixture_commands=true bounded=true parent_retained=true'
        client = args.chamber_configurator_client_gradle_log.read_text()
        minecraft_log = PROJECT / 'run/client/logs/latest.log'
        if 'BUILD SUCCESSFUL' not in client or marker not in client or marker not in minecraft_log.read_text():
            raise SystemExit('Current native chamber panel client validation was not confirmed')
        target = DEST / 'chamber-configurator-client'; target.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(args.chamber_configurator_client_gradle_log, target / 'gradle-client.log')
        shutil.copyfile(minecraft_log, target / 'minecraft-client.log')
        for name in ['circuits', 'refresh', 'tags']:
            filename = 'native-chamber-' + name + '.png'
            shutil.copyfile(PROJECT / 'run/client/screenshots' / filename, target / filename)
        (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')
    if args.tag_stock_client_gradle_log:
        marker = 'GTL_NATIVE_TAG_STOCK_CLIENT=PASS menus=2 count_sort_c2s=true tag_popup_s2c=true'
        client = args.tag_stock_client_gradle_log.read_text()
        minecraft_log = PROJECT / 'run/client/logs/latest.log'
        if 'BUILD SUCCESSFUL' not in client or marker not in client or marker not in minecraft_log.read_text():
            raise SystemExit('Current native tag stocking client validation was not confirmed')
        target = DEST / 'tag-stock-client'; target.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(args.tag_stock_client_gradle_log, target / 'gradle-client.log')
        shutil.copyfile(minecraft_log, target / 'minecraft-client.log')
        for name in ['tag-stock-item', 'tag-stock-fluid']:
            filename = 'native-menu-' + name + '.png'
            shutil.copyfile(PROJECT / 'run/client/screenshots' / filename, target / filename)
        (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')
    if args.maintenance_client_gradle_log:
        marker = 'GTL_NATIVE_MAINTENANCE_CLIENT=PASS menus=4 duration_click_c2s=true gravity_c2s_s2c=true'
        client = args.maintenance_client_gradle_log.read_text()
        minecraft_log = PROJECT / 'run/client/logs/latest.log'
        if 'BUILD SUCCESSFUL' not in client or marker not in client or marker not in minecraft_log.read_text():
            raise SystemExit('Current native maintenance client validation was not confirmed')
        target = DEST / 'maintenance-client'; target.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(args.maintenance_client_gradle_log, target / 'gradle-client.log')
        shutil.copyfile(minecraft_log, target / 'minecraft-client.log')
        for name in ['auto-maintenance', 'clean-maintenance', 'gravity-maintenance', 'gravity-only']:
            filename = 'native-menu-' + name + '.png'
            shutil.copyfile(PROJECT / 'run/client/screenshots' / filename, target / filename)
        (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')
    if args.machine_client_gradle_log:
        client = args.machine_client_gradle_log.read_text()
        marker = 'GTL_NATIVE_MACHINE_UI_SERVER_SAVE_RESTORE=PASS batch=true distinct=true lock=true'
        if 'BUILD SUCCESSFUL' not in client or marker not in client: raise SystemExit('Machine client verification was not confirmed')
        if marker not in (PROJECT / 'run/client/logs/latest.log').read_text():
            raise SystemExit('Current Minecraft client log is not the verified machine run; retain its existing separate evidence instead')
        target = DEST / 'machine-client'; target.mkdir(exist_ok=True)
        shutil.copyfile(args.machine_client_gradle_log, target / 'gradle-client.log')
        shutil.copyfile(PROJECT / 'run/client/logs/latest.log', target / 'minecraft-client.log')
        (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')
    if args.renderer_client_gradle_log:
        client = args.renderer_client_gradle_log.read_text()
        marker = 'GTL_NATIVE_MATERIAL_RENDERERS=PASS providers=true special_models=true item=true fluid=true unbound=true frames=60'
        if 'BUILD SUCCESSFUL' not in client or marker not in client: raise SystemExit('Native renderer client verification was not confirmed')
        if marker not in (PROJECT / 'run/client/logs/latest.log').read_text():
            raise SystemExit('Current Minecraft client log is not the verified renderer run; retain its existing separate evidence instead')
        target = DEST / 'renderer-client'; target.mkdir(exist_ok=True)
        shutil.copyfile(args.renderer_client_gradle_log, target / 'gradle-client.log')
        shutil.copyfile(PROJECT / 'run/client/logs/latest.log', target / 'minecraft-client.log')
        shutil.copyfile(PROJECT / 'run/client/screenshots/native-material-renderers.png', target / 'native-material-renderers.png')
        (target / 'source-hashes.json').write_text(json.dumps(source, indent=2, sort_keys=True) + '\n')
    evidence = {path.relative_to(DEST).as_posix(): digest(path) for path in sorted(DEST.rglob('*')) if path.is_file() and path.name != 'manifest.json'}
    manifest = {
        'date_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'scope': 'Selected production classes in an isolated NeoForge/FML mod. Full GTLCore and GTLAdditions are not loaded and do not yet compile.',
        'minecraft': '1.21.1', 'neoforge': '21.1.248', 'java': 21,
        'gtceu_reference_commit': 'c72dc16b52795cbb0456b6e15e13cc4a85c0ed1b',
        'gtmthings_commit': 'a3fb3a88d9ea060aa703c8b627949606e907f6ed',
        'ae2_version': '19.2.8',
        'jade_version': '15.10.0+neoforge',
        'ae2_reference_commit': 'ef766823c7d0c42b2bccb5994113e612a25035fd',
        'ae2wtlib_version': '19.2.0',
        'ae2wtlib_reference_commit': 'e62cbf9e409f060d6f164987559237b0eae9434c',
        'ae2wtlib_patch': 'migration/dependencies/ae2wtlib/client-registration.patch',
        'gradle_command': './gradlew -p migration/recipe-tests test verifyNativeWorld' + (' verifyNativeWorldgenData' if worldgen_marker in log else ''),
        'worldgen_data': '30 generated native GT bedrock-fluid JSONs exactly match shipped production settings' if worldgen_marker in log else 'not verified in this run',
        'unit_tests': totals, 'required_gametests': expected,
        'loaded_recipes': int(re.findall(r'Loaded (\d+) recipes', log)[-1]),
        'world_checks': ['Original registered bronze large boiler, full native pattern and actual LargeBoilerLogic coal recipe exercise one thermal adjustment, one native throttle adjustment, progress conservation across throttle changes, one complete physical fuel cycle and bounded server throttle. Real ticks verify hot boiler steam, SaveField reload, restored native temperature subscription and original water/steam ratio. Hot state is controlled; complete cold warmup, all boiler tiers and physical boiler GUI remain unverified', 'Original registered native LuV fusion ring, energy hatches and fluid IO exercise repeated pure preview, rejected physical-input refunds, blocked output, one startup heat payment, native recipe tick/output, persisted heat/internal energy, cooldown and a new paid cycle after native SaveField reload. Original GT Java is unchanged. Full Core advanced fusion controllers, physical fusion GUI and multiplayer remain unverified', 'Strict native MultiPredicate hook counts all overlapping limited root OR alternatives using their original native identities, retains slice/global maxima and unlimited fallback only for unmatched positions, and leaves AND/XOR/mixed trees unchanged. Real BlockPattern repetition probes, native context/cache rollback and repaired structures are exercised; complete original Core/Additions pattern registration remains unverified', 'Original native wireless receiving cover is attached to the production neutron accelerator fixture. Real account debit, rate/free-capacity limits, same-tick rejection, disabled input, native subscription stop/start, component-bearing cover persistence and removal are exercised in a real world. Actual native wireless monitor uses the shared transfer snapshot with team/all scope, bounded rows, signed sorting, overflow-safe statistics, styled component JSON and cache invalidation. Complete production registration, all receiver tiers and physical multiplayer remain unverified', 'Both original native creative energy/laser hatches use the production infinity traits. Actual native recipe handlers exercise repeated maximum demands, simulation, exact odd-long voltage, zero amps, saturating capacities, native SaveField reload and reconstructed traits. A complete production controller recipe cycle is unverified', 'Actual native AE2 ME chest routes both production infinite cell classes to the native storage menu, retains normal cell menus and type-filter eligibility. A real ServerPlayer with a native packet sink verifies the server menu path; physical chest screen rendering is unverified', 'Original ExtendedAE pattern modifier item, host, inventory and menu exercise scoped processing multipliers, malformed actions, exact large replacement ratios, metadata and native component persistence. Production infinity cell Mixin is loaded. Complete crafting-pattern replacement and physical multiplayer remain unverified', 'Original GT alloy blast smelter pattern and native muffler exercise blocked front, styled Core failure status, unblocking recovery and detached-controller safety in a real world', 'Actual native low/high-pressure boiler holders exercise strict Core/Additions Jade hooks, styled failure JSON, steam EU conversion and signed arbitrary-precision wireless rows; native SaveField reload restores the wireless recipe subtype, exact energy and Core batch state. These scenarios verify server providers and new-world native persistence; physical Jade HUD rendering and complete production wireless controllers remain unverified', 'Production chamber domain commands edit native circuit traits and tag stocking settings, keep zero distinct from an empty slot, preserve physical inventory components and restore both original GT hatches and production tag parts in a real world. Unsupported targets, IDs, negative/oversized integers, truncated/malformed payloads, oversized UTF strings and trailing data are rejected before mutation. Full terminal authorization and packet registration/traffic remain unverified', 'Complete pinned native FTB Ultimine common/NeoForge source loads with the reference FTB Library and Architectury dependencies. Production area shape is registered in its real shape registry and preserves configured range updates, distance order, block matching and block-count limits. The strict native protection Mixin is exercised through actual FTBUltiminePlayerData and preserves wildcard blacklist, allowed same-block selection and empty-blacklist behavior. Native fake players exercise selection; complete player mining, client shape rendering and optional ranks runtime remain unverified',
                         'Production pattern relay uses native AE item interaction, registry-aware NBT/stream and data-component settings. Real powered cable hosts verify native GT wrench mode changes, memory-card mode copy and saved host recreation. Unit validation preserves exact long component item/fluid stock and pending outputs, visual stream fields and unrelated memory-card components. A native fake player supplies the world interaction; actual player chat, complete production registration, routing jobs and client rendering remain unverified',
                         'Production bounded chamber snapshot codec uses RegistryFriendlyByteBuf for native item/fluid component keys, empty stacks, styled/translatable names and exact long counts. Unit validation verifies multi-chunk reassembly publishes only after full decoding, rejects wrong revisions, missing/out-of-order chunks, bad lengths, truncated and trailing data, replaces unfinished snapshots without combining contents and recovers after failure. Oversized/failed encoders send no partial snapshots. Complete production chamber menu and physical wireless packet traffic remain unverified',
                         'Production throughput monitors use native AE item interaction, registry-aware NBT/stream and native storage mixins. Actual powered AE cable hosts, drives and GT machine action sources verify component matching, ignored simulations, exact inserted/extracted events, source dimension/position/facing, dominant-direction sampling, routine changes and saved host recreation. Native nested/composite storages deduplicate listeners, refresh topology and retain manual-crafting reservations. Separate fixture part/configurator registrations do not validate complete production registration, wireless terminal traffic or client trend rendering',
                         'Production fission reactor uses native accepted fuel/cooler positions and adjacency, pure parallel previews, saved accepted thermal recipe parameters, exact long coolant ratios and output-before-consumption preflight. Actual native hatches verify blocked cooling never consumes water, successful cooling accelerates only committed exchanges, the 825K sodium-potassium transition, idle heat/damage repair, exact EU/outputs, blocked paid completion and finish pause across reload, in-flight reload, actual part unload/rebind without lost paid progress, and real subscribed overheat damage/explosion. Sparse coolant materials and a separate reduced structure do not validate complete Core materials, original production registrations/structure or the fission client menu',
                         'Native CWU parallel dispatch checks actual powered optical pipes/providers without paying energy or reserving CWU during preview. Native adjacent-fluid conditions retain original data-driven horizontal rules and inspect every actual multiblock input tank, reject output stock and unknown/malformed/incomplete IDs, refresh changed recipe metadata, preserve duplicate fluid requirements/reverse, and retain explicit native three-dimensional source semantics and registry-aware condition codecs; reduced controllers/structures use separate fixture registrations',
                         'Production advanced infinite rig retains its original seven coil limits, neutronium/vibranium head rates, five-by-five native deposit query, temperature/voltage/overclock formulas, scaled exact-long fluid outputs and EU, and whole-twenty-tick energy admission. Actual native thermal subscriptions, exact blaze quantities, four-coolant priority and 20000-mB consumption, sensor redstone, 300-tick head melting, invalidation/reformation, in-flight native/Core pause preservation and blocked owned output are tested. Sparse material/coil fixtures and a separate reduced structure do not validate complete Core materials or original production structures',
                         'Native Core harvest tiers extend the existing GT tag arrays through level ten without changing levels zero through six; actual tool components allow level-ten drops and reject lower tiers including vanilla gold/diamond. Test tag resources supply the separate fixture block; production material/block generation remains unverified',
                         'Production Core infinite fluid rigs use attached native recipe traits, unchanged 256 production and fifty-percent one-tier overclock, exact long fluid ingredients and real energy/output hatches. Native/Core finish pauses survive an in-flight recipe entity recreation and blocked completed output without extra EU or lost production; Core never depletes the deposit. Strict native GT hooks retain exact long recipe outputs, public int display saturation, once-per-cycle depletion and native finish pause after blocked-output recovery. Deposits and reduced structures are controlled fixtures; original production registrations, planet generation and full structures remain unverified',
                         'Original GT item/fluid tag covers apply Core namespace-independent boolean expressions through real NeoForge capabilities, keep component stacks and pure simulations, invalidate native result caches after expression changes and restore expressions and unrelated components on entity recreation',
                         'All eight production Core cover definitions register through native deferred holders with their original gtceu IDs; native conveyor, robot-arm, pump and regulator constructors retain Core rates for all fifteen tiers. Actual infinite-water subscriptions fill the full 64-bucket GT hatch, resume on reload and stop on removal. Native ULV regulator transport preserves over thirty billion millibuckets across fourteen distinct component fluids using a long budget over int capability calls, never duplicates on budget refill, restores unlimited settings and stops on removal. MAX conveyors and ULV robot arms transfer real component stacks and persist rate/working settings; original Core cover-item registration, cover rendering and physical multiplayer remain unverified',
                         'Production Additions super pattern buffers publish FOA processing patterns on a powered native AE grid, consume real controller inputs and deliver multiplied outputs exactly once; all 162 menu slots, authoritative bounded FOA settings, exact long pending ownership and actual entity reload are verified. Original special recipe IDs are looked up from fresh native recipe holders with component-aware item/fluid predicates. Independent native data-stick markers reject cross-dimension/ordinary bindings, retain unrelated components and restore a real proxy controller that matches through native RecipeHelper, consumes once and returns exact outputs to its source. Fixture registrations and cycle-container items are separate; the complete FOA controller/module and original production registrations remain unverified',
                         'Actual production wildcard buffers expand native material item/fluid tags with material, property and flag filters; circuit zero is reusable; ordinary and stocking controllers consume exact real inputs and deliver outputs into native AE cells; overlapping reservations do not reuse network stock; detached saves retain pending restoration and reload exact quantities, recipe IDs, components and server menu settings. Segmented owned refunds preserve totals above Long.MAX_VALUE through pattern removal, deferred restoration, real AE cell insertion and actual machine destruction/BlockItem placement without returning dropped ownership again. Only the external item/protocol is supplied by the fixture; the external editor, original production registration and full Core/Additions are not loaded',
                         'Production harmony controller moves physical hydrogen/helium on actual subscribed native ticks, uses native placement/data-stick account ownership, pure candidate modifiers, exact accepted-start gas/BigInteger wireless energy charging, input/output/balance/rate rejection without resource consumption, original circuit-one/four costs and a complete three-hundred-tick cycle; native server menu text, SaveField gas quantities above int/double precision and invalidation stopping the startup subscription are verified with a fixture structure. Full production structure/registration and physical client menu remain unverified',
                         'Production MetaMachine tick extension strictly wraps native trait execution while retaining superclass dirty-field handling; cancelled/deferred native blocks keep S2C and new-world SaveField data, startup AE/normal count budgets are separate, time exhaustion defers them, critical work bypasses limits and deferred native block ticks resume in forced loaded chunks',
                         'Actual registered native wire cutter toggles Core batching through ExtendedUseOnContext; a real soft mallet arms cycle pause, rejects duplicate arming, completes exactly one native recipe and resumes with a normal click; LDLib async service queues native dirty changes on the server and discards changes for removed block entities',
                         'Production performance monitor retains equal-time machine rows, caches snapshots for forty native level ticks and refreshes from actual native MetaMachine tick observations',
                         'Production native machine panel builder strictly injects the original four-controls-per-column policy for actual ME pattern buffers; all nine controls remain unique across three columns, all six popup handlers and two native value sync handlers remain available',
                         'Production native recipe mode factory strictly injects into actual GT MachineUIPanel creation, opens a real synced child panel and accepts all seven server-owned modes while rejecting invalid indices, out-of-range players and removed hosts; S2C current-mode values and native SaveField reload preserve the accepted selection',
                         'Production structure detector preserves physical offhand CustomData and other item components, checks independent native main/auxiliary PatternStates across all 48 orientations and mirrors, reports exact failed positions and leaves formed repeated-pattern caches and shared slice repetition dimensions unchanged',
                         'Native multiblock dispatch ignores active-coil/model-only changes, invalidates a required hatch IO replacement, retains failed-position repair watches, automatically discovers and binds the repaired hatch and removes all index memberships and bindings after actual controller removal; the Core adapter exposes an immutable native index snapshot',
                         'Production slaughterhouse uses native ENTITY loot contexts and original HV nine-mob spawning, retains blocked physical drops and recovers their exact components, leaves a native NeoForge FakePlayer present in the world entity query alive, accepts native server mode clicks and persists mode across entity recreation; no connected multiplayer client is claimed',
                         'Production greenhouse checks actual tempered-glass skylight through the real light engine, rejects recipes before consuming physical inputs in an enclosed dark chamber and recovers when the enclosure opens; no in-flight low-light cycle or client display is claimed',
                         'Production per-material ore/dust hooks strictly inject and generated integrated ore/electric implosion recipes load in the real RecipeManager alongside native downstream washing; Core stone-only ore rules affect the native recipe total',
                         'Production lightning/magic generators attach real native emitter traits; normal world ticks produce exact quarter-charge/crystal pulses, restore energy and subscriptions after entity recreation, stop after source removal and request the native overcharge explosion while removing the machine; test cancels surrounding explosion damage only',
                         'Production wireless optical data hatches bind in both orders without changing unrelated native stick components, persist actual remote coordinates, route research through physically powered native Data Banks with exact energy use, pause/resume with power, reject other dimensions/unloaded chunks, terminate cycles and serialize the linked endpoint through their native Jade provider',
                         'Actual native Jade recipe-output provider on a working reduced production controller preserves ordinary codecs and separates component-aware item/fluid rows above double/int precision; no physical long-output cycle or connected-client Jade rendering is claimed',
                         'Actual native vanilla ChestMenu creative clone of production ordinary/fast infinity cells assigns independent UUID/storage, retains components and exact Int128 amounts, rejects survival cloning and survives native SavedData round trip',
                         'Reduced production bedrock driller requires bedrock nine blocks below before physical input consumption, produces actual native recipe output and rebinds/unbinds its watcher on target changes, invalidation, entity recreation and unload',
                         'Production LevelChunk hook publishes successful real state changes, ignores identical writes and permits callbacks to remove their own watcher without skipping other callbacks',
                         'Actual native powered HPCA controller uses 8 CWU per component; damage survives entity replacement, structure repair binds the replacement part and restoring damage restores computation',
                         'Native simulator accepts one multiblock blueprint, creates an oriented virtual controller without replacing the real entity and restores its native storage slot; full client preview remains unverified',
                         'All 30 production native bedrock-fluid registry keys/codecs/settings match the manifest and bootstrap; planet filters exclude overworld generation, vein depletion and native SavedData restore all deposit identities',
                         'Finite/infinite optical providers use real native energy/input hatches, pure energy simulation, per-tick CWU reservations, mainframe removal, native power pause/resume and saved controller replacement',
                         'Tier casing/repeated assembly/storage lifecycle, exact runtime NBT and recipe packets',
                         'Controller distinct and native colored input groups, cache invalidation/reformation, NC ghost circuits',
                         'Batch setup/material consumption/native ticks, powered energy, five-attempt power suspension, output blocking/resumption',
                         'Single chance roll and complete pending output capacity without advancing chance caches',
                         'Recipe locking and native SaveFields',
                         'Cross-recipe combination, lock restriction, pending work persistence and output resumption',
                         'Steam EU/mB conversion, dedicated energy tank exclusion, fixed in-flight OC rate and native OC SaveFields',
                         'Actual native low/high pressure SimpleSteam recipes, exhaust obstruction and voltage rejection',
                         'Heat exchanger input/output blocking, pure preview and coolant consumption only at setup',
                         'Cross-recipe coil tier/parallel selection, mixed-coil rejection and repaired structure reformation',
                         'Inserted generator selection, physical dynamo output and unchanged installed machine inventory',
                         'Native GTMThings wireless accounts, rate-limit waiting/resumption, fixed in-flight account/mode and save/load',
                         'Actual native SimpleGenerator eight-amp energy capability transfer and complete fuel cycle',
                         'Combustion engine auxiliary consumption only on accepted ticks, paused output and saved lubricant timer',
                         'Chemical engine oxygen/dinitrogen boost combination, intake obstruction and saved running state',
                         'Mega turbine pure fuel preview, native single rotor tick, material identity, reserve rotor transfer and saved inventory',
                         'Original GT combustion and gas-turbine registration IDs, native complete structure previews, real hatches, formation and full fuel cycles',
                         'Registered GCYM maceration energy requirement and reduced physical recipe cycle, alloy-blast coil uniformity and recovery',
                         'Registered GCYM distillery minimum/maximum repeats, mandatory native fluid output per layer and repaired structure reformation',
                         'Neutron activator strict energy budgets, saved controller replacement and pipe-count speed, waiting/resumption and rejected working callbacks without output',
                         'Plasma generator pure blocked previews, one-mB setup-only boosters, combined advanced boosts, pending recipe save/replacement and physical dynamo output',
                         'Molecular assembler native part discovery/speed/parallel/pattern slots, registry-aware IO queue and pending recipe entity recreation, structure repair, atomic output waiting and exactly-once completion',
                         'Actual Core MEIO part node creation/destruction, native AE creative power and cell storage, component-aware insertion/extraction, tick sleeping/wakeup, front rotation and all-facing reconnection, saved node ownership',
                         'Actual GT ME bus/hatch all-facing native nodes, saved refresh intervals distinct from native tick staggering and entity recreation',
                         'Actual 108-slot molecular pattern container save/recreation and native crafting-to-processing conversion',
                         'Production molecular IO publishes patterns to the real AE crafting service, executes queued crafts on native controller ticks and flushes exact output into a real AE drive without later duplication',
                         'Production molecular IO node/controller replacement preserves large pending crafts, remaining queues, reusable tool inventory, terminal visibility/name and no-storage output buffer',
                         'Native molecular menu opening payload fixes vanilla slot numbering before initialization, second-page real slot clicks update production pattern providers, native client contents sync and rename/visibility packet handlers, obsolete source binding rejection',
                         'Actual vanilla honey crafting extraction, glass-bottle remainders, whole-operation input rollback and expected-output counter overflow rejection',
                         'Production filtered/async ME output parts discovered by a native no-energy controller, full recipe setup/ticks/finish, component-aware insertion into real item/fluid AE cells and exactly-once delivery',
                         'Async output saved before draining and native entity recreation, exact long component queues retained on a powered AE network without storage',
                         'Production output filter popup server ghost-slot edits, component switches and priority packet handlers, component-aware data-stick copy/paste and native machine item-component replacement',
                         'Production normal pattern buffer publishes to real AE crafting service, delivers exact native controller processing output to real cells, circuit zero, long pending inputs/catalysts/output save and entity recreation',
                         'Normal pattern buffer native server catalyst/circuit/name/visibility/menu handlers and saved cache limits; data-stick proxy rebinding and single subscription; copy/cut preservation and consumed-once clipboard',
                         'Stocking pattern buffer native controller processing with real AE item/fluid cells, overlapping predicate reservations and duplicate stock-key deduplication, unchanged stock on rejection, native ghost-slot server editing and saved refresh interval',
                         'Virtual supply real powered AE grid exposes sealed tokens and never consumes backing items; native AE cached removal; complete 288-slot server menu and saved block-item/replacement/drop behavior without duplication',
                         'Pattern box full 360-slot native menu, locked held slot, final-page vanilla click, preserved pattern components and allocated pages after configuration shrink, actual box-to-production-pattern-buffer transfers without duplication',
                         'Pattern modifier authoritative native menu handlers with bounded and invalid text, independent per-item settings, component-preserving repeated scaling through actual production pattern-provider interaction',
                         'Ultimate terminal mirrored/offhand repeated survival construction, rejected placement rollback, partial dismantling, hatch quota preservation, native water placement and persisted pending fluid refunds',
                         'Ultimate terminal actual powered AE2 item/fluid cell construction and dismantle refunds; disabled AE mode preserves pending fluid and online rebinding returns it exactly once',
                         'Native pattern search repeat backtracking, global predicate count rollback, grouped/optional slices and rejection of unloaded chunks without loading them',
                         'Configuration copier offhand machine settings, zero circuits, component-aware quantum chest/tank locks and exact native MAX quantum chest voiding/save/reload/extraction',
                         'Pattern tool native authoritative menu configuration, independent physical item data, offhand swap protection, actual recipe-holder filtering, server generation/analysis buttons and exact long component patterns with quick-upload metadata',
                         'Structure writer physical offhand settings, native rotate handlers and emitted native builders matched against real asymmetric 3x2x4 structures in all six directions',
                         'Mixed 64-slot stocking hatch real powered item/fluid AE cells, overlapping recipe reservations, native page/save/reload/data-stick handlers, four pull modes and no duplicated virtual inventory on removal',
                         'Owned mixed 64-slot input hatch actual AE-cell refill and recipe consumption, refused returns after cell removal, exact long config/data-stick handlers and real block loot/drop followed by native item-component replacement without duplicate stock',
                         'Actual native GT/GTMThings output handlers accept exact long quantities, retain rejected duplicate-key amounts, preserve paused/configured/manual cycles above byte range, survive entity reload and actual destruction/BlockItem placement without double refunds, and execute a real native controller recipe',
                         'Actual shared item/fluid catalysts satisfy a real native controller recipe without consumption; entity reload refreshes component-aware caches, externally hidden capabilities remain hidden, and clearing inventories invalidates reusable demands',
                         'Native grinding-ball controller callbacks, real Player damage and creative exemption, native inventory menu, saved working state, detachment and actual destruction/BlockItem placement retain idle/active drop rules without duplicate ownership',
                         'Native GT static-owner item stocking list and slots use real AE cell reservations without duplicate configured-key counts, refresh after recipe consumption, reject stopped access, rebind decoded slot owners after native reload, allow standalone auto-pull and keep virtual stock out of actual machine drops',
                         'Native GT fluid input balances real-cell stock without deleting refused component-key returns, preserves exact long native-menu/data-stick configuration and owned buffers through actual destruction and BlockItem placement; native fluid stocking deduplicates reservations, rejects disabled/component-mismatched drains, restores inner-slot owners on reload and never drops virtual inventory',
                         'Native RecipeDB search keeps real distinct buses separate and supplements controller ghost circuits; actual production pattern-buffer dispatch is discoverable with component-aware item/fluid inputs and circuit zero before native consumption',
                         'Production tag-filter item/fluid stocking parts select real native item/fluid tags with ExtendedAE wildcard expressions and whitelist/blacklist semantics, preserve component keys and count sorting, reserve duplicate-key demands, reject disabled access, rebind item slots after SaveField reload, copy configuration through native data sticks and apply popup C2S settings; actual fluid destruction and BlockItem placement never serialize or reinsert virtual network stock, and native item UI pickup extracts real cell contents',
                         'Production native maintenance parts discovered through the real native maintenance base, controller duration modification, active/reversed cleanroom and gravity conditions, owned provider removal/reformation and preservation of replacement providers, modified native menu C2S clicks and bounded gravity settings with entity recreation',
                         'Production auxiliary item/fluid IO rejects insufficient or mismatched component inputs and blocked outputs without partial changes, keeps circuit zero non-consuming and consumes exact native EU once; large/negative/zero budgets and server-worker recipe manager access are checked in a real world',
                         'Production quick upload service discovers a real formed provider on a powered AE grid, inserts an exact component pattern, deduplicates encoder metadata differences, undoes only matching patterns once, recovers after formation and refuses unloaded remote chunks',
                         'Pinned complete AE2WTLib/API with recorded client-registration fix loads alongside ExtendedAE on the native dedicated test server'],
        'unit_checks': ['Native AE amount expressions preserve exact rational integer arithmetic through Long.MAX_VALUE, reject malformed or oversized inputs and saturate native scroll changes. Immutable configuration deltas survive repeated or delayed encoding after cache and inventory mutation, include empty initial slots and honor native handlers exposing only a prefix of an inventory',
                        'Native CWU capability parallel previews cap every tick input against currently available long-summed budgets, honor requested limits, ignore output handlers, deduplicate physical receivers and shared optical providers, and preserve absence/non-tick native skip semantics without allocating computation',
                        'Original advanced drill seven-material thermal limits, all factor thresholds and overheated output, unchanged base/scaled EU formulas, exact-long native recipe quantities above double precision, positive/cycle-cost overflow admission and original level-ten native tool tag identities',
                        'Shared native bedrock-fluid holder calculations multiply yield and remaining operations in long before division, retain depleted-yield floors and original overclock rounding, reject absent definitions and preserve production above int',
                        'Native GT unified item/fluid TagFilter uses Core original namespace-independent boolean syntax, preserves native update callbacks and result-cache invalidation, and rejects malformed, empty or untagged expressions without crashing',
                        'Native Additions FOA transformation preserves component keys and exact long quantities, reusable cycle outputs, multiplier bounds and saturation; extra special inputs merge into full patterns or reject unsupported scaling without advertising free outputs. Native super buffer fields, the last of 162 slots and long pending/output ownership survive save/reload; 1800-slot and one-slot layouts preserve actual server indices, bounded page values and native slot callbacks. Strict native data-stick component markers reject malformed/research/cross-dimension data without discarding unrelated components',
                        'Production segmented AE refund queues preserve multiple Long.MAX_VALUE inventories with partial transfers, native component-aware save/reload and non-consuming drop snapshots; malformed ledgers and invalid second inventories cannot consume or partially replace owned data', 'Native wildcard persistence maps complete component input/output signatures to FIFO slot queues, preserves duplicate expanded patterns and exact long quantities, validates cached recipe snapshots independently from dispatched inputs, and retains stocking/reusable ingredients; registry-aware preview codecs preserve multipliers and reject malformed lengths',
                        'Shared native ME stocking transaction reserves overlapping predicates and unique component keys before committing owned buffers, refunds partial network withdrawals exactly once, persists refused rollback ownership and rejects overflow before extracting',
                        'Production performance timing rows preserve signed positions, dimensions and maximum integer microsecond timing through RegistryFriendlyByteBuf, snapshot mutable positions, reject negative packet timings and retain equal-time entries through the actual native ModularUI GenericListSyncHandler codec',
                        'Production structure detector formats all native global/layer minimum/maximum count errors and actual block/fluid candidates without mutating error positions; signed immutable highlight positions, dimension and deadline round-trip through the actual Glodium native payload buffer codec',
                        'Native preview geometry uses real GT IBlockPattern helpers and retains variable-height tower pages, six orthogonal predicate mappings, explicit native hatch facing, independent palettes, all 48 orthogonal and mirrored world orientations, out-of-layer controller anchors, custom repeated-shell candidate predicates and sparse immutable state snapshots without holding shared block entities',
                        'Native registry-aware GTLUtil item/recipe cache codecs preserve item and fluid components, exact long amounts above double precision and recipe IDs, and reject malformed wrappers without changing their source',
                        'Production ore/dust per-material hooks retain Core multiplier, original integrated/electric recipe IDs, byproducts and wash-fluid pairs; strict helper Mixin repairs the pinned GT tag/ingredient blasting factory and all three overloads retain separate smelting/blasting IDs and 200/100 tick timing',
                        'Native Jade long output rows preserve full component keys, exact native registry codec counts, saturated aggregation and exact half-up chance/batch calculation without modifying native sized entries',
                        'Core/Additions native Jade status rows compose signed arbitrary-precision wireless energy with styled registry-aware failure reasons, native voltage/amperage, steam conversion, waiting/setup failure and generator capacity warnings; native wireless recipe copies, builder copies and JSON/NBT/stream codecs retain Core batch/parallels/overclock state',
                        'Native HPCA traits retain 8/32 CWU, cooling and energy settings, damage NBT/client packets and machine-builder description callbacks',
                        'All 30 native bedrock-fluid production resources retain original IDs/dimensions/yield/depletion, survive JSON/NBT codecs and use the shared actual UnknownWater material',
                        'Graph job native registry-aware codec preserves component item/fluid keys, exact deferred amounts above long precision, suspension and replay state; duplicate physical ownership is rejected without mutating the source',
                        'Exact signed 128-bit energy statistics beyond long, independent snapshots and saturating compatibility getters',
                        'Native database staging and Core/Additions composed search Mixins: independent input/ME-slot isolation, shared fluids and controller circuits, cached recipe type/predicate checks, deduplication/reset/custom fallback, mutable multiple-candidate mode and strict/partial overlapping component fluid predicates with long ingredient indexing',
                        'Signed 128-bit decimal boundaries and 2048 deterministic BigInteger round trips',
                        'Native deferred part tier ranges, circuit filtering, dual-hatch and buffer capacities and model properties',
                        'Original Core semi-fluid/rocket/naquadah generator IDs, deferred fuel types and configured physical amperage',
                        'Single native deferred entry for 23 shared Core GCYM IDs, separate original mega-blast ID, preserved native research/AE initialization',
                        'All 24 Core GCYM native previews, original high-tier parallel limits/sharing, deferred lightning duration and native coil display sync',
                        'Native builder chance boosts and fractional denominators with exact item/fluid quantities and data components, per-tick routing, empty optional outputs, recipe copies, JSON/packet round trips and actual rolls',
                        'All 159 original structure templates, independent mutable pattern factories including the shared dirt-forge template',
                        'Vacuum-drying modifier order, temperature rejection/recovery, ordinary versus precision laser parallelism and dimensional mixer recipe-type selection',
                        'Native MEStorage provider front-face and null-side rules, current host orientation and rejection of unrelated block entities',
                        'Neutron energy bounds and large parallel budgets without integer overflow, pure modifiers and speed reset on invalidation',
                        'Server-owned native ModularUI text sync retaining translations/styles/hover text and dynamic client row removal/rebuild',
                        'Tier-specific and combined advanced plasma selection, fallback without boosters and unmodified inventories across repeated previews',
                        'Actual AE2 processing patterns with sparse slots, exact long quantities, item/fluid components, multiplier/division/capacity rejection and native item NBT/network round trips',
                        'Molecular craft trait attachment, component-aware long queued outputs, atomic buffer transactions, native storage and pending SaveFields, callback blocking, interruption/resume and required Additions infinity-mode mixin',
                        'Independent native controller and additional-display sync handlers',
                        'Actual Core MEIO trait ownership, unique persistence, native online client sync and GT bus/hatch refresh SaveFields',
                        'Actual 108-slot container inventory/components/client sync and real ModularUI slot layout without padded phantom slots',
                        'Native composite item handler simulation/component preservation, mutable slot boundaries, independent snapshots and source-order validation before rollback',
                        'Native molecular pagination with unloaded client inventory sources, all 108 real slots, bounded stable page selection and authoritative edit callbacks',
                        'Native GT/GTMThings output duplicate-key reservations, exact long/component quantities, transactional failure retention and strict block-item ownership codecs',
                        'Shared catalyst native GTMThings handlers compare exact component keys and independent long demands, refresh saved inventories, reject consumed/catalyst overlap and hide external capabilities; grinding-ball trait/filter and saved working state retain their native ownership',
                        'Native AE recipe input extraction with exact long maps and circuit components, one circuit per processing operation, missing-input refund and preflighted input/output/counter overflow',
                        'Native item/fluid predicate cache component matching, live quantity re-selection, separate non-consumed catalysts, registry codec persistence and invalid-cache rejection',
                        'Native explicitly attached ME proxy traits, sized long recipe demand forwarding, empty-input reset and unsubscribe-safe lifecycle callbacks',
                        'Native ME output component filters and slots, exact long persistence, async immediate-save/worker drain ownership, capacity overflow rejection, preserved native ranged rolls and component-safe GT fluid builder overloads',
                        'Normal AE pattern trait ownership and sized demands, exact long saved components, aggregate predicate reservations, output overflow rejection, native persisted predicate maps and virtual wrapper sealing',
                        'Virtual supply exact long token insertion/extraction, unmarked wrapper sealing, duplicate-key deduplication and cached removal; native JEI recipe-holder non-consumed item/fluid encoding with data components',
                        'Pattern box maximum pages and preserved other item data, encoded-only slot admission, rejected target/non-pattern transfer safety and invalid saved size rejection without mutation',
                        'Pattern modifier independent per-item native codec settings, long and bounded parameters, repeated exact quantities above double precision, unchanged rejected/overflow patterns and preserved physical stack counts',
                        'Ultimate terminal independent native per-item mode/tier/repeat settings, all 48 native orientations and bounded repeat planning',
                        'Pattern generator native item/fluid components and exact long keys across scaling/save, recipe-holder type/circuit/four-keyword filtering, circuit-free conflict analysis and multiplier/duplicate-key overflow rejection',
                        'Native owned item/fluid AE slots with bounded MEStorage fixtures preserve rejected and partially accepted returns above double/int precision, idempotent flush and component-aware fluid capability drain',
                        'Tag stocking selection with bounded MEStorage fixtures chooses the largest sixteen accessible component keys without long truncation, keeps item/fluid types separate, preserves real quantities and invalidates already-empty stale displays after extraction',
                        'Native maintenance duration bounds, finite setters, floor rounding and unchanged source recipes; gravity-only settings, immutable cleanroom type sets and independent native providers; SaveField/client packets and full gravity condition JSON/NBT/network/reverse/altitude round trips',
                        'Native quick upload RecipeHolder scanning and proxy holders, exact long item/fluid components, integral scaling, ambiguity rejection, native chemical builder copy pair, vanilla molecular types and safe overflow rejection',
                        'Structure writer emitted code compiled by the actual Java compiler against the native GT/NeoForge classpath in all six directions'],
        'client_checks': {'energy': 'energy-client/ records both original native creative hatches and the original wireless monitor. Physical dropdown clicks, typed odd-long and Long.MAX_VALUE voltages, zero-amp input and increment packets exercise real C2S/S2C and reconstructed native SaveField state. Physical monitor team/all scope, styled native rows, wheel scrolling and original dimension/position highlighting are checked with eleven screenshots and scaled panel bounds. Full Core/Additions registration, complete controller bindings and physical multiplayer remain unverified.' if (DEST / 'energy-client/source-hashes.json').is_file() else 'not included', 'prospector': 'prospector-client/ records the actual GT HV handheld menu, original native map, real physical clicks creating negative-coordinate waypoints, opening-chunk anchoring after moving the player, selective removal retaining manual names, and real FTB/Architectury teleport C2S/S2C. Four screenshots check panel and clear button bounds. Complete ore-deposit discovery, all scanner tiers, cross-dimension transport and physical multiplayer remain unverified.' if (DEST / 'prospector-client/source-hashes.json').is_file() else 'not included', 'extendedae': 'extendedae-client/ records the original native item, host, menu and screen, scope changes from an explicit reset, input-only multiply C2S/S2C, native page switch, physical middle-click popup, typed Long.MAX_VALUE through native AE2 number entry and SET_FILTER packets, native replace packets and retained output/name metadata. Four screenshots check the current integrated client. Complete crafting-pattern replacement and physical multiplayer remain unverified.' if (DEST / 'extendedae-client/source-hashes.json').is_file() else 'not included', 'ae_widgets': 'ae-widgets-client/ records the two original GT input bus/hatch registrations, actual native menus, component-sensitive long configuration and stock S2C, physical keyboard popup submission and wheel changes through actual native C2S/S2C, item/fluid JEI clickable ingredients without changing ghost-item class, stock hover and native stack-display rendering with bucket units. Five screenshots check the current client. Full Core/Additions registration, physical multiplayer, EMI ghost drops, REI integration and physical dual-stock amount popup input remain unverified.' if (DEST / 'ae-widgets-client/source-hashes.json').is_file() else 'not included', 'dynamic_animations': 'dynamic-render-client/ records three shared production model initializers generated by actual GTBlockstateProvider, six original OBJ bakes, native DynamicRender codec identity, actual machine model dispatch on explicit native fixture states, compiled harmony shader, direct native mesh compilation/upload/disposal, original range/global admission, six native PNG decodes and three non-missing atlas sprites, and repeat drawing after full resource reload. Original production IDs are mapped from generated resources by the recorded script. Full production registration, real controller states/structures, connected-world batching and optional shader packs remain unverified.' if (DEST / 'dynamic-render-client/source-hashes.json').is_file() else 'not included', 'avaritia': 'avaritia-client/ records fifty unchanged production model JSON files, SHA256 checked against native runtime resources, actual CosmicBakeModel/HaloBakedModel types, registered native cosmic shader and all fifty native item-renderer draws over sixty frames in a bounded client grid. Uses the published Re:Avaritia NeoForge 1.4.2 artifact. Full production material registration, external KubeJS mask textures and physical armor rendering remain unverified.' if (DEST / 'avaritia-client/source-hashes.json').is_file() else 'not included', 'chamber_configurators': 'chamber-configurator-client/ records the actual production embedded widget host, original wireless textures, all three native panels, real mouse/keyboard edits of zero/maximum/cleared circuits, maximum integer interval and 256-character Unicode tags, scaled bounds and parent menu retention. Commands are applied on an actual integrated server through explicit fixture callbacks. Full chamber terminal permission checks and physical terminal packet traffic remain unverified.' if (DEST / 'chamber-configurator-client/source-hashes.json').is_file() else 'not included', 'advanced_fluid_rig': 'advanced-fluid-rig-client/ independently records the actual production advanced rig menu, server-owned heat/mode/exact-long display, physical fast toggle C2S/S2C, real component-bearing drill-head pickup and return, physical power clicks completing exactly one fully paid cycle, both wrapped long fluid rows reached by native wheel input, and controller/head/mode/heat reload. Seven screenshots and actual title/sidebar/main-panel bounds are checked at GUI scale two on an 854x480 window. Sparse material/coil fixtures and reduced structure are separate; complete materials, original production registration/full structure and physical multiplayer remain unverified.' if (DEST / 'advanced-fluid-rig-client/source-hashes.json').is_file() else 'not included',
                          'fluid_rigs': 'fluid-rig-client/ independently records three actual production Core infinite-rig menus, normal/overclocked/long readonly production S2C, physical native power-button C2S/S2C, one fully paid cycle and entity reload. Nine screenshots and bounds checks include the native title bar and sidebars at GUI scale 2 on an 854x480 window. Fixture registration IDs and reduced structures are separate; original production registration, full structures and physical multiplayer remain unverified.' if (DEST / 'fluid-rig-client/source-hashes.json').is_file() else 'not included',
                          'super_patterns': 'super-pattern-client/ independently records three actual production Additions menus with 162, 1800 and one native pattern slots, physical FOA toggle and page mouse clicks, multiplier C2S/SaveField S2C, both viewport axes, final encoded pattern component/output rendering, final-slot catalyst middle-click and bounded GUI scale 2 layouts. Fixture registration IDs are separate; complete production registration, full FOA controller/module and physical multiplayer remain unverified.' if (DEST / 'super-pattern-client/source-hashes.json').is_file() else 'not included',
                          'gt_output': 'gt-output-client/ independently records actual GT item/fluid and GTMThings dual output menus through strict production Core mixins, twelve component keys per native readonly list, exact long S2C and last-row hover tooltips, physical wheel access, return-button C2S/readiness S2C, integer refresh popup C2S/S2C and bounded GUI scale 2 layouts. Fixture registration IDs are separate; complete production registration and physical multiplayer remain unverified.' if (DEST / 'gt-output-client/source-hashes.json').is_file() else 'not included',
                          'multiblock_preview': 'preview-client/ independently records production LDLib scenes embedded in actual native ModularUI, native GT EBF/tower definitions, actual page/fullscreen/pan input, parent restoration, real GT JEI category layouts with live exact material/component queries, MeshData world ghosts, native layer cycling, expiration, registered custom world shells, all native JEI recipe/category ID comparisons and resource-pack reload/rebuild. Full Core registrations/modules, simulator preview, optional EMI/REI runtime and physical multiplayer remain unverified.' if (DEST / 'preview-client/source-hashes.json').is_file() else 'not included',
                          'wildcard_buffers': 'wildcard-client/ independently records both production menus, component-bearing long input previews and twenty-tick server rotation, read-only ingredient cells, visibility and rename C2S/S2C, all four stock pages and final configured fluid position, reload, interval C2S, screen bounds and exact long fluid hover text. The supplied external item uses its original model; its mod/editor and complete production registrations remain unverified.' if (DEST / 'wildcard-client/source-hashes.json').is_file() else 'not included',
                          'performance_monitor': 'performance-client/ independently records twelve deterministic server timing rows through the real native menu S2C path, equal-time entries, periodic list refresh and disposal, actual wheel scrolling and mouse clicks reaching ExtendedAE/Glodium dimension/position highlighting with a fifteen-second deadline, and bounded GUI scale 2 layouts. Native tick metering, startup count/time budgets, critical exemptions, dirty/save handling, tool cycle pause and LDLib async server-thread dispatch have separate world tests. Full production registrations and physical multiplayer remain unverified.' if (DEST / 'performance-client/source-hashes.json').is_file() else 'not included',
                          'recipe_viewer': 'client/ has independent screenshots/logs/source-hashes from actual JEI render validation; these predate the later machine-runtime ports',
                          'recipe_modes': 'recipe-mode-client/ independently records real production native machine mode popups, six visible rows with actual screen wheel access to the seventh, mouse selection C2S, selected-row S2C and actual saved entity recreation followed by reopening the popup. Full production registration and physical multiplayer remain unverified.' if (DEST / 'recipe-mode-client/source-hashes.json').is_file() else 'not included',
                          'configurator_columns': 'configurator-columns-client/ independently records the production nine-control pattern-buffer menu arranged into three native columns, actual mouse clicks opening first/third-column popups, second-column visibility C2S/SaveField S2C, native name editing C2S/SaveField S2C and bounded main panels/popups/controls at GUI scale 2, 108 real pattern slots, native wheel scrolling to the last row, its middle-click catalyst popup and authoritative page C2S/S2C with the final encoded pattern preserved. Full production registrations, wildcard buffers and physical multiplayer remain unverified.' if (DEST / 'configurator-columns-client/source-hashes.json').is_file() else 'not included',
                          'machine_menu': 'machine-client/ has independent actual native ModularUI C2S click and SaveField restore logs/screenshots/source-hashes; its earlier source hashes are retained separately' if (DEST / 'machine-client/source-hashes.json').is_file() else 'not included',
                          'material_renderers': 'renderer-client/ records native LDLib provider dispatch on a registered rotating material item with a fixture model, special-model generation protection and real production virtual item/fluid/unbound sprites over 60 frames; does not verify Avaritia model loaders' if (DEST / 'renderer-client/source-hashes.json').is_file() else 'not included',
                          'item_menus': 'item-menus-client/ independently records six real native menus rendered from production behaviors/parts on an integrated server, offhand opening, native screen input events for page/rotation C2S, slot 63 stock S2C and bounded layouts. Terminal tier choices use fixture blocks; full production registration bindings and physical multiplayer are unverified.' if (DEST / 'item-menus-client/source-hashes.json').is_file() else 'not included',
                          'maintenance': 'maintenance-client/ independently records four actual production maintenance menus on an integrated server, real screen mouse events for duration C2S and native gravity value C2S/SaveField S2C. Full production machine registration and physical multiplayer remain unverified.' if (DEST / 'maintenance-client/source-hashes.json').is_file() else 'not included',
                          'tag_stocking': 'tag-stock-client/ independently records both production tag stocking machine menus and tag popups on a real integrated server, actual native screen mouse events for sorting C2S, whitelist S2C and bounded panels. Remote chamber terminals and physical multiplayer remain unverified.' if (DEST / 'tag-stock-client/source-hashes.json').is_file() else 'not included'},
        'unverified': ['Full production compilation/startup and remaining registration/recipe/structure bindings',
                       'Actual connected-client Jade tooltip rendering and AE infinity-cell terminal display/creative clone beyond the tested native server menu and disk codecs',
                       'Actual planet dimension loading and bedrock-fluid drilling on those planets; original definition filters are validated against native overworld registries',
                       'Simulator full connected-client 3D preview, modules, GUI interactions and original production registration',
                       'Transfinite AE CPU controller/interface popup, full submitted crafting jobs and graph executor integration; only graph-job serialization has dedicated native JUnit validation',
                       'Complete Additions MutableRecipesLogic and its native search callers; current multiple-mode search validation uses its public interface contract on a native RecipeLogic fixture',
                       'Rapid upload selection menu/screen and complete wireless-terminal entrypoint, native alternate tag ingredient matching beyond representative recipe signatures, and physical multiplayer',
                       'Production-wide component fluid index performance and complete chamber terminal permission/physical packet traffic beyond tested native editors',
                       'AE CPU submitted crafting jobs, the external wildcard editor and complete production wildcard registration, remaining wireless terminal paths and physical multiplayer connections',
                       'Ordinary stocking-pattern-buffer/virtual-supply menus and recipe-viewer ghost dragging beyond the independently archived native client cases',
                       'Configuration-copy client interactions and tool GUI interactions beyond the independently recorded item menu render/page/rotation checks',
                       'Dynamic animations full production registration and controller states/structures, connected-world GPU batching and optional shader-pack interoperability',
                       'Avaritia full production material registration/model overrides, external KubeJS cosmic mask textures, and physical armor entity rendering beyond the fifty independently validated native model bakes and renders',
                       'Molecular GUI actual client rendering, shared-tools popup and remote pattern inventories beyond the tested native menu packet handlers',
                       'Extended/filtered/async ME output GUI client rendering, fluid ghost drag/click networking and physical client/server data-stick interaction beyond tested native server handlers; GT/GTMThings native output menus have independent client evidence',
                       'All production machine GUIs and third-party integrations; fixture IDs/assets do not replace production IDs/assets',
                       'Mixed material and EU/CWU tick routes and long non-OR chance logic beyond native int run limits'],
        'inputs_sha256': source, 'evidence_sha256': evidence,
    }
    (DEST / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + '\n')
    print(json.dumps(dict(unit=totals, gametests=expected, inputs=len(source), evidence=len(evidence)), ensure_ascii=False))
if __name__ == '__main__': main()
