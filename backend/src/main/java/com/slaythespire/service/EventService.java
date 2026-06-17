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
     * 随机选择一个事件
     */
    public EventTemplate rollEvent() {
        if (events.isEmpty()) return null;
        return events.get(random.nextInt(events.size()));
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
        boolean valid = true;
        List<String> logs = new ArrayList<>();

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
                        // 彩蛋遗物弹窗（事件获得时随日志展示）
                        if ("jiucai_anger".equals(rid)) logs.add("😡 怪物太难都是九才8干的，大家一起骂他");
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
                        // 按索引降序排列，从后往前删避免下标偏移
                        List<Integer> sorted = new ArrayList<>(cardIndices);
                        sorted.sort(Collections.reverseOrder());
                        for (int idx : sorted) {
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

        // 🆕 移除 charId 参数，直接保存全局存档
        saveService.saveGame(saveData);
        return String.join("; ", logs);
    }
}