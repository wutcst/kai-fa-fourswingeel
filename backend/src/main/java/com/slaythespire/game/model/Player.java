package com.slaythespire.game.model;

import com.slaythespire.repository.GameDataRepository;
import com.slaythespire.repository.RelicTemplate;

public class Player extends Combatant {
    private int energy;
    private GameDataRepository dataRepo;

    public Player(int hp, int maxHp, GameDataRepository dataRepo) {
        super(hp, maxHp);
        this.dataRepo = dataRepo;
        this.energy = 3;
        loadStartingRelics();
    }

    private void loadStartingRelics() {
        RelicTemplate blood = dataRepo.getRelicById("burning_blood");
        if (blood != null) this.addRelic(new GameRelic(blood));

        RelicTemplate skull = dataRepo.getRelicById("red_skull");
        if (skull != null) {
            GameRelic relic = new GameRelic(skull);
            this.addRelic(relic);
            if ("MAX_HP".equals(relic.getEffectType())) {
                this.maxHp += relic.getValue();
                this.hp += relic.getValue();
            }
        }
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