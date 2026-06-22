package com.slaythespire.controller;

import com.slaythespire.game.model.CardEffect;
import com.slaythespire.repository.CardTemplate;
import com.slaythespire.repository.GameDataRepository;
import com.slaythespire.repository.RelicTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class RewardController {
    @Autowired private GameDataRepository dataRepo;
    @Autowired private com.slaythespire.game.service.RelicPoolService relicPoolService;
    private final Random random = new Random();

    @GetMapping("/reward")
    public Map<String, Object> getReward(@RequestParam(defaultValue = "monster") String nodeType,
                                          @RequestParam(required = false) List<String> ownedRelics,
                                          @RequestParam(defaultValue = "1") String charId) {
        Map<String, Object> reward = new LinkedHashMap<>();
        int gold = 0;
        List<Map<String, Object>> relics = new ArrayList<>();

        switch (nodeType.toLowerCase()) {
            case "elite":
                gold = 40 + random.nextInt(15);
                relics.add(drawRelicMap(charId, ownedRelics, "rare"));
                break;
            case "boss":
                gold = 100 + random.nextInt(50);
                // Boss 不给普通遗物，但给 Boss 遗物选项
                break;
            case "chest":
                gold = 30 + random.nextInt(20);
                RelicTemplate chestRelic = relicPoolService.drawRelic(charId, ownedRelics);
                if (chestRelic != null) relics.add(relicToMap(chestRelic));
                break;
            case "monster":
            default:
                gold = 15 + random.nextInt(15);
                break;
        }
        reward.put("gold", gold);
        reward.put("relics", relics);
        
        // 为Boss战生成Boss遗物选项
        if ("boss".equalsIgnoreCase(nodeType)) {
            List<RelicTemplate> bossRelics = relicPoolService.drawBossRelics(charId, ownedRelics, 3);
            List<Map<String, Object>> bossRelicOptions = new ArrayList<>();
            for (RelicTemplate tpl : bossRelics) {
                Map<String, Object> br = new LinkedHashMap<>();
                br.put("id", tpl.getId());
                br.put("name", tpl.getName());
                br.put("description", tpl.getDescription());
                br.put("effectType", tpl.getEffectType());
                br.put("value", tpl.getValue());
                br.put("rarity", tpl.getRarity());
                bossRelicOptions.add(br);
            }
            reward.put("bossRelicOptions", bossRelicOptions);
            
            // Boss 战后必定掉落三张 RARE 卡牌
            reward.put("cards", generateCardPoolRarity(3, false, charId, "boss"));
        } else if ("chest".equalsIgnoreCase(nodeType)) {
            reward.put("cards", Collections.emptyList());
        } else {
            // 普通、精英战斗采用新的稀有度权重生成
            reward.put("cards", generateCardPoolRarity(3, false, charId, nodeType));
        }
        return reward;
    }

    @GetMapping("/cardPool")
    public List<Map<String, Object>> getCardPool(@RequestParam(defaultValue = "1") String charParam) {
        // 默认按普通怪物权重生成
        return generateCardPoolRarity(3, false, charParam, "monster");
    }

    // ============ 新的卡牌池生成方法（稀有度权重） ============

    /**
     * 基于稀有度权重的卡牌池生成。
     * @param count         生成的卡牌数量
     * @param allowUpgraded 是否允许升级卡牌（目前为 false）
     * @param charId        角色 ID
     * @param nodeType      节点类型 ("monster", "elite", "boss")
     * @return 卡牌 Map 列表
     */
    private List<Map<String, Object>> generateCardPoolRarity(int count, boolean allowUpgraded, String charId, String nodeType) {
        List<CardTemplate> allCards = dataRepo.getAllCards();
        if (allCards.isEmpty()) return Collections.emptyList();

        boolean isBoss = "boss".equalsIgnoreCase(nodeType);
        if (isBoss) {
            // Boss 战斗：直接返回三张 RARE 卡（尽量不重复）
            List<CardTemplate> rarePool = getFilteredCards(allCards, allowUpgraded, charId, "RARE");
            if (rarePool.isEmpty()) {
                // 如果没有 RARE 卡，回退到 UNCOMMON
                rarePool = getFilteredCards(allCards, allowUpgraded, charId, "UNCOMMON");
                if (rarePool.isEmpty()) {
                    rarePool = getFilteredCards(allCards, allowUpgraded, charId, "COMMON");
                }
            }
            List<Map<String, Object>> result = new ArrayList<>();
            Set<String> usedIds = new HashSet<>();
            // 先从池中选不同的卡，不足时允许重复
            for (int i = 0; i < count; i++) {
                CardTemplate selected = pickRandom(rarePool, usedIds);
                if (selected == null) {
                    // 所有卡都已被选过，允许重复选取
                    selected = rarePool.get(random.nextInt(rarePool.size()));
                }
                usedIds.add(selected.getId());
                result.add(cardToMap(selected));
            }
            return result;
        }

        // ====== 非 Boss 战斗 ======
        boolean isElite = "elite".equalsIgnoreCase(nodeType);
        int uncommonBase = isElite ? 40 : 37;      // UNCOMMON 基础概率（百分比）
        int rareBase     = isElite ? 10 : 3;       // RARE 初始概率（百分比）
        int rareModifier = -5;                     // RARE 概率修正值（初始 -5%）

        // 按稀有度过滤卡牌池
        List<CardTemplate> commonPool   = getFilteredCards(allCards, allowUpgraded, charId, "COMMON");
        List<CardTemplate> uncommonPool = getFilteredCards(allCards, allowUpgraded, charId, "UNCOMMON");
        List<CardTemplate> rarePool     = getFilteredCards(allCards, allowUpgraded, charId, "RARE");

        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();

        for (int i = 0; i < count; i++) {
            // 当前 RARE 概率 = 基础 + 修正值，最小 0
            int currentRareProb = Math.max(0, rareBase + rareModifier);

            int roll = random.nextInt(100);
            CardTemplate selected = null;

            int attempts = 0;
            while (selected == null && attempts < 200) {
                attempts++;
                if (roll < currentRareProb) {
                    // 选中 RARE
                    if (rarePool.isEmpty()) {
                        // 降级到 UNCOMMON
                        selected = pickRandom(uncommonPool, usedIds);
                        if (selected == null) selected = pickRandom(commonPool, usedIds);
                    } else {
                        selected = pickRandom(rarePool, usedIds);
                    }
                    rareModifier = -5; // 出现 RARE 后重置修正值
                } else if (roll < currentRareProb + uncommonBase) {
                    // 选中 UNCOMMON
                    if (uncommonPool.isEmpty()) {
                        selected = pickRandom(commonPool, usedIds);
                        if (selected == null) selected = pickRandom(rarePool, usedIds);
                    } else {
                        selected = pickRandom(uncommonPool, usedIds);
                    }
                    rareModifier++; // 未出 RARE，修正值 +1
                } else {
                    // 选中 COMMON
                    if (commonPool.isEmpty()) {
                        selected = pickRandom(uncommonPool, usedIds);
                        if (selected == null) selected = pickRandom(rarePool, usedIds);
                    } else {
                        selected = pickRandom(commonPool, usedIds);
                    }
                    rareModifier++; // 未出 RARE，修正值 +1
                }
            }

            if (selected != null) {
                usedIds.add(selected.getId());
                result.add(cardToMap(selected));
            }
        }
        return result;
    }

    /**
     * 从所有卡牌中过滤出指定稀有度的可用卡牌（排除升级卡、起始卡、特殊卡，且匹配角色）
     */
    private List<CardTemplate> getFilteredCards(List<CardTemplate> allCards, boolean allowUpgraded, String charId, String rarity) {
        List<CardTemplate> result = new ArrayList<>();
        for (CardTemplate tpl : allCards) {
            if (!allowUpgraded && tpl.isUpgraded()) continue;
            String r = tpl.getRarity();
            if ("START".equals(r) || "SPECIAL".equals(r)) continue;
            if (!rarity.equals(r)) continue;
            if (charId != null && tpl.getCharId() != null && !tpl.getCharId().equals(charId)) continue;
            result.add(tpl);
        }
        return result;
    }

    /**
     * 从池中随机选一张未被使用的卡牌。若所有卡都被使用过，返回 null。
     */
    private CardTemplate pickRandom(List<CardTemplate> pool, Set<String> usedIds) {
        List<CardTemplate> candidates = new ArrayList<>();
        for (CardTemplate tpl : pool) {
            if (!usedIds.contains(tpl.getId())) candidates.add(tpl);
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(random.nextInt(candidates.size()));
    }

    // ============ 原有方法（保持不动） ============

    private Map<String, Object> cardToMap(CardTemplate tpl) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", tpl.getId()); map.put("name", tpl.getName()); map.put("cost", tpl.getCost());
        map.put("damage", tpl.getDamage()); map.put("block", tpl.getBlock()); map.put("type", tpl.getType().name());
        map.put("effects", CardEffect.listToMapList(tpl.getEffects()));
        map.put("charId", tpl.getCharId());
        map.put("drawCount", tpl.getDrawCount()); map.put("upgraded", tpl.isUpgraded()); map.put("rarity", tpl.getRarity());  
        map.put("selfDamage", tpl.getSelfDamage()); map.put("energyGain", tpl.getEnergyGain()); map.put("multiHitCount", tpl.getMultiHitCount());
        map.put("exhaust", tpl.isExhaust()); map.put("ethereal", tpl.isEthereal()); map.put("retain", tpl.isRetain());
        map.put("exhaustHandCount", tpl.getExhaustHandCount()); map.put("exhaustHandMode", tpl.getExhaustHandMode());
        map.put("unplayable", tpl.isUnplayable()); map.put("innate", tpl.isInnate()); map.put("discardCount", tpl.getDiscardCount()); map.put("discardMode", tpl.getDiscardMode());
        map.put("xCost", tpl.isXCost()); map.put("aoe", tpl.isAoe());
        map.put("drawFirst", tpl.isDrawFirst());
        map.put("copyToDiscard", tpl.isCopyToDiscard());
        map.put("copyToDraw", tpl.isCopyToDraw());
        map.put("strengthMultiplier", tpl.getStrengthMultiplier());
        map.put("randomTarget", tpl.isRandomTarget());
        map.put("endOfTurnDamage", tpl.getEndOfTurnDamage());
        map.put("energyLossOnDraw", tpl.getEnergyLossOnDraw());
        map.put("exhaustNonAttackBlock", tpl.getExhaustNonAttackBlock());
        map.put("addWoundCount", tpl.getAddWoundCount());
        map.put("blockToDamage", tpl.isBlockToDamage());
        map.put("blockPerAttack", tpl.getBlockPerAttack());
        map.put("energyGainIfDiscarded", tpl.getEnergyGainIfDiscarded());
        map.put("discardAllForCards", tpl.getDiscardAllForCards());
        map.put("discardAllForDraw", tpl.isDiscardAllForDraw());
        map.put("buffCardName", tpl.getBuffCardName());
        map.put("buffDamageAmount", tpl.getBuffDamageAmount());
        map.put("doublePoison", tpl.isDoublePoison());
        map.put("poisonAllPerCard", tpl.getPoisonAllPerCard());
        map.put("extraPoisonTick", tpl.isExtraPoisonTick());
        map.put("addCardId", tpl.getAddCardId());
        map.put("addCardCount", tpl.getAddCardCount());
        map.put("upgradeHandCount", tpl.getUpgradeHandCount());
        map.put("upgradeHandMode", tpl.getUpgradeHandMode());
        map.put("upgradeAllInHand", tpl.isUpgradeAllInHand());
        map.put("requiresEmptyDrawPile", tpl.isRequiresEmptyDrawPile());
        return map;
    }

    private Map<String, Object> relicToMap(RelicTemplate tpl) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", tpl.getId()); map.put("name", tpl.getName()); map.put("effectType", tpl.getEffectType());
        map.put("value", tpl.getValue()); map.put("rarity", tpl.getRarity());
        return map;
    }

    private Map<String, Object> drawRelicMap(String charId, List<String> ownedRelics, String tier) {
        RelicTemplate tpl = relicPoolService.drawRelic(charId, ownedRelics, tier);
        return (tpl != null) ? relicToMap(tpl) : null;
    }
}
