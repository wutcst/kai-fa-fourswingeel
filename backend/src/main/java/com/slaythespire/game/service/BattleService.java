package com.slaythespire.game.service;

import com.slaythespire.game.model.Card;
import com.slaythespire.game.model.Enemy;
import com.slaythespire.game.model.Player;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BattleService {

    // 交互战斗的当前状态
    private Player player;
    private Enemy enemy;
    private List<Card> drawPile;
    private List<Card> hand;
    private Random random;
    private List<String> log;
    private boolean gameOver;
    private String winner;

    /**
     * 开始一场新的交互式战斗，返回初始状态
     */
    public synchronized Map<String, Object> newBattle() {
        this.player = new Player(70);
        this.enemy = new Enemy("邪教徒", 50, 8);
        this.drawPile = initCards();
        this.hand = new ArrayList<>();
        this.random = new Random();
        this.log = new ArrayList<>();
        this.gameOver = false;
        this.winner = null;
        drawHand(); // 抽取开局手牌
        return getCurrentState();
    }

    /**
     * 玩家选择一张手牌打出，执行该回合，并返回新的状态
     * @param index 手牌列表中的索引（从0开始）
     */
    public synchronized Map<String, Object> playCard(int index) {
        if (gameOver) {
            throw new IllegalStateException("战斗已经结束，请重新开始");
        }
        if (index < 0 || index >= hand.size()) {
            throw new IllegalArgumentException("无效的卡牌编号: " + index);
        }
        Card chosen = hand.get(index);
        log.clear();

        // 1. 玩家回合
        log.add("玩家使用: " + chosen.getName());
        if (chosen.getType() == Card.CardType.ATTACK) {
            enemy.takeDamage(chosen.getDamage());
            log.add("对敌人造成 " + chosen.getDamage() + " 点伤害，剩余 HP: " + enemy.getHp());
        } else {
            player.addBlock(chosen.getBlock());
            log.add("获得 " + chosen.getBlock() + " 点格挡，当前格挡: " + player.getBlock());
        }

        // 2. 判断敌人是否死亡
        if (!enemy.isAlive()) {
            gameOver = true;
            winner = "玩家";
            log.add("敌人被击败！");
            return getCurrentState();
        }

        // 3. 敌人回合
        int dmg = enemy.getAttackDamage();
        int blocked = Math.min(player.getBlock(), dmg);
        player.takeDamage(dmg);
        log.add("敌人攻击，造成 " + dmg + " 点伤害（挡掉 " + blocked + "），玩家 HP: " + player.getHp() + " 格挡: " + player.getBlock());

        if (!player.isAlive()) {
            gameOver = true;
            winner = "敌人";
            log.add("玩家倒下...");
            return getCurrentState();
        }

        // 4. 下一回合抽牌
        drawHand();
        return getCurrentState();
    }

    private void drawHand() {
        hand.clear();
        for (int i = 0; i < 3; i++) {
            hand.add(drawPile.get(random.nextInt(drawPile.size())));
        }
    }

    private List<Card> initCards() {
        List<Card> cards = new ArrayList<>();
        cards.add(new Card("打击", 6, 0, Card.CardType.ATTACK));
        cards.add(new Card("打击", 6, 0, Card.CardType.ATTACK));
        cards.add(new Card("打击", 6, 0, Card.CardType.ATTACK));
        cards.add(new Card("防御", 0, 5, Card.CardType.SKILL));
        cards.add(new Card("防御", 0, 5, Card.CardType.SKILL));
        cards.add(new Card("剑柄打击", 8, 0, Card.CardType.ATTACK));
        return cards;
    }

    private Map<String, Object> getCurrentState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("playerHp", player.getHp());
        state.put("playerBlock", player.getBlock());
        state.put("enemyName", enemy.getName());
        state.put("enemyHp", enemy.getHp());
        state.put("enemyMaxHp", enemy.getMaxHp());

        List<Map<String, Object>> handCards = new ArrayList<>();
        for (int i = 0; i < hand.size(); i++) {
            Card c = hand.get(i);
            Map<String, Object> cardInfo = new LinkedHashMap<>();
            cardInfo.put("index", i);
            cardInfo.put("name", c.getName());
            cardInfo.put("damage", c.getDamage());
            cardInfo.put("block", c.getBlock());
            cardInfo.put("type", c.getType().name());
            handCards.add(cardInfo);
        }
        state.put("handCards", handCards);
        state.put("log", new ArrayList<>(log));
        state.put("gameOver", gameOver);
        state.put("winner", winner);
        return state;
    }
}
