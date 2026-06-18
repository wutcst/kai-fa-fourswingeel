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
                // Boss 不给普通遗物，Boss遗物通过独立的三选一界面发放
                break;
            case "chest":
                gold = 30 + random.nextInt(20);
                // 宝箱按权重抽取：普通60% 稀有35% 传说5%
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
        
        // 为Boss战生成Boss遗物选项（从Boss遗物池抽取3个）
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
        }
        if ("chest".equalsIgnoreCase(nodeType)) reward.put("cards", Collections.emptyList());
        else reward.put("cards", generateCardPool(3, false, charId));
        return reward;
    }

    @GetMapping("/cardPool")
    public List<Map<String, Object>> getCardPool(@RequestParam(defaultValue = "1") String charParam) {
        return generateCardPool(3, false, charParam);
    }

    private List<Map<String, Object>> generateCardPool(int count, boolean allowUpgraded, String charId) {
        List<CardTemplate> allCards = dataRepo.getAllCards();
        if (allCards.isEmpty()) return Collections.emptyList();
        List<CardTemplate> validCards = new ArrayList<>();
        for (CardTemplate tpl : allCards) {
            if (!allowUpgraded && tpl.isUpgraded()) continue;
            if ("START".equals(tpl.getRarity()) || "SPECIAL".equals(tpl.getRarity())) continue;
            if (charId != null && tpl.getCharId() != null && !tpl.getCharId().equals(charId)) continue;
            validCards.add(tpl);
        }
        if (validCards.isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> pool = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        int attempts = 0;
        while (pool.size() < count && attempts < count * 3) {
            CardTemplate tpl = validCards.get(random.nextInt(validCards.size()));
            if (!usedIds.contains(tpl.getId())) { usedIds.add(tpl.getId()); pool.add(cardToMap(tpl)); }
            attempts++;
        }
        while (pool.size() < count) pool.add(cardToMap(validCards.get(random.nextInt(validCards.size()))));
        return pool;
    }

    /**
     * Boss遗物三选一接口 — 从Boss遗物池抽取3个供玩家选择
     */
    @GetMapping("/bossRelics")
    public List<Map<String, Object>> getBossRelics(@RequestParam(defaultValue = "1") String charId,
                                                     @RequestParam(required = false) List<String> ownedRelics) {
        List<RelicTemplate> relics = relicPoolService.drawBossRelics(charId, ownedRelics, 3);
        List<Map<String, Object>> result = new ArrayList<>();
        for (RelicTemplate tpl : relics) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", tpl.getId());
            map.put("name", tpl.getName());
            map.put("description", tpl.getDescription());
            map.put("effectType", tpl.getEffectType());
            map.put("value", tpl.getValue());
            map.put("rarity", tpl.getRarity());
            result.add(map);
        }
        return result;
    }

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