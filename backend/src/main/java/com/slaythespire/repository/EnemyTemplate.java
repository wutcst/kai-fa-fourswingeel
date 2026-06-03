package com.slaythespire.repository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EnemyTemplate {
    private final String id;
    private final String name;
    private final int maxHp;
    private final int baseAttack;
    private final List<IntentTemplate> intents;  // ✅ 移除 final，允许在构造函数中修改

    @JsonCreator
    public EnemyTemplate(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("maxHp") int maxHp,
            @JsonProperty("attackDamage") int baseAttack,
            @JsonProperty("intents") List<IntentTemplate> intents) {
        this.id = id;
        this.name = name;
        this.maxHp = maxHp;
        this.baseAttack = baseAttack;
        
        // ✅ 容错处理：如果 intents 为 null 或空，使用默认攻击意图
        List<IntentTemplate> tempIntents;
        if (intents == null || intents.isEmpty()) {
            tempIntents = Collections.singletonList(
                new IntentTemplate(IntentType.ATTACK, baseAttack, "攻击")
            );
        } else {
            // 过滤掉 null 值
            tempIntents = new ArrayList<>();
            for (IntentTemplate intent : intents) {
                if (intent != null) {
                    tempIntents.add(intent);
                }
            }
            // 如果过滤后为空，使用默认意图
            if (tempIntents.isEmpty()) {
                tempIntents = Collections.singletonList(
                    new IntentTemplate(IntentType.ATTACK, baseAttack, "攻击")
                );
            }
        }
        this.intents = tempIntents;  // ✅ 只赋值一次
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getMaxHp() { return maxHp; }
    public int getBaseAttack() { return baseAttack; }
    
    public List<IntentTemplate> getIntents() {
        return intents;
    }

    @Override
    public String toString() {
        return String.format("EnemyTemplate{id='%s', name='%s', hp=%d, intents=%s}", 
                           id, name, maxHp, intents);
    }
}