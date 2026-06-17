package com.slaythespire.game.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 卡牌效果 — 表示一个状态施加动作
 * 替代旧的三独立字段 (applyStatusType / applyStatusCount / applyStatusTarget)
 * 一张卡可携带多个 CardEffect
 */
public class CardEffect {
    private final String type;   // 状态类型 ID (如 VULNERABLE, WEAK, STRENGTH)
    private final int count;     // 层数
    private final String target; // 目标: "ENEMY" / "SELF"

    @JsonCreator
    public CardEffect(
            @JsonProperty("type") String type,
            @JsonProperty("count") int count,
            @JsonProperty("target") String target) {
        this.type = type;
        this.count = count;
        this.target = (target != null) ? target : "ENEMY";
    }

    public String getType() { return type; }
    public int getCount() { return count; }
    public String getTarget() { return target; }

    /** 转为前端可用的 Map */
    public java.util.Map<String, Object> toMap() {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("type", type);
        m.put("count", count);
        m.put("target", target);
        return m;
    }

    /** 批量转换 */
    public static java.util.List<java.util.Map<String, Object>> listToMapList(java.util.List<CardEffect> effects) {
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        if (effects != null) {
            for (CardEffect e : effects) list.add(e.toMap());
        }
        return list;
    }
}
