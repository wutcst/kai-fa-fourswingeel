package com.slaythespire.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slaythespire.game.service.RelicPoolService;
import com.slaythespire.model.EventTemplate;
import com.slaythespire.model.SaveData;
import com.slaythespire.repository.GameDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
public class EventService {

    @Autowired
    private SaveService saveService;

    @Autowired
    private RelicPoolService relicPoolService;

    @Autowired
    private GameDataRepository dataRepo;

    private List<EventTemplate> events = new ArrayList<>();
    private final Random random = new Random();

    @PostConstruct
    public void init() {
        loadEvents();
    }

    private void loadEvents() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            InputStream is = new ClassPathResource("config/events.json").getInputStream();
            events = mapper.readValue(is, new TypeReference<List<EventTemplate>>() {});
            System.out.println("✅ 加载事件配置成功 (" + events.size() + " 个)");
        } catch (IOException e) {
            System.err.println("❌ 加载事件配置失败: " + e.getMessage());
        }
    }

    /**
     * 随机选择一个事件（不限制阶段）
     */
    public EventTemplate rollEvent() {
        return rollEvent(1);
    }

    /**
     * 随机选择一个事件（按阶段过滤）
     * @param act 当前阶段 (1/2/3)，事件无 acts 字段则在所有阶段出现
     */
    public EventTemplate rollEvent(int act) {
        if (events.isEmpty()) return null;

        // 加载存档获取已见事件
        SaveData saveData = saveService.loadGame();
        List<String> seen = (saveData != null && saveData.getSeenEvents() != null)
            ? saveData.getSeenEvents() : new ArrayList<String>();

        // 按阶段过滤
        List<EventTemplate> pool = new ArrayList<>();
        for (EventTemplate e : events) {
            if (e.getActs() == null || e.getActs().isEmpty() || e.getActs().contains(act)) {
                pool.add(e);
            }
        }
        if (pool.isEmpty()) return events.get(random.nextInt(events.size()));

        // 排除已见过的事件
        List<EventTemplate> fresh = new ArrayList<>();
        for (EventTemplate e : pool) {
            if (!seen.contains(e.getId())) {
                fresh.add(e);
            }
        }

        // 如果所有事件都见过了，重置
        if (fresh.isEmpty()) {
            seen.clear();
            fresh.addAll(pool);
        }

        return fresh.get(random.nextInt(fresh.size()));
    }

    /**
     * 执行选项效果，直接修改存档并持久化
     * @param eventId 事件 ID
     * @param optionIndex 选项索引
     * @param charId 角色 ID (可选，若为空则从全局存档中读取)
     * @return 执行结果（成功/失败消息）
     */
    public String executeOption(String eventId, int optionIndex, String charId) {
        return executeOption(eventId, optionIndex, charId, null);
    }

    public String executeOption(String eventId, int optionIndex, String charId, List<Integer> cardIndices) {
        // 查找事件
        EventTemplate event = events.stream().filter(e -> e.getId().equals(eventId)).findFirst().orElse(null);
        if (event == null) return "事件不存在";
        if (optionIndex < 0 || optionIndex >= event.getOptions().size()) return "选项不存在";

        // 🆕 移除 charId 参数，直接加载全局存档
        SaveData saveData = saveService.loadGame();
        if (saveData == null) return "存档不存在";

        // 🆕 确定当前角色 ID：优先使用传入的 charId，否则使用全局存档中记录的 charId
        if (charId == null || charId.isEmpty()) {
            charId = saveData.getCharId();
        }

        EventTemplate.EventOption option = event.getOptions().get(optionIndex);
        List<String> logs = new ArrayList<>();

        // 🆕 前置校验：检查玩家是否满足选项条件
        for (Map<String, Object> effect : option.getEffects()) {
            String type = (String) effect.get("type");
            if (type == null) continue;
            if ("GOLD".equals(type)) {
                int value = ((Number) effect.get("value")).intValue();
                if (value < 0 && saveData.getGold() + value < 0) {
                    return "金币不足，无法执行此选项";
                }
            } else if ("REMOVE_CARD".equals(type)) {
                if (saveData.getDeck().isEmpty()) {
                    return "卡组为空，没有可删除的卡牌";
                }
            }
        }

        for (Map<String, Object> effect : option.getEffects()) {
            String type = (String) effect.get("type");
            if (type == null) continue;

            switch (type) {
                case "GOLD": {
                    int value = ((Number) effect.get("value")).intValue();
                    saveData.setGold(Math.max(0, saveData.getGold() + value));
                    logs.add("金币变化: " + value);
                    break;
                }
                case "HP_CHANGE": {
                    int value = ((Number) effect.get("value")).intValue();
                    int newHp = saveData.getPlayerHp() + value;
                    newHp = Math.min(Math.max(1, newHp), saveData.getMaxHp());
                    saveData.setPlayerHp(newHp);
                    logs.add("生命变化: " + value);
                    break;
                }
                case "ADD_RELIC": {
                    String tier = (String) effect.get("tier");
                    // 从对应稀有度池中抽取一个遗物
                    com.slaythespire.repository.RelicTemplate relic = null;
                    if (tier != null) {
                        relic = relicPoolService.drawRelic(charId, saveData.getRelics(), tier);
                    }
                    if (relic == null || "ring".equals(relic.getId())) {
                        // 降级到任何稀有度
                        relic = relicPoolService.drawRelic(charId, saveData.getRelics());
                    }
                    if (relic != null) {
                        saveData.getRelics().add(relic.getId());
                        String rid = relic.getId();
                        // 华夫饼：最大生命+10 并回满血
                        if ("waffle".equals(rid)) {
                            saveData.setMaxHp(saveData.getMaxHp() + relic.getValue());
                            saveData.setPlayerHp(saveData.getMaxHp());
                        }
                        // 处理 MAX_HP 类遗物（除华夫饼外的通用逻辑）
                        else if ("MAX_HP".equals(relic.getEffectType()) && relic.getValue() > 0) {
                            saveData.setMaxHp(saveData.getMaxHp() + relic.getValue());
                            saveData.setPlayerHp(Math.min(saveData.getPlayerHp() + relic.getValue(), saveData.getMaxHp()));
                        }
                        // 处理拾起金币遗物
                        if ("small_gold_bag".equals(rid)) saveData.setGold(saveData.getGold() + 50);
                        else if ("treasure_bag".equals(rid)) saveData.setGold(saveData.getGold() + 150);
                        else if ("ancient_coin".equals(rid)) saveData.setGold(saveData.getGold() + 500);
                        // 处理拾起回血遗物
                        else if ("small_blood".equals(rid)) saveData.setPlayerHp(Math.min(saveData.getPlayerHp() + 10, saveData.getMaxHp()));
                        else if ("apple".equals(rid)) saveData.setPlayerHp(Math.min(saveData.getPlayerHp() + 5, saveData.getMaxHp()));
                        else if ("pear".equals(rid)) saveData.setPlayerHp(Math.min(saveData.getPlayerHp() + 20, saveData.getMaxHp()));
                        logs.add("获得遗物: " + relic.getName());
                    } else {
                        logs.add("没有可获得的遗物");
                    }
                    break;
                }
                case "REMOVE_CARD": {
                    int count = effect.get("count") != null ? ((Number) effect.get("count")).intValue() : 1;
                    List<Map<String, Object>> deck = saveData.getDeck();
                    int removed = 0;
                    List<String> removedNames = new ArrayList<>();
                    for (int i = 0; i < count && !deck.isEmpty(); i++) {
                        int idx = random.nextInt(deck.size());
                        Map<String, Object> card = deck.remove(idx);
                        String name = (String) card.getOrDefault("name", "未知卡牌");
                        removedNames.add(name);
                        removed++;
                    }
                    if (removed > 0) {
                        logs.add("删除了 " + removed + " 张卡牌: " + String.join(", ", removedNames));
                    } else {
                        logs.add("卡组为空，没有可删除的卡牌");
                    }
                    break;
                }
                case "CHOOSE_REMOVE_CARD": {
                    int count = effect.get("count") != null ? ((Number) effect.get("count")).intValue() : 1;
                    List<Map<String, Object>> deck = saveData.getDeck();
                    int removed = 0;
                    List<String> removedNames = new ArrayList<>();
                    if (cardIndices != null && !cardIndices.isEmpty()) {
                        if (cardIndices.size() > count) {
                            logs.add("警告：选择了 " + cardIndices.size() + " 张，超出上限 " + count + "，仅删除前 " + count + " 张");
                        }
                        // 按索引降序排列，从后往前删避免下标偏移
                        List<Integer> sorted = new ArrayList<>(cardIndices);
                        sorted.sort(Collections.reverseOrder());
                        for (int idx : sorted) {
                            if (removed >= count) break;
                            if (idx >= 0 && idx < deck.size()) {
                                Map<String, Object> card = deck.remove(idx);
                                String name = (String) card.getOrDefault("name", "未知卡牌");
                                removedNames.add(name);
                                removed++;
                            }
                        }
                    }
                    if (removed > 0) {
                        logs.add("选择性删除了 " + removed + " 张卡牌: " + String.join(", ", removedNames));
                    } else {
                        logs.add("未选择任何卡牌");
                    }
                    break;
                }
                case "ADD_RANDOM_CARD": {
                    // 从卡牌池随机选一张（未升级、非起始、匹配角色）
                    List<com.slaythespire.repository.CardTemplate> allCards = dataRepo.getAllCards();
                    List<com.slaythespire.repository.CardTemplate> validCards = new ArrayList<>();
                    for (com.slaythespire.repository.CardTemplate tpl : allCards) {
                        if (tpl.isUpgraded()) continue;
                        if ("START".equals(tpl.getRarity())) continue;
                        // 按角色过滤
                        if (tpl.getCharId() != null && !tpl.getCharId().equals(charId)) continue;
                        validCards.add(tpl);
                    }
                    if (!validCards.isEmpty()) {
                        com.slaythespire.repository.CardTemplate chosen = validCards.get(random.nextInt(validCards.size()));
                        // 转换为 Map 结构
                        Map<String, Object> cardMap = new LinkedHashMap<>();
                        cardMap.put("id", chosen.getId());
                        cardMap.put("name", chosen.getName());
                        cardMap.put("cost", chosen.getCost());
                        cardMap.put("damage", chosen.getDamage());
                        cardMap.put("block", chosen.getBlock());
                        cardMap.put("type", chosen.getType().name());
                        cardMap.put("effects", com.slaythespire.game.model.CardEffect.listToMapList(chosen.getEffects()));
                        cardMap.put("exhaust", chosen.isExhaust());
                        cardMap.put("retain", chosen.isRetain());
                        cardMap.put("ethereal", chosen.isEthereal());
                        cardMap.put("selfDamage", chosen.getSelfDamage());
                        cardMap.put("energyGain", chosen.getEnergyGain());
                        cardMap.put("multiHitCount", chosen.getMultiHitCount());
                        cardMap.put("exhaustHandCount", chosen.getExhaustHandCount());
                        cardMap.put("exhaustHandMode", chosen.getExhaustHandMode());
                        cardMap.put("unplayable", chosen.isUnplayable());
                        cardMap.put("innate", chosen.isInnate());
                        cardMap.put("discardCount", chosen.getDiscardCount());
                        cardMap.put("discardMode", chosen.getDiscardMode());
                        cardMap.put("xCost", chosen.isXCost());
                        cardMap.put("aoe", chosen.isAoe());
                        cardMap.put("drawFirst", chosen.isDrawFirst());
                        cardMap.put("copyToDiscard", chosen.isCopyToDiscard());
                        cardMap.put("strengthMultiplier", chosen.getStrengthMultiplier());
                        cardMap.put("randomTarget", chosen.isRandomTarget());
                        cardMap.put("endOfTurnDamage", chosen.getEndOfTurnDamage());
                        cardMap.put("energyLossOnDraw", chosen.getEnergyLossOnDraw());
                        cardMap.put("exhaustNonAttackBlock", chosen.getExhaustNonAttackBlock());
                        cardMap.put("addWoundCount", chosen.getAddWoundCount());
                        cardMap.put("blockToDamage", chosen.isBlockToDamage());
                        cardMap.put("blockPerAttack", chosen.getBlockPerAttack());
                        cardMap.put("energyGainIfDiscarded", chosen.getEnergyGainIfDiscarded());
                        cardMap.put("discardAllForCards", chosen.getDiscardAllForCards());
                        cardMap.put("discardAllForDraw", chosen.isDiscardAllForDraw());
                        cardMap.put("buffCardName", chosen.getBuffCardName());
                        cardMap.put("buffDamageAmount", chosen.getBuffDamageAmount());
                        cardMap.put("doublePoison", chosen.isDoublePoison());
                        cardMap.put("drawPoisonAll", chosen.getDrawPoisonAll());
                        cardMap.put("extraPoisonTick", chosen.isExtraPoisonTick());
                        cardMap.put("addCardId", chosen.getAddCardId());
                        cardMap.put("addCardCount", chosen.getAddCardCount());
                        cardMap.put("upgradeHandCount", chosen.getUpgradeHandCount());
                        cardMap.put("upgradeHandMode", chosen.getUpgradeHandMode());
                        cardMap.put("upgradeAllInHand", chosen.isUpgradeAllInHand());
                        cardMap.put("requiresEmptyDrawPile", chosen.isRequiresEmptyDrawPile());
                        cardMap.put("charId", chosen.getCharId());
                        cardMap.put("drawCount", chosen.getDrawCount());
                        cardMap.put("upgraded", chosen.isUpgraded());
                        cardMap.put("rarity", chosen.getRarity());
                        saveData.getDeck().add(cardMap);
                        logs.add("获得卡牌: " + chosen.getName());
                    } else {
                        logs.add("没有可获得的卡牌");
                    }
                    break;
                }
                default:
                    logs.add("未知效果: " + type);
            }
        }

        // 🆕 标记事件为已见，防止重复
        if (saveData.getSeenEvents() == null) {
            saveData.setSeenEvents(new ArrayList<>());
        }
        if (!saveData.getSeenEvents().contains(eventId)) {
            saveData.getSeenEvents().add(eventId);
        }

        // 🆕 移除 charId 参数，直接保存全局存档
        saveService.saveGame(saveData);
        return String.join("; ", logs);
    }
}