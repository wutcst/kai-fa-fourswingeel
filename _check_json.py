import json
e = json.load(open('backend/src/main/resources/config/enemies.json', encoding='utf-8'))
g = json.load(open('backend/src/main/resources/config/enemy_groups.json', encoding='utf-8'))
print(f"enemies.json: {len(e)} monsters")
for x in e:
    print(f"  [{x['type']:6s}] {x['name']} (HP:{x['maxHp']})")
print(f"\nenemy_groups.json: {len(g)} groups")
for x in g:
    print(f"  [{x['type']:6s}] {x['name']} ({len(x['enemies'])}只)")
