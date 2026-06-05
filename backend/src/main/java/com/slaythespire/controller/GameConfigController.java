package com.slaythespire.controller;

import com.slaythespire.repository.CardTemplate;
import com.slaythespire.repository.GameDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class GameConfigController {

    @Autowired
    private GameDataRepository dataRepo;
    
    private final Random random = new Random();

    @GetMapping("/map")
    public Map<String, Object> getMapData() {
        Map<String, Object> mapData = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(createNode("start", "start", "起点", 50, 90, "️"));
        nodes.add(createNode("m1", "monster", "小怪", 25, 70, "👾"));
        nodes.add(createNode("e1", "elite", "精英", 50, 70, "💀"));
        nodes.add(createNode("m2", "monster", "小怪", 75, 70, "👾"));
        nodes.add(createNode("m3", "monster", "小怪", 25, 50, "👾"));
        nodes.add(createNode("chest1", "chest", "宝箱", 50, 50, "📦"));
        nodes.add(createNode("shop1", "shop", "商店", 75, 50, ""));
        nodes.add(createNode("q1", "question", "?", 90, 50, "❓"));
        nodes.add(createNode("e2", "elite", "精英", 37.5, 30, "💀"));
        nodes.add(createNode("m4", "monster", "小怪", 62.5, 30, ""));
        nodes.add(createNode("boss", "boss", "BOSS", 50, 10, "👑"));
        
        List<Map<String, String>> edges = new ArrayList<>();
        edges.add(createEdge("start", "m1")); edges.add(createEdge("start", "e1")); edges.add(createEdge("start", "m2"));
        edges.add(createEdge("m1", "m3")); edges.add(createEdge("m1", "chest1"));
        edges.add(createEdge("e1", "chest1")); edges.add(createEdge("e1", "shop1"));
        edges.add(createEdge("m2", "shop1")); edges.add(createEdge("m2", "q1"));
        edges.add(createEdge("m3", "e2")); edges.add(createEdge("chest1", "e2")); edges.add(createEdge("chest1", "m4"));
        edges.add(createEdge("shop1", "m4")); edges.add(createEdge("q1", "m4"));
        edges.add(createEdge("e2", "boss")); edges.add(createEdge("m4", "boss"));
        
        mapData.put("nodes", nodes);
        mapData.put("edges", edges);
        return mapData;
    }

    @GetMapping("/shop")
    public Map<String, Object> getShopData(@RequestParam(defaultValue = "1") String charParam) {
        Map<String, Object> shop = new LinkedHashMap<>();
        
        List<CardTemplate> allCards = dataRepo.getAllCards();
        List<Map<String, Object>> shopCards = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        
        int attempts = 0;
        while (shopCards.size() < 3 && attempts < 10 && !allCards.isEmpty()) {
            CardTemplate tpl = allCards.get(random.nextInt(allCards.size()));
            if (!usedIds.contains(tpl.getId())) {
                usedIds.add(tpl.getId());
                Map<String, Object> cardMap = new LinkedHashMap<>();
                cardMap.put("name", tpl.getName());
                cardMap.put("cost", tpl.getCost());
                cardMap.put("damage", tpl.getDamage());
                cardMap.put("block", tpl.getBlock());
                cardMap.put("type", tpl.getType().name());
                cardMap.put("applyStatusType", tpl.getApplyStatusType());
                cardMap.put("applyStatusCount", tpl.getApplyStatusCount());
                cardMap.put("applyStatusTarget", tpl.getApplyStatusTarget());
                
                int price = 50 + (tpl.getCost() * 25);
                if (tpl.getDamage() > 10 || tpl.getBlock() > 10) price += 20;
                cardMap.put("price", price);
                
                shopCards.add(cardMap);
            }
            attempts++;
        }
        shop.put("cards", shopCards);
        
        List<Map<String, Object>> shopRelics = new ArrayList<>();
        shopRelics.add(createRelic("古老金币", 100));
        shopRelics.add(createRelic("治愈药水", 50));
        shop.put("relics", shopRelics);
        
        shop.put("deleteCost", 75);
        
        return shop;
    }

    /**
     * ✅ 核心修复：角色初始牌组从配置读取，而非硬编码
     */
    @GetMapping("/character/{charId}")
    public Map<String, Object> getCharacterInfo(@PathVariable String charId) {
        Map<String, Object> info = new LinkedHashMap<>();
        
        if ("1".equals(charId)) {
            info.put("name", "铁甲战士");
            info.put("maxHp", 70);
            info.put("gold", 0);
            
            // 从 cards.json 读取初始牌组 ID
            List<String> starterIds = Arrays.asList("strike", "strike", "strike", "defend", "defend");
            List<Map<String, Object>> deck = new ArrayList<>();
            
            for (String id : starterIds) {
                CardTemplate tpl = dataRepo.getCardById(id);
                if (tpl != null) {
                    Map<String, Object> cardMap = new LinkedHashMap<>();
                    cardMap.put("name", tpl.getName());
                    cardMap.put("cost", tpl.getCost());
                    cardMap.put("damage", tpl.getDamage());
                    cardMap.put("block", tpl.getBlock());
                    cardMap.put("type", tpl.getType().name());
                    // 补全状态字段，确保前端存档完整
                    cardMap.put("applyStatusType", tpl.getApplyStatusType());
                    cardMap.put("applyStatusCount", tpl.getApplyStatusCount());
                    cardMap.put("applyStatusTarget", tpl.getApplyStatusTarget());
                    deck.add(cardMap);
                }
            }
            info.put("startingDeck", deck);
        } else {
            info.put("name", "未知角色");
            info.put("maxHp", 50);
            info.put("gold", 0);
            info.put("startingDeck", Collections.emptyList());
        }
        return info;
    }

    private Map<String, Object> createNode(String id, String type, String label, double x, double y, String icon) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id); node.put("type", type); node.put("label", label);
        node.put("x", x); node.put("y", y); node.put("icon", icon);
        return node;
    }

    private Map<String, String> createEdge(String from, String to) {
        Map<String, String> edge = new LinkedHashMap<>();
        edge.put("from", from); edge.put("to", to);
        return edge;
    }

    private Map<String, Object> createRelic(String name, int price) {
        Map<String, Object> relic = new LinkedHashMap<>();
        relic.put("name", name); relic.put("price", price);
        return relic;
    }
}