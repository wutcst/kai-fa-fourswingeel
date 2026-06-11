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

    // 效果字段
    private String applyStatusType;
    private int applyStatusCount;
    private String applyStatusTarget;

    // 卡牌属性
    private boolean exhaust;    // 消耗：使用后进入消耗堆而非弃牌堆
    private boolean retain;     // 保留：回合结束时留在手牌，不进弃牌堆
    private boolean ethereal;   // 虚无：回合结束时消耗

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
        this.exhaust = template.isExhaust();
        this.retain = template.isRetain();
        this.ethereal = template.isEthereal();
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

    // ================= Getter =================
    public String getName() { return name; }
    public int getCost() { return cost; }
    public int getDamage() { return damage; }
    public int getBlock() { return block; }
    public CardType getType() { return type; }
    public String getApplyStatusType() { return applyStatusType; }
    public int getApplyStatusCount() { return applyStatusCount; }
    public String getApplyStatusTarget() { return applyStatusTarget; }
    public boolean isExhaust() { return exhaust; }
    public boolean isRetain() { return retain; }
    public boolean isEthereal() { return ethereal; }

    // ================= Setter =================
    public void setName(String name) { this.name = name; }
    public void setDamage(int damage) { this.damage = damage; }
    public void setBlock(int block) { this.block = block; }
    public void setApplyStatusType(String applyStatusType) { this.applyStatusType = applyStatusType; }
    public void setApplyStatusCount(int applyStatusCount) { this.applyStatusCount = applyStatusCount; }
    public void setApplyStatusTarget(String applyStatusTarget) { this.applyStatusTarget = applyStatusTarget; }
    public void setExhaust(boolean exhaust) { this.exhaust = exhaust; }
    public void setRetain(boolean retain) { this.retain = retain; }
    public void setEthereal(boolean ethereal) { this.ethereal = ethereal; }
}
