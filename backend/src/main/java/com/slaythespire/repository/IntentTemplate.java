package com.slaythespire.repository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 怪物意图数据模板
 */
public class IntentTemplate {
    private final IntentType type;
    private final int value;
    private final String desc;
    
    // 状态效果配置
    private final String applyStatusType;
    private final int applyStatusCount;
    private final String applyStatusTarget;

    @JsonCreator
    public IntentTemplate(
            @JsonProperty("type") IntentType type,
            @JsonProperty("value") int value,
            @JsonProperty("desc") String desc,
            @JsonProperty("applyStatusType") String applyStatusType,
            @JsonProperty("applyStatusCount") int applyStatusCount,
            @JsonProperty("applyStatusTarget") String applyStatusTarget) {
        this.type = type;
        this.value = value;
        this.desc = desc;
        this.applyStatusType = applyStatusType;
        this.applyStatusCount = applyStatusCount;
        // 怪物意图默认目标是玩家
        this.applyStatusTarget = applyStatusTarget != null ? applyStatusTarget : "PLAYER";
    }

    public IntentType getType() { return type; }
    public int getValue() { return value; }
    public String getDesc() { return desc; }
    
    public String getApplyStatusType() { return applyStatusType; }
    public int getApplyStatusCount() { return applyStatusCount; }
    public String getApplyStatusTarget() { return applyStatusTarget; }

    @Override
    public String toString() {
        return String.format("Intent{type=%s, value=%d, desc='%s'}", type, value, desc);
    }
}