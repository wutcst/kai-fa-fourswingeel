package com.slaythespire.repository;

import java.util.List;

/**
 * 敌方阵容模板，对应 enemy_groups.json 中每个阵容
 */
public class EnemyGroupTemplate {
    private String id;
    private String name;
    private List<String> enemies;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getEnemies() { return enemies; }
    public void setEnemies(List<String> enemies) { this.enemies = enemies; }
}
