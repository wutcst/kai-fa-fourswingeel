package com.slaythespire.repository;

import java.util.List;

public class EnemyGroupTemplate {
    private String id;
    private String name;
    private String type;     // NORMAL, ELITE, BOSS
    private int minAct;
    private int maxAct;
    private List<String> enemies;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public int getMinAct() { return minAct; }
    public void setMinAct(int minAct) { this.minAct = minAct; }
    public int getMaxAct() { return maxAct; }
    public void setMaxAct(int maxAct) { this.maxAct = maxAct; }
    public List<String> getEnemies() { return enemies; }
    public void setEnemies(List<String> enemies) { this.enemies = enemies; }
}
