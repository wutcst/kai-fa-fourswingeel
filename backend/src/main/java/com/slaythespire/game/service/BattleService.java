package com.slaythespire.game.service;

import com.slaythespire.game.model.Card;
import com.slaythespire.game.model.Enemy;
import com.slaythespire.game.model.Player;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BattleService {

    private Player player;
    private Enemy enemy;
    private List<Card> drawPile;
    private List<Card> hand;
    private List<Card> discardPile;
    private int energy;
    private Random random;
    private List<String> log;
    private boolean gameOver;
    private String winner;

    public synchronized Map<String, Object> newBattle() {
        this.player = new Player(70);
        this.enemy = new Enemy("邪教徒", 50, 8);
        this.drawPile = initCards();
        Collections.shuffle(drawPile);
        this.hand = new ArrayList<>();
        this.discardPile = new ArrayList<>();
        this.energy = 3;
        this.random = new Random();
        this.log = new ArrayList<>();
        this.gameOver = false;
        this.winner = null;
        drawCards(5);
        return getCurrentState();
    }

    public synchronized Map<String, Object> playCard(int index) {
        if (gameOver) {
            throw new IllegalStateException("战斗已经结束，请重新开始");
        }
        if (index < 0 || index >= hand.size()) {
            throw new IllegalArgumentException("无效的卡牌编号: " + index);
        }
        Card card = hand.get(index);
        if (card.getCost() > energy) {
            throw new IllegalStateException("能量不足，无法打出该卡牌");
        }
        energy -= card.getCost();

        log.clear();
        log.add("玩家使用: " + card.getName());
        if (card.getType() == Card.CardType.ATTACK) {
            enemy.takeDamage(card.getDamage());
            log.add("造成 " + card.getDamage() + " 点伤害，敌人 HP: " + enemy.getHp());
        } else {
            player.addBlock(card.getBlock());
            log.add("获得 " + card.getBlock() + " 点格挡，当前格挡: " + player.getBlock());
        }

        hand.remove(index);
        discardPile.add(card);

        if (!enemy.isAlive()) {
            gameOver = true;
            winner = "玩家";
            log.add("敌人被击败！");
            return getCurrentState();
        }

        return getCurrentState();
    }

    public synchronized Map<String, Object> endTurn() {
        if (gameOver) {
            throw new IllegalStateException("战斗已经结束");
        }

        int dmg = enemy.getAttackDamage();
        int blocked = Math.min(player.getBlock(), dmg);
        player.takeDamage(dmg);
        log.clear();
        log.add("敌人攻击，造成 " + dmg + " 点伤害（挡掉 " + blocked + "），玩家 HP: " + player.getHp() + " 格挡: " + player.getBlock());

        if (!player.isAlive()) {
            gameOver = true;
            winner = "敌人";
            log.add("玩家倒下...");
            return getCurrentState();
        }

        // 回合结束：格挡清零，弃掉手牌，重置能量，抽五张
        player.addBlock(-player.getBlock());
        discardPile.addAll(hand);
        hand.clear();
        energy = 3;
        drawCards(5);

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
            hand.add(drawPile.remove(0));
        }
    }

    private List<Card> initCards() {
        List<Card> cards = new ArrayList<>();
        cards.add(new Card("打击", 1, 6, 0, Card.CardType.ATTACK));
        cards.add(new Card("打击", 1, 6, 0, Card.CardType.ATTACK));
        cards.add(new Card("防御", 1, 0, 5, Card.CardType.SKILL));
        cards.add(new Card("防御", 1, 0, 5, Card.CardType.SKILL));
        cards.add(new Card("重击", 2, 10, 0, Card.CardType.ATTACK));
        cards.add(new Card("重击", 2, 10, 0, Card.CardType.ATTACK));
        cards.add(new Card("盾击", 2, 0, 8, Card.CardType.SKILL));
        cards.add(new Card("盾击", 2, 0, 8, Card.CardType.SKILL));
        cards.add(new Card("猛击", 3, 18, 0, Card.CardType.ATTACK));
        cards.add(new Card("铁壁", 3, 0, 12, Card.CardType.SKILL));
        return cards;
    }

    private Map<String, Object> getCurrentState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("playerHp", player.getHp());
        state.put("playerBlock", player.getBlock());
        state.put("enemyName", enemy.getName());
        state.put("enemyHp", enemy.getHp());
        state.put("enemyMaxHp", enemy.getMaxHp());
        state.put("energy", energy);
        state.put("drawPileSize", drawPile.size());
        state.put("discardPileSize", discardPile.size());

        // 敌人下回合将造成的伤害（固定值，因为敌人意图目前不变）
        state.put("enemyNextDamage", enemy.getAttackDamage());

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
        state.put("hand", handCards);   // 修改键名为 "hand"，与前端对接
        state.put("log", new ArrayList<>(log));
        state.put("gameOver", gameOver);
        state.put("winner", winner);
        return state;
    }
}
