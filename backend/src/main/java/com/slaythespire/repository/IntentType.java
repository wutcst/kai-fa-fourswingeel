package com.slaythespire.repository;

/**
 * 怪物意图类型枚举
 * 用于定义怪物在某一回合的行为类型
 */
public enum IntentType {
    ATTACK,      // 攻击玩家
    DEFEND,      // 给自己加格挡（暂保留，可扩展）
    BUFF,        // 给自己加增益（暂保留）
    DEBUFF,      // 给玩家加负面状态（暂保留）
    MULTI_ATTACK // 多段攻击（暂保留）
}