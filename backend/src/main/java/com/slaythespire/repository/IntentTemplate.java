package com.slaythespire.repository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 怪物意图数据模板
 * 定义怪物在特定回合的行为参数
 */
public class IntentTemplate {
    private final IntentType type;
    private final int value;      // 攻击值/格挡值等
    private final String desc;    // 前端显示的意图描述

    @JsonCreator
    public IntentTemplate(
            @JsonProperty("type") IntentType type,
            @JsonProperty("value") int value,
            @JsonProperty("desc") String desc) {
        this.type = type;
        this.value = value;
        this.desc = desc;
    }

    public IntentType getType() { return type; }
    public int getValue() { return value; }
    public String getDesc() { return desc; }

    @Override
    public String toString() {
        return String.format("Intent{type=%s, value=%d, desc='%s'}", type, value, desc);
    }
}