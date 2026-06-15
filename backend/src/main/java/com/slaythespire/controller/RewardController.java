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
            case "elite":
                gold = 40 + random.nextInt(15);
                relic = drawRelicMap(charId, ownedRelics, "rare");
                break;
            case "boss":
                gold = 100 + random.nextInt(50);
                relic = drawRelicMap(charId, ownedRelics, "rare");
                break;
            case "chest":
                gold = 30 + random.nextInt(20);
                relic = drawRelicMap(charId, ownedRelics, "common");
                break;
            case "monster":
            default:
                gold = 15 + random.nextInt(15);
                relic = null;
                break;
        }

        reward.put("gold", gold);
        reward.put("relic", relic);
        reward.put("cards", generateCardPool(3, false));

        return reward;
    }

    @GetMapping("/cardPool")
    public List<Map<String, Object>> getCardPool(@RequestParam(defaultValue = "1") String charParam) {
        return generateCardPool(3, false);
    }

    private List<Map<String, Object>> generateCardPool(int count, boolean allowUpgraded) {
        List<CardTemplate> allCards = dataRepo.getAllCards();
        if (allCards.isEmpty()) return Collections.emptyList();

        List<CardTemplate> validCards = new ArrayList<>();
        for (CardTemplate tpl : allCards) {
            if (allowUpgraded || !tpl.isUpgraded()) {
                validCards.add(tpl);
            }
        }
        if (validCards.isEmpty()) return Collections.emptyList();

        List<Map<String, Object>> pool = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        int attempts = 0;
        
        // 尝试生成不重复的卡牌
        while (pool.size() < count && attempts < count * 3) {
            CardTemplate tpl = validCards.get(random.nextInt(validCards.size()));
            if (!usedIds.contains(tpl.getId())) {
                usedIds.add(tpl.getId());
                pool.add(cardToMap(tpl));
            }
            attempts++;
        }
        
        // 如果实在抽不出不重复的（比如卡池太小），则允许重复
        while (pool.size() < count) {
            CardTemplate tpl = validCards.get(random.nextInt(validCards.size()));
            pool.add(cardToMap(tpl));
        }
        return pool;
    }

    /**
     * 将卡牌模板转换为 Map 返回给前端
     * ✅ 已补全所有新增机制字段 (selfDamage, energyGain, multiHitCount)
     */
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
        
        // 🆕 核心修复：补全自身伤害、获得能量、多段攻击字段
        map.put("selfDamage", tpl.getSelfDamage());
        map.put("energyGain", tpl.getEnergyGain());
        map.put("multiHitCount", tpl.getMultiHitCount());
        
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