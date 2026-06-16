package com.slaythespire.game.model;

import com.slaythespire.repository.GameDataRepository;
import java.util.ArrayList;

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
    public GameDataRepository getDataRepo() { return this.dataRepo; }

    @Override
    public void onTurnStart() {
        actualDamageTakenThisTurn = 0;
        clearBlock();
        turnStartLogs.clear(); // 使用父类的 protected 字段
        
        // ✅ 遍历副本防止并发修改异常
        for (StatusEffect s : new ArrayList<>(statuses)) {
            addTurnStartLog(s.onTurnStart(this)); // 使用父类提供的方法
        }
        
        for (Relic r : getRelics()) r.onTurnStart(this);
    }
}