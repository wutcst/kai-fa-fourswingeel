package com.slaythespire.repository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EnemyTemplate {
    private final String id;
    private final String name;
    private final int maxHp;
    private final int attackDamage;

    @JsonCreator
    public EnemyTemplate(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("maxHp") int maxHp,
            @JsonProperty("attackDamage") int attackDamage) {
        this.id = id;
        this.name = name;
        this.maxHp = maxHp;
        this.attackDamage = attackDamage;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getMaxHp() { return maxHp; }
    public int getAttackDamage() { return attackDamage; }

    @Override
    public String toString() {
        return String.format("EnemyTemplate{id='%s', name='%s', hp=%d, atk=%d}", id, name, maxHp, attackDamage);
    }
}