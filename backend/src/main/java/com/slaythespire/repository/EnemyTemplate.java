package com.slaythespire.repository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnemyTemplate {
    private final String id;
    private final String name;
    private final int maxHp;
    private final int baseAttack;
    private final List<IntentTemplate> intents;
    
    // ✅ 新增：怪物开局自带的状态效果（如 Boss 自带力量）
    private final Map<String, Integer> initialStatuses;

    @JsonCreator
    public EnemyTemplate(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("maxHp") int maxHp,
            @JsonProperty("attackDamage") int baseAttack,
            @JsonProperty("intents") List<IntentTemplate> intents,
            @JsonProperty("initialStatuses") Map<String, Integer> initialStatuses) {
        this.id = id;
        this.name = name;
        this.maxHp = maxHp;
        this.baseAttack = baseAttack;
        
        // 处理意图列表（兼容旧数据）
        List<IntentTemplate> tempIntents;
        if (intents == null || intents.isEmpty()) {
            tempIntents = Collections.singletonList(
                new IntentTemplate(IntentType.ATTACK, baseAttack, "攻击", null, 0, null)
            );
        } else {
            tempIntents = new ArrayList<>();
            for (IntentTemplate intent : intents) {
                if (intent != null) tempIntents.add(intent);
            }
            if (tempIntents.isEmpty()) {
                tempIntents = Collections.singletonList(
                    new IntentTemplate(IntentType.ATTACK, baseAttack, "攻击", null, 0, null)
                );
            }
        }
        this.intents = tempIntents;

        // 处理初始状态
        this.initialStatuses = (initialStatuses != null) ? initialStatuses : new HashMap<>();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getMaxHp() { return maxHp; }
    public int getBaseAttack() { return baseAttack; }
    public List<IntentTemplate> getIntents() { return intents; }
    
    // ✅ 新增 Getter
    public Map<String, Integer> getInitialStatuses() { return initialStatuses; }

    @Override
    public String toString() {
        return String.format("EnemyTemplate{id='%s', name='%s', hp=%d, intents=%s}", 
                           id, name, maxHp, intents);
    }
}