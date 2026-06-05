package com.slaythespire.game.model;

import com.slaythespire.repository.GameDataRepository;

public class Player extends Combatant {
    private int energy;
    private GameDataRepository dataRepo;

    public Player(int hp, int maxHp, GameDataRepository dataRepo) {
        super(hp, maxHp);
        this.dataRepo = dataRepo;
        this.energy = 3;
        // ✅ 修复：移除硬编码的测试遗物，初始不加载任何遗物
        // loadStartingRelics(); 
    }

    public int getEnergy() { return energy; }
    public void resetEnergy() { this.energy = 3; }
    public void useEnergy(int cost) { this.energy -= cost; }

    @Override
    public void onTurnStart() {
        clearBlock();
        for (Relic r : getRelics()) r.onTurnStart(this);
    }
    
    // ✅ 新增：提供一个公开方法，供 BattleService 在战斗开始时动态添加初始遗物（未来扩展用）
    public void addInitialRelic(String relicId) {
        if (dataRepo != null) {
            var template = dataRepo.getRelicById(relicId);
            if (template != null) {
                GameRelic relic = new GameRelic(template);
                this.addRelic(relic);
                // 处理被动属性（如加血上限）
                if ("MAX_HP".equals(relic.getEffectType())) {
                    this.maxHp += relic.getValue();
                    this.hp += relic.getValue();
                }
            }
        }
    }
}