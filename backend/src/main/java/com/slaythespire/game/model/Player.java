package com.slaythespire.game.model;

public class Player {
    private int hp;
    private int maxHp;
    private int block;

    public Player(int hp) {
        this.hp = hp;
        this.maxHp = hp;
        this.block = 0;
    }

    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public int getBlock() { return block; }

    public void takeDamage(int dmg) {
        int blocked = Math.min(block, dmg);
        block -= blocked;
        hp -= (dmg - blocked);
        if (hp < 0) hp = 0;
    }

    public void addBlock(int amount) {
        block += amount;
    }

    public void heal(int amount) {
        hp = Math.min(hp + amount, maxHp);
    }

    public boolean isAlive() {
        return hp > 0;
    }
}
