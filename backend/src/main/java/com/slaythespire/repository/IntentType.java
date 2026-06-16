package com.slaythespire.repository;

public enum IntentType {
    ATTACK,      // 攻击玩家
    DEFEND,      // 给自己加格挡
    BUFF,        // 给自己加增益（力量、格挡等）
    DEBUFF,      // 给玩家加负面状态（晕眩、灼伤等）
    MULTI_ATTACK // 多段攻击（六火亡魂的双击/地狱火）
}
