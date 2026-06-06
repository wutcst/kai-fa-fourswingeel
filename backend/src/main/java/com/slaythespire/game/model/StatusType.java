package com.slaythespire.game.model;

/**
 * 状态效果类型枚举
 * 定义游戏中所有持续回合数的Buff/Debuff
 */
public enum StatusType {
    VULNERABLE, // 易伤：受到伤害增加 50%
    WEAK,       // 虚弱：造成伤害减少 25%
    FRAIL       // 脆弱：获得格挡减少 25%
}