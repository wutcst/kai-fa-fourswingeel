package com.slaythespire.game.model;

import com.slaythespire.game.model.factory.StatusFactory;
import com.slaythespire.repository.EnemyTemplate;
import com.slaythespire.repository.GameDataRepository;
import com.slaythespire.repository.IntentTemplate;
import com.slaythespire.repository.IntentType;

import java.util.List;
import java.util.Map;

public class Enemy extends Combatant {
    private String name;
    private final List<IntentTemplate> intentSequence;
    private int currentTurn;
    private IntentTemplate currentIntent;
    private GameDataRepository dataRepo;

    public Enemy(EnemyTemplate template, GameDataRepository dataRepo) {
        super(template.getMaxHp(), template.getMaxHp());
        this.dataRepo = dataRepo;
        this.name = template.getName();
        this.intentSequence = template.getIntents();
        this.currentTurn = 0;
        
        if (template.getInitialStatuses() != null) {
            for (Map.Entry<String, Integer> entry : template.getInitialStatuses().entrySet()) {
                StatusEffect status = StatusFactory.create(entry.getKey(), entry.getValue(), dataRepo);
                if (status != null) this.addStatus(status);
            }
        }
        updateCurrentIntent();
    }

    @Override
    public void onTurnStart() { clearBlock(); }

    public int executeCurrentIntent(Combatant target) {
        if (currentIntent == null) return 0;
        if (currentIntent.getType() == IntentType.ATTACK) return target.takeDamage(currentIntent.getValue(), this);
        if (currentIntent.getType() == IntentType.DEFEND) { this.gainBlock(currentIntent.getValue()); return 0; }
        return 0;
    }

    public void advanceIntent() { currentTurn++; updateCurrentIntent(); }
    public String getEnemyName() { return name; }
    public IntentTemplate getCurrentIntentTemplate() { return currentIntent; }
    public String getIntentDesc() { return currentIntent != null ? currentIntent.getDesc() : "待机"; }

    public int getNextDamage() {
        if (currentIntent == null || currentIntent.getType() != IntentType.ATTACK) return 0;
        int dmg = currentIntent.getValue();
        for (StatusEffect s : statuses) dmg = s.onDamageDealt(dmg, this, null);
        return dmg;
    }

    public int getNextBlock() {
        if (currentIntent == null || currentIntent.getType() != IntentType.DEFEND) return 0;
        int blk = currentIntent.getValue();
        for (StatusEffect s : statuses) blk = s.onBlockGained(blk, this);
        return blk;
    }

    private void updateCurrentIntent() {
        if (intentSequence == null || intentSequence.isEmpty()) { this.currentIntent = null; return; }
        this.currentIntent = intentSequence.get(Math.floorMod(currentTurn, intentSequence.size()));
    }
}