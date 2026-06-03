package com.slaythespire.repository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.slaythespire.game.model.Card.CardType;

public class CardTemplate {
    private final String id;
    private final String name;
    private final int cost;
    private final int damage;
    private final int block;
    private final CardType type;

    @JsonCreator
    public CardTemplate(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("cost") int cost,
            @JsonProperty("damage") int damage,
            @JsonProperty("block") int block,
            @JsonProperty("type") CardType type) {
        this.id = id;
        this.name = name;
        this.cost = cost;
        this.damage = damage;
        this.block = block;
        this.type = type;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getCost() { return cost; }
    public int getDamage() { return damage; }
    public int getBlock() { return block; }
    public CardType getType() { return type; }

    @Override
    public String toString() {
        return String.format("CardTemplate{id='%s', name='%s', cost=%d, type=%s}", id, name, cost, type);
    }
}