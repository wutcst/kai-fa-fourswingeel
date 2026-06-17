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
    private final String type;    // NORMAL, ELITE, BOSS
    private final int maxHp;
    private final int baseAttack;
    private final String description;
    private final Map<String, Object> passive;
    private final List<IntentTemplate> intents;
    private final Map<String, Integer> initialStatuses;

    @JsonCreator
    public EnemyTemplate(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("maxHp") int maxHp,
            @JsonProperty("attackDamage") int baseAttack,
            @JsonProperty("description") String description,
            @JsonProperty("passive") Map<String, Object> passive,
            @JsonProperty("intents") List<IntentTemplate> intents,
            @JsonProperty("initialStatuses") Map<String, Integer> initialStatuses) {
        this.id = id;
        this.name = name;
        this.type = type != null ? type : "NORMAL";
        this.maxHp = maxHp;
        this.baseAttack = baseAttack;
        this.description = description != null ? description : "";
        this.passive = passive != null ? passive : new HashMap<>();

        List<IntentTemplate> tempIntents;
        if (intents == null || intents.isEmpty()) {
            tempIntents = Collections.singletonList(
                new IntentTemplate(IntentType.ATTACK, baseAttack, "攻击", null, 0, null, null, null, null, null, null, null, null)
            );
        } else {
            tempIntents = new ArrayList<>();
            for (IntentTemplate intent : intents) {
                if (intent != null) tempIntents.add(intent);
            }
            if (tempIntents.isEmpty()) {
                tempIntents = Collections.singletonList(
                    new IntentTemplate(IntentType.ATTACK, baseAttack, "攻击", null, 0, null, null, null, null, null, null, null, null)
                );
            }
        }
        this.intents = tempIntents;
        this.initialStatuses = (initialStatuses != null) ? initialStatuses : new HashMap<>();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public int getMaxHp() { return maxHp; }
    public int getBaseAttack() { return baseAttack; }
    public String getDescription() { return description; }
    public Map<String, Object> getPassive() { return passive; }
    public List<IntentTemplate> getIntents() { return intents; }
    public Map<String, Integer> getInitialStatuses() { return initialStatuses; }

    @Override
    public String toString() {
        return String.format("EnemyTemplate{id='%s', name='%s', type='%s', hp=%d}", id, name, type, maxHp);
    }
}
