package com.slaythespire.controller;

import com.slaythespire.repository.CardTemplate;
import com.slaythespire.repository.GameDataRepository;
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
        String relic = null;
        
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
                break;
        }
        
        reward.put("gold", gold);
        reward.put("relic", relic);
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

    /**
     * ✅ 核心修复：将 CardTemplate 完整转换为前端需要的 Map 格式（包含状态配置）
     */
    private Map<String, Object> cardToMap(CardTemplate tpl) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", tpl.getName());
        map.put("cost", tpl.getCost());
        map.put("damage", tpl.getDamage());
        map.put("block", tpl.getBlock());
        map.put("type", tpl.getType().name());
        
        // 补全状态配置字段
        map.put("applyStatusType", tpl.getApplyStatusType());
        map.put("applyStatusCount", tpl.getApplyStatusCount());
        map.put("applyStatusTarget", tpl.getApplyStatusTarget());
        
        return map;
    }

    private String getRandomRelic(String tier) {
        List<String> commonRelics = Arrays.asList("燃烧之血", "红头骨", "黑星");
        List<String> rareRelics = Arrays.asList("虚空蛋", "贤者之石", "滑鼠胶带");
        return "rare".equals(tier) 
            ? rareRelics.get(random.nextInt(rareRelics.size())) 
            : commonRelics.get(random.nextInt(commonRelics.size()));
    }
}