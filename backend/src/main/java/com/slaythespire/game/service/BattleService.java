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
 * 负责管理单场战斗的生命周期、回合流转、卡牌交互与状态同步。
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
    private final Random random = new Random();
    private List<String> logList;
    private boolean gameOver;
    private String winner;

    /**
     * 初始化并开启一场新战斗
     * @param playerDeck 前端传入的玩家当前卡组（可为 null）
     */
    public synchronized Map<String, Object> newBattle(List<Map<String, Object>> playerDeck) {
        log.info("🎮 开始新战斗...");
        
        // 1. 初始化玩家
        this.player = new Player(70);

        // 2. 加载怪物数据
        List<EnemyTemplate> enemies = dataRepo.getAllEnemies();
        if (enemies.isEmpty()) {
            throw new IllegalStateException("怪物配置为空，请检查 enemies.json");
        }
        this.enemy = new Enemy(enemies.get(0));
        log.info("👾 敌人加载: {}", enemy.getName());

        // 3. 构建牌堆（优先使用玩家真实卡组）
        this.drawPile = buildDeckFromPlayerData(playerDeck);
        Collections.shuffle(drawPile);
        log.info("🎴 牌堆构建完成: {} 张卡牌", drawPile.size());

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
     * 根据玩家存档数据构建牌堆
     * 如果没有传入卡组，则使用默认的新手 5 张牌（3打击+2防御）作为兜底
     */
    private List<Card> buildDeckFromPlayerData(List<Map<String, Object>> playerDeck) {
        List<Card> deck = new ArrayList<>();
        
        if (playerDeck != null && !playerDeck.isEmpty()) {
            for (Map<String, Object> cardData : playerDeck) {
                String name = (String) cardData.get("name");
                int cost = ((Number) cardData.get("cost")).intValue();
                
                Object dmgObj = cardData.get("damage");
                int damage = dmgObj != null ? ((Number) dmgObj).intValue() : 0;
                
                Object blkObj = cardData.get("block");
                int block = blkObj != null ? ((Number) blkObj).intValue() : 0;
                
                Card.CardType type = Card.CardType.valueOf((String) cardData.get("type"));
                
                deck.add(new Card(name, cost, damage, block, type));
            }
        } else {
            log.info("️ 未接收到玩家卡组，使用默认新手卡组(3打击+2防御)");
            for(int i=0; i<3; i++) deck.add(new Card("打击", 1, 6, 0, Card.CardType.ATTACK));
            for(int i=0; i<2; i++) deck.add(new Card("防御", 1, 0, 5, Card.CardType.SKILL));
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
     * 结束玩家回合，触发怪物意图执行与回合结算
     */
    public synchronized Map<String, Object> endTurn() {
        validateBattleActive();

        int dmg = enemy.executeNextIntent();
        
        if (dmg > 0) {
            player.takeDamage(dmg);
            logList.clear();
            logList.add(String.format("⚔️ %s %s，造成 %d 点伤害。玩家 HP: %d | 格挡: %d", 
                                    enemy.getName(), enemy.getIntentDesc(), dmg, player.getHp(), player.getBlock()));
        } else {
            logList.clear();
            logList.add(String.format("🛡️ %s %s", enemy.getName(), enemy.getIntentDesc()));
        }

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
        state.put("enemyNextDamage", enemy.getNextDamage());
        state.put("enemyIntentDesc", enemy.getIntentDesc());
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
        state.put("log", new ArrayList<>(logList));
        state.put("gameOver", gameOver);
        state.put("winner", winner);
        return state;
    }

    // ================= 辅助校验方法 =================
    private void validateBattleActive() {
        if (gameOver) throw new IllegalStateException("战斗已经结束，请重新开始");
    }

    private void validateCardIndex(int index) {
        if (index < 0 || index >= hand.size()) {
            throw new IllegalArgumentException("无效的卡牌编号: " + index);
        }
    }
}