package com.slaythespire.game.service;

import com.slaythespire.game.model.*;
import com.slaythespire.game.model.factory.StatusFactory;
import com.slaythespire.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BattleService {
    private static final Logger log = LoggerFactory.getLogger(BattleService.class);
    @Autowired private GameDataRepository dataRepo;

    private Player player;
    private Enemy enemy;
    private List<Card> drawPile, hand, discardPile;
    private int energy;
    private List<String> logList;
    private boolean gameOver;
    private String winner;

    public synchronized Map<String, Object> newBattle(List<Map<String, Object>> playerDeck, int playerHp, int playerMaxHp) {
        this.player = new Player(playerHp, playerMaxHp, dataRepo); // 传入 dataRepo
        List<EnemyTemplate> enemies = dataRepo.getAllEnemies();
        if (enemies.isEmpty()) throw new IllegalStateException("怪物配置为空");
        this.enemy = new Enemy(enemies.get(0), dataRepo); // 传入 dataRepo
        
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
                int damage = cardData.get("damage") != null ? ((Number) cardData.get("damage")).intValue() : 0;
                int block = cardData.get("block") != null ? ((Number) cardData.get("block")).intValue() : 0;
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
            int actualDmg = enemy.takeDamage(card.getDamage(), player);           
            logList.add(String.format("造成 %d 点伤害，敌人 HP: %d", actualDmg, enemy.getHp()));
        } else {
            player.gainBlock(card.getBlock());
            logList.add(String.format("获得 %d 点格挡，当前格挡: %d", card.getBlock(), player.getBlock()));
        }

        // 施加状态：使用工厂类创建对象
        if (card.getApplyStatusType() != null && !card.getApplyStatusType().isEmpty()) {
            StatusEffect status = StatusFactory.create(card.getApplyStatusType(), card.getApplyStatusCount(), dataRepo);
            if (status != null) {
                String target = card.getApplyStatusTarget();
                if (target == null || target.isEmpty()) target = (card.getType() == Card.CardType.ATTACK) ? "ENEMY" : "SELF";
                
                if ("ENEMY".equals(target)) {
                    enemy.addStatus(status);
                    logList.add(String.format("给敌人施加了 %d 层 %s", card.getApplyStatusCount(), status.getName()));
                } else {
                    player.addStatus(status);
                    logList.add(String.format("给自己施加了 %d 层 %s", card.getApplyStatusCount(), status.getName()));
                }
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

    public synchronized Map<String, Object> endTurn() {
        validateBattleActive();

        player.onTurnEnd(); 
        discardPile.addAll(hand);
        hand.clear();
        energy = 3;
        drawCards(5); 
        
        enemy.onTurnStart(); 

        int actualDmg = enemy.executeCurrentIntent(player); 
        
        if (actualDmg > 0) {
            logList.clear();
            logList.add(String.format("⚔️ %s %s，造成 %d 点伤害。玩家 HP: %d | 格挡: %d", 
                                    enemy.getEnemyName(), enemy.getIntentDesc(), actualDmg, player.getHp(), player.getBlock()));
        } else {
            logList.clear();
            logList.add(String.format("🛡️ %s %s", enemy.getEnemyName(), enemy.getIntentDesc()));
        }

        IntentTemplate currentIntent = enemy.getCurrentIntentTemplate();
        if (currentIntent != null && currentIntent.getApplyStatusType() != null) {
            StatusEffect status = StatusFactory.create(currentIntent.getApplyStatusType(), currentIntent.getApplyStatusCount(), dataRepo);
            if (status != null) {
                String target = currentIntent.getApplyStatusTarget();
                if ("ENEMY".equals(target)) {
                    enemy.addStatus(status);
                    logList.add(String.format("怪物给自己施加了 %d 层 %s", currentIntent.getApplyStatusCount(), status.getName()));
                } else {
                    player.addStatus(status);
                    logList.add(String.format("怪物给玩家施加了 %d 层 %s", currentIntent.getApplyStatusCount(), status.getName()));
                }
            }
        }

        enemy.onTurnEnd(); 
        player.onTurnStart(); 

        if (!player.isAlive()) {
            gameOver = true; winner = "敌人";
            logList.add("💀 玩家倒下... 战斗失败。");
            return getCurrentState();
        }

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
        
        // 返回包含 name 和 color 的状态列表
        List<Map<String, Object>> playerStatusList = new ArrayList<>();
        for (StatusEffect s : player.getStatuses()) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", s.getId());
            info.put("count", s.getCount());
            info.put("name", s.getName());
            StatusTemplate tpl = dataRepo.getStatusById(s.getId());
            if (tpl != null) info.put("color", tpl.getColor());
            playerStatusList.add(info);
        }
        state.put("playerStatuses", playerStatusList);

        state.put("enemyName", enemy.getEnemyName());
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
        
        List<Map<String, Object>> enemyStatusList = new ArrayList<>();
        for (StatusEffect s : enemy.getStatuses()) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", s.getId());
            info.put("count", s.getCount());
            info.put("name", s.getName());
            StatusTemplate tpl = dataRepo.getStatusById(s.getId());
            if (tpl != null) info.put("color", tpl.getColor());
            enemyStatusList.add(info);
        }
        state.put("enemyStatuses", enemyStatusList);

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

    private void validateBattleActive() { if (gameOver) throw new IllegalStateException("战斗已经结束"); }
    private void validateCardIndex(int index) { if (index < 0 || index >= hand.size()) throw new IllegalArgumentException("无效的卡牌编号"); }
}