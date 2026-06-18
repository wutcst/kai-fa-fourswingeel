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

import com.slaythespire.game.model.CardEffect;
import com.slaythespire.model.SaveData;
import com.slaythespire.game.service.RelicPoolService;
import com.slaythespire.repository.CardTemplate;
import com.slaythespire.repository.GameDataRepository;
import com.slaythespire.repository.RelicTemplate;
import com.slaythespire.service.SaveService;

@RestController
@RequestMapping("/api")
public class GameConfigController {

    @Autowired
    private GameDataRepository dataRepo;

    @Autowired
    private RelicPoolService relicPoolService;

    @Autowired
    private SaveService saveService;

    private final Random random = new Random();

    // ================ 地图生成与读取 ================

    @GetMapping("/map")
    public Map<String, Object> getMapData() {
        SaveData saveData = saveService.loadGame();
        if (saveData != null 
                && saveData.getMapNodes() != null && !saveData.getMapNodes().isEmpty()
                && saveData.getMapEdges() != null && !saveData.getMapEdges().isEmpty()) {
            
            Map<String, Object> mapData = new LinkedHashMap<>();
            mapData.put("nodes", saveData.getMapNodes());
            mapData.put("edges", saveData.getMapEdges());
            System.out.println("✅ 从存档加载地图，节点数: " + saveData.getMapNodes().size());
            return mapData;
        }

        Map<String, Object> newMap = generateNewMap();

        if (saveData == null) {
            saveData = new SaveData();
        }
        saveData.setMapNodes((List<Map<String, Object>>) newMap.get("nodes"));
        saveData.setMapEdges((List<Map<String, String>>) newMap.get("edges"));
        saveService.saveGame(saveData);
        System.out.println("✅ 生成新地图并保存到全局存档");

        return newMap;
    }

    private Map<String, Object> generateNewMap() {
        Map<String, Object> mapData = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, String>> edges = new ArrayList<>();

        final int TOTAL_FLOORS = 17; 
        final int BOSS_FLOOR = 16;
        final int CHEST_FLOOR = 9;   
        final int CAMPFIRE_FLOOR = 15; 

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

        int[] nodeCount = new int[TOTAL_FLOORS];
        nodeCount[0] = 1;
        nodeCount[BOSS_FLOOR] = 1;

        for (int floorIdx = 1; floorIdx <= BOSS_FLOOR - 1; floorIdx++) {
            int prevCount = nodeCount[floorIdx - 1];
            int targetCount;

            if (floorIdx == 1) {
                targetCount = 3 + random.nextInt(2); 
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

        for (int floorIdx = 1; floorIdx <= BOSS_FLOOR - 1; floorIdx++) {
            int count = nodeCount[floorIdx];
            List<Map<String, Object>> curFloor = new ArrayList<>();

            for (int k = 0; k < count; k++) {
                Map<String, Object> childNode = new LinkedHashMap<>();
                String id = "n" + nodes.size();
                childNode.put("id", id);
                childNode.put("type", null);   
                childNode.put("icon", null);
                childNode.put("label", null);

                double yPercent = 95.0 - floorIdx * (93.0 / BOSS_FLOOR);
                double xPercent;
                double dell= 15*(count-1)/2.0;
                xPercent= 50.0 - dell + k * 15.0;
                childNode.put("x", xPercent);
                childNode.put("y", yPercent);

                nodes.add(childNode);
                curFloor.add(childNode);
            }
            floors.add(curFloor);
        }

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

        for (int floorIdx = 1; floorIdx <= BOSS_FLOOR - 1; floorIdx++) {
            List<Map<String, Object>> prevFloor = floors.get(floorIdx - 1);
            List<Map<String, Object>> curFloor = floors.get(floorIdx);
            boolean reverse = (floorIdx % 2 == 0);
            generateMonotonicEdges(edges, prevFloor, curFloor, reverse);
        }
        generateMonotonicEdges(edges, floors.get(15), bossFloor, false);

        int totalSpecialNodes = 0;
        for (int floorIdx = 2; floorIdx <= 14; floorIdx++) {
            if (floorIdx == CHEST_FLOOR || floorIdx == CAMPFIRE_FLOOR) continue;
            totalSpecialNodes += nodeCount[floorIdx];
        }

        int shopCount = (int) Math.round(totalSpecialNodes * 0.08);
        int questionCount = (int) Math.round(totalSpecialNodes * 0.24);
        int eliteCount = (int) Math.round(totalSpecialNodes * 0.11);
        int campfireCount = (int) Math.round(totalSpecialNodes * 0.12);
        if (campfireCount < 1) campfireCount = 1;
        int monsterCount = totalSpecialNodes - shopCount - questionCount - eliteCount - campfireCount;

        int remainingShop = shopCount;
        int remainingCampfire = campfireCount;
        int remainingQuestion = questionCount;
        int remainingElite = eliteCount;
        int remainingMonster = monsterCount;

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
                boolean forbidShop = parentTypes.contains("shop");
                boolean forbidEliteCont = parentTypes.contains("elite");

                String type = rollBucketType(floorIdx,
                        remainingShop, remainingCampfire, remainingQuestion, remainingElite, remainingMonster,
                        forbidShop, false, forbidEliteCont, forbidElite);
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
        } else {
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

        for (int i = 0; i < nextCount; i++) {
            if (childEdgeCount[i] == 0) {
                if (!reverse) {
                    addEdge(edges, curFloor.get(curCount - 1), nextFloor.get(i));
                } else {
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

    // ================ 商店、角色、卡牌接口 (保持不变) ================

    @GetMapping("/shop")
    public Map<String, Object> getShopData(@RequestParam(defaultValue = "1") String charParam,
                                            @RequestParam(required = false) List<String> ownedRelics) {
        Map<String, Object> shop = new LinkedHashMap<>();

        List<CardTemplate> allCards = dataRepo.getAllCards();
        List<CardTemplate> normalCards = new ArrayList<>();
        for (CardTemplate tpl : allCards) {
            if (tpl.isUpgraded()) continue;
            if ("START".equals(tpl.getRarity()) || "SPECIAL".equals(tpl.getRarity())) continue;
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
                cardMap.put("effects", CardEffect.listToMapList(tpl.getEffects()));
                cardMap.put("charId", tpl.getCharId());
                cardMap.put("drawCount", tpl.getDrawCount());
                cardMap.put("rarity", tpl.getRarity());
                
                cardMap.put("upgraded", tpl.isUpgraded());
                cardMap.put("selfDamage", tpl.getSelfDamage());
                cardMap.put("energyGain", tpl.getEnergyGain());
                cardMap.put("multiHitCount", tpl.getMultiHitCount());
                cardMap.put("exhaust", tpl.isExhaust());
                cardMap.put("ethereal", tpl.isEthereal());
                cardMap.put("retain", tpl.isRetain());
                cardMap.put("exhaustHandCount", tpl.getExhaustHandCount());
                cardMap.put("exhaustHandMode", tpl.getExhaustHandMode());
                cardMap.put("unplayable", tpl.isUnplayable());
                cardMap.put("innate", tpl.isInnate());
                cardMap.put("discardCount", tpl.getDiscardCount());
                cardMap.put("discardMode", tpl.getDiscardMode());
                cardMap.put("aoe", tpl.isAoe());
                cardMap.put("drawFirst", tpl.isDrawFirst()); 
                cardMap.put("endOfTurnDamage", tpl.getEndOfTurnDamage());
                cardMap.put("energyLossOnDraw", tpl.getEnergyLossOnDraw());
                cardMap.put("copyToDiscard", tpl.isCopyToDiscard());
                cardMap.put("strengthMultiplier", tpl.getStrengthMultiplier());
                cardMap.put("randomTarget", tpl.isRandomTarget());
                cardMap.put("xCost", tpl.isXCost());
                cardMap.put("exhaustNonAttackBlock", tpl.getExhaustNonAttackBlock());
                cardMap.put("addWoundCount", tpl.getAddWoundCount());
                cardMap.put("blockToDamage", tpl.isBlockToDamage());
                cardMap.put("blockPerAttack", tpl.getBlockPerAttack());
                cardMap.put("energyGainIfDiscarded", tpl.getEnergyGainIfDiscarded());
                cardMap.put("discardAllForCards", tpl.getDiscardAllForCards());
                cardMap.put("discardAllForDraw", tpl.isDiscardAllForDraw());
                cardMap.put("buffCardName", tpl.getBuffCardName());
                cardMap.put("buffDamageAmount", tpl.getBuffDamageAmount());
                cardMap.put("doublePoison", tpl.isDoublePoison());
                cardMap.put("drawPoisonAll", tpl.getDrawPoisonAll());
                cardMap.put("extraPoisonTick", tpl.isExtraPoisonTick());
                cardMap.put("addCardId", tpl.getAddCardId());
                cardMap.put("addCardCount", tpl.getAddCardCount());
                cardMap.put("upgradeHandCount", tpl.getUpgradeHandCount());
                cardMap.put("upgradeHandMode", tpl.getUpgradeHandMode());
                cardMap.put("upgradeAllInHand", tpl.isUpgradeAllInHand());
                cardMap.put("requiresEmptyDrawPile", tpl.isRequiresEmptyDrawPile());

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
        List<String> starterIds=Arrays.asList();
        if ("1".equals(charId)) {
            info.put("name", "铁甲战士");
            info.put("maxHp", 80);               
            info.put("gold", 99);                
            starterIds = Arrays.asList(
                "strike", "strike", "strike", "strike", "strike",
                "defend", "defend", "defend", "defend",
                "bash",
                "oneshot"
            );
        } else if ("2".equals(charId)) {
            info.put("name", "静默猎手");
            info.put("maxHp", 70);
            info.put("gold", 99);
            info.put("startingRelicId", "snake_eye_ring");
            starterIds = Arrays.asList(
                "strike_silent", "strike_silent", "strike_silent", "strike_silent", "strike_silent",
                "defend_silent", "defend_silent", "defend_silent", "defend_silent","defend_silent",
                "survivor","neutralize",
                "oneshot"
            );
        }else{
            info.put("name", "未知角色");
            info.put("maxHp", 50);
            info.put("gold", 0);
            info.put("startingDeck", Collections.emptyList());
            info.put("startingRelicId", null);
        }
        
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
                    cardMap.put("effects", CardEffect.listToMapList(tpl.getEffects()));
                    cardMap.put("charId", tpl.getCharId());
                    cardMap.put("drawCount", tpl.getDrawCount());
                    cardMap.put("rarity", tpl.getRarity());
                    
                    cardMap.put("upgraded", tpl.isUpgraded());
                    cardMap.put("selfDamage", tpl.getSelfDamage());
                    cardMap.put("energyGain", tpl.getEnergyGain());
                    cardMap.put("multiHitCount", tpl.getMultiHitCount());
                    cardMap.put("exhaust", tpl.isExhaust());
                    cardMap.put("ethereal", tpl.isEthereal());
                    cardMap.put("retain", tpl.isRetain());
                    cardMap.put("exhaustHandCount", tpl.getExhaustHandCount());
                    cardMap.put("exhaustHandMode", tpl.getExhaustHandMode());
                    cardMap.put("unplayable", tpl.isUnplayable());
                    cardMap.put("innate", tpl.isInnate());
                    cardMap.put("discardCount", tpl.getDiscardCount());
                    cardMap.put("discardMode", tpl.getDiscardMode());
                    cardMap.put("aoe", tpl.isAoe());
                    cardMap.put("drawFirst", tpl.isDrawFirst());
                    cardMap.put("endOfTurnDamage", tpl.getEndOfTurnDamage());
                    cardMap.put("energyLossOnDraw", tpl.getEnergyLossOnDraw());
                    cardMap.put("copyToDiscard", tpl.isCopyToDiscard());
                    cardMap.put("strengthMultiplier", tpl.getStrengthMultiplier());
                    cardMap.put("randomTarget", tpl.isRandomTarget());
                    cardMap.put("xCost", tpl.isXCost());
                    cardMap.put("exhaustNonAttackBlock", tpl.getExhaustNonAttackBlock());
                    cardMap.put("addWoundCount", tpl.getAddWoundCount());
                    cardMap.put("blockToDamage", tpl.isBlockToDamage());
                    cardMap.put("blockPerAttack", tpl.getBlockPerAttack());
                    cardMap.put("forgeDamageBonus", 0);
                    cardMap.put("energyGainIfDiscarded", tpl.getEnergyGainIfDiscarded());
                    cardMap.put("discardAllForCards", tpl.getDiscardAllForCards());
                    cardMap.put("discardAllForDraw", tpl.isDiscardAllForDraw());
                    cardMap.put("buffCardName", tpl.getBuffCardName());
                    cardMap.put("buffDamageAmount", tpl.getBuffDamageAmount());
                    cardMap.put("doublePoison", tpl.isDoublePoison());
                    cardMap.put("drawPoisonAll", tpl.getDrawPoisonAll());
                    cardMap.put("extraPoisonTick", tpl.isExtraPoisonTick());
                    cardMap.put("addCardId", tpl.getAddCardId());
                    cardMap.put("addCardCount", tpl.getAddCardCount());
                    cardMap.put("upgradeHandCount", tpl.getUpgradeHandCount());
                    cardMap.put("upgradeHandMode", tpl.getUpgradeHandMode());
                    cardMap.put("upgradeAllInHand", tpl.isUpgradeAllInHand());
                    cardMap.put("requiresEmptyDrawPile", tpl.isRequiresEmptyDrawPile());

                    deck.add(cardMap);
                }
            }
            info.put("startingDeck", deck);
            if (!info.containsKey("startingRelicId")) {
                info.put("startingRelicId", "burning_blood");
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
        map.put("effects", CardEffect.listToMapList(tpl.getEffects()));
        map.put("charId", tpl.getCharId());
        map.put("drawCount", tpl.getDrawCount());
        map.put("rarity", tpl.getRarity());
        map.put("upgraded", tpl.isUpgraded());
        map.put("selfDamage", tpl.getSelfDamage());
        map.put("energyGain", tpl.getEnergyGain());
        map.put("multiHitCount", tpl.getMultiHitCount());
        map.put("exhaust", tpl.isExhaust());
        map.put("ethereal", tpl.isEthereal());
        map.put("retain", tpl.isRetain());
        map.put("exhaustHandCount", tpl.getExhaustHandCount());
        map.put("exhaustHandMode", tpl.getExhaustHandMode());
        map.put("unplayable", tpl.isUnplayable());
        map.put("innate", tpl.isInnate());
        map.put("discardCount", tpl.getDiscardCount());
        map.put("discardMode", tpl.getDiscardMode());
        map.put("xCost", tpl.isXCost());
        map.put("aoe", tpl.isAoe());
        map.put("drawFirst", tpl.isDrawFirst());
        map.put("endOfTurnDamage", tpl.getEndOfTurnDamage());
        map.put("energyLossOnDraw", tpl.getEnergyLossOnDraw());
        map.put("copyToDiscard", tpl.isCopyToDiscard());
        map.put("strengthMultiplier", tpl.getStrengthMultiplier());
        map.put("randomTarget", tpl.isRandomTarget());
        map.put("exhaustNonAttackBlock", tpl.getExhaustNonAttackBlock());
        map.put("addWoundCount", tpl.getAddWoundCount());
        map.put("blockToDamage", tpl.isBlockToDamage());
        map.put("blockPerAttack", tpl.getBlockPerAttack());
        map.put("energyGainIfDiscarded", tpl.getEnergyGainIfDiscarded());
        map.put("discardAllForCards", tpl.getDiscardAllForCards());
        map.put("discardAllForDraw", tpl.isDiscardAllForDraw());
        map.put("buffCardName", tpl.getBuffCardName());
        map.put("buffDamageAmount", tpl.getBuffDamageAmount());
        map.put("doublePoison", tpl.isDoublePoison());
        map.put("drawPoisonAll", tpl.getDrawPoisonAll());
        map.put("extraPoisonTick", tpl.isExtraPoisonTick());
        map.put("addCardId", tpl.getAddCardId());
        map.put("addCardCount", tpl.getAddCardCount());
        map.put("upgradeHandCount", tpl.getUpgradeHandCount());
        map.put("upgradeHandMode", tpl.getUpgradeHandMode());
        map.put("upgradeAllInHand", tpl.isUpgradeAllInHand());
        map.put("requiresEmptyDrawPile", tpl.isRequiresEmptyDrawPile());
        return map;
    }
}
