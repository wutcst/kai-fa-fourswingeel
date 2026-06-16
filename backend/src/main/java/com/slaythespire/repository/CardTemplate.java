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
    private final int exhaustHandCount;
    private final String exhaustHandMode;
    private final boolean unplayable;
    private final boolean innate;
    private final int discardCount;
    private final boolean xCost;
    private final boolean aoe;
    private final boolean drawFirst; // 🆕 新增：是否先抽牌再执行其他手牌交互

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
            @JsonProperty("multiHitCount") Integer multiHitCount,
            @JsonProperty("exhaustHandCount") Integer exhaustHandCount,
            @JsonProperty("exhaustHandMode") String exhaustHandMode,
            @JsonProperty("unplayable") Boolean unplayable,
            @JsonProperty("innate") Boolean innate,
            @JsonProperty("discardCount") Integer discardCount,
            @JsonProperty("xCost") Boolean xCost,
            @JsonProperty("aoe") Boolean aoe,
            @JsonProperty("drawFirst") Boolean drawFirst) { // 🆕 新增参数
        
        this.id = id; this.name = name; this.cost = cost; this.damage = damage; this.block = block;
        this.type = type; this.applyStatusType = applyStatusType; this.applyStatusCount = applyStatusCount;
        this.applyStatusTarget = applyStatusTarget;
        this.exhaust = exhaust != null ? exhaust : false; this.retain = retain != null ? retain : false;
        this.ethereal = ethereal != null ? ethereal : false; this.drawCount = drawCount != null ? drawCount : 0;
        this.upgraded = upgraded != null ? upgraded : false; this.charId = charId != null ? charId : "1";
        this.rarity = rarity != null ? rarity : "COMMON";
        this.selfDamage = selfDamage != null ? selfDamage : 0; this.energyGain = energyGain != null ? energyGain : 0;
        this.multiHitCount = multiHitCount != null && multiHitCount > 0 ? multiHitCount : 1;
        this.exhaustHandCount = exhaustHandCount != null ? exhaustHandCount : 0;
        this.exhaustHandMode = exhaustHandMode != null ? exhaustHandMode.toUpperCase() : "RANDOM";
        this.unplayable = unplayable != null ? unplayable : false;
        this.innate = innate != null ? innate : false;
        this.discardCount = discardCount != null ? discardCount : 0;
        this.xCost = xCost != null ? xCost : false;
        this.aoe = aoe != null ? aoe : false;
        this.drawFirst = drawFirst != null ? drawFirst : false; // 🆕 默认 false (先丢弃/消耗，后抽牌)
    }

    public String getId() { return id; } public String getName() { return name; } public int getCost() { return cost; }
    public int getDamage() { return damage; } public int getBlock() { return block; } public CardType getType() { return type; }
    public String getApplyStatusType() { return applyStatusType; } public int getApplyStatusCount() { return applyStatusCount; }
    public String getApplyStatusTarget() { return applyStatusTarget; } public boolean isExhaust() { return exhaust; }
    public boolean isRetain() { return retain; } public boolean isEthereal() { return ethereal; } public int getDrawCount() { return drawCount; }
    public boolean isUpgraded() { return upgraded; } public String getCharId() { return charId; } public String getRarity() { return rarity; }
    public int getSelfDamage() { return selfDamage; } public int getEnergyGain() { return energyGain; }
    public int getMultiHitCount() { return multiHitCount; } public int getExhaustHandCount() { return exhaustHandCount; }
    public String getExhaustHandMode() { return exhaustHandMode; }
    public boolean isUnplayable() { return unplayable; } public boolean isInnate() { return innate; }
    public int getDiscardCount() { return discardCount; } public boolean isXCost() { return xCost; } public boolean isAoe() { return aoe; }
    public boolean isDrawFirst() { return drawFirst; } // 🆕 新增 Getter
}