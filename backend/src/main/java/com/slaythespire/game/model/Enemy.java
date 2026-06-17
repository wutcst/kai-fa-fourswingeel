package com.slaythespire.game.model;

import com.slaythespire.game.model.factory.StatusFactory;
import com.slaythespire.repository.EnemyTemplate;
import com.slaythespire.repository.GameDataRepository;
import com.slaythespire.repository.IntentTemplate;
import com.slaythespire.repository.IntentType;

import java.util.*;
import java.util.function.Consumer;

public class Enemy extends Combatant {
    private String name;
    private String enemyType;
    private final List<IntentTemplate> intentSequence;
    private int currentTurn;
    private IntentTemplate currentIntent;
    private GameDataRepository dataRepo;
    private int lastIntentIndex = -1;
    private int consecutiveSameIntent = 0;
    private Consumer<String> drawer; // 回调：向抽牌堆塞牌

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

    /** 设置塞牌回调（由 BattleService 在创建敌人后调用） */
    public void setDrawer(Consumer<String> drawer) { this.drawer = drawer; }

    public String getEnemyType() { return enemyType; }

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
    }

    public int executeCurrentIntent(Combatant target) {
        if (currentIntent == null) return 0;
        if (target == null) return 0;
        IntentType type = currentIntent.getType();

        if (type == IntentType.ATTACK) {
            int result = target.takeDamage(currentIntent.getValue(), this);
            if (target instanceof Player) {
                Player p = (Player) target;
                if (!currentIntent.isSkipBurnCards() && currentIntent.getBurnCards() > 0) {
                    for (int i = 0; i < currentIntent.getBurnCards(); i++) {
                        if (drawer != null) drawer.accept("burn");
                    }
                }
                if (currentIntent.getStunCards() > 0) {
                    for (int i = 0; i < currentIntent.getStunCards(); i++) {
                        if (drawer != null) drawer.accept("dazed");
                    }
                }
            }
            return result;
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
            // 🆕 塞牌逻辑
            if (target instanceof Player) {
                Player p = (Player) target;
                if (!currentIntent.isSkipBurnCards() && currentIntent.getBurnCards() > 0) {
                    for (int i = 0; i < currentIntent.getBurnCards(); i++) {
                        if (drawer != null) drawer.accept("burn");
                    }
                }
                if (currentIntent.getStunCards() > 0) {
                    for (int i = 0; i < currentIntent.getStunCards(); i++) {
                        if (drawer != null) drawer.accept("dazed");
                    }
                }
            }
            return totalDmg;
        }
        if (type == IntentType.DEBUFF) {
            int stunCards = currentIntent.getStunCards();
            int burnCards = currentIntent.getBurnCards();
            if (target instanceof Player) {
                Player p = (Player) target;
                if (burnCards > 0) {
                    for (int i = 0; i < burnCards; i++) {
                        if (drawer != null) drawer.accept("burn");
                    }
                }
                if (stunCards > 0) {
                    for (int i = 0; i < stunCards; i++) {
                        if (drawer != null) drawer.accept("dazed");
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

        int size = intentSequence.size();

        // === 第1回合：固定使用 intents[0] ===
        if (currentTurn == 0) {
            this.currentIntent = intentSequence.get(0);
            this.lastIntentIndex = 0;
            this.consecutiveSameIntent = 1;
            return;
        }

        // === 第2回合起 ===
        // 特殊规则：如果 intents 只有1个元素，固定使用它
        if (size == 1) {
            this.currentIntent = intentSequence.get(0);
            this.lastIntentIndex = 0;
            return;
        }

        // 如果 size == 2，则是严格的交替模式（如哨卫）：第2回合用另一个，第3回合交替回来
        if (size == 2) {
            int altIndex = (currentTurn % 2 == 1) ? 1 : 0;
            this.currentIntent = intentSequence.get(altIndex);
            this.lastIntentIndex = altIndex;
            return;
        }

        Random random = new Random();

        // 尝试从 intents[1] 开始随机选，避开不能选的
        List<Integer> candidates = new ArrayList<>();
        for (int i = 1; i < size; i++) {
            candidates.add(i);
        }

        // 过滤：不能连续使用的规则
        List<Integer> validCandidates = new ArrayList<>();
        for (int idx : candidates) {
            if (shouldSkipIntent(idx)) continue;
            validCandidates.add(idx);
        }

        // 如果所有候选都被过滤了，放宽限制
        if (validCandidates.isEmpty()) {
            validCandidates = candidates;
        }

        int chosenIndex = validCandidates.get(random.nextInt(validCandidates.size()));

        // 更新连续计数
        if (chosenIndex == lastIntentIndex) {
            consecutiveSameIntent++;
        } else {
            consecutiveSameIntent = 1;
        }

        this.currentIntent = intentSequence.get(chosenIndex);
        this.lastIntentIndex = chosenIndex;
    }

    /** 判断某个意图索引是否应该被跳过 */
    private boolean shouldSkipIntent(int idx) {
        if (idx == lastIntentIndex) {
            // 不能连续两次同招
            if (consecutiveSameIntent >= 1) return true;
        }
        return false;
    }
}
