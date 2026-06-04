package com.slaythespire.game.model;

import com.slaythespire.repository.EnemyTemplate;
import com.slaythespire.repository.IntentTemplate;
import com.slaythespire.repository.IntentType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Enemy {
    private String name;
    private int hp;
    private int maxHp;
    
    private final List<IntentTemplate> intentSequence;
    private int currentTurn;
    private IntentTemplate currentIntent;
    private final Random random = new Random();
    private Map<StatusType, Integer> statuses;

    public Enemy(EnemyTemplate template) {
        this.name = template.getName();
        this.maxHp = template.getMaxHp();
        this.hp = this.maxHp;
        this.intentSequence = template.getIntents();
        this.currentTurn = 0;
        this.currentIntent = null;
        this.statuses = new EnumMap<>(StatusType.class);
        
        if (template.getInitialStatuses() != null) {
            for (Map.Entry<String, Integer> entry : template.getInitialStatuses().entrySet()) {
                try {
                    StatusType type = StatusType.valueOf(entry.getKey());
                    if (entry.getValue() > 0) this.addStatus(type, entry.getValue());
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // ✅ 初始化时，计算出玩家第一回合结束时怪物要做的动作
        updateCurrentIntent();
    }

    // ================= 核心修复：意图执行与推进分离 =================

    /**
     * 1. 执行当前意图（不改变 currentIntent，仅供 BattleService 在 endTurn 时调用）
     * @return 本次行动造成的伤害
     */
    public int executeCurrentIntent() {
        if (currentIntent == null) return 0;
        
        switch (currentIntent.getType()) {
            case ATTACK:
                return modifyDamage(currentIntent.getValue());
            case DEFEND:
            case BUFF:
                return 0; 
            default:
                return 0;
        }
    }

    /**
     * 2. 推进到下一个意图（在回合结算的最后调用）
     */
    public void advanceIntent() {
        currentTurn++;
        updateCurrentIntent();
    }

    // ================= 状态与 Getter =================

    public void addStatus(StatusType type, int count) {
        int current = statuses.getOrDefault(type, 0);
        statuses.put(type, current + count);
    }

    public void takeDamage(int dmg) {
        if (dmg < 0) return;
        if (statuses.getOrDefault(StatusType.VULNERABLE, 0) > 0) {
            dmg = (int) Math.ceil(dmg * 1.5);
        }
        hp = Math.max(0, hp - dmg);
    }

    public int modifyDamage(int originalDamage) {
        if (originalDamage <= 0) return originalDamage;
        if (statuses.getOrDefault(StatusType.WEAK, 0) > 0) {
            return (int) Math.floor(originalDamage * 0.75);
        }
        return originalDamage;
    }

    public void onTurnEnd() {
        for (StatusType type : StatusType.values()) {
            int count = statuses.getOrDefault(type, 0);
            if (count > 0) statuses.put(type, count - 1);
        }
    }

    public String getName() { return name; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public IntentTemplate getCurrentIntentTemplate() { return currentIntent; }
    public Map<StatusType, Integer> getStatuses() { return statuses; }
    
    /**
     * 获取当前意图的描述（用于前端预警）
     * 语义修正：这是“玩家按下结束回合后，怪物马上要做的动作”
     */
    public String getIntentDesc() {
        return currentIntent != null ? currentIntent.getDesc() : "待机";
    }

    /**
     * 获取当前意图的伤害（用于前端预警）
     */
    public int getNextDamage() {
        if (currentIntent == null || currentIntent.getType() != IntentType.ATTACK) return 0;
        return modifyDamage(currentIntent.getValue());
    }

    public boolean isAlive() { return hp > 0; }

    private void updateCurrentIntent() {
        if (intentSequence == null || intentSequence.isEmpty()) {
            this.currentIntent = null;
            return;
        }
        int index = Math.floorMod(currentTurn, intentSequence.size());
        this.currentIntent = intentSequence.get(index);
    }
}