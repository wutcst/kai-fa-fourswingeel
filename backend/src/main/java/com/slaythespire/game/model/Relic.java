package com.slaythespire.game.model;

public interface Relic {
    String getId();
    String getName();
    String getDescription();

    default void onTurnEnd(Combatant owner) {}
    default void onTurnStart(Combatant owner) {}
    default int onDamageTaken(int amount, Combatant owner) { return amount; }
    default int onDamageDealt(int amount, Combatant source, Combatant target) { return amount; }
}