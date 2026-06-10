package com.slaythespire.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.slaythespire.game.service.RelicPoolService;
import com.slaythespire.repository.CardTemplate;
import com.slaythespire.repository.GameDataRepository;
import com.slaythespire.repository.RelicTemplate;

@RestController
@RequestMapping("/api")
public class GameConfigController {

    @Autowired
    private GameDataRepository dataRepo;

    @Autowired
    private RelicPoolService relicPoolService;

    private final Random random = new Random();

    /**
     * 生成随机15层无交叉路径的地图
     */
    @GetMapping("/map")
    public Map<String, Object> getMapData() {
        Map<String, Object> mapData = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, String>> edges = new ArrayList<>();

        String[] nodeTypes = {"monster", "elite", "shop", "campfire", "chest", "question"};
        String[] icons      = {"👾",      "💀",    "🛒",   "🔥",       "📦",    "❓"};
        String[] labels     = {"小怪",     "精英",   "商店",  "篝火",      "宝箱",   "?"};
        
        int[] layerSizes = new int[15];
        layerSizes[0] = 1;
        layerSizes[14] = 1;
        for (int i = 1; i < 14; i++) {
            layerSizes[i] = random.nextInt(5) + 1;
        }

        List<List<Map<String, Object>>> layerNodes = new ArrayList<>();
        int nodeId = 0;

        for (int layer = 0; layer < 15; layer++) {
            List<Map<String, Object>> curLayer = new ArrayList<>();
            int count = layerSizes[layer];
            double yPercent = 8.0 + (14 - layer) * 6.0;
            for (int j = 0; j < count; j++) {
                Map<String, Object> node = new LinkedHashMap<>();
                String id;
                if (layer == 0) {
                    id = "start";
                } else if (layer == 14) {
                    id = "boss";
                } else {
                    id = "n" + nodeId;
                    nodeId++;
                }
                node.put("id", id);
                
                String type, icon, label;
                if (layer == 0) {
                    type = "start"; icon = "🛡️"; label = "起点";
                } else if (layer == 14) {
                    type = "boss";  icon = "👑"; label = "BOSS";
                } else {
                    int idx = random.nextInt(nodeTypes.length);
                    type  = nodeTypes[idx];
                    icon  = icons[idx];
                    label = labels[idx];
                }
                node.put("type", type);
                node.put("icon", icon);
                node.put("label", label);
                double xPercent = 100.0 * (j + 1) / (count + 1);
                node.put("x", xPercent);
                node.put("y", yPercent);

                curLayer.add(node);
                nodes.add(node);
            }
            layerNodes.add(curLayer);
        }

        for (int layer = 0; layer < 14; layer++) {
            List<Map<String, Object>> fromLayer = layerNodes.get(layer);
            List<Map<String, Object>> toLayer   = layerNodes.get(layer + 1);
            int nFrom = fromLayer.size();
            int nTo   = toLayer.size();
            
            boolean[] connected = new boolean[nTo];
            int prevIdx = -1;
            
            for (int i = 0; i < nFrom; i++) {
                int minIdx = Math.max(0, prevIdx);
                int maxIdx = nTo - 1;
                int targetIdx;
                if (prevIdx >= maxIdx) {
                    targetIdx = maxIdx;
                } else {
                    targetIdx = minIdx + random.nextInt(maxIdx - minIdx + 1);
                }
                Map<String, Object> fromNode = fromLayer.get(i);
                Map<String, Object> toNode   = toLayer.get(targetIdx);
                Map<String, String> edge = new LinkedHashMap<>();
                edge.put("from", fromNode.get("id").toString());
                edge.put("to",   toNode.get("id").toString());
                edges.add(edge);
                
                connected[targetIdx] = true;
                prevIdx = targetIdx;
            }

            for (int j = 0; j < nTo; j++) {
                if (!connected[j]) {
                    int candidate = Math.min(nFrom - 1, Math.max(0, (int) Math.floor(j * nFrom / (double) nTo)));
                    int tries = 0;
                    int finalj = j;
                    int finalCandidate = candidate;
                    while (tries < 5) {
                        boolean duplicate = edges.stream()
                                .anyMatch(e -> e.get("from").equals(fromLayer.get(finalCandidate).get("id").toString())
                                        && e.get("to").equals(toLayer.get(finalj).get("id").toString()));
                        if (!duplicate) {
                            break;
                        }
                        if (candidate > 0 && (tries % 2 == 0)) candidate--;
                        else if (candidate < nFrom - 1) candidate++;
                        tries++;
                    }
                    
                    Map<String, String> edge = new LinkedHashMap<>();
                    edge.put("from", fromLayer.get(candidate).get("id").toString());
                    edge.put("to",   toLayer.get(j).get("id").toString());
                    edges.add(edge);
                    connected[j] = true;
                }
            }
        }

        mapData.put("nodes", nodes);
        mapData.put("edges", edges);
        return mapData;
    }

    /**
     * 商店数据
     */
    @GetMapping("/shop")
    public Map<String, Object> getShopData(@RequestParam(defaultValue = "1") String charParam,
                                            @RequestParam(required = false) List<String> ownedRelics) {
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
                cardMap.put("id", tpl.getId());
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

        // 商店遗物：从 RelicPoolService 按权重抽取 2 件
        List<Map<String, Object>> shopRelics = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            RelicTemplate tpl = relicPoolService.drawRelic(charParam, ownedRelics);
            if (tpl == null) break;
            // 抽取后从 ownedRelics 排除，防止两次抽到相同遗物
            if (ownedRelics == null) ownedRelics = new ArrayList<>();
            ownedRelics.add(tpl.getId());
            Map<String, Object> relicMap = new LinkedHashMap<>();
            relicMap.put("id", tpl.getId());
            relicMap.put("name", tpl.getName());
            relicMap.put("price", "ring".equals(tpl.getId()) ? 0 : 100 + new Random().nextInt(50));
            relicMap.put("effectType", tpl.getEffectType());
            relicMap.put("value", tpl.getValue());
            relicMap.put("rarity", tpl.getRarity());
            shopRelics.add(relicMap);
        }
        shop.put("relics", shopRelics);
        shop.put("deleteCost", 75);
        return shop;
    }

    /**
     * 角色初始数据
     */
    @GetMapping("/character/{charId}")
    public Map<String, Object> getCharacterInfo(@PathVariable String charId) {
        Map<String, Object> info = new LinkedHashMap<>();
        if ("1".equals(charId)) {
            info.put("name", "铁甲战士");
            info.put("maxHp", 70);
            info.put("gold", 0);
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
                    cardMap.put("applyStatusType", tpl.getApplyStatusType());
                    cardMap.put("applyStatusCount", tpl.getApplyStatusCount());
                    cardMap.put("applyStatusTarget", tpl.getApplyStatusTarget());
                    deck.add(cardMap);
                }
            }
            info.put("startingDeck", deck);
            info.put("startingRelicId", "burning_blood");
        } else {
            info.put("name", "未知角色");
            info.put("maxHp", 50);
            info.put("gold", 0);
            info.put("startingDeck", Collections.emptyList());
            info.put("startingRelicId", null);
        }
        return info;
    }

    /**
     * ✅ 新增：获取所有遗物配置（供前端将 ID 翻译为中文名和描述）
     */
    @GetMapping("/relics")
    public List<Map<String, Object>> getAllRelics() {
        List<RelicTemplate> allRelics = dataRepo.getAllRelics();
        List<Map<String, Object>> result = new ArrayList<>();
        for (RelicTemplate tpl : allRelics) {
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
}