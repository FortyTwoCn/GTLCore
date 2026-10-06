#!/usr/bin/env python3
"""Publish the three shared native model initializers under their original production IDs."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'migration/recipe-tests/build/generated-machine-models/assets/gtlcore_recipe_test'
TARGET = ROOT / 'src/main/resources/assets/gtceu'
MACHINES = {'annihilate': 'annihilate_generator', 'harmony': 'eye_of_harmony', 'elevator': 'space_elevator'}

for fixture, production in MACHINES.items():
    old_id = 'gtlcore_recipe_test:native_' + fixture + '_render'
    old_model = 'gtlcore_recipe_test:block/machine/native_' + fixture + '_render'
    new_id, new_model = 'gtceu:' + production, 'gtceu:block/machine/' + production
    for kind in ['blockstates', 'models/block/machine', 'models/item']:
        source = SOURCE / kind / ('native_' + fixture + '_render.json')
        data = json.loads(source.read_text())
        if kind == 'models/block/machine':
            if data.get('loader') != 'gtceu:machine' or data.get('machine') != old_id:
                raise SystemExit('Unexpected native generator output: ' + str(source))
            if data.get('dynamic_renders') != [{'type': 'gtlcore:' + production}]:
                raise SystemExit('Production renderer type differs: ' + str(source))
        text = json.dumps(data, indent=2).replace(old_model, new_model).replace(old_id, new_id) + '\n'
        target = TARGET / kind / (production + '.json')
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)
print('GTL_NATIVE_ANIMATION_ASSETS=PASS machines=3 resources=9 original_ids=true native_generator=true')
