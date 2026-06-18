import json

files = [
    'D:/kai-fa-fourswingeel/backend/src/main/java/com/slaythespire/game/model/GameStatus.java',
    'D:/kai-fa-fourswingeel/backend/src/main/java/com/slaythespire/game/model/StatusEffect.java',
    'D:/kai-fa-fourswingeel/backend/src/main/java/com/slaythespire/game/model/Combatant.java',
    'D:/kai-fa-fourswingeel/backend/src/main/resources/config/statuses.json',
    'D:/kai-fa-fourswingeel/backend/src/main/resources/config/enemies.json',
]
for f in files:
    with open(f, 'r', encoding='utf-8') as fh:
        c = fh.read()
    if f.endswith('.json'):
        json.loads(c)
        print(f'  ✅ {f.split(chr(92))[-1]}: Valid JSON')
    else:
        depth = 0
        for ch in c:
            if ch == '{': depth += 1
            elif ch == '}': depth -= 1
        print(f'  {\"✅\" if depth==0 else \"❌\"} {f.split(chr(92))[-1]}: braces={depth}')
print('Done')
