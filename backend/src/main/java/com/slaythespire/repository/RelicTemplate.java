package com.slaythespire.repository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RelicTemplate {
    private final String id;
    private final String name;
    private final String description;
    private final String effectType;
    private final int value;
    private final String rarity;
    private final String charId; // null=公用, "1"=铁甲战士, "2"=静默猎手等

    @JsonCreator
    public RelicTemplate(@JsonProperty("id") String id, @JsonProperty("name") String name,
                         @JsonProperty("description") String description, @JsonProperty("effectType") String effectType,
                         @JsonProperty("value") int value, @JsonProperty("rarity") String rarity,
                         @JsonProperty("charId") String charId) {
        this.id = id; this.name = name; this.description = description;
        this.effectType = effectType; this.value = value; this.rarity = rarity;
        this.charId = charId;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getEffectType() { return effectType; }
    public int getValue() { return value; }
    public String getRarity() { return rarity; }
    public String getCharId() { return charId; }
}