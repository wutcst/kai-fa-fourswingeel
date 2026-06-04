package com.slaythespire.game.model;

import java.util.EnumMap;
import java.util.Map;

/**
 * 玩家运行时实例 - 管理血量、格挡、状态效果
 */
public class Player {
    private int hp;
    private int maxHp;
    private int block;
    private Map<StatusType, Integer> statuses;

    public Player(int hp) {
        this(hp, hp);
    }

    public Player(int hp, int maxHp) {
        this.maxHp = Math.max(1, maxHp);
        this.hp = Math.max(0, Math.min(hp, this.maxHp));
        this.block = 0;
        this.statuses = new EnumMap<>(StatusType.class);
    }

    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) {
        this.maxHp = Math.max(1, maxHp);
        if (this.hp > this.maxHp) this.hp = this.maxHp;
    }
    public int getBlock() { return block; }
    public Map<StatusType, Integer> getStatuses() { return statuses; }

    public void addStatus(StatusType type, int count) {
        int current = statuses.getOrDefault(type, 0);
        statuses.put(type, current + count);
    }

    /**
     * 受到伤害：优先消耗格挡，并应用【易伤】效果（50% 伤害，向上取整）
     * @return 实际受到的伤害值（用于日志显示）
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

    /**
     * 获得格挡：应用【脆弱】效果（25% 减少，向下取整）
     */
    public void addBlock(int amount) {
        if (amount <= 0) return;
        if (statuses.getOrDefault(StatusType.FRAIL, 0) > 0) {
            amount = (int) Math.floor(amount * 0.75);
        }
        block += amount;
    }

    /**
     * 修正攻击伤害：应用【虚弱】效果（25% 减少，向下取整）
     */
    public int modifyDamage(int originalDamage) {
        if (originalDamage <= 0) return originalDamage;
        if (statuses.getOrDefault(StatusType.WEAK, 0) > 0) {
            return (int) Math.floor(originalDamage * 0.75);
        }
        return originalDamage;
    }

    /**
     * ✅ 回合结束结算：状态层数 -1（格挡保留到玩家下回合开始前）
     */
    public void onTurnEnd() {
        for (StatusType type : StatusType.values()) {
            int count = statuses.getOrDefault(type, 0);
            if (count > 0) statuses.put(type, count - 1);
        }
    }

    /**
     * ✅ 新增：玩家下回合开始前（抽取新牌、重置能量后调用）
     * 此时清空上回合遗留的格挡
     */
    public void onTurnStart() {
        block = 0;
    }

    public void heal(int amount) {
        if (amount < 0) return;
        hp = Math.min(hp + amount, maxHp);
    }

    public boolean isAlive() { return hp > 0; }

    @Override
    public String toString() {
        return String.format("Player{hp=%d/%d, block=%d, statuses=%s}", hp, maxHp, block, statuses);
    }
}