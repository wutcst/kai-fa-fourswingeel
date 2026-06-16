package com.slaythespire.game.model;

import com.slaythespire.repository.RelicTemplate;

public class GameRelic implements Relic {
    private final String id;
    private final String name;
    private final String description;
    private final String effectType;
    private final int value;

    public GameRelic(RelicTemplate template) {
        this.id = template.getId();
        this.name = template.getName();
        this.description = template.getDescription();
        this.effectType = template.getEffectType();
        this.value = template.getValue();
    }

    @Override public String getId() { return id; }
    @Override public String getName() { return name; }
    @Override public String getDescription() { return description; }
    public String getEffectType() { return effectType; }
    public int getValue() { return value; }

    @Override
    public void onTurnStart(Combatant owner) {
        if ("NO_EFFECT".equals(effectType)) return;
        if ("HEAL_START_TURN".equals(effectType)) owner.heal(value);
        if ("BLOCK_START_TURN".equals(effectType)) owner.gainBlock(value);
    }

    @Override
    public void onTurnEnd(Combatant owner) {
        if ("NO_EFFECT".equals(effectType)) return;
        if ("HEAL_END_TURN".equals(effectType)) owner.heal(value);
        if ("BLOCK_END_TURN".equals(effectType)) owner.gainBlock(value);
    }

    @Override
    public int onDamageTaken(int amount, Combatant owner) {
        if ("NO_EFFECT".equals(effectType)) return amount;
        if ("DAMAGE_CAP".equals(effectType)) return Math.min(amount, value);
        // DAMAGE_CAP_PER_TURN 在 Combatant.takeDamage() 中处理
        return amount;
    }
}