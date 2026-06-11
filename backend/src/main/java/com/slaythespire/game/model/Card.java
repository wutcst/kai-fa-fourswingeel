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
    
    // 运行时状态配置
    private String applyStatusType;
    private int applyStatusCount;
    private String applyStatusTarget;

    public enum CardType { ATTACK, SKILL }

    /**
     * 基于数据模板构造卡牌实例
     */
    public Card(CardTemplate template) {
        this.name = template.getName();
        this.cost = template.getCost();
        this.damage = template.getDamage();
        this.block = template.getBlock();
        this.type = template.getType();
        this.applyStatusType = template.getApplyStatusType();
        this.applyStatusCount = template.getApplyStatusCount();
        this.applyStatusTarget = template.getApplyStatusTarget();
    }

    /**
     * 直接根据属性构造（用于读取玩家存档卡组）
     * 注意：初始构造时状态字段为 null，需通过 setter 恢复
     */
    public Card(String name, int cost, int damage, int block, CardType type) {
        this.name = name;
        this.cost = cost;
        this.damage = damage;
        this.block = block;
        this.type = type;
        this.applyStatusType = null;
        this.applyStatusCount = 0;
        this.applyStatusTarget = null;
    }

    // ================= Getter 方法 =================
    public String getName() { return name; }
    public int getCost() { return cost; }
    public int getDamage() { return damage; }
    public int getBlock() { return block; }
    public CardType getType() { return type; }
    
    public String getApplyStatusType() { return applyStatusType; }
    public int getApplyStatusCount() { return applyStatusCount; }
    public String getApplyStatusTarget() { return applyStatusTarget; }

    // ================= Setter 方法 =================
    public void setName(String name) { this.name = name; }
    public void setDamage(int damage) { this.damage = damage; }
    public void setBlock(int block) { this.block = block; }
    public void setApplyStatusType(String applyStatusType) { this.applyStatusType = applyStatusType; }
    public void setApplyStatusCount(int applyStatusCount) { this.applyStatusCount = applyStatusCount; }
    public void setApplyStatusTarget(String applyStatusTarget) { this.applyStatusTarget = applyStatusTarget; }
}