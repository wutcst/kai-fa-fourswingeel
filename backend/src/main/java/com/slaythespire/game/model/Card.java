package com.slaythespire.game.model;

import com.slaythespire.repository.CardTemplate;

/**
 * 卡牌实例类 - 运行时状态与基础属性
 */
public class Card {
    private String name;
    private int cost;
    private int damage;
    private int block;
    private CardType type;

    public enum CardType {
        ATTACK, SKILL
    }

    /**
     * 基于数据模板构造卡牌实例
     */
    public Card(CardTemplate template) {
        this.name = template.getName();
        this.cost = template.getCost();
        this.damage = template.getDamage();
        this.block = template.getBlock();
        this.type = template.getType();
    }

    /**
     * 直接根据属性构造（用于读取玩家存档卡组）
     */
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