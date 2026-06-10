package com.slaythespire.game.model;

import com.slaythespire.repository.GameDataRepository;

public class Player extends Combatant {
    private int energy;
    private GameDataRepository dataRepo;

    public Player(int hp, int maxHp, GameDataRepository dataRepo) {
        super(hp, maxHp);
        this.dataRepo = dataRepo;
        this.energy = 3;
    }

    public int getEnergy() { return energy; }
    public void resetEnergy() { this.energy = 3; }
    public void useEnergy(int cost) { this.energy -= cost; }

    @Override
    public void onTurnStart() {
        clearBlock();
        for (Relic r : getRelics()) r.onTurnStart(this);
    }
}