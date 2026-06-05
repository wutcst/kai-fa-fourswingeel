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
    default void onTurnEnd(Combatant owner) {}
}