package com.slaythespire.game.model;

public class Enemy {
    private String name;
    private int hp;
    private int maxHp;
    private int attackDamage;

    public Enemy(String name, int hp, int attackDamage) {
        this.name = name;
        this.hp = hp;
        this.maxHp = hp;
        this.attackDamage = attackDamage;
    }

    public String getName() { return name; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public int getAttackDamage() { return attackDamage; }

    public void takeDamage(int dmg) {
        hp -= dmg;
        if (hp < 0) hp = 0;
    }

    public boolean isAlive() {
        return hp > 0;
    }
}
