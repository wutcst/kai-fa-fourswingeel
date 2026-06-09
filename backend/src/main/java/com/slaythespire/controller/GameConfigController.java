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

import com.slaythespire.repository.CardTemplate;
import com.slaythespire.repository.GameDataRepository;

@RestController
@RequestMapping("/api")
public class GameConfigController {

    @Autowired
    private GameDataRepository dataRepo;
    
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
        
        // 每层节点数量（0层为起点，14层为Boss）
        int[] layerSizes = new int[15];
        layerSizes[0] = 1;           // 起点
        layerSizes[14] = 1;          // BOSS
        for (int i = 1; i < 14; i++) {
            layerSizes[i] = random.nextInt(5) + 1; // 1‑5
        }

        // 按层保存节点，方便后续连线
        List<List<Map<String, Object>>> layerNodes = new ArrayList<>();
        int nodeId = 0;

        for (int layer = 0; layer < 15; layer++) {
            List<Map<String, Object>> curLayer = new ArrayList<>();
            int count = layerSizes[layer];
            // y坐标：底层靠近底部(92%)，顶层靠近顶部(8%)
            double yPercent = 8.0 + (14 - layer) * 6.0;
            for (int j = 0; j < count; j++) {
                Map<String, Object> node = new LinkedHashMap<>();
                // 固定 ID 以匹配前端
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
                // 确定类型与图标
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
                // x均匀分布，避免重叠
                double xPercent = 100.0 * (j + 1) / (count + 1);
                node.put("x", xPercent);
                node.put("y", yPercent);

                curLayer.add(node);
                nodes.add(node);
            }
            layerNodes.add(curLayer);
        }

        // 构造层间连线（保证无交叉）
        for (int layer = 0; layer < 14; layer++) {
            List<Map<String, Object>> fromLayer = layerNodes.get(layer);
            List<Map<String, Object>> toLayer   = layerNodes.get(layer + 1);
            int nFrom = fromLayer.size();
            int nTo   = toLayer.size();
            
            // 标记下层节点是否已被连接
            boolean[] connected = new boolean[nTo];
            int prevIdx = -1;
            
            // 第一遍：为每个上层节点分配一个下层节点，连接关系保持非递减，避免线条交叉
            for (int i = 0; i < nFrom; i++) {
                // 为了视觉效果，尽量让连接不出现“回头”的情况
                int minIdx = Math.max(0, prevIdx);
                int maxIdx = nTo - 1;
                int targetIdx;
                if (prevIdx >= maxIdx) {
                    targetIdx = maxIdx;
                } else {
                    // 随机在允许范围内选择，但不能小于 prevIdx
                    targetIdx = minIdx + random.nextInt(maxIdx - minIdx + 1);
                }
                // 创建边
                Map<String, Object> fromNode = fromLayer.get(i);
                Map<String, Object> toNode   = toLayer.get(targetIdx);
                Map<String, String> edge = new LinkedHashMap<>();
                edge.put("from", fromNode.get("id").toString());
                edge.put("to",   toNode.get("id").toString());
                edges.add(edge);
                
                connected[targetIdx] = true;
                prevIdx = targetIdx;
            }

            // 第二遍：为尚未被连接的下层节点补充连接
            // 为了保证线条依然不交叉，从与它“附近”的上层节点中选择连边
            for (int j = 0; j < nTo; j++) {
                if (!connected[j]) {
                    // 按比例计算出与其最匹配的上层节点索引
                    int candidate = Math.min(nFrom - 1, Math.max(0, (int) Math.floor(j * nFrom / (double) nTo)));
                    // 确保 candidate 不超过上层节点数，并确认这条边尚未存在
                    // 若 candidate 与 j 的连接已经存在，则尝试相邻索引
                    int tries = 0;
                    int finalj=j;
                    int finalCandidate = candidate;
                    while (tries < 5) {
                        boolean duplicate = edges.stream()
                                .anyMatch(e -> e.get("from").equals(fromLayer.get(finalCandidate).get("id").toString())
                                        && e.get("to").equals(toLayer.get(finalj).get("id").toString()));
                        if (!duplicate) {
                            break;
                        }
                        // 尝试左右偏移
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
        } else {
            info.put("name", "未知角色");
            info.put("maxHp", 50);
            info.put("gold", 0);
            info.put("startingDeck", Collections.emptyList());
        }
        return info;
    }

    private Map<String, Object> createRelic(String name, int price) {
        Map<String, Object> relic = new LinkedHashMap<>();
        relic.put("name", name); relic.put("price", price);
        return relic;
    }
}
