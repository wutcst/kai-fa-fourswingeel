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
        turnStartLogs.clear();

        for (StatusEffect s : new ArrayList<>(statuses)) {
            addTurnStartLog(s.onTurnStart(this));
        }

        // 遗物效果统一由 RelicEffectHandler 处理
        RelicEffectHandler.onPlayerTurnStart(this);
    }

    /** 添加状态牌（灼伤、晕眩等）到玩家的弃牌堆/手中 */
    public void addStatusCard(String statusCardType) {
        // 状态牌暂为简化实现：直接造成伤害或效果
        if ("burn".equals(statusCardType)) {
            // 灼伤：可考虑在未来实现为牌堆中的负面状态牌
            this.takeDamage(2, null, false);
        } else if ("dazed".equals(statusCardType)) {
            // 晕眩：目前简化实现，后续可扩展为塞入弃牌堆
            // 空实现，作为占位
        }
    }

}
