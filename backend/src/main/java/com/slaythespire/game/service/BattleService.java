package com.slaythespire.game.service;

import com.slaythespire.game.model.Card;
import com.slaythespire.game.model.Enemy;
import com.slaythespire.game.model.Player;
import com.slaythespire.repository.CardTemplate;
import com.slaythespire.repository.EnemyTemplate;
import com.slaythespire.repository.GameDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 战斗核心服务类
 * <p>
 * 负责管理单场战斗的生命周期、回合流转、卡牌交互与状态同步。
 * 已全面改造为数据驱动架构：
 * - 卡牌/怪物数据通过 GameDataRepository 动态加载
 * - 怪物行为通过 Intent 系统实现多重意图（递增攻击/多段行为/条件触发）
 * - 所有公共方法采用 synchronized 保证多线程环境下的状态一致性
 * </p>
 *
 * @author 你的姓名
 * @version 3.0 (Multi-Intent System)
 */
@Service
public class BattleService {

    private static final Logger log = LoggerFactory.getLogger(BattleService.class);
    
    @Autowired
    private GameDataRepository dataRepo;

    // ========== 战斗核心实体 ==========
    private Player player;
    private Enemy enemy;

    // ========== 牌堆管理 ==========
    private List<Card> drawPile;
    private List<Card> hand;
    private List<Card> discardPile;

    // ========== 回合与状态 ==========
    private int energy;
    private final Random random = new Random();  // 用于随机抽卡 + 随机意图（预留）
    private List<String> logList;  // 战斗日志（重命名避免与 Logger 冲突）
    private boolean gameOver;
    private String winner;

    /**
     * 初始化并开启一场新战斗
     * <p>
     * 执行流程：
     * 1. 初始化玩家属性
     * 2. 从配置仓库加载怪物（支持多重意图）
     * 3. 随机构建 10 张卡牌的初始牌堆
     * 4. 重置回合上下文
     * 5. 初始抽 5 张手牌
     * </p>
     */
    public synchronized Map<String, Object> newBattle() {
        log.info("🎮 开始新战斗...");
        
        // 1. 初始化玩家
        this.player = new Player(70);
        log.debug("👤 玩家初始化: HP=70");

        // 2. 加载怪物数据（当前默认取配置表第一个）
        List<EnemyTemplate> enemies = dataRepo.getAllEnemies();
        if (enemies.isEmpty()) {
            throw new IllegalStateException("怪物配置为空，请检查 enemies.json");
        }
        this.enemy = new Enemy(enemies.get(0));
        log.info("👾 敌人加载: {} (HP={}/{}, 意图序列={})", 
                enemy.getName(), enemy.getHp(), enemy.getMaxHp(), enemy.getIntentDesc());

        // 3. 构建随机初始牌堆并洗牌（每次战斗 10 张不同卡牌）
        this.drawPile = buildInitialDeck();
        Collections.shuffle(drawPile);
        log.info("🎴 初始牌堆: {} 张卡牌", drawPile.size());

        // 4. 重置回合上下文
        this.hand = new ArrayList<>();
        this.discardPile = new ArrayList<>();
        this.energy = 3;
        this.logList = new ArrayList<>();
        this.gameOver = false;
        this.winner = null;

        // 5. 初始抽牌
        drawCards(5);
        log.info("✋ 初始手牌: {} 张 | 能量: {}", hand.size(), energy);
        
        return getCurrentState();
    }

    /**
     * 随机构建初始牌堆（每次战斗不同）
     * <p>
     * 从所有可用卡牌模板中随机抽取 10 张（允许重复），
     * 符合杀戮尖塔"每局初始牌组随机"的肉鸽设计。
     * </p>
     */
    private List<Card> buildInitialDeck() {
        List<Card> deck = new ArrayList<>();
        List<CardTemplate> allCards = dataRepo.getAllCards();
        
        if (allCards.isEmpty()) {
            log.warn("⚠️ 卡牌配置池为空，初始牌组将为空");
            return deck;
        }
        
        // 随机抽取 10 张卡牌（允许重复）
        for (int i = 0; i < 10; i++) {
            int randomIndex = random.nextInt(allCards.size());
            CardTemplate template = allCards.get(randomIndex);
            deck.add(new Card(template));
        }
        
        return deck;
    }

    /**
     * 玩家打出指定索引的卡牌
     * @param index 手牌列表中的索引（0-based）
     * @throws IllegalStateException 如果能量不足或战斗已结束
     * @throws IllegalArgumentException 如果索引越界
     */
    public synchronized Map<String, Object> playCard(int index) {
        validateBattleActive();
        validateCardIndex(index);

        Card card = hand.get(index);
        if (card.getCost() > energy) {
            throw new IllegalStateException("能量不足，无法打出该卡牌");
        }

        // 扣除能量并执行卡牌效果
        energy -= card.getCost();
        logList.clear();
        logList.add("🃏 玩家使用: " + card.getName());

        if (card.getType() == Card.CardType.ATTACK) {
            enemy.takeDamage(card.getDamage());
            logList.add("造成 " + card.getDamage() + " 点伤害，敌人 HP: " + enemy.getHp());
        } else {
            player.addBlock(card.getBlock());
            logList.add("获得 " + card.getBlock() + " 点格挡，当前格挡: " + player.getBlock());
        }

        // 卡牌移入弃牌堆
        hand.remove(index);
        discardPile.add(card);
        log.debug("🗑️ 卡牌移入弃牌堆 | 手牌剩余: {}", hand.size());

        // 判定战斗胜负
        if (!enemy.isAlive()) {
            gameOver = true;
            winner = "玩家";
            logList.add("🎉 敌人被击败！战斗胜利！");
            log.info("✅ 战斗胜利 | 玩家剩余 HP: {}", player.getHp());
        }

        return getCurrentState();
    }

    /**
     * 结束玩家回合，触发怪物意图执行与回合结算
     * <p>
     * 执行流程：
     * 1. 调用 enemy.executeNextIntent() 执行怪物行为（支持多重意图）
     * 2. 应用伤害/效果到玩家
     * 3. 判定玩家生死
     * 4. 回合结算：清空格挡、弃置手牌、重置能量、抽牌
     * </p>
     */
    public synchronized Map<String, Object> endTurn() {
        validateBattleActive();
        log.debug("🔄 玩家结束回合，敌人行动...");

        // ✅ 核心改动：调用怪物的意图执行系统
        int dmg = enemy.executeNextIntent();  // 执行意图并获取伤害值
        
        if (dmg > 0) {
            player.takeDamage(dmg);
            logList.clear();
            logList.add(String.format("⚔️ %s %s，造成 %d 点伤害。玩家 HP: %d | 格挡: %d", 
                                    enemy.getName(), 
                                    enemy.getIntentDesc(),  // 显示"轻击"/"重击"等描述
                                    dmg, 
                                    player.getHp(), 
                                    player.getBlock()));
            log.debug("💥 敌人攻击: {} 伤害，玩家剩余 HP: {}", dmg, player.getHp());
        } else {
            logList.clear();
            logList.add(String.format("🛡️ %s %s", enemy.getName(), enemy.getIntentDesc()));
            log.debug("🛡️ 敌人执行非攻击意图: {}", enemy.getIntentDesc());
        }

        // 判定玩家生死
        if (!player.isAlive()) {
            gameOver = true;
            winner = "敌人";
            logList.add("💀 玩家倒下... 战斗失败。");
            log.info("❌ 战斗失败 | 敌人剩余 HP: {}", enemy.getHp());
            return getCurrentState();
        }

        // ========== 回合结算 ==========
        // 1. 清空临时格挡（杀戮尖塔规则：回合结束格挡清零）
        player.addBlock(-player.getBlock());
        
        // 2. 弃置所有手牌
        discardPile.addAll(hand);
        hand.clear();
        
        // 3. 重置能量
        energy = 3;
        
        // 4. 抽 5 张新牌（支持弃牌堆洗牌重组）
        drawCards(5);
        
        log.debug("🔄 回合结算完成 | 能量重置: {} | 新手牌: {}", energy, hand.size());

        return getCurrentState();
    }

    /**
     * 抽牌逻辑（支持弃牌堆洗牌重组）
     * <p>
     * 规则：
     * - 优先从抽牌堆抽牌
     * - 抽牌堆为空时，将弃牌堆洗牌后重组为新抽牌堆
     * - 两者均为空时停止抽牌
     * </p>
     */
    private void drawCards(int count) {
        for (int i = 0; i < count; i++) {
            if (drawPile.isEmpty()) {
                if (discardPile.isEmpty()) {
                    log.debug("📦 无牌可抽 | 抽牌堆: 0, 弃牌堆: 0");
                    break;  // 无牌可抽
                }
                // 弃牌堆洗牌重组
                drawPile.addAll(discardPile);
                discardPile.clear();
                Collections.shuffle(drawPile);
                log.debug("🔄 弃牌堆重组为抽牌堆 | 新抽牌堆: {} 张", drawPile.size());
            }
            if (!drawPile.isEmpty()) {
                hand.add(drawPile.remove(0));
            }
        }
    }

    /**
     * 序列化当前战斗状态，供前端渲染
     * <p>
     * 返回字段说明：
     * - playerHp/MaxHp/Block: 玩家战斗属性
     * - enemyName/Hp/MaxHp: 敌人基础信息
     * - enemyNextDamage: 下回合怪物将造成的伤害（用于前端预警）
     * - enemyIntentDesc: 下回合怪物意图描述（新增，支持多重意图展示）
     * - energy: 当前可用能量
     * - hand: 手牌列表（含 index 用于打出操作）
     * - log: 本回合战斗日志
     * - gameOver/winner: 战斗结束状态
     * </p>
     */
    private Map<String, Object> getCurrentState() {
        Map<String, Object> state = new LinkedHashMap<>();
        
        // 玩家状态
        state.put("playerHp", player.getHp());
        state.put("playerMaxHp", player.getMaxHp());
        state.put("playerBlock", player.getBlock());
        
        // 敌人状态
        state.put("enemyName", enemy.getName());
        state.put("enemyHp", enemy.getHp());
        state.put("enemyMaxHp", enemy.getMaxHp());
        state.put("enemyNextDamage", enemy.getNextDamage());      // 下回合伤害值
        state.put("enemyIntentDesc", enemy.getIntentDesc());      // ✅ 新增：意图描述
        
        // 回合资源
        state.put("energy", energy);
        state.put("drawPileSize", drawPile.size());
        state.put("discardPileSize", discardPile.size());

        // 序列化手牌（添加 index 供前端打出操作）
        List<Map<String, Object>> handCards = new ArrayList<>();
        for (int i = 0; i < hand.size(); i++) {
            Card c = hand.get(i);
            Map<String, Object> cardInfo = new LinkedHashMap<>();
            cardInfo.put("index", i);
            cardInfo.put("name", c.getName());
            cardInfo.put("cost", c.getCost());
            cardInfo.put("damage", c.getDamage());
            cardInfo.put("block", c.getBlock());
            cardInfo.put("type", c.getType().name());
            handCards.add(cardInfo);
        }
        state.put("hand", handCards);
        
        // 战斗日志与状态
        state.put("log", new ArrayList<>(logList));  // 返回给前端的键名保持 "log"
        state.put("gameOver", gameOver);
        state.put("winner", winner);
        
        return state;
    }

    // ================= 辅助校验方法 =================

    /**
     * 校验战斗是否仍在进行中
     */
    private void validateBattleActive() {
        if (gameOver) {
            throw new IllegalStateException("战斗已经结束，请重新开始");
        }
    }

    /**
     * 校验卡牌索引是否有效
     */
    private void validateCardIndex(int index) {
        if (index < 0 || index >= hand.size()) {
            throw new IllegalArgumentException("无效的卡牌编号: " + index);
        }
    }
}