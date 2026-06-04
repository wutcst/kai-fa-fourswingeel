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
    private int block = 0; // ✅ 新增：怪物格挡
    
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
            case DEFEND: 
                addBlock(currentIntent.getValue()); // ✅ 支持怪物加盾意图
                return 0;
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

    public void addBlock(int amount) {
        if (amount > 0) block += amount;
    }

    /**
     * 受到伤害：应用【易伤】效果，优先消耗格挡
     * @return 实际受到的伤害值
     */
    public int takeDamage(int dmg) {
        if (dmg < 0) return 0;
        int actualDmg = calculateIncomingDamage(dmg);
        
        int blocked = Math.min(block, actualDmg);
        block -= blocked;
        hp = Math.max(0, hp - (actualDmg - blocked));
        
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

    /**
     * ✅ 回合结束结算：状态延迟衰减（标记为负数，下回合开始前真正扣除）
     */
    public void onTurnEnd() {
        for (StatusType type : StatusType.values()) {
            int count = statuses.getOrDefault(type, 0);
            if (count > 0) statuses.put(type, -count); 
        }
    }

    /**
     * ✅ 新增：怪物下回合行动前调用
     * 1. 清空怪物上回合遗留的格挡
     * 2. 结算状态的真正衰减
     */
    public void onTurnStart() {
        block = 0; 

        for (StatusType type : StatusType.values()) {
            int count = statuses.getOrDefault(type, 0);
            if (count < 0) {
                int realCount = Math.abs(count);
                int newCount = realCount - 1;
                if (newCount > 0) {
                    statuses.put(type, newCount); 
                } else {
                    statuses.remove(type); 
                }
            }
        }
    }

    public String getName() { return name; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public int getBlock() { return block; }
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