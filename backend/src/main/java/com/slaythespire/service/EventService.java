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
     * @param charId 角色 ID
     * @return 执行结果（成功/失败消息）
     */
    public String executeOption(String eventId, int optionIndex, String charId) {
        // 查找事件
        EventTemplate event = events.stream().filter(e -> e.getId().equals(eventId)).findFirst().orElse(null);
        if (event == null) return "事件不存在";
        if (optionIndex < 0 || optionIndex >= event.getOptions().size()) return "选项不存在";

        SaveData saveData = saveService.loadGame(charId);
        if (saveData == null) return "存档不存在";

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
                        // 处理 MAX_HP 类遗物
                        if ("MAX_HP".equals(relic.getEffectType()) && relic.getValue() > 0) {
                            saveData.setMaxHp(saveData.getMaxHp() + relic.getValue());
                            saveData.setPlayerHp(Math.min(saveData.getPlayerHp() + relic.getValue(), saveData.getMaxHp()));
                        }
                        logs.add("获得遗物: " + relic.getName());
                    } else {
                        logs.add("没有可获得的遗物");
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

        saveService.saveGame(charId, saveData);
        return String.join("; ", logs);
    }
}
