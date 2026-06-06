package com.slaythespire.game.model;

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

        // ✅ 核心修改：处理毒(无视格挡) 和 再生(回血)，并返回日志
    @Override
    public String onTurnEnd(Combatant owner) {
        if ("TURN_END_DAMAGE".equals(effectType)) {
            int dmg = (int) (value * count);
            owner.takeDamage(dmg, null, true); // 第三个参数 true 表示无视格挡
            return "☠️ " + name + "发作，造成 " + dmg + " 点伤害";
        }
        if ("TURN_END_HEAL".equals(effectType)) {
            int healAmount = (int) (value * count);
            owner.heal(healAmount);
            return "🌿 " + name + "恢复 " + healAmount + " 点生命";
        }
        return null;
    }
}