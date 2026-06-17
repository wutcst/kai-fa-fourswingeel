package com.slaythespire.game.model;

import com.slaythespire.game.model.factory.StatusFactory;
import com.slaythespire.repository.StatusTemplate;

public class GameStatus implements StatusEffect {
    private final String id;
    private final String name;
    private final String effectType;
    private final double value;
    private final boolean decay;
    private int count;

    public GameStatus(StatusTemplate template, int count) {
        this.id = template.getId();
        this.name = template.getName();
        this.effectType = template.getEffectType();
        this.value = template.getValue();
        this.decay = template.isDecay();
        this.count = count;
    }

    @Override public String getId() { return id; }
    @Override public String getName() { return name; }
    @Override public int getCount() { return count; }
    @Override public void setCount(int count) { this.count = count; }
    @Override public void decrement() { if (decay) count--; }

    @Override
    public int onDamageTaken(int amount, Combatant source, Combatant target) {
        if ("INCOMING_DAMAGE_MULTIPLIER".equals(effectType)) return (int) Math.ceil(amount * value);
        return amount;
    }

    @Override
    public int onDamageDealt(int amount, Combatant source, Combatant target) {
        if ("OUTGOING_DAMAGE_MULTIPLIER".equals(effectType)) return (int) Math.floor(amount * value);
        if ("OUTGOING_DAMAGE_FLAT".equals(effectType)) return amount + (int) (value * count);
        return amount;
    }

    @Override
    public int onBlockGained(int amount, Combatant target) {
        if ("BLOCK_GAIN_MULTIPLIER".equals(effectType)) return (int) Math.floor(amount * value);
        if ("OUTGOING_BLOCK_FLAT".equals(effectType)) return amount + (int) (value * count);
        return amount;
    }

    @Override
    public String onTurnStart(Combatant owner) {
        if ("TURN_START_GAIN_STRENGTH".equals(effectType)) {
            int amount = (int) (value * count); 
            // 通过 owner 获取 dataRepo 来创建力量状态
            StatusEffect strength = StatusFactory.create("STRENGTH", amount, owner.getDataRepo());
            if (strength != null) {
                owner.addStatus(strength);
                return "🔥 " + name + "生效，获得 " + amount + " 点力量";
            }
        }
        return null;
    }

    // 处理回合结束时的效果（毒、再生、多层护甲）
    @Override
    public String onTurnEnd(Combatant owner) {
        if ("TURN_END_DAMAGE".equals(effectType)) {
            int dmg = (int) (value * count);
            owner.takeDamage(dmg, null, true);
            return "☠️ " + name + "发作，造成 " + dmg + " 点伤害";
        }
        if ("TURN_END_HEAL".equals(effectType)) {
            int healAmount = (int) (value * count);
            owner.heal(healAmount);
            return "🌿 " + name + "恢复 " + healAmount + " 点生命";
        }
        // 🆕 多层护甲：回合结束时获得等量格挡
        if ("GAIN_BLOCK_PER_TURN".equals(effectType)) {
            int blockAmt = (int) (value * count);
            if (blockAmt > 0) {
                owner.gainBlock(blockAmt);
                return "🛡️ " + name + "提供 " + blockAmt + " 点格挡";
            }
        }
        return null;
    }

    // 🆕 受到生命伤害时减少层数（多层护甲、分裂判断等）
    @Override
    public String onHpLost(Combatant owner) {
        if ("GAIN_BLOCK_PER_TURN".equals(effectType)) {
            // 多层护甲：每次受到生命伤害减少1层
            count--;
            if (count <= 0) {
                return "💔 " + name + "被击破！";
            }
        }
        return null;
    }
}