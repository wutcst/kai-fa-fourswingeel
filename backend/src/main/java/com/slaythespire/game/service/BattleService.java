package com.slaythespire.game.service;

import com.slaythespire.game.model.Card;
import com.slaythespire.game.model.Enemy;
import com.slaythespire.game.model.Player;
import com.slaythespire.game.model.StatusType;
import com.slaythespire.repository.EnemyTemplate;
import com.slaythespire.repository.GameDataRepository;
import com.slaythespire.repository.IntentTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BattleService {
    private static final Logger log = LoggerFactory.getLogger(BattleService.class);
    
    @Autowired
    private GameDataRepository dataRepo;

    private Player player;
    private Enemy enemy;
    private List<Card> drawPile, hand, discardPile;
    private int energy;
    private final Random random = new Random();
    private List<String> logList;
    private boolean gameOver;
    private String winner;

    public synchronized Map<String, Object> newBattle(List<Map<String, Object>> playerDeck, int playerHp, int playerMaxHp) {
        log.info(" 开始新战斗... 玩家血量: {}/{}", playerHp, playerMaxHp);
        this.player = new Player(playerHp, playerMaxHp);
        List<EnemyTemplate> enemies = dataRepo.getAllEnemies();
        if (enemies.isEmpty()) throw new IllegalStateException("怪物配置为空");
        this.enemy = new Enemy(enemies.get(0));
        
        this.drawPile = buildDeckFromPlayerData(playerDeck);
        Collections.shuffle(drawPile);
        this.hand = new ArrayList<>();
        this.discardPile = new ArrayList<>();
        this.energy = 3;
        this.logList = new ArrayList<>();
        this.gameOver = false;
        this.winner = null;
        
        player.onTurnStart(); 
        drawCards(5);
        return getCurrentState();
    }

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
                
                Card card = new Card(name, cost, damage, block, type);
                if (cardData.get("applyStatusType") != null) card.setApplyStatusType((String) cardData.get("applyStatusType"));
                if (cardData.get("applyStatusCount") != null) card.setApplyStatusCount(((Number) cardData.get("applyStatusCount")).intValue());
                if (cardData.get("applyStatusTarget") != null) card.setApplyStatusTarget((String) cardData.get("applyStatusTarget"));
                deck.add(card);
            }
        } else {
            for(int i=0; i<3; i++) deck.add(new Card("打击", 1, 6, 0, Card.CardType.ATTACK));
            for(int i=0; i<2; i++) deck.add(new Card("防御", 1, 0, 5, Card.CardType.SKILL));
        }
        return deck;
    }

    public synchronized Map<String, Object> playCard(int index) {
        validateBattleActive();
        validateCardIndex(index);

        Card card = hand.get(index);
        if (card.getCost() > energy) throw new IllegalStateException("能量不足");

        energy -= card.getCost();
        logList.clear();
        logList.add("🃏 玩家使用: " + card.getName());

        if (card.getType() == Card.CardType.ATTACK) {
            int baseDmg = player.modifyDamage(card.getDamage()); 
            int actualDmg = enemy.takeDamage(baseDmg);           
            logList.add(String.format("造成 %d 点伤害，敌人 HP: %d", actualDmg, enemy.getHp()));
        } else {
            player.addBlock(card.getBlock());
            logList.add(String.format("获得 %d 点格挡，当前格挡: %d", card.getBlock(), player.getBlock()));
        }

        if (card.getApplyStatusType() != null && !card.getApplyStatusType().isEmpty()) {
            StatusType type = StatusType.valueOf(card.getApplyStatusType());
            int count = card.getApplyStatusCount();
            String target = card.getApplyStatusTarget();
            if (target == null || target.isEmpty()) target = (card.getType() == Card.CardType.ATTACK) ? "ENEMY" : "SELF";

            if ("ENEMY".equals(target)) {
                enemy.addStatus(type, count);
                logList.add(String.format("给敌人施加了 %d 层 %s", count, translateStatus(type)));
            } else {
                player.addStatus(type, count);
                logList.add(String.format("给自己施加了 %d 层 %s", count, translateStatus(type)));
            }
        }

        hand.remove(index);
        discardPile.add(card);
        if (!enemy.isAlive()) {
            gameOver = true; winner = "玩家";
            logList.add("🎉 敌人被击败！战斗胜利！");
        }
        return getCurrentState();
    }

    /**
     * ✅ 核心修复：调整 endTurn 时序，确保怪物在执行意图时状态未衰减
     */
    public synchronized Map<String, Object> endTurn() {
        validateBattleActive();

        // ================= 阶段 1：玩家回合结束 =================
        player.onTurnEnd(); // 玩家状态减 1
        
        discardPile.addAll(hand);
        hand.clear();
        energy = 3;
        drawCards(5); 
        
        // ================= 阶段 2：怪物回合开始 =================
        enemy.onTurnStart(); // 怪物格挡清空（此时状态保持不变）

        // ================= 阶段 3：怪物执行当前意图 =================
        int baseDmg = enemy.executeCurrentIntent(); // ✅ 此时怪物拥有完整的状态（如 1 层脆弱）
        int actualDmg = player.takeDamage(baseDmg);
        
        if (actualDmg > 0) {
            logList.clear();
            logList.add(String.format("⚔️ %s %s，造成 %d 点伤害。玩家 HP: %d | 格挡: %d", 
                                    enemy.getName(), enemy.getIntentDesc(), actualDmg, player.getHp(), player.getBlock()));
        } else {
            logList.clear();
            logList.add(String.format("🛡️ %s %s", enemy.getName(), enemy.getIntentDesc()));
        }

        IntentTemplate currentIntent = enemy.getCurrentIntentTemplate();
        if (currentIntent != null && currentIntent.getApplyStatusType() != null && !currentIntent.getApplyStatusType().isEmpty()) {
            StatusType type = StatusType.valueOf(currentIntent.getApplyStatusType());
            int count = currentIntent.getApplyStatusCount();
            String target = currentIntent.getApplyStatusTarget();
            if ("ENEMY".equals(target)) {
                enemy.addStatus(type, count);
                logList.add(String.format("怪物给自己施加了 %d 层 %s", count, translateStatus(type)));
            } else {
                player.addStatus(type, count);
                logList.add(String.format("怪物给玩家施加了 %d 层 %s", count, translateStatus(type)));
            }
        }

        // ================= 阶段 4：怪物回合结束 =================
        enemy.onTurnEnd(); // ✅ 核心修复：怪物行动完毕后，状态才减 1

        // ================= 阶段 5：玩家回合开始 =================
        player.onTurnStart(); // 玩家格挡清空

        if (!player.isAlive()) {
            gameOver = true; winner = "敌人";
            logList.add("💀 玩家倒下... 战斗失败。");
            return getCurrentState();
        }

        // ================= 阶段 6：推进怪物意图到下一轮 =================
        enemy.advanceIntent();

        return getCurrentState();
    }

    private void drawCards(int count) {
        for (int i = 0; i < count; i++) {
            if (drawPile.isEmpty()) {
                if (discardPile.isEmpty()) break;
                drawPile.addAll(discardPile);
                discardPile.clear();
                Collections.shuffle(drawPile);
            }
            if (!drawPile.isEmpty()) hand.add(drawPile.remove(0));
        }
    }

    private Map<String, Object> getCurrentState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("playerHp", player.getHp());
        state.put("playerMaxHp", player.getMaxHp());
        state.put("playerBlock", player.getBlock());
        
        Map<String, Integer> playerStatusMap = new LinkedHashMap<>();
        for (Map.Entry<StatusType, Integer> entry : player.getStatuses().entrySet()) {
            if (entry.getValue() > 0) playerStatusMap.put(entry.getKey().name(), entry.getValue());
        }
        state.put("playerStatuses", playerStatusMap);

        state.put("enemyName", enemy.getName());
        state.put("enemyHp", enemy.getHp());
        state.put("enemyMaxHp", enemy.getMaxHp());
        state.put("enemyBlock", enemy.getBlock()); 
        
        state.put("enemyNextDamage", enemy.getNextDamage());
        state.put("enemyNextBlock", enemy.getNextBlock()); 
        state.put("enemyIntentDesc", enemy.getIntentDesc());
        
        IntentTemplate currentIntent = enemy.getCurrentIntentTemplate();
        if (currentIntent != null && currentIntent.getApplyStatusType() != null) {
            state.put("enemyIntentStatusType", currentIntent.getApplyStatusType());
            state.put("enemyIntentStatusCount", currentIntent.getApplyStatusCount());
        } else {
            state.put("enemyIntentStatusType", null);
            state.put("enemyIntentStatusCount", 0);
        }
        
        Map<String, Integer> enemyStatusMap = new LinkedHashMap<>();
        for (Map.Entry<StatusType, Integer> entry : enemy.getStatuses().entrySet()) {
            if (entry.getValue() > 0) enemyStatusMap.put(entry.getKey().name(), entry.getValue());
        }
        state.put("enemyStatuses", enemyStatusMap);

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
            cardInfo.put("applyStatusType", c.getApplyStatusType());
            cardInfo.put("applyStatusCount", c.getApplyStatusCount());
            handCards.add(cardInfo);
        }
        state.put("hand", handCards);
        state.put("log", new ArrayList<>(logList));
        state.put("gameOver", gameOver);
        state.put("winner", winner);
        return state;
    }

    private String translateStatus(StatusType type) {
        switch (type) {
            case VULNERABLE: return "易伤";
            case WEAK: return "虚弱";
            case FRAIL: return "脆弱";
            default: return type.name();
        }
    }

    private void validateBattleActive() { if (gameOver) throw new IllegalStateException("战斗已经结束"); }
    private void validateCardIndex(int index) { if (index < 0 || index >= hand.size()) throw new IllegalArgumentException("无效的卡牌编号"); }
}