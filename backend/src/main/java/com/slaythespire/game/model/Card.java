package com.slaythespire.game.model;

import com.slaythespire.repository.CardTemplate;

/**
 * 卡牌实例类 - 运行时状态与基础属性
 * 属性数据由 CardTemplate 注入，实现数据与逻辑分离
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
     * @param template 卡牌静态配置数据
     */
    public Card(CardTemplate template) {
        this.name = template.getName();
        this.cost = template.getCost();
        this.damage = template.getDamage();
        this.block = template.getBlock();
        this.type = template.getType();
    }

    public String getName() { return name; }
    public int getCost() { return cost; }
    public int getDamage() { return damage; }
    public int getBlock() { return block; }
    public CardType getType() { return type; }
}