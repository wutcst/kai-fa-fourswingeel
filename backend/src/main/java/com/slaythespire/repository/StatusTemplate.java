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

    @JsonCreator
    public StatusTemplate(@JsonProperty("id") String id, @JsonProperty("name") String name,
                          @JsonProperty("description") String description, @JsonProperty("effectType") String effectType,
                          @JsonProperty("value") double value, @JsonProperty("color") String color) {
        this.id = id; this.name = name; this.description = description;
        this.effectType = effectType; this.value = value; this.color = color;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getEffectType() { return effectType; }
    public double getValue() { return value; }
    public String getColor() { return color; }
}