package com.slaythespire.game.model;

import com.slaythespire.repository.RelicTemplate;

/**
 * 遗物运行时实例 — 仅保存数据，效果由 RelicEffectHandler 统一处理
 */
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

    // 效果逻辑已移至 RelicEffectHandler，这些接口方法保留空实现
    @Override public void onTurnStart(Combatant owner) {}
    @Override public void onTurnEnd(Combatant owner) {}
    @Override public int onDamageTaken(int amount, Combatant owner) { return amount; }
    @Override public int onDamageDealt(int amount, Combatant source, Combatant target) { return amount; }
}
