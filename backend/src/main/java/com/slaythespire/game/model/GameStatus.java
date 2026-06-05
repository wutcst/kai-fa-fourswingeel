package com.slaythespire.game.model;

import com.slaythespire.repository.StatusTemplate;

public class GameStatus implements StatusEffect {
    private final String id;
    private final String name;
    private final String effectType;
    private final double value;
    private final boolean decay; // ✅ 新增：读取是否衰减
    private int count;

    public GameStatus(StatusTemplate template, int count) {
        this.id = template.getId();
        this.name = template.getName();
        this.effectType = template.getEffectType();
        this.value = template.getValue();
        this.decay = template.isDecay(); // ✅ 赋值
        this.count = count;
    }

    @Override public String getId() { return id; }
    @Override public String getName() { return name; }
    @Override public int getCount() { return count; }
    @Override public void setCount(int count) { this.count = count; }
    
    // ✅ 核心修改：如果不衰减，则层数不减
    @Override 
    public void decrement() { 
        if (decay) count--; 
    }

    @Override
    public int onDamageTaken(int amount, Combatant source, Combatant target) {
        if ("INCOMING_DAMAGE_MULTIPLIER".equals(effectType)) return (int) Math.ceil(amount * value);
        return amount;
    }

    @Override
    public int onDamageDealt(int amount, Combatant source, Combatant target) {
        if ("OUTGOING_DAMAGE_MULTIPLIER".equals(effectType)) return (int) Math.floor(amount * value);
        if ("OUTGOING_DAMAGE_FLAT".equals(effectType)) return amount + (int) (value * count); // 力量
        return amount;
    }

    // ✅ 核心修改：增加敏捷 (OUTGOING_BLOCK_FLAT) 的处理
    @Override
    public int onBlockGained(int amount, Combatant target) {
        if ("BLOCK_GAIN_MULTIPLIER".equals(effectType)) return (int) Math.floor(amount * value); // 脆弱
        if ("OUTGOING_BLOCK_FLAT".equals(effectType)) return amount + (int) (value * count);     // 敏捷
        return amount;
    }

    @Override
    public void onTurnEnd(Combatant owner) {
        if ("TURN_END_DAMAGE".equals(effectType)) owner.takeDamage((int) value * count, null); // 毒
    }
}