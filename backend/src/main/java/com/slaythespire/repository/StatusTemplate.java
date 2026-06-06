package com.slaythespire.repository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class StatusTemplate {
    private final String id;
    private final String name;
    private final String description;
    private final String effectType;
    private final double value;
    private final String color;
    private final boolean decay; // ✅ 新增：是否随回合衰减

    @JsonCreator
    public StatusTemplate(@JsonProperty("id") String id, @JsonProperty("name") String name,
                          @JsonProperty("description") String description, @JsonProperty("effectType") String effectType,
                          @JsonProperty("value") double value, @JsonProperty("color") String color,
                          @JsonProperty("decay") boolean decay) {
        this.id = id; this.name = name; this.description = description;
        this.effectType = effectType; this.value = value; this.color = color;
        this.decay = decay;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getEffectType() { return effectType; }
    public double getValue() { return value; }
    public String getColor() { return color; }
    public boolean isDecay() { return decay; } // ✅ 新增 Getter
}