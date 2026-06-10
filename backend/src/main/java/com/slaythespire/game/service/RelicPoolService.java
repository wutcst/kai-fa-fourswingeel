package com.slaythespire.game.service;

import com.slaythespire.repository.GameDataRepository;
import com.slaythespire.repository.RelicTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 遗物池服务 — 所有获取遗物的入口统一走这里
 * 根据角色 ID 自动合并公用遗物 + 角色专属遗物
 */
@Service
public class RelicPoolService {

    @Autowired
    private GameDataRepository dataRepo;

    private final Random random = new Random();

    /** 各稀有度权重，key按优先级排列（后续可移到配置文件） */
    private static final LinkedHashMap<String, Integer> TIER_WEIGHTS = new LinkedHashMap<>();
    static {
        TIER_WEIGHTS.put("COMMON", 70);
        TIER_WEIGHTS.put("RARE", 30);
    }

    /**
     * 按权重随机抽取遗物（用于商店等不限定稀有度的场景）
     * 先按权重ROLL稀有度，再从该稀有度中随机选一件
     * 若该稀有度已空则顺延至其他稀有度
     */
    public RelicTemplate drawRelic(String charId, List<String> ownedRelicIds) {
        List<String> tiers = new ArrayList<>(TIER_WEIGHTS.keySet());
        List<String> remaining = new ArrayList<>(tiers);

        while (!remaining.isEmpty()) {
            // 计算剩余稀有度的总权重
            int totalWeight = 0;
            for (String t : remaining) totalWeight += TIER_WEIGHTS.getOrDefault(t, 0);

            // ROLL 稀有度
            int roll = random.nextInt(totalWeight);
            int cumulative = 0;
            String chosenTier = null;
            for (String t : remaining) {
                cumulative += TIER_WEIGHTS.getOrDefault(t, 0);
                if (roll < cumulative) { chosenTier = t; break; }
            }
            if (chosenTier == null) chosenTier = remaining.get(remaining.size() - 1);

            // 从该稀有度池中抽取
            List<RelicTemplate> pool = buildPool(charId, ownedRelicIds, chosenTier);
            if (!pool.isEmpty()) {
                return pool.get(random.nextInt(pool.size()));
            }

            // 该稀有度已空，从剩余列表中移除并重试
            remaining.remove(chosenTier);
        }

        // 全部已空 → 给头环
        return dataRepo.getRelicById("ring");
    }

    /**
     * 指定稀有度抽取（用于奖励等限定场景）
     */
    public RelicTemplate drawRelic(String charId, List<String> ownedRelicIds, String tier) {
        List<RelicTemplate> pool = buildPool(charId, ownedRelicIds, tier);
        if (pool.isEmpty()) return dataRepo.getRelicById("ring");
        return pool.get(random.nextInt(pool.size()));
    }

    /**
     * 构建指定稀有度的可用遗物池
     */
    private List<RelicTemplate> buildPool(String charId, List<String> ownedRelicIds, String tier) {
        List<RelicTemplate> allRelics = dataRepo.getAllRelics();
        if (allRelics.isEmpty()) return Collections.emptyList();

        Set<String> owned = new HashSet<>();
        if (ownedRelicIds != null) {
            for (String id : ownedRelicIds) {
                if (id != null && !id.isEmpty()) owned.add(id);
            }
        }

        List<RelicTemplate> pool = new ArrayList<>();
        for (RelicTemplate tpl : allRelics) {
            if (tpl.getCharId() != null && !tpl.getCharId().equals(charId)) continue;
            String rarity = tpl.getRarity();
            if ("STARTER".equals(rarity) || "SPECIAL".equals(rarity)) continue;
            if (owned.contains(tpl.getId())) continue;
            if (tier.equalsIgnoreCase(rarity)) {
                pool.add(tpl);
            }
        }
        return pool;
    }
}
