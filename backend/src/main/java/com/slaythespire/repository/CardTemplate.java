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
    private final String applyStatusType;
    private final int applyStatusCount;
    private final String applyStatusTarget;
    private final boolean exhaust;
    private final boolean retain;
    private final boolean ethereal;
    private final int drawCount;
    private final boolean upgraded;
    private final String charId;
    private final String rarity;
    private final int selfDamage;
    private final int energyGain;
    private final int multiHitCount;

    @JsonCreator
    public CardTemplate(
            @JsonProperty("id") String id, @JsonProperty("name") String name,
            @JsonProperty("cost") int cost, @JsonProperty("damage") int damage,
            @JsonProperty("block") int block, @JsonProperty("type") CardType type,
            @JsonProperty("applyStatusType") String applyStatusType, @JsonProperty("applyStatusCount") int applyStatusCount,
            @JsonProperty("applyStatusTarget") String applyStatusTarget,
            @JsonProperty("exhaust") Boolean exhaust, @JsonProperty("retain") Boolean retain,
            @JsonProperty("ethereal") Boolean ethereal, @JsonProperty("drawCount") Integer drawCount,
            @JsonProperty("upgraded") Boolean upgraded, @JsonProperty("charId") String charId,
            @JsonProperty("rarity") String rarity,
            @JsonProperty("selfDamage") Integer selfDamage, @JsonProperty("energyGain") Integer energyGain,
            @JsonProperty("multiHitCount") Integer multiHitCount) {
        
        this.id = id; this.name = name; this.cost = cost; this.damage = damage; this.block = block;
        this.type = type; this.applyStatusType = applyStatusType; this.applyStatusCount = applyStatusCount;
        this.applyStatusTarget = applyStatusTarget;
        this.exhaust = exhaust != null ? exhaust : false; this.retain = retain != null ? retain : false;
        this.ethereal = ethereal != null ? ethereal : false; this.drawCount = drawCount != null ? drawCount : 0;
        this.upgraded = upgraded != null ? upgraded : false; this.charId = charId != null ? charId : "1";
        this.rarity = rarity != null ? rarity : "COMMON";
        this.selfDamage = selfDamage != null ? selfDamage : 0; this.energyGain = energyGain != null ? energyGain : 0;
        this.multiHitCount = multiHitCount != null && multiHitCount > 0 ? multiHitCount : 1;
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
    public boolean isExhaust() { return exhaust; }
    public boolean isRetain() { return retain; } 
    public boolean isEthereal() { return ethereal; } 
    public int getDrawCount() { return drawCount; }
    public boolean isUpgraded() { return upgraded; } 
    public String getCharId() { return charId; } 
    public String getRarity() { return rarity; }
    public int getSelfDamage() { return selfDamage; } 
    public int getEnergyGain() { return energyGain; }
    public int getMultiHitCount() { return multiHitCount; }

    // ✅ 补全并增强了 toString 方法，方便后端控制台调试查看新字段
    @Override
    public String toString() {
        return String.format("CardTemplate{id='%s', name='%s', cost=%d, damage=%d, block=%d, type=%s, " +
                        "selfDamage=%d, energyGain=%d, multiHitCount=%d, upgraded=%s, charId=%s, rarity=%s}",
                id, name, cost, damage, block, type, selfDamage, energyGain, multiHitCount, upgraded, charId, rarity);
    }
}