package com.slaythespire.game.model;

import com.slaythespire.repository.EnemyTemplate;
import com.slaythespire.repository.IntentTemplate;
import com.slaythespire.repository.IntentType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Enemy {
    private String name;
    private int hp;
    private int maxHp;
    
    private final List<IntentTemplate> intentSequence;
    private int currentTurn;
    private IntentTemplate currentIntent;
    private final Random random = new Random();
    private Map<StatusType, Integer> statuses;

    public Enemy(EnemyTemplate template) {
        this.name = template.getName();
        this.maxHp = template.getMaxHp();
        this.hp = this.maxHp;
        this.intentSequence = template.getIntents();
        this.currentTurn = 0;
        this.currentIntent = null;
        this.statuses = new EnumMap<>(StatusType.class);
        
        if (template.getInitialStatuses() != null) {
            for (Map.Entry<String, Integer> entry : template.getInitialStatuses().entrySet()) {
                try {
                    StatusType type = StatusType.valueOf(entry.getKey());
                    if (entry.getValue() > 0) this.addStatus(type, entry.getValue());
                } catch (IllegalArgumentException ignored) {}
            }
        }

        updateCurrentIntent();
    }

    public int executeCurrentIntent() {
        if (currentIntent == null) return 0;
        switch (currentIntent.getType()) {
            case ATTACK: return modifyDamage(currentIntent.getValue());
            default: return 0;
        }
    }

    public void advanceIntent() {
        currentTurn++;
        updateCurrentIntent();
    }

    public void addStatus(StatusType type, int count) {
        int current = statuses.getOrDefault(type, 0);
        statuses.put(type, current + count);
    }

    /**
     * 受到伤害：应用【易伤】效果
     * @return 实际受到的伤害值
     */
    public int takeDamage(int dmg) {
        if (dmg < 0) return 0;
        int actualDmg = calculateIncomingDamage(dmg);
        hp = Math.max(0, hp - actualDmg);
        return actualDmg;
    }

    private int calculateIncomingDamage(int dmg) {
        if (dmg <= 0) return 0;
        if (statuses.getOrDefault(StatusType.VULNERABLE, 0) > 0) {
            return (int) Math.ceil(dmg * 1.5);
        }
        return dmg;
    }

    public int modifyDamage(int originalDamage) {
        if (originalDamage <= 0) return originalDamage;
        if (statuses.getOrDefault(StatusType.WEAK, 0) > 0) {
            return (int) Math.floor(originalDamage * 0.75);
        }
        return originalDamage;
    }

    public void onTurnEnd() {
        for (StatusType type : StatusType.values()) {
            int count = statuses.getOrDefault(type, 0);
            if (count > 0) statuses.put(type, count - 1);
        }
    }

    public String getName() { return name; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public IntentTemplate getCurrentIntentTemplate() { return currentIntent; }
    public Map<StatusType, Integer> getStatuses() { return statuses; }
    
    public String getIntentDesc() { return currentIntent != null ? currentIntent.getDesc() : "待机"; }
    public int getNextDamage() {
        if (currentIntent == null || currentIntent.getType() != IntentType.ATTACK) return 0;
        return modifyDamage(currentIntent.getValue());
    }
    public boolean isAlive() { return hp > 0; }

    private void updateCurrentIntent() {
        if (intentSequence == null || intentSequence.isEmpty()) {
            this.currentIntent = null;
            return;
        }
        int index = Math.floorMod(currentTurn, intentSequence.size());
        this.currentIntent = intentSequence.get(index);
    }
}