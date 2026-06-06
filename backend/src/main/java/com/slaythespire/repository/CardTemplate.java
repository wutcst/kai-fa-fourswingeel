package com.slaythespire.repository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.slaythespire.game.model.Card.CardType;

/**
 * 卡牌数据模板类 - 定义卡牌的静态配置信息
 */
public class CardTemplate {
    private final String id;
    private final String name;
    private final int cost;
    private final int damage;
    private final int block;
    private final CardType type;
    
    // 状态效果配置
    private final String applyStatusType;    // 如 "VULNERABLE", "WEAK", "FRAIL"
    private final int applyStatusCount;      // 层数
    private final String applyStatusTarget;  // "ENEMY" 或 "SELF"

    @JsonCreator
    public CardTemplate(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("cost") int cost,
            @JsonProperty("damage") int damage,
            @JsonProperty("block") int block,
            @JsonProperty("type") CardType type,
            @JsonProperty("applyStatusType") String applyStatusType,
            @JsonProperty("applyStatusCount") int applyStatusCount,
            @JsonProperty("applyStatusTarget") String applyStatusTarget) {
        this.id = id;
        this.name = name;
        this.cost = cost;
        this.damage = damage;
        this.block = block;
        this.type = type;
        this.applyStatusType = applyStatusType;
        this.applyStatusCount = applyStatusCount;
        this.applyStatusTarget = applyStatusTarget;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getCost() { return cost; }
    public int getDamage() { return damage; }
    public int getBlock() { return block; }
    public CardType getType() { return type; }
    
    public String getApplyStatusType() { return applyStatusType; }
    public int getApplyStatusCount() { return applyStatusCount; }
    public String getApplyStatusTarget() { return applyStatusTarget; }

    @Override
    public String toString() {
        return String.format("CardTemplate{id='%s', name='%s', cost=%d, type=%s}", id, name, cost, type);
    }
}