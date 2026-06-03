package com.slaythespire.game.model;

import com.slaythespire.repository.EnemyTemplate;
import com.slaythespire.repository.IntentTemplate;
import com.slaythespire.repository.IntentType;

import java.util.List;

/**
 * 怪物运行时实例 - 管理生命值、意图执行与回合计数
 */
public class Enemy {
    private String name;
    private int hp;
    private int maxHp;
    
    // 意图系统相关
    private final List<IntentTemplate> intentSequence;  // 预定义的意图序列
    private int currentTurn;  // 当前是第几个玩家回合结束（怪物行动次数）
    private IntentTemplate currentIntent;  // 本回合将要执行的意图

    public Enemy(EnemyTemplate template) {
        this.name = template.getName();
        this.maxHp = template.getMaxHp();
        this.hp = this.maxHp;
        this.intentSequence = template.getIntents();
        this.currentTurn = 0;
        this.currentIntent = null;
        
        // 初始化：预计算第一回合的意图
        updateCurrentIntent();
    }

    /**
     * 玩家回合结束后调用：更新怪物意图并执行
     * @return 本次行动造成的伤害（用于前端日志）
     */
    public int executeNextIntent() {
        currentTurn++;  // 回合数+1
        updateCurrentIntent();  // 更新本回合意图
        
        if (currentIntent == null) {
            return 0;  // 无行动
        }
        
        // 执行意图逻辑（当前只实现 ATTACK）
        switch (currentIntent.getType()) {
            case ATTACK:
                return currentIntent.getValue();  // 返回伤害值，由 BattleService 应用
            case DEFEND:
            case BUFF:
            case DEBUFF:
                // 后续扩展：实现对应逻辑
                return 0;
            default:
                return 0;
        }
    }

    /**
     * 根据当前回合数，从序列中获取对应意图（支持循环）
     */
    private void updateCurrentIntent() {
        if (intentSequence == null || intentSequence.isEmpty()) {
            this.currentIntent = null;
            return;
        }
        
        // ✅ 修复：使用 Math.floorMod 确保索引始终非负
        // 回合 1→意图[0], 回合 2→意图[1], 回合 5→意图[0]...
        int index = Math.floorMod(currentTurn - 1, intentSequence.size());
        this.currentIntent = intentSequence.get(index);
    }

    // ============ Getter 方法 ============
    
    public String getName() { return name; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    
    /**
     * 获取下回合怪物将造成的伤害（用于前端提示）
     */
    public int getNextDamage() {
        return currentIntent != null && currentIntent.getType() == IntentType.ATTACK 
            ? currentIntent.getValue() 
            : 0;
    }
    
    /**
     * 获取当前意图的描述（用于前端显示）
     */
    public String getIntentDesc() {
        return currentIntent != null ? currentIntent.getDesc() : "待机";
    }

    public void takeDamage(int dmg) {
        if (dmg < 0) return;
        hp -= dmg;
        if (hp < 0) hp = 0;
    }

    public boolean isAlive() {
        return hp > 0;
    }
}