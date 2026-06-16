package com.slaythespire.game.model;

import java.util.ArrayList;
import java.util.List;
import com.slaythespire.repository.GameDataRepository;

public abstract class Combatant {
    protected int hp;
    protected int maxHp;
    protected int block = 0;
    protected List<StatusEffect> statuses = new ArrayList<>();
    protected List<Relic> relics = new ArrayList<>();
    
    protected List<String> lastTurnEndLogs = new ArrayList<>();
    protected List<String> turnStartLogs = new ArrayList<>();
    protected List<String> lastCombatLogs = new ArrayList<>();

    public Combatant(int hp, int maxHp) {
        this.maxHp = maxHp;
        this.hp = Math.max(0, Math.min(hp, maxHp));
    }

    public void addStatus(StatusEffect status) {
        for (StatusEffect s : statuses) {
            if (s.getId().equals(status.getId())) { 
                s.setCount(s.getCount() + status.getCount()); 
                return; 
            }
        }
        statuses.add(status);
    }
    
    public void addRelic(Relic relic) { relics.add(relic); }
    public List<Relic> getRelics() { return relics; }
    public List<StatusEffect> getStatuses() { return statuses; }
    
    public List<String> getLastTurnEndLogs() { 
        List<String> copy = new ArrayList<>(lastTurnEndLogs);
        lastTurnEndLogs.clear();
        return copy; 
    }
    
    public List<String> getLastTurnStartLogs() {
        List<String> copy = new ArrayList<>(turnStartLogs);
        turnStartLogs.clear();
        return copy;
    }

    public List<String> getLastCombatLogs() {
        List<String> copy = new ArrayList<>(lastCombatLogs);
        lastCombatLogs.clear();
        return copy;
    }
    
    protected void addTurnStartLog(String log) {
        if (log != null) turnStartLogs.add(log);
    }

    public int takeDamage(int rawDamage, Combatant source) {
        return takeDamage(rawDamage, source, false);
    }

    public int takeDamage(int rawDamage, Combatant source, boolean ignoreBlock) {
        lastCombatLogs.clear();
        int finalDamage = rawDamage;
        
        if (source != null) {
            for (StatusEffect s : source.statuses) finalDamage = s.onDamageDealt(finalDamage, source, this);
            for (Relic r : source.relics) finalDamage = r.onDamageDealt(finalDamage, source, this);
        }
        for (StatusEffect s : this.statuses) finalDamage = s.onDamageTaken(finalDamage, source, this);
        for (Relic r : this.relics) finalDamage = r.onDamageTaken(finalDamage, this);

        // ==========================================
        // 🛡️ 【无实体】判定：持续一回合，不消耗层数
        // ==========================================
        StatusEffect intangible = null;
        for (StatusEffect s : this.statuses) {
            if ("INTANGIBLE".equals(s.getId()) && s.getCount() > 0) {
                intangible = s;
                break;
            }
        }
        
        if (intangible != null && finalDamage > 1) {
            finalDamage = 1; // 伤害强制截断为 1
            // 🆕 不再减少层数，因为现在是“持续一回合”
            
            String entityName = (this instanceof Player) ? "玩家" : ((Enemy) this).getEnemyName();
            lastCombatLogs.add(String.format("👻 %s 的【无实体】生效，伤害被截断为 1", entityName));
        }

        int blocked = 0;
        if (!ignoreBlock) { 
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

    public void onTurnEnd() {
        lastTurnEndLogs.clear();
        for (Relic r : relics) {
            r.onTurnEnd(this);
        }
        List<StatusEffect> toRemove = new ArrayList<>();
        
        for (StatusEffect s : new ArrayList<>(statuses)) {
            // 🆕 【关键修改】跳过无实体的自动衰减，由 BattleService 在特定时机精确控制
            if ("INTANGIBLE".equals(s.getId())) continue; 
            
            String log = s.onTurnEnd(this); 
            if (log != null) lastTurnEndLogs.add(log);
            s.decrement();
            if (s.getCount() <= 0) toRemove.add(s);
        }
        statuses.removeAll(toRemove);
    }

    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }
    
    public int getBlock() { return block; }
    public void clearBlock() { block = 0; }
    public void heal(int amount) { if (amount > 0) hp = Math.min(hp + amount, maxHp); }
    public boolean isAlive() { return hp > 0; }
    
    public abstract void onTurnStart();
    public abstract GameDataRepository getDataRepo();
}