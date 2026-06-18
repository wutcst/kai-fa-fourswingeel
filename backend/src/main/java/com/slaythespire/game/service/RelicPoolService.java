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

    /** 各稀有度权重，key按优先级排列 */
    private static final LinkedHashMap<String, Integer> TIER_WEIGHTS = new LinkedHashMap<>();
    static {
        TIER_WEIGHTS.put("COMMON", 80);
        TIER_WEIGHTS.put("RARE", 15);
        TIER_WEIGHTS.put("LEGENDARY", 5);
    }

    /** 稀有度降级链（从高到低），用于指定稀有度抽取时空了自动降级 */
    private static final List<String> TIER_FALLBACK = Arrays.asList("LEGENDARY", "RARE", "COMMON");

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
     * 如果该稀有度已空，按 TIER_FALLBACK 降级到更低稀有度
     */
    public RelicTemplate drawRelic(String charId, List<String> ownedRelicIds, String tier) {
        String targetTier = tier.toUpperCase();
        int startIndex = TIER_FALLBACK.indexOf(targetTier);
        if (startIndex < 0) {
            // 不在降级链中（如 STARTER、SPECIAL），直接尝试给定稀有度
            List<RelicTemplate> pool = buildPool(charId, ownedRelicIds, targetTier);
            if (pool.isEmpty()) return dataRepo.getRelicById("ring");
            return pool.get(random.nextInt(pool.size()));
        }

        // 从目标稀有度开始，逐级降级尝试
        for (int i = startIndex; i < TIER_FALLBACK.size(); i++) {
            List<RelicTemplate> pool = buildPool(charId, ownedRelicIds, TIER_FALLBACK.get(i));
            if (!pool.isEmpty()) {
                return pool.get(random.nextInt(pool.size()));
            }
        }

        // 全部已空 → 给头环
        return dataRepo.getRelicById("ring");
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
            if ("STARTER".equals(rarity) || "SPECIAL".equals(rarity) || "BOSS".equals(rarity)) continue;
            if (owned.contains(tpl.getId())) continue;
            if (tier.equalsIgnoreCase(rarity)) {
                pool.add(tpl);
            }
        }
        return pool;
    }

    /**
     * Boss 遗物池 - 从所有 BOSS 稀有度遗物中等概率抽取
     * 抽干则返回头环（ring）
     */
    public RelicTemplate drawBossRelic(String charId, List<String> ownedRelicIds) {
        List<RelicTemplate> bossPool = buildBossPool(charId, ownedRelicIds);
        if (bossPool.isEmpty()) return dataRepo.getRelicById("ring");
        return bossPool.get(random.nextInt(bossPool.size()));
    }

    /**
     * 批量抽取 Boss 遗物（用于三选一展示）
     * 不会重复抽取同一遗物，抽干后用头环补齐数量
     * @param act 当前阶段（1/2），用于决定调色盘专属遗物组
     */
    public List<RelicTemplate> drawBossRelics(String charId, List<String> ownedRelicIds, int count) {
        // 🆕 第二阶段：若玩家拥有「调色盘·手绘童话书」，返回第二阶段三个故事书遗物
        if (ownedRelicIds != null && ownedRelicIds.contains("palette_boss_reward_3")) {
            return collectPaletteRelics("palette_boss_reward_stage2_1", "palette_boss_reward_stage2_2", "palette_boss_reward_stage2_3", count);
        }
        // 🆕 第一阶段：若玩家拥有「诡异的调色盘」，返回第一阶段三个调色盘 Boss 遗物
        if (ownedRelicIds != null && ownedRelicIds.contains("mysterious_palette")) {
            return collectPaletteRelics("palette_boss_reward_1", "palette_boss_reward_2", "palette_boss_reward_3", count);
        }

        

        // 其他情况（包括 act==3 或条件不满足）使用常规 boss 池
        return drawBossRelicsFallback(charId, ownedRelicIds, count);
    }

    /** 辅助方法：收集三个指定ID的遗物，不足用头环补齐 */
    private List<RelicTemplate> collectPaletteRelics(String id1, String id2, String id3, int count) {
        List<RelicTemplate> fixed = new ArrayList<>();
        RelicTemplate r1 = dataRepo.getRelicById(id1);
        RelicTemplate r2 = dataRepo.getRelicById(id2);
        RelicTemplate r3 = dataRepo.getRelicById(id3);
        if (r1 != null) fixed.add(r1);
        if (r2 != null) fixed.add(r2);
        if (r3 != null) fixed.add(r3);
        // 若不足三个，用头环补齐
        while (fixed.size() < count) {
            RelicTemplate ring = dataRepo.getRelicById("ring");
            if (ring != null) fixed.add(ring);
            else break;
        }
        return fixed;
    }

    /** 常规 Boss 三选一逻辑（抽常规池，不混淆调色盘专属） */
    private List<RelicTemplate> drawBossRelicsFallback(String charId, List<String> ownedRelicIds, int count) {
        List<RelicTemplate> bossPool = buildBossPool(charId, ownedRelicIds);
        List<RelicTemplate> result = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        if (ownedRelicIds != null) usedIds.addAll(ownedRelicIds);

        // 检查 ring 是否存在，用于兜底
        RelicTemplate ring = dataRepo.getRelicById("ring");

        // 安全上限：最多循环 100 次，防止 ring 不存在时死循环
        int safety = 0;
        while (result.size() < count && safety < 100) {
            safety++;
            // 从剩余 Boss 池中找未使用的
            RelicTemplate chosen = null;
            List<RelicTemplate> remaining = new ArrayList<>();
            for (RelicTemplate tpl : bossPool) {
                if (!usedIds.contains(tpl.getId())) remaining.add(tpl);
            }
            if (!remaining.isEmpty()) {
                chosen = remaining.get(random.nextInt(remaining.size()));
                usedIds.add(chosen.getId());
            } else if (ring != null) {
                // Boss 池已抽干，用头环补齐
                chosen = ring;
            }
            if (chosen != null) result.add(chosen);
        }
        // 如果 ring 不存在且池已空，返回已有的（可能不足 count）
        return result;
    }

    /**
     * 构建可用 Boss 遗物池（等概率抽取用）
     * 排除所有调色盘专属 Boss 遗物（palette_boss_reward_*），
     * 这些遗物只能通过特殊事件获得。
     */
    private List<RelicTemplate> buildBossPool(String charId, List<String> ownedRelicIds) {
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
            if ("BOSS".equals(tpl.getRarity())) {
                if (owned.contains(tpl.getId())) continue;
                if (tpl.getCharId() != null && !tpl.getCharId().equals(charId)) continue;
                // 🆕 跳过所有 palette_boss 开头的遗物（只能通过特殊事件获得）
                if (tpl.getId() != null && tpl.getId().startsWith("palette_boss")) continue;
                pool.add(tpl);
            }
        }
        return pool;
    }
}
