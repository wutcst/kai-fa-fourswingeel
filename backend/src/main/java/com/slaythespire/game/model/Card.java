package com.slaythespire.game.model;

public class Card {
    private String name;
    private int cost;
    private int damage;
    private int block;
    private CardType type;

    public enum CardType {
        ATTACK, SKILL
    }

    public Card(String name, int cost, int damage, int block, CardType type) {
        this.name = name;
        this.cost = cost;
        this.damage = damage;
        this.block = block;
        this.type = type;
    }

    public String getName() { return name; }
    public int getCost() { return cost; }
    public int getDamage() { return damage; }
    public int getBlock() { return block; }
    public CardType getType() { return type; }
}
