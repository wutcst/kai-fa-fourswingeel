package com.slaythespire.game.model;

import java.util.ArrayList;
import java.util.List;

public abstract class Combatant {
    protected int hp;
    protected int maxHp;
    protected int block = 0;
    protected List<StatusEffect> statuses = new ArrayList<>();
    protected List<Relic> relics = new ArrayList<>();

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

    public int takeDamage(int rawDamage, Combatant source) {
        int finalDamage = rawDamage;
        if (source != null) {
            for (StatusEffect s : source.statuses) finalDamage = s.onDamageDealt(finalDamage, source, this);
            for (Relic r : source.relics) finalDamage = r.onDamageDealt(finalDamage, source, this);
        }
        for (StatusEffect s : this.statuses) finalDamage = s.onDamageTaken(finalDamage, source, this);
        for (Relic r : this.relics) finalDamage = r.onDamageTaken(finalDamage, this);

        int blocked = Math.min(block, finalDamage);
        block -= blocked;
        hp = Math.max(0, hp - (finalDamage - blocked));
        return finalDamage;
    }

    public void gainBlock(int rawBlock) {
        int finalBlock = rawBlock;
        for (StatusEffect s : this.statuses) finalBlock = s.onBlockGained(finalBlock, this);
        block += finalBlock;
    }

    public void onTurnEnd() {
        for (Relic r : relics) r.onTurnEnd(this);
        List<StatusEffect> toRemove = new ArrayList<>();
        for (StatusEffect s : statuses) {
            s.onTurnEnd(this);
            s.decrement();
            if (s.getCount() <= 0) toRemove.add(s);
        }
        statuses.removeAll(toRemove);
    }

    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public int getBlock() { return block; }
    public void clearBlock() { block = 0; }
    public void heal(int amount) { if (amount > 0) hp = Math.min(hp + amount, maxHp); }
    public boolean isAlive() { return hp > 0; }
    public abstract void onTurnStart();
}