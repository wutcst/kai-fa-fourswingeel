package com.slaythespire.repository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class IntentTemplate {
    private final IntentType type;
    private final int value;
    private final String desc;
    private final String applyStatusType;
    private final int applyStatusCount;
    private final String applyStatusTarget;
    private final int multiHit;
    private final boolean hpScaling;
    private final int burnCards;
    private final int stunCards;
    private final boolean skipBurnCards;
    private final boolean upgradeBurns;

    @JsonCreator
    public IntentTemplate(
            @JsonProperty("type") IntentType type,
            @JsonProperty("value") int value,
            @JsonProperty("desc") String desc,
            @JsonProperty("applyStatusType") String applyStatusType,
            @JsonProperty("applyStatusCount") int applyStatusCount,
            @JsonProperty("applyStatusTarget") String applyStatusTarget,
            @JsonProperty("multiHit") Integer multiHit,
            @JsonProperty("hpScaling") Boolean hpScaling,
            @JsonProperty("burnCards") Integer burnCards,
            @JsonProperty("stunCards") Integer stunCards,
            @JsonProperty("skipBurnCards") Boolean skipBurnCards,
            @JsonProperty("upgradeBurns") Boolean upgradeBurns) {
        this.type = type;
        this.value = value;
        this.desc = desc;
        this.applyStatusType = applyStatusType;
        this.applyStatusCount = applyStatusCount;
        this.applyStatusTarget = applyStatusTarget != null ? applyStatusTarget : "PLAYER";
        this.multiHit = multiHit != null ? multiHit : 1;
        this.hpScaling = hpScaling != null ? hpScaling : false;
        this.burnCards = burnCards != null ? burnCards : 0;
        this.stunCards = stunCards != null ? stunCards : 0;
        this.skipBurnCards = skipBurnCards != null ? skipBurnCards : false;
        this.upgradeBurns = upgradeBurns != null ? upgradeBurns : false;
    }

    public IntentType getType() { return type; }
    public int getValue() { return value; }
    public String getDesc() { return desc; }
    public String getApplyStatusType() { return applyStatusType; }
    public int getApplyStatusCount() { return applyStatusCount; }
    public String getApplyStatusTarget() { return applyStatusTarget; }
    public int getMultiHit() { return multiHit; }
    public boolean isHpScaling() { return hpScaling; }
    public int getBurnCards() { return burnCards; }
    public int getStunCards() { return stunCards; }
    public boolean isSkipBurnCards() { return skipBurnCards; }
    public boolean isUpgradeBurns() { return upgradeBurns; }

    @Override
    public String toString() {
        return String.format("Intent{type=%s, value=%d, desc='%s'}", type, value, desc);
    }
}
