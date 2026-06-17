package com.slaythespire.game.model;

import com.slaythespire.game.model.factory.StatusFactory;
import com.slaythespire.repository.EnemyTemplate;
import com.slaythespire.repository.GameDataRepository;
import com.slaythespire.repository.IntentTemplate;
import com.slaythespire.repository.IntentType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Enemy extends Combatant {
    private String name;
    private String enemyType;   // NORMAL, ELITE, BOSS
    private final List<IntentTemplate> intentSequence;
    private int currentTurn;
    private IntentTemplate currentIntent;
    private GameDataRepository dataRepo;
    private boolean hexaghostFirstCycleDone = false;
    private int hexCyclePosition = 0;

    public Enemy(EnemyTemplate template, GameDataRepository dataRepo) {
        super(template.getMaxHp(), template.getMaxHp());
        this.dataRepo = dataRepo;
        this.name = template.getName();
        this.enemyType = template.getType();
        this.intentSequence = template.getIntents();
        this.currentTurn = 0;

        if (template.getInitialStatuses() != null) {
            for (Map.Entry<String, Integer> entry : template.getInitialStatuses().entrySet()) {
                StatusEffect status = StatusFactory.create(entry.getKey(), entry.getValue(), dataRepo);
                if (status != null) this.addStatus(status);
            }
        }
        updateCurrentIntent();
    }

    public String getEnemyType() { return enemyType; }

    @Override
    public GameDataRepository getDataRepo() { return this.dataRepo; }

    @Override
    public void onTurnStart() {
        actualDamageTakenThisTurn = 0;
        clearBlock();
        turnStartLogs.clear(); // 使用父类的 protected 字段
        
        // ✅ 遍历副本防止并发修改异常
        for (StatusEffect s : new ArrayList<>(statuses)) {
            addTurnStartLog(s.onTurnStart(this));
        }
    }

    public int executeCurrentIntent(Combatant target) {
        if (currentIntent == null) return 0;
        IntentType type = currentIntent.getType();

        if (type == IntentType.ATTACK) {
            return target.takeDamage(currentIntent.getValue(), this);
        }
        if (type == IntentType.DEFEND) {
            this.gainBlock(currentIntent.getValue());
            return 0;
        }
        if (type == IntentType.BUFF) {
            if (currentIntent.getApplyStatusType() != null) {
                StatusEffect status = StatusFactory.create(currentIntent.getApplyStatusType(), currentIntent.getApplyStatusCount(), dataRepo);
                if (status != null) {
                    this.addStatus(status);
                }
            }
            if (currentIntent.getValue() > 0) {
                this.gainBlock(currentIntent.getValue());
            }
            return 0;
        }
        if (type == IntentType.MULTI_ATTACK) {
            int hitCount = currentIntent.getMultiHit();
            int dmg = currentIntent.getValue();
            if (currentIntent.isHpScaling()) {
                dmg = Math.max(1, target.getHp() / 12 + 1);
            }
            int totalDmg = 0;
            for (int i = 0; i < hitCount; i++) {
                totalDmg += target.takeDamage(dmg, this);
            }
            // 地狱火特殊：第一次不塞灼伤，skipBurnCards标记处理
            if (!currentIntent.isSkipBurnCards() && currentIntent.getBurnCards() > 0) {
                if (target instanceof Player) {
                    for (int i = 0; i < currentIntent.getBurnCards(); i++) {
                        ((Player)target).addStatusCard("burn");
                    }
                }
            }
            return totalDmg;
        }
        if (type == IntentType.DEBUFF) {
            int stunCards = currentIntent.getStunCards();
            int burnCards = currentIntent.getBurnCards();
            if (burnCards > 0) {
                if (target instanceof Player) {
                    for (int i = 0; i < burnCards; i++) {
                        ((Player)target).addStatusCard("burn");
                    }
                }
            }
            if (stunCards > 0) {
                if (target instanceof Player) {
                    for (int i = 0; i < stunCards; i++) {
                        ((Player)target).addStatusCard("dazed");
                    }
                }
            }
            return 0;
        }
        return 0;
    }

    public void advanceIntent() {
        currentTurn++;
        updateCurrentIntent();
    }

    public String getEnemyName() { return name; }
    public IntentTemplate getCurrentIntentTemplate() { return currentIntent; }
    public String getIntentDesc() { return currentIntent != null ? currentIntent.getDesc() : "待机"; }

    public int getNextDamage() {
        if (currentIntent == null) return 0;
        if (currentIntent.getType() == IntentType.ATTACK || currentIntent.getType() == IntentType.MULTI_ATTACK) {
            int dmg = currentIntent.getValue();
            if (currentIntent.isHpScaling()) {
                // hpScaling由execute时动态计算，预览时用近似值
                dmg = Math.max(1, 6);
            }
            if (currentIntent.getType() == IntentType.MULTI_ATTACK) {
                dmg = dmg * currentIntent.getMultiHit();
            }
            for (StatusEffect s : statuses) dmg = s.onDamageDealt(dmg, this, null);
            return dmg;
        }
        return 0;
    }

    public int getNextBlock() {
        if (currentIntent == null || currentIntent.getType() != IntentType.DEFEND) return 0;
        int blk = currentIntent.getValue();
        for (StatusEffect s : statuses) blk = s.onBlockGained(blk, this);
        return blk;
    }

    private void updateCurrentIntent() {
        if (intentSequence == null || intentSequence.isEmpty()) {
            this.currentIntent = null;
            return;
        }

        if (currentTurn == 0) {
            this.currentIntent = intentSequence.get(0);
        } else {
            int remainingSize = intentSequence.size() - 1;
            if (remainingSize <= 0) {
                this.currentIntent = null;
                return;
            }
            int index = 1 + ((currentTurn - 1) % remainingSize);
            this.currentIntent = intentSequence.get(index);
        }
    }
}
