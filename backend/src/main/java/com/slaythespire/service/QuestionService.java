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

    // 基础概率（单位：百分点，总和=100）
    private static final int BASE_ENEMY = 10;
    private static final int BASE_CHEST = 2;
    private static final int BASE_SHOP  = 3;
    private static final int BASE_EVENT = 85;

    // 每个角色 4 个偏移量: [enemy, chest, shop, event]
    private final Map<String, int[]> charOffsets = new HashMap<>();

    private final Random random = new Random();

    public synchronized QuestionResult roll(String charId) {
        int[] off = charOffsets.computeIfAbsent(charId, k -> new int[4]);
        int enemyProb = BASE_ENEMY + off[0];
        int chestProb = BASE_CHEST + off[1];
        int shopProb  = BASE_SHOP  + off[2];
        int eventProb = BASE_EVENT + off[3];
        int total = enemyProb + chestProb + shopProb + eventProb;

        // 归一化到 100
        int roll = random.nextInt(total);
        if (roll < enemyProb) {
            off[0] = 0; // 命中→重置该类型偏移
            return QuestionResult.ENEMY;
        } else if (roll < enemyProb + chestProb) {
            off[1] = 0;
            return QuestionResult.CHEST;
        } else if (roll < enemyProb + chestProb + shopProb) {
            off[2] = 0;
            return QuestionResult.SHOP;
        } else {
            // 命中事件：重置事件偏移，其他三类增加概率
            off[3] = 0;
            off[0] += BASE_ENEMY;
            off[1] += BASE_CHEST;
            off[2] += BASE_SHOP;
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
