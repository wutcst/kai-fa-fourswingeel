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
 * 已全面改造为数据驱动架构，卡牌/怪物数据通过 GameDataRepository 动态加载。
 * 所有公共方法采用 synchronized 保证多线程环境下的状态一致性。
 * </p>
 *
 * @author 你的姓名
 * @version 2.1 (Random Deck)
 */
@Service
public class BattleService {

    private static final Logger log = LoggerFactory.getLogger(BattleService.class);
    
    @Autowired
    private GameDataRepository dataRepo;

    // 战斗核心实体
    private Player player;
    private Enemy enemy;

    // 牌堆管理
    private List<Card> drawPile;
    private List<Card> hand;
    private List<Card> discardPile;

    // 回合与状态
    private int energy;
    private final Random random = new Random();  // ✅ 用于随机抽卡
    private List<String> logList;  // 重命名避免与 Logger 冲突
    private boolean gameOver;
    private String winner;

    /**
     * 初始化并开启一场新战斗
     * 从配置仓库加载数据，重置所有战斗状态
     */
    public synchronized Map<String, Object> newBattle() {
        log.info("🎮 开始新战斗...");
        
        // 1. 初始化玩家
        this.player = new Player(70);

        // 2. 加载怪物数据（当前默认取配置表第一个）
        List<EnemyTemplate> enemies = dataRepo.getAllEnemies();
        if (enemies.isEmpty()) {
            throw new IllegalStateException("怪物配置为空，请检查 enemies.json");
        }
        this.enemy = new Enemy(enemies.get(0));
        log.info("👾 敌人加载: {}", enemy.getName());

        // 3. 构建随机初始牌堆并洗牌
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
        log.info("✋ 初始手牌: {} 张", hand.size());
        
        return getCurrentState();
    }

    /**
     * 随机构建初始牌堆（每次战斗不同）
     * 从所有可用卡牌模板中随机抽取 10 张（允许重复）
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
     */
    public synchronized Map<String, Object> playCard(int index) {
        validateBattleActive();
        validateCardIndex(index);

        Card card = hand.get(index);
        if (card.getCost() > energy) {
            throw new IllegalStateException("能量不足，无法打出该卡牌");
        }

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

        hand.remove(index);
        discardPile.add(card);

        if (!enemy.isAlive()) {
            gameOver = true;
            winner = "玩家";
            logList.add("🎉 敌人被击败！战斗胜利！");
        }

        return getCurrentState();
    }

    /**
     * 结束玩家回合，触发敌人行动与回合结算
     */
    public synchronized Map<String, Object> endTurn() {
        validateBattleActive();

        int dmg = enemy.getAttackDamage();
        player.takeDamage(dmg);
        logList.clear();
        logList.add("⚔️ 敌人攻击，造成 " + dmg + " 点伤害。玩家 HP: " + player.getHp() + " | 格挡: " + player.getBlock());

        if (!player.isAlive()) {
            gameOver = true;
            winner = "敌人";
            logList.add("💀 玩家倒下... 战斗失败。");
            return getCurrentState();
        }

        player.addBlock(-player.getBlock());
        discardPile.addAll(hand);
        hand.clear();
        energy = 3;
        drawCards(5);

        return getCurrentState();
    }

    /**
     * 抽牌逻辑（支持弃牌堆洗牌重组）
     */
    private void drawCards(int count) {
        for (int i = 0; i < count; i++) {
            if (drawPile.isEmpty()) {
                if (discardPile.isEmpty()) break;
                drawPile.addAll(discardPile);
                discardPile.clear();
                Collections.shuffle(drawPile);
            }
            if (!drawPile.isEmpty()) {
                hand.add(drawPile.remove(0));
            }
        }
    }

    /**
     * 序列化当前战斗状态，供前端渲染
     */
    private Map<String, Object> getCurrentState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("playerHp", player.getHp());
        state.put("playerMaxHp", player.getMaxHp());
        state.put("playerBlock", player.getBlock());
        state.put("enemyName", enemy.getName());
        state.put("enemyHp", enemy.getHp());
        state.put("enemyMaxHp", enemy.getMaxHp());
        state.put("enemyNextDamage", enemy.getAttackDamage());
        state.put("energy", energy);
        state.put("drawPileSize", drawPile.size());
        state.put("discardPileSize", discardPile.size());

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
        state.put("log", new ArrayList<>(logList));  // 注意：返回给前端的键名仍是 "log"
        state.put("gameOver", gameOver);
        state.put("winner", winner);
        return state;
    }

    // ================= 辅助校验方法 =================

    private void validateBattleActive() {
        if (gameOver) {
            throw new IllegalStateException("战斗已经结束，请重新开始");
        }
    }

    private void validateCardIndex(int index) {
        if (index < 0 || index >= hand.size()) {
            throw new IllegalArgumentException("无效的卡牌编号: " + index);
        }
    }
}