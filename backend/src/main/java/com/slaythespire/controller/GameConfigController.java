package com.slaythespire.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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

    // ================ 重写的地图生成 ================

    /**
     * 生成15层随机地图：起点一个，合并与扩展保持顺序且无死路；
     * 每层节点数偏好3~4，1、2、5为少数；
     * boss前一层全篝火，前两层只有小怪/未知，降低精英、商店、篝火概率。
     */
    @GetMapping("/map")
    public Map<String, Object> getMapData() {
        Map<String, Object> mapData = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, String>> edges = new ArrayList<>();

        final int FLOOR_COUNT = 15; // 0~14
        List<List<Map<String, Object>>> floors = new ArrayList<>();

        // ===== 第0层（底部）单一起点 =====
        List<Map<String, Object>> floor0 = new ArrayList<>();
        Map<String, Object> startNode = new LinkedHashMap<>();
        startNode.put("id", "start");
        startNode.put("type", "start");
        startNode.put("icon", "🏁");
        startNode.put("label", "起点");
        startNode.put("x", 50.0);
        startNode.put("y", 90.0);
        nodes.add(startNode);
        floor0.add(startNode);
        floors.add(floor0);

        // ===== 第1~13层 =====
        for (int floorIdx = 1; floorIdx <= 13; floorIdx++) {
            List<Map<String, Object>> prevFloor = floors.get(floorIdx - 1);
            int prevCount = prevFloor.size();

            int targetCount;

            // ---- 第1层（起点后）：强制3~4个怪物节点 ----
            if (floorIdx == 1) {
                targetCount = 3 + random.nextInt(2); // 3或4
            } else {
                // 根据权重选择节点数（偏好3~4）
                double r = random.nextDouble();
                if (r < 0.05)         targetCount = 1;
                else if (r < 0.15)    targetCount = 2;
                else if (r < 0.50)    targetCount = 3;
                else if (r < 0.85)    targetCount = 4;
                else                  targetCount = 5;
                // 与上层差别不超过1，保证变化平滑
                targetCount = Math.min(5, Math.max(1,
                        prevCount + (random.nextBoolean() ? 1 : -1) * random.nextInt(2)));
                // 再次应用权重偏好，但若不满足则修正
                if (targetCount > 4) targetCount = 4;
                if (targetCount < 2) targetCount = 2;
                // 再随机调整到3或4为主
                // 最终：若上层较小则可能为2，较大则可能为4，中间大多3或4
                targetCount = Math.min(5, Math.max(1, targetCount));
            }

            // 构建 parentSeq（子节点对应的父节点索引列表）
            List<Integer> parentSeq = new ArrayList<>();
            for (int i = 0; i < prevCount; i++) parentSeq.add(i);

            // 用于记录被合并的父节点 -> 合并到的父节点
            Map<Integer, Integer> mergedMap = new HashMap<>();

            // 扩展：插入副本（使同一父节点对应多个子节点）
            while (parentSeq.size() < targetCount) {
                int idx = random.nextInt(parentSeq.size());
                int val = parentSeq.get(idx);
                parentSeq.add(idx + 1, val);
            }
            // 合并：删除相邻元素，同时记录被删元素
            while (parentSeq.size() > targetCount) {
                int idx = random.nextInt(parentSeq.size() - 1);
                int removedIdx = parentSeq.get(idx + 1);
                int keptIdx = parentSeq.get(idx);
                parentSeq.remove(idx + 1);
                mergedMap.put(removedIdx, keptIdx);
            }

            // 生成子节点，同时记录每个父节点对应的第一个子节点（用于额外边）
            Map<Integer, Map<String, Object>> firstChildForParent = new HashMap<>();
            List<Map<String, Object>> curFloor = new ArrayList<>();
            int totalChildren = parentSeq.size();

            for (int k = 0; k < totalChildren; k++) {
                int parentIndex = parentSeq.get(k);
                Map<String, Object> parentNode = prevFloor.get(parentIndex);

                Map<String, Object> childNode = new LinkedHashMap<>();
                String id = "n" + nodes.size();
                childNode.put("id", id);

                // 节点类型
                String type;
                String icon;
                String label;
                if (floorIdx == 13) {
                    type = "campfire";
                    icon = "🔥";
                    label = "篝火";
                } else if (floorIdx == 1) {
                    // 第1层强制为怪物
                    type = "monster";
                    icon = "👾";
                    label = "小怪";
                } else if (floorIdx == 2) {
                    // 第2层小怪或未知
                    type = random.nextBoolean() ? "monster" : "question";
                    icon = type.equals("monster") ? "👾" : "❓";
                    label = type.equals("monster") ? "小怪" : "?";
                } else {
                    double rr = random.nextDouble();
                    if (rr < 0.45) {
                        type = "monster";
                    } else if (rr < 0.75) {
                        type = "question";
                    } else if (rr < 0.85) {
                        type = "chest";
                    } else if (rr < 0.90) {
                        type = "elite";
                    } else if (rr < 0.95) {
                        type = "shop";
                    } else {
                        type = "campfire";
                    }
                    switch (type) {
                        case "monster": icon = "👾"; label = "小怪"; break;
                        case "elite":   icon = "💀"; label = "精英"; break;
                        case "shop":    icon = "🛒"; label = "商店"; break;
                        case "campfire":icon = "🔥"; label = "篝火"; break;
                        case "chest":   icon = "📦"; label = "宝箱"; break;
                        default:        icon = "❓"; label = "?";   break;
                    }
                }
                childNode.put("type", type);
                childNode.put("icon", icon);
                childNode.put("label", label);

                // 位置
                double yPercent = 90.0 - floorIdx * 6.0;
                double xPercent = 100.0 * (k + 1) / (totalChildren + 1);
                childNode.put("x", xPercent);
                childNode.put("y", yPercent);

                nodes.add(childNode);
                curFloor.add(childNode);

                // 正常边：父节点 -> 当前子节点
                addEdge(edges, parentNode, childNode);

                // 记录该父节点的第一个子节点（若尚未记录）
                if (!firstChildForParent.containsKey(parentIndex)) {
                    firstChildForParent.put(parentIndex, childNode);
                }
            }

            // 额外边：被合并的父节点连接到其合并目标父节点所对应的子节点
            for (Map.Entry<Integer, Integer> entry : mergedMap.entrySet()) {
                int removedIdx = entry.getKey();
                int keptIdx = entry.getValue();
                Map<String, Object> removedParent = prevFloor.get(removedIdx);
                Map<String, Object> childOfKept = firstChildForParent.get(keptIdx);
                if (childOfKept != null) {
                    addEdge(edges, removedParent, childOfKept);
                }
            }

            floors.add(curFloor);
        }

        // ===== 第14层（顶部）Boss =====
        List<Map<String, Object>> floor14 = new ArrayList<>();
        Map<String, Object> bossNode = new LinkedHashMap<>();
        bossNode.put("id", "boss");
        bossNode.put("type", "boss");
        bossNode.put("icon", "👑");
        bossNode.put("label", "BOSS");
        bossNode.put("x", 50.0);
        bossNode.put("y", 6.0);
        nodes.add(bossNode);
        floor14.add(bossNode);
        floors.add(floor14);

        // 第13层 -> Boss 连线
        List<Map<String, Object>> prevFloor = floors.get(13);
        for (Map<String, Object> pn : prevFloor) {
            addEdge(edges, pn, bossNode);
        }

        // ===== 额外交汇（增加第二条入边）30% =====
        for (int floorIdx = 0; floorIdx < FLOOR_COUNT - 1; floorIdx++) {
            List<Map<String, Object>> fromList = floors.get(floorIdx);
            List<Map<String, Object>> toList = floors.get(floorIdx + 1);
            int toCount = toList.size();
            int[] inEdges = new int[toCount];
            // 统计入度
            for (Map<String, String> e : edges) {
                String toId = e.get("to");
                for (int t = 0; t < toCount; t++) {
                    if (toList.get(t).get("id").equals(toId)) {
                        inEdges[t]++;
                        break;
                    }
                }
            }
            for (int t = 0; t < toCount; t++) {
                if (inEdges[t] == 1 && random.nextDouble() < 0.3) {
                    int f = random.nextInt(fromList.size());
                    addEdge(edges, fromList.get(f), toList.get(t));
                }
            }
        }

        mapData.put("nodes", nodes);
        mapData.put("edges", edges);
        return mapData;
    }

    private void addEdge(List<Map<String, String>> edges,
                         Map<String, Object> from,
                         Map<String, Object> to) {
        Map<String, String> edge = new LinkedHashMap<>();
        edge.put("from", from.get("id").toString());
        edge.put("to", to.get("id").toString());
        edges.add(edge);
    }

    // ================ 以下原方法保持不变 ================

    @GetMapping("/shop")
    public Map<String, Object> getShopData(@RequestParam(defaultValue = "1") String charParam,
                                            @RequestParam(required = false) List<String> ownedRelics) {
        Map<String, Object> shop = new LinkedHashMap<>();

        List<CardTemplate> allCards = dataRepo.getAllCards();
        List<CardTemplate> normalCards = new ArrayList<>();
        for (CardTemplate tpl : allCards) {
            if (!tpl.isUpgraded()) normalCards.add(tpl);
        }

        List<Map<String, Object>> shopCards = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        int attempts = 0;
        while (shopCards.size() < 3 && attempts < 10 && !normalCards.isEmpty()) {
            CardTemplate tpl = normalCards.get(random.nextInt(normalCards.size()));
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
                cardMap.put("charId", tpl.getCharId());
                cardMap.put("drawCount", tpl.getDrawCount());
                cardMap.put("rarity", tpl.getRarity());  // ✅ 添加稀有度
                int price = 50 + (tpl.getCost() * 25);
                if (tpl.getDamage() > 10 || tpl.getBlock() > 10) price += 20;
                cardMap.put("price", price);
                shopCards.add(cardMap);
            }
            attempts++;
        }
        shop.put("cards", shopCards);

        List<Map<String, Object>> shopRelics = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            RelicTemplate tpl = relicPoolService.drawRelic(charParam, ownedRelics);
            if (tpl == null) break;
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
                    cardMap.put("id", tpl.getId());
                    cardMap.put("name", tpl.getName());
                    cardMap.put("cost", tpl.getCost());
                    cardMap.put("damage", tpl.getDamage());
                    cardMap.put("block", tpl.getBlock());
                    cardMap.put("type", tpl.getType().name());
                    cardMap.put("applyStatusType", tpl.getApplyStatusType());
                    cardMap.put("applyStatusCount", tpl.getApplyStatusCount());
                    cardMap.put("applyStatusTarget", tpl.getApplyStatusTarget());
                    cardMap.put("charId", tpl.getCharId());
                    cardMap.put("drawCount", tpl.getDrawCount());
                    cardMap.put("rarity", tpl.getRarity());  // ✅ 添加稀有度
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

    @GetMapping("/card/{id}")
    public Map<String, Object> getCardById(@PathVariable String id) {
        CardTemplate tpl = dataRepo.getCardById(id);
        if (tpl == null) return null;
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
        map.put("drawCount", tpl.getDrawCount());
        map.put("upgraded", tpl.isUpgraded());
        map.put("rarity", tpl.getRarity());  // ✅ 添加稀有度
        return map;
    }
}
