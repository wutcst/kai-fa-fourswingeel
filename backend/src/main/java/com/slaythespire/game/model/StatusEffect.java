package com.slaythespire.game.model;

public interface StatusEffect {
    String getId();
    String getName();
    int getCount();
    void setCount(int count);
    void decrement();

    
    default int onDamageTaken(int amount, Combatant source, Combatant target) { return amount; }
    default int onDamageDealt(int amount, Combatant source, Combatant target) { return amount; }
    default int onBlockGained(int amount, Combatant target) { return amount; }
    
    // ✅ 修改：返回日志字符串，如果没有动作则返回 null
    default String onTurnEnd(Combatant owner) { return null; }
    default String onTurnStart(Combatant owner) { return null; }
    
}