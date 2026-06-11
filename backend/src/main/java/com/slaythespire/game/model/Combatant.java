package com.slaythespire.game.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 战斗实体基类 - 统一处理血量、格挡、状态和遗物的触发逻辑
 */
public abstract class Combatant {
    protected int hp;
    protected int maxHp;
    protected int block = 0;
    protected List<StatusEffect> statuses = new ArrayList<>();
    protected List<Relic> relics = new ArrayList<>();
    
    // ✅ 新增：用于收集回合结束时的日志
    protected List<String> lastTurnEndLogs = new ArrayList<>();

    public Combatant(int hp, int maxHp) {
        this.maxHp = maxHp;
        this.hp = Math.max(0, Math.min(hp, maxHp));
    }

    public void addStatus(StatusEffect status) {
        for (StatusEffect s : statuses) {
            if (s.getId().equals(status.getId())) { s.setCount(s.getCount() + status.getCount()); return; }
        }
        statuses.add(status);
    }
    
    public void addRelic(Relic relic) { relics.add(relic); }
    public List<Relic> getRelics() { return relics; }
    public List<StatusEffect> getStatuses() { return statuses; }
    public List<String> getLastTurnEndLogs() { return lastTurnEndLogs; }

    // ✅ 修改：增加 ignoreBlock 参数
    public int takeDamage(int rawDamage, Combatant source) {
        return takeDamage(rawDamage, source, false);
    }

    public int takeDamage(int rawDamage, Combatant source, boolean ignoreBlock) {
        int finalDamage = rawDamage;
        if (source != null) {
            for (StatusEffect s : source.statuses) finalDamage = s.onDamageDealt(finalDamage, source, this);
            for (Relic r : source.relics) finalDamage = r.onDamageDealt(finalDamage, source, this);
        }
        for (StatusEffect s : this.statuses) finalDamage = s.onDamageTaken(finalDamage, source, this);
        for (Relic r : this.relics) finalDamage = r.onDamageTaken(finalDamage, this);

        int blocked = 0;
        if (!ignoreBlock) { // ✅ 毒伤害无视格挡
            blocked = Math.min(block, finalDamage);
            block -= blocked;
        }
        hp = Math.max(0, hp - (finalDamage - blocked));
        return finalDamage;
    }

    public void gainBlock(int rawBlock) {
        int finalBlock = rawBlock;
        for (StatusEffect s : this.statuses) finalBlock = s.onBlockGained(finalBlock, this);
        block += finalBlock;
    }

    // ✅ 修改：收集状态和遗物产生的日志
    public void onTurnEnd() {
        lastTurnEndLogs.clear();
        for (Relic r : relics) {
            r.onTurnEnd(this);
        }
        List<StatusEffect> toRemove = new ArrayList<>();
        for (StatusEffect s : statuses) {
            String log = s.onTurnEnd(this); // ✅ 获取日志
            if (log != null) lastTurnEndLogs.add(log);
            s.decrement();
            if (s.getCount() <= 0) toRemove.add(s);
        }
        statuses.removeAll(toRemove);
    }

    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    
    // ✅ 新增：设置最大生命值
    public void setMaxHp(int maxHp) {
        this.maxHp = maxHp;
    }
    
    public int getBlock() { return block; }
    public void clearBlock() { block = 0; }
    public void heal(int amount) { if (amount > 0) hp = Math.min(hp + amount, maxHp); }
    public boolean isAlive() { return hp > 0; }
    public abstract void onTurnStart();
}