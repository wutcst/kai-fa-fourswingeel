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

    private final Random random = new Random();

    @GetMapping("/reward")
    public Map<String, Object> getReward(@RequestParam(defaultValue = "monster") String nodeType) {
        Map<String, Object> reward = new LinkedHashMap<>();
        
        int gold = 0;
        Map<String, Object> relic = null; // ✅ 修改为 Map 以包含 id 和 name
        
        switch (nodeType.toLowerCase()) {
            case "elite":
                gold = 40 + random.nextInt(15); 
                relic = getRandomRelic("rare");
                break;
            case "boss":
                gold = 100 + random.nextInt(50); 
                relic = getRandomRelic("rare");
                break;
            case "chest":
                gold = 30 + random.nextInt(20); 
                relic = getRandomRelic("common");
                break;
            case "monster":
            default:
                gold = 15 + random.nextInt(15); 
                relic = null; // 普通小怪通常不给遗物
                break;
        }
        
        reward.put("gold", gold);
        reward.put("relic", relic); // ✅ 放入包含 id 和 name 的 Map 对象
        reward.put("cards", generateCardPool(3));
        
        return reward;
    }

    @GetMapping("/cardPool")
    public List<Map<String, Object>> getCardPool(@RequestParam(defaultValue = "1") String charParam) {
        return generateCardPool(3);
    }

    private List<Map<String, Object>> generateCardPool(int count) {
        List<CardTemplate> allCards = dataRepo.getAllCards();
        if (allCards.isEmpty()) return Collections.emptyList();
        
        List<Map<String, Object>> pool = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        
        int attempts = 0;
        while (pool.size() < count && attempts < count * 3) {
            CardTemplate tpl = allCards.get(random.nextInt(allCards.size()));
            if (!usedIds.contains(tpl.getId())) {
                usedIds.add(tpl.getId());
                pool.add(cardToMap(tpl));
            }
            attempts++;
        }
        
        while (pool.size() < count) {
            CardTemplate tpl = allCards.get(random.nextInt(allCards.size()));
            pool.add(cardToMap(tpl));
        }
        
        return pool;
    }

    private Map<String, Object> cardToMap(CardTemplate tpl) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", tpl.getName());
        map.put("cost", tpl.getCost());
        map.put("damage", tpl.getDamage());
        map.put("block", tpl.getBlock());
        map.put("type", tpl.getType().name());
        map.put("applyStatusType", tpl.getApplyStatusType());
        map.put("applyStatusCount", tpl.getApplyStatusCount());
        map.put("applyStatusTarget", tpl.getApplyStatusTarget());
        return map;
    }

    /**
     * ✅ 核心修复：从配置中读取真实遗物，并返回包含 id 和 name 的 Map
     */
    private Map<String, Object> getRandomRelic(String tier) {
        List<RelicTemplate> allRelics = dataRepo.getAllRelics();
        if (allRelics.isEmpty()) return null;

        // 根据稀有度筛选 (假设 relics.json 中有 rarity 字段，如 "COMMON", "RARE")
        List<RelicTemplate> filtered = new ArrayList<>();
        for (RelicTemplate tpl : allRelics) {
            if (tier.equalsIgnoreCase(tpl.getRarity())) {
                filtered.add(tpl);
            }
        }

        // 如果没有匹配稀有度的，就从所有遗物中随机
        if (filtered.isEmpty()) {
            filtered = allRelics;
        }

        RelicTemplate chosen = filtered.get(random.nextInt(filtered.size()));
        
        // ✅ 构造返回给前端的对象
        Map<String, Object> relicMap = new LinkedHashMap<>();
        relicMap.put("id", chosen.getId());     // 前端需要这个 ID 存入存档
        relicMap.put("name", chosen.getName()); // 前端需要这个 Name 用于显示
        return relicMap;
    }
}