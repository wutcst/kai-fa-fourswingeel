package com.slaythespire.controller;

import com.slaythespire.repository.CardTemplate;
import com.slaythespire.repository.GameDataRepository;
import com.slaythespire.repository.RelicTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class RewardController {

    @Autowired
    private GameDataRepository dataRepo;

    @Autowired
    private com.slaythespire.game.service.RelicPoolService relicPoolService;

    private final Random random = new Random();

    @GetMapping("/reward")
    public Map<String, Object> getReward(@RequestParam(defaultValue = "monster") String nodeType,
                                          @RequestParam(required = false) List<String> ownedRelics,
                                          @RequestParam(defaultValue = "1") String charId) {
        Map<String, Object> reward = new LinkedHashMap<>();
        int gold = 0;
        Map<String, Object> relic = null;

        switch (nodeType.toLowerCase()) {
            case "elite": gold = 40 + random.nextInt(15); relic = drawRelicMap(charId, ownedRelics, "rare"); break;
            case "boss": gold = 100 + random.nextInt(50); relic = drawRelicMap(charId, ownedRelics, "rare"); break;
            case "chest": gold = 30 + random.nextInt(20); relic = drawRelicMap(charId, ownedRelics, "common"); break;
            case "monster": default: gold = 15 + random.nextInt(15); relic = null; break;
        }

        reward.put("gold", gold);
        reward.put("relic", relic);
        
        if ("chest".equalsIgnoreCase(nodeType)) {
            reward.put("cards", Collections.emptyList());
        } else {
            reward.put("cards", generateCardPool(3, false, charId));
        }
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
            if ("START".equals(tpl.getRarity())) continue;
            if (charId != null && tpl.getCharId() != null && !tpl.getCharId().equals(charId)) continue;
            validCards.add(tpl);
        }
        if (validCards.isEmpty()) return Collections.emptyList();

        List<Map<String, Object>> pool = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        int attempts = 0;
        
        while (pool.size() < count && attempts < count * 3) {
            CardTemplate tpl = validCards.get(random.nextInt(validCards.size()));
            if (!usedIds.contains(tpl.getId())) {
                usedIds.add(tpl.getId());
                pool.add(cardToMap(tpl));
            }
            attempts++;
        }
        while (pool.size() < count) {
            CardTemplate tpl = validCards.get(random.nextInt(validCards.size()));
            pool.add(cardToMap(tpl));
        }
        return pool;
    }

    private Map<String, Object> cardToMap(CardTemplate tpl) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", tpl.getId());
        map.put("name", tpl.getName());
        map.put("cost", tpl.getCost());
        map.put("damage", tpl.getDamage());
        map.put("block", tpl.getBlock());
        map.put("type", tpl.getType().name());
        map.put("applyStatusType", tpl.getApplyStatusType());
        map.put("applyStatusCount", tpl.getApplyStatusCount());
        map.put("applyStatusTarget", tpl.getApplyStatusTarget());
        map.put("charId", tpl.getCharId());
        map.put("drawCount", tpl.getDrawCount());
        map.put("upgraded", tpl.isUpgraded());
        map.put("rarity", tpl.getRarity());  
        map.put("selfDamage", tpl.getSelfDamage());
        map.put("energyGain", tpl.getEnergyGain());
        map.put("multiHitCount", tpl.getMultiHitCount());
        // 🆕 补全基础词条和新机制字段
        map.put("exhaust", tpl.isExhaust());
        map.put("ethereal", tpl.isEthereal());
        map.put("retain", tpl.isRetain());
        map.put("exhaustHandCount", tpl.getExhaustHandCount());
        map.put("exhaustHandMode", tpl.getExhaustHandMode());
        return map;
    }

    private Map<String, Object> relicToMap(RelicTemplate tpl) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", tpl.getId());
        map.put("name", tpl.getName());
        map.put("effectType", tpl.getEffectType());
        map.put("value", tpl.getValue());
        map.put("rarity", tpl.getRarity());
        return map;
    }

    private Map<String, Object> drawRelicMap(String charId, List<String> ownedRelics, String tier) {
        RelicTemplate tpl = relicPoolService.drawRelic(charId, ownedRelics, tier);
        return (tpl != null) ? relicToMap(tpl) : null;
    }
}