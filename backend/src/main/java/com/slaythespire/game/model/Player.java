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
        
        // ✅ 新增：给玩家添加初始遗物"燃烧之血"
        RelicTemplate bloodTemplate = dataRepo.getRelicById("burning_blood");
        if (bloodTemplate != null) {
            GameRelic bloodRelic = new GameRelic(bloodTemplate);
            this.addRelic(bloodRelic);
            System.out.println("✅ 玩家获得初始遗物: " + bloodRelic.getName());
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