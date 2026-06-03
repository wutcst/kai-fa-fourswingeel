package com.slaythespire.game.model;

import com.slaythespire.repository.EnemyTemplate;

/**
 * 怪物实例类 - 运行时状态与基础属性
 * 属性数据由 EnemyTemplate 注入，方便后续按地图节点动态生成
 */
public class Enemy {
    private String name;
    private int hp;
    private int maxHp;
    private int attackDamage;

    /**
     * 基于数据模板构造怪物实例
     * @param template 怪物静态配置数据
     */
    public Enemy(EnemyTemplate template) {
        this.name = template.getName();
        this.maxHp = template.getMaxHp();
        this.hp = this.maxHp; // 实例化时默认满血
        this.attackDamage = template.getAttackDamage();
    }

    public String getName() { return name; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public int getAttackDamage() { return attackDamage; }

    public void takeDamage(int dmg) {
        if (dmg < 0) return;
        hp -= dmg;
        if (hp < 0) hp = 0;
    }

    public boolean isAlive() {
        return hp > 0;
    }
}