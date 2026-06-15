package com.slaythespire.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 未知房间（question node）概率管理
 * 每个角色独立维护概率偏移，仅当重新开始游戏（/api/init）时重置。
 */
@Service
public class QuestionService {

    // 基础概率（单位：百分点）
    private static final int BASE_ENEMY    = 10;
    private static final int BASE_CHEST    = 2;
    private static final int BASE_SHOP     = 3;
    // 事件概率 = 100 - (BASE_ENEMY+BASE_CHEST+BASE_SHOP) = 85
    private static final int BASE_EVENT    = 85;

    // 每个角色当前的偏移量（初始0）
    private final Map<String, int[]> charOffsets = new HashMap<>();
    // int[] 顺序: [enemy, chest, shop] (事件偏移永远是事件 = -(偏移之和)，我们不存事件偏移)
    // 但更简单：存三个偏移，事件概率自动计算为 100 - 三个概率之和

    private final Random random = new Random();

    public synchronized QuestionResult roll(String charId) {
        int[] offsets = charOffsets.computeIfAbsent(charId, k -> new int[3]);
        int enemyProb = BASE_ENEMY    + offsets[0];
        int chestProb = BASE_CHEST    + offsets[1];
        int shopProb  = BASE_SHOP     + offsets[2];
        int total = enemyProb + chestProb + shopProb;
        // 事件概率
        int eventProb = 100 - total;
        if (eventProb < 0) eventProb = 0; // 防止负概率

        int roll = random.nextInt(100);
        if (roll < enemyProb) {
            // 命中敌人
            offsets[0] = 0; // 重置
            return QuestionResult.ENEMY;
        } else if (roll < enemyProb + chestProb) {
            // 命中宝箱
            offsets[1] = 0;
            return QuestionResult.CHEST;
        } else if (roll < enemyProb + chestProb + shopProb) {
            // 命中商店
            offsets[2] = 0;
            return QuestionResult.SHOP;
        } else {
            // 命中事件
            // 非事件结果增加自己的基础概率
            offsets[0] += BASE_ENEMY;
            offsets[1] += BASE_CHEST;
            offsets[2] += BASE_SHOP;
            return QuestionResult.EVENT;
        }
    }

    /** 重置角色概率（新游戏） */
    public synchronized void resetChar(String charId) {
        charOffsets.remove(charId);
    }

    public enum QuestionResult {
        ENEMY,
        CHEST,
        SHOP,
        EVENT
    }
}
