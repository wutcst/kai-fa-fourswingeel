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

    @GetMapping("/map")
    public Map<String, Object> getMapData() {
        Map<String, Object> mapData = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, String>> edges = new ArrayList<>();

        final int TOTAL_FLOORS = 17; // 0:起点, 1~15:中间层, 16:boss
        final int BOSS_FLOOR = 16;
        final int CHEST_FLOOR = 9;   // 第9层（1-index）整层宝箱
        final int CAMPFIRE_FLOOR = 15; // 第15层（1-index）整层篝火

        // 第0层（底部）单一起点
        List<Map<String, Object>> floor0 = new ArrayList<>();
        Map<String, Object> startNode = new LinkedHashMap<>();
        startNode.put("id", "start");
        startNode.put("type", "start");
        startNode.put("icon", "🏁");
        startNode.put("label", "起点");
        startNode.put("x", 50.0);
        startNode.put("y", 95.0);
        nodes.add(startNode);
        floor0.add(startNode);

        List<List<Map<String, Object>>> floors = new ArrayList<>();
        floors.add(floor0);

        // 预先确定每层的节点数
        int[] nodeCount = new int[TOTAL_FLOORS];
        nodeCount[0] = 1;
        nodeCount[BOSS_FLOOR] = 1;

        for (int floorIdx = 1; floorIdx <= BOSS_FLOOR - 1; floorIdx++) {
            int prevCount = nodeCount[floorIdx - 1];
            int targetCount;

            if (floorIdx == 1) {
                targetCount = 3 + random.nextInt(2); // 3或4
            } else {
                double r = random.nextDouble();
                if (r < 0.02) targetCount = 1;
                else if (r < 0.05) targetCount = 2;
                else if (r < 0.20) targetCount = 3;
                else if (r < 0.55) targetCount = 4;
                else if (r < 0.90) targetCount = 5;
                else targetCount = 6;
                if (targetCount > prevCount + 2) targetCount = prevCount + 2;
                if (targetCount < prevCount - 2) targetCount = prevCount - 2;
                targetCount = Math.max(1, Math.min(6, targetCount));
            }
            nodeCount[floorIdx] = targetCount;
        }

        // ---------- 第一阶段：创建所有节点（占位），然后生成连线 ----------
        for (int floorIdx = 1; floorIdx <= BOSS_FLOOR - 1; floorIdx++) {
            int count = nodeCount[floorIdx];
            List<Map<String, Object>> curFloor = new ArrayList<>();

            for (int k = 0; k < count; k++) {
                Map<String, Object> childNode = new LinkedHashMap<>();
                String id = "n" + nodes.size();
                childNode.put("id", id);
                childNode.put("type", null);   // 占位
                childNode.put("icon", null);
                childNode.put("label", null);

                double yPercent = 95.0 - floorIdx * (93.0 / BOSS_FLOOR);
                // ✅ 节点水平位置均匀分布，避免右偏
                double xPercent;
                if (count == 1) {
                    xPercent = 50.0;
                } else {
                    xPercent = 5.0 + 90.0 * k / (count - 1);
                }
                childNode.put("x", xPercent);
                childNode.put("y", yPercent);

                nodes.add(childNode);
                curFloor.add(childNode);
            }
            floors.add(curFloor);
        }

        // ===== Boss层（占位）=====
        List<Map<String, Object>> bossFloor = new ArrayList<>();
        Map<String, Object> bossNode = new LinkedHashMap<>();
        bossNode.put("id", "boss");
        bossNode.put("type", "boss");
        bossNode.put("icon", "👑");
        bossNode.put("label", "BOSS");
        bossNode.put("x", 50.0);
        bossNode.put("y", 2.0);
        nodes.add(bossNode);
        bossFloor.add(bossNode);
        floors.add(bossFloor);

        // ===== 生成连线（根据当前占位节点），奇偶层交替方向 =====
        for (int floorIdx = 1; floorIdx <= BOSS_FLOOR - 1; floorIdx++) {
            List<Map<String, Object>> prevFloor = floors.get(floorIdx - 1);
            List<Map<String, Object>> curFloor = floors.get(floorIdx);
            // 从prevFloor连接至curFloor，方向交替：
            // 奇数层从左向右（递增），偶数层从右向左（递减）
            boolean reverse = (floorIdx % 2 == 0) ? true : false;
            generateMonotonicEdges(edges, prevFloor, curFloor, reverse);
        }
        // 连接第15层 -> Boss（第15层为奇数层，正向）
        generateMonotonicEdges(edges, floors.get(15), bossFloor, false);

        // ---------- 第二阶段：分配节点类型 ----------
        // 计算桶数（不含campfire，因为campfire只出现在第15层）
        int totalSpecialNodes = 0;
        for (int floorIdx = 2; floorIdx <= 14; floorIdx++) {
            if (floorIdx == CHEST_FLOOR || floorIdx == CAMPFIRE_FLOOR) continue;
            totalSpecialNodes += nodeCount[floorIdx];
        }

        int shopCount = (int) Math.round(totalSpecialNodes * 0.05);
        // campfireCount = 0 （第15层已经固定为篝火，其他层不允许出现篝火）
        int questionCount = (int) Math.round(totalSpecialNodes * 0.22);
        int eliteCount = (int) Math.round(totalSpecialNodes * 0.08);
        int monsterCount = totalSpecialNodes - shopCount - questionCount - eliteCount;

        int remainingShop = shopCount;
        int remainingCampfire = 0;  // 始终为0
        int remainingQuestion = questionCount;
        int remainingElite = eliteCount;
        int remainingMonster = monsterCount;

        // 先设置固定层类型（第1、9、15层）
        for (int floorIdx = 1; floorIdx <= BOSS_FLOOR - 1; floorIdx++) {
            List<Map<String, Object>> curFloor = floors.get(floorIdx);
            for (Map<String, Object> node : curFloor) {
                String type;
                String icon;
                String label;
                if (floorIdx == 1) {
                    type = "monster"; icon = "👾"; label = "小怪";
                } else if (floorIdx == CHEST_FLOOR) {
                    type = "chest"; icon = "📦"; label = "宝箱";
                } else if (floorIdx == CAMPFIRE_FLOOR) {
                    type = "campfire"; icon = "🔥"; label = "篝火";
                } else {
                    type = null; icon = null; label = null;
                }
                node.put("type", type);
                node.put("icon", icon);
                node.put("label", label);
            }
        }

        // 为其他层分配类型，同时检查连续类型限制
        for (int floorIdx = 2; floorIdx <= 14; floorIdx++) {
            if (floorIdx == CHEST_FLOOR || floorIdx == CAMPFIRE_FLOOR) continue;
            List<Map<String, Object>> curFloor = floors.get(floorIdx);
            for (Map<String, Object> node : curFloor) {
                String nodeId = (String) node.get("id");
                List<Map<String, String>> parentEdges = new ArrayList<>();
                for (Map<String, String> e : edges) {
                    if (e.get("to").equals(nodeId)) parentEdges.add(e);
                }
                Set<String> parentTypes = new HashSet<>();
                for (Map<String, String> e : parentEdges) {
                    String fromId = e.get("from");
                    Map<String, Object> fromNode = getNodeById(fromId, nodes);
                    if (fromNode != null) {
                        String pt = (String) fromNode.get("type");
                        if (pt != null) parentTypes.add(pt);
                    }
                }
                boolean forbidElite = (floorIdx <= 5);
                boolean forbidShop       = parentTypes.contains("shop");
                boolean forbidEliteCont   = parentTypes.contains("elite");

                String type = rollBucketType(floorIdx,
                        remainingShop, remainingCampfire, remainingQuestion, remainingElite, remainingMonster,
                        forbidShop, true, forbidEliteCont, forbidElite);
                switch (type) {
                    case "shop":     remainingShop--; break;
                    case "campfire": remainingCampfire--; break;
                    case "question": remainingQuestion--; break;
                    case "elite":    remainingElite--; break;
                    case "monster":  remainingMonster--; break;
                }
                node.put("type", type);
                String icon, label;
                switch (type) {
                    case "monster":  icon = "👾"; label = "小怪"; break;
                    case "elite":    icon = "💀"; label = "精英"; break;
                    case "shop":     icon = "🛒"; label = "商店"; break;
                    case "campfire": icon = "🔥"; label = "篝火"; break;
                    case "chest":    icon = "📦"; label = "宝箱"; break;
                    default:         icon = "❓"; label = "?";   break;
                }
                node.put("icon", icon);
                node.put("label", label);
            }
        }

        mapData.put("nodes", nodes);
        mapData.put("edges", edges);
        return mapData;
    }

    private Map<String, Object> getNodeById(String id, List<Map<String, Object>> nodes) {
        for (Map<String, Object> n : nodes) {
            if (id.equals(n.get("id"))) return n;
        }
        return null;
    }

    private String rollBucketType(int floorIdx,
                                   int remShop, int remCampfire, int remQuestion, int remElite, int remMonster,
                                   boolean forbidShop, boolean forbidCampfire,
                                   boolean forbidEliteCont, boolean forbidElite) {
        List<String> types = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();

        if (remShop > 0 && !forbidShop) {
            types.add("shop");
            weights.add(remShop);
        }
        if (remCampfire > 0 && !forbidCampfire) {
            types.add("campfire");
            weights.add(remCampfire);
        }
        if (remQuestion > 0) {
            types.add("question");
            weights.add(remQuestion);
        }
        if (remElite > 0 && !forbidElite && !forbidEliteCont) {
            types.add("elite");
            weights.add(remElite);
        }
        if (remMonster > 0) {
            types.add("monster");
            weights.add(remMonster);
        }

        if (types.isEmpty()) return "monster";

        int totalWeight = 0;
        for (int w : weights) totalWeight += w;
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (int i = 0; i < types.size(); i++) {
            cumulative += weights.get(i);
            if (roll < cumulative) return types.get(i);
        }
        return types.get(types.size() - 1);
    }

    private void generateMonotonicEdges(List<Map<String, String>> edges,
                                    List<Map<String, Object>> curFloor,
                                    List<Map<String, Object>> nextFloor,
                                    boolean reverse) {
    int curCount = curFloor.size();
    int nextCount = nextFloor.size();
    if (curCount == 0 || nextCount == 0) return;

    int[] childEdgeCount = new int[nextCount];

    if (curCount == 1) {
        for (int i = 0; i < nextCount; i++) {
            addEdge(edges, curFloor.get(0), nextFloor.get(i));
            childEdgeCount[i]++;
        }
    } else if (!reverse) {
        // ================================================================
        // 【正向（奇数层）：父节点与子节点均从左向右遍历】
        // ================================================================
        int childIdx = 0; 
        int lastIdx = nextCount - 1;

        for (int p = 0; p < curCount; p++) {
            int maxConnect = Math.min(3, lastIdx - childIdx + 1);
            
            int remainingParents = curCount - 1 - p;
            if (lastIdx - childIdx + 1 - maxConnect > remainingParents * 3) {
                maxConnect = Math.min(3, lastIdx - childIdx + 1 - remainingParents * 1);
            }
            if (p == curCount - 1) {
                maxConnect = lastIdx - childIdx + 1;
            }

            int connectCount = maxConnect > 0 ? (1 + random.nextInt(maxConnect)) : 1;
            
            for (int j = 0; j < connectCount; j++) {
                if (childIdx > lastIdx) break;
                
                addEdge(edges, curFloor.get(p), nextFloor.get(childIdx));
                childEdgeCount[childIdx]++; 
                childIdx++;
            }
            
            if (childIdx > 0) {
                childIdx--;
            }
        }
    } 
    else {
        // ================================================================
        // 【反向（偶数层）：父节点与子节点均从右向左连】
        // ================================================================
        int childIdx = nextCount - 1; 

        for (int p = curCount - 1; p >= 0; p--) {
            int maxConnect = Math.min(3, childIdx + 1);
            
            int remainingParents = p; 
            if (childIdx + 1 - maxConnect > remainingParents * 3) {
                maxConnect = Math.min(3, childIdx + 1 - remainingParents * 1);
            }
            if (p == 0) {
                maxConnect = childIdx + 1;
            }

            int connectCount = maxConnect > 0 ? (1 + random.nextInt(maxConnect)) : 1;

            for (int j = 0; j < connectCount; j++) {
                if (childIdx < 0) break;
                
                addEdge(edges, curFloor.get(p), nextFloor.get(childIdx));
                childEdgeCount[childIdx]++; 
                childIdx--; 
            }

            if (childIdx < nextCount - 1) {
                childIdx++;
            }
        }
    }

    // ================================================================
    // 【听老哥的：严格区分奇偶，安全收容至尽头节点】
    // ================================================================
    for (int i = 0; i < nextCount; i++) {
        if (childEdgeCount[i] == 0) {
            if (!reverse) {
                // 正向层：既然是从左往右漏掉的，说明是在右侧收尾处有残留，直接无脑连给最后一个父节点
                addEdge(edges, curFloor.get(curCount - 1), nextFloor.get(i));
            } else {
                // 反向层：从右往左收尾时漏掉的，残留必然在最左侧，直接无脑连给第 0 个父节点
                addEdge(edges, curFloor.get(0), nextFloor.get(i));
            }
            childEdgeCount[i]++;
        }
    }
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
            if (tpl.isUpgraded()) continue;
            if ("START".equals(tpl.getRarity())) continue;
            if (tpl.getCharId() != null && !tpl.getCharId().equals(charParam)) continue;
            normalCards.add(tpl);
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
                cardMap.put("rarity", tpl.getRarity());
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
            List<String> starterIds = Arrays.asList(
                "strike", "strike", "strike", "strike", "strike",
                "defend", "defend", "defend", "defend",
                "bash"
            );
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
                    cardMap.put("rarity", tpl.getRarity());
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
        map.put("rarity", tpl.getRarity());
        return map;
    }
}
