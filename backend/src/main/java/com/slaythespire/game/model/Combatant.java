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

    /** 本回合已扣除的实际生命（用于每回合伤害上限遗物） */
    protected int actualDamageTakenThisTurn = 0;

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
        int hpLost = finalDamage - blocked;

        // 每回合实际扣血上限（魔法护盾类遗物）
        int capPerTurn = getDamageCapPerTurn();
        if (capPerTurn > 0) {
            int remaining = Math.max(0, capPerTurn - actualDamageTakenThisTurn);
            if (hpLost > remaining) hpLost = remaining;
        }
        actualDamageTakenThisTurn += hpLost;

        hp = Math.max(0, hp - hpLost);

        // ================= Boss 遗物效果：伤害相关 =================
        // 🩸 灵魂之炉：每场战斗首次受伤时恢复生命（仅限玩家）
        if (hpLost > 0 && source != null && this instanceof Player) {
            Player p = (Player) this;
            if (!p.isFirstDamageTakenThisBattle() && RelicEffectHandler.hasEffect(this, "FIRST_HIT_HEAL")) {
                int healAmt = RelicEffectHandler.getEffectValue(this, "FIRST_HIT_HEAL");
                p.heal(healAmt);
                p.markFirstDamageTaken();
                lastCombatLogs.add("🔥 灵魂之炉触发，恢复 " + healAmt + " 点生命");
            }
        }

        // 🪶 凤凰之羽：每场战斗首次濒死时恢复至百分比生命（仅限玩家）
        if (hp <= 0 && this instanceof Player) {
            Player p = (Player) this;
            if (!p.isDeathSaveUsedThisBattle() && RelicEffectHandler.hasEffect(this, "DEATH_SAVE")) {
                int pct = Math.max(1, Math.min(100, RelicEffectHandler.getEffectValue(this, "DEATH_SAVE")));
                hp = Math.max(1, maxHp * pct / 100);
                p.markDeathSaveUsed();
                lastCombatLogs.add("🪶 凤凰之羽触发，恢复至 " + hp + " 点生命（" + pct + "%）");
            }
        }

        // 荆棘甲反伤（对攻击者造成固定伤害）
        if (hpLost > 0 && source != null) {
            RelicEffectHandler.handleThornsDamage(source, this);
        }

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

    /** 检查遗物中的每回合伤害上限（委托给处理器） */
    private int getDamageCapPerTurn() {
        return RelicEffectHandler.getDamageCapPerTurn(this);
    }
    
    public abstract void onTurnStart();
    public abstract GameDataRepository getDataRepo();
}