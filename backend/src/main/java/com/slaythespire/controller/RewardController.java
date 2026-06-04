package com.slaythespire.controller;

import com.slaythespire.repository.CardTemplate;
import com.slaythespire.repository.GameDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 奖励与商店接口控制器
 * 负责根据节点类型生成动态奖励，以及提供卡牌池数据
 */
@RestController
@RequestMapping("/api")
public class RewardController {

    @Autowired
    private GameDataRepository dataRepo;

    private final Random random = new Random();

    /**
     * 获取战斗/宝箱奖励
     * 前端调用: GET /api/reward?nodeType=monster|elite|boss|chest
     */
    @GetMapping("/reward")
    public Map<String, Object> getReward(@RequestParam(defaultValue = "monster") String nodeType) {
        Map<String, Object> reward = new LinkedHashMap<>();
        
        int gold = 0;
        String relic = null;
        
        // 根据节点类型区分奖励丰厚程度
        switch (nodeType.toLowerCase()) {
            case "elite": // 精英怪：高金币 + 稀有遗物
                gold = 40 + random.nextInt(15); 
                relic = getRandomRelic("rare");
                break;
            case "boss": // Boss：极高金币 + 稀有遗物
                gold = 100 + random.nextInt(50); 
                relic = getRandomRelic("rare");
                break;
            case "chest": // 宝箱：中量金币 + 普通遗物
                gold = 30 + random.nextInt(20); 
                relic = getRandomRelic("common");
                break;
            case "monster": // 普通小怪：低金币，无遗物
            default:
                gold = 15 + random.nextInt(15); 
                break;
        }
        
        reward.put("gold", gold);
        reward.put("relic", relic); // 小怪战斗时 relic 为 null，前端会自动隐藏遗物按钮
        reward.put("cards", generateCardPool(3)); // 固定提供3张卡供选择
        
        return reward;
    }

    /**
     * 获取供选择的卡牌池（备用接口）
     * 前端调用: GET /api/cardPool?char=1
     */
    @GetMapping("/cardPool")
    public List<Map<String, Object>> getCardPool(@RequestParam(defaultValue = "1") String charParam) {
        return generateCardPool(3);
    }

    // ================= 辅助方法 =================

    /**
     * 从配置仓库中随机抽取指定数量的卡牌，尽量保证不重复
     */
    private List<Map<String, Object>> generateCardPool(int count) {
        List<CardTemplate> allCards = dataRepo.getAllCards();
        if (allCards.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Map<String, Object>> pool = new ArrayList<>();
        Set<String> usedIds = new HashSet<>(); // 用于去重
        
        // 尝试抽取不重复的卡牌
        int attempts = 0;
        while (pool.size() < count && attempts < count * 3) {
            CardTemplate tpl = allCards.get(random.nextInt(allCards.size()));
            if (!usedIds.contains(tpl.getId())) {
                usedIds.add(tpl.getId());
                pool.add(cardToMap(tpl));
            }
            attempts++;
        }
        
        // 如果卡牌池种类不足（比如配置表里只有2种卡），允许重复补齐
        while (pool.size() < count) {
            CardTemplate tpl = allCards.get(random.nextInt(allCards.size()));
            pool.add(cardToMap(tpl));
        }
        
        return pool;
    }

    /**
     * 将 CardTemplate 转换为前端需要的 Map 格式
     */
    private Map<String, Object> cardToMap(CardTemplate tpl) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", tpl.getName());
        map.put("cost", tpl.getCost());
        map.put("damage", tpl.getDamage());
        map.put("block", tpl.getBlock());
        map.put("type", tpl.getType().name());
        return map;
    }

    /**
     * 模拟遗物池（后续可扩展为读取 relics.json）
     */
    private String getRandomRelic(String tier) {
        List<String> commonRelics = Arrays.asList("燃烧之血", "红头骨", "黑星");
        List<String> rareRelics = Arrays.asList("虚空蛋", "贤者之石", "滑鼠胶带");
        
        if ("rare".equals(tier)) {
            return rareRelics.get(random.nextInt(rareRelics.size()));
        } else {
            return commonRelics.get(random.nextInt(commonRelics.size()));
        }
    }
}