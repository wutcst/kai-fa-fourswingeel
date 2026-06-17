package com.slaythespire.game.model;

import com.slaythespire.repository.GameDataRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Player extends Combatant {
    private int energy;
    private GameDataRepository dataRepo;

    // ================ 每场战斗生命周期标志（Boss 遗物相关） ================
    private boolean firstDamageTakenThisBattle = false;
    private boolean deathSaveUsedThisBattle = false;

    public Player(int hp, int maxHp, GameDataRepository dataRepo) {
        super(hp, maxHp);
        this.dataRepo = dataRepo;
        this.energy = 3;
    }

    public int getEnergy() { return energy; }
    public void resetEnergy() { this.energy = 3; }
    public void useEnergy(int cost) { this.energy -= cost; }

    /** 每场战斗开始前调用，重置一次性的遗物效果标志 */
    public void resetBattleFlags() {
        this.firstDamageTakenThisBattle = false;
        this.deathSaveUsedThisBattle = false;
    }

    // ----- FIRST_HIT_HEAL 灵魂之炉 -----
    public boolean isFirstDamageTakenThisBattle() { return firstDamageTakenThisBattle; }
    public void markFirstDamageTaken() { this.firstDamageTakenThisBattle = true; }

    // ----- DEATH_SAVE 凤凰之羽 -----
    public boolean isDeathSaveUsedThisBattle() { return deathSaveUsedThisBattle; }
    public void markDeathSaveUsed() { this.deathSaveUsedThisBattle = true; }

    @Override
    public GameDataRepository getDataRepo() { return this.dataRepo; }

    @Override
    public void onTurnStart() {
        actualDamageTakenThisTurn = 0;
        boolean hasAsgard = false;
        for (Relic r : relics) {
            if (r instanceof GameRelic && "ASGARD_PROTECTION".equals(((GameRelic) r).getEffectType())) {
                hasAsgard = true;
                break;
            }
        }
        if (!hasAsgard) clearBlock();
        turnStartLogs.clear();

        for (StatusEffect s : new ArrayList<>(statuses)) {
            addTurnStartLog(s.onTurnStart(this));
        }

        RelicEffectHandler.onPlayerTurnStart(this);
    }

    /** 🔥 向抽牌堆中洗入状态牌（晕眩、灼伤、伤口等） */
    public void addCardToDrawPile(String cardType) {
        // 由 BattleService 实现具体塞牌逻辑，这里作为标记接口
    }

    /** 添加状态牌（灼伤、晕眩等）到玩家的弃牌堆/手中 */
    public void addStatusCard(String statusCardType) {
        if ("burn".equals(statusCardType)) {
            this.takeDamage(2, null, false);
        } else if ("dazed".equals(statusCardType)) {
            // 空实现，通过 BattleService 直接塞入抽牌堆
        }
    }

}
