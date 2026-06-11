package com.slaythespire.game.service;

import com.slaythespire.game.model.*;
import com.slaythespire.game.model.factory.StatusFactory;
import com.slaythespire.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 战斗核心服务类
 * 管理抽牌堆/弃牌堆/消耗堆/手牌，处理回合生命周期
 */
@Service
public class BattleService {
    private static final Logger log = LoggerFactory.getLogger(BattleService.class);
    private static final int HAND_LIMIT = 10;

    @Autowired
    private GameDataRepository dataRepo;

    private Player player;
    private Enemy enemy;
    private List<Card> drawPile, hand, discardPile, exhaustPile;
    private int energy;
    private List<String> logList;
    private boolean gameOver;
    private String winner;

    // =========================== 战斗生命周期 ===========================

    /**
     * 初始化并开启一场新战斗
     */
    public synchronized Map<String, Object> newBattle(List<Map<String, Object>> playerDeck,
                                                       List<String> playerRelics,
                                                       int playerHp, int playerMaxHp) {
        this.player = new Player(playerHp, playerMaxHp, dataRepo);

        // 加载遗物
        if (playerRelics != null) {
            for (String relicId : playerRelics) {
                RelicTemplate tpl = dataRepo.getRelicById(relicId);
                if (tpl != null) player.addRelic(new GameRelic(tpl));
            }
        }

        // 加载怪物
        List<EnemyTemplate> enemies = dataRepo.getAllEnemies();
        if (enemies.isEmpty()) throw new IllegalStateException("怪物配置为空");
        this.enemy = new Enemy(enemies.get(0), dataRepo);

        // 构建牌堆
        this.drawPile = buildDeckFromPlayerData(playerDeck);
        Collections.shuffle(drawPile);

        this.hand = new ArrayList<>();
        this.discardPile = new ArrayList<>();
        this.exhaustPile = new ArrayList<>();
        this.energy = 0;
        this.logList = new ArrayList<>();
        this.gameOver = false;
        this.winner = null;

        // 玩家回合开始 → 抽初始手牌
        player.onTurnStart();
        energy = 3;
        drawCards(5);

        return getCurrentState();
    }

    /**
     * 玩家打出指定索引的卡牌
     */
    public synchronized Map<String, Object> playCard(int index) {
        validateBattleActive();
        validateCardIndex(index);

        Card card = hand.get(index);
        if (card.getCost() > energy) throw new IllegalStateException("能量不足");

        energy -= card.getCost();
        logList.clear();
        logList.add("🃏 使用: " + card.getName());

        // 1. 造成伤害
        if (card.getDamage() > 0) {
            int actualDmg = enemy.takeDamage(card.getDamage(), player);
            logList.add(String.format("造成 %d 点伤害，敌人 HP: %d", actualDmg, enemy.getHp()));
        }

        // 2. 获得格挡
        if (card.getBlock() > 0) {
            player.gainBlock(card.getBlock());
            logList.add(String.format("获得 %d 点格挡，当前格挡: %d", card.getBlock(), player.getBlock()));
        }

        // 3. 施加状态效果
        if (card.getApplyStatusType() != null && !card.getApplyStatusType().isEmpty()) {
            StatusEffect status = StatusFactory.create(card.getApplyStatusType(), card.getApplyStatusCount(), dataRepo);
            if (status != null) {
                String target = card.getApplyStatusTarget();
                if (target == null || target.isEmpty())
                    target = (card.getType() == Card.CardType.ATTACK) ? "ENEMY" : "SELF";
                if ("ENEMY".equals(target)) {
                    enemy.addStatus(status);
                    logList.add(String.format("给敌人施加了 %d 层 %s", card.getApplyStatusCount(), status.getName()));
                } else {
                    player.addStatus(status);
                    logList.add(String.format("给自己施加了 %d 层 %s", card.getApplyStatusCount(), status.getName()));
                }
            }
        }

        // 4. 处理卡牌去向：消耗 or 弃牌
        hand.remove(index);
        if (card.isExhaust()) {
            exhaustPile.add(card);
            logList.add(card.getName() + "被消耗");
        } else {
            discardPile.add(card);
        }

        if (!enemy.isAlive()) {
            gameOver = true;
            winner = "玩家";
            logList.add("🎉 敌人被击败！战斗胜利！");
        }
        return getCurrentState();
    }

    /**
     * 结束玩家回合 → 敌人回合 → 玩家回合开始
     */
    public synchronized Map<String, Object> endTurn() {
        validateBattleActive();

        // ========== 阶段 1：玩家回合结束结算 ==========
        // 1a. 状态效果 tick（毒/再生）
        player.onTurnEnd();
        logList.addAll(player.getLastTurnEndLogs());

        // 1b. 处理手牌：保留留下，虚无消耗，其余进弃牌堆
        List<Card> retained = new ArrayList<>();
        for (Card card : hand) {
            if (card.isEthereal()) {
                exhaustPile.add(card);
                logList.add(card.getName() + "因【虚无】被消耗");
            } else if (card.isRetain()) {
                retained.add(card);
                logList.add(card.getName() + "因【保留】留在手牌");
            } else {
                discardPile.add(card);
            }
        }
        hand.clear();
        hand.addAll(retained);

        // ========== 阶段 2：敌人回合 ==========
        // 2a. 敌人回合开始
        enemy.onTurnStart();

        // 2b. 敌人执行意图
        int actualDmg = enemy.executeCurrentIntent(player);
        if (actualDmg > 0) {
            logList.add(String.format("⚔️ %s %s，造成 %d 点伤害。玩家 HP: %d | 格挡: %d",
                    enemy.getEnemyName(), enemy.getIntentDesc(), actualDmg, player.getHp(), player.getBlock()));
        } else {
            logList.add(String.format("🛡️ %s %s", enemy.getEnemyName(), enemy.getIntentDesc()));
        }

        // 2c. 敌人意图附带状态
        IntentTemplate intent = enemy.getCurrentIntentTemplate();
        if (intent != null && intent.getApplyStatusType() != null) {
            StatusEffect status = StatusFactory.create(intent.getApplyStatusType(), intent.getApplyStatusCount(), dataRepo);
            if (status != null) {
                if ("ENEMY".equals(intent.getApplyStatusTarget())) {
                    enemy.addStatus(status);
                    logList.add(String.format("怪物给自己施加了 %d 层 %s", intent.getApplyStatusCount(), status.getName()));
                } else {
                    player.addStatus(status);
                    logList.add(String.format("怪物给玩家施加了 %d 层 %s", intent.getApplyStatusCount(), status.getName()));
                }
            }
        }

        // 2d. 敌人回合结束
        enemy.onTurnEnd();
        logList.addAll(enemy.getLastTurnEndLogs());

        // ========== 阶段 3：玩家回合开始（下回合） ==========
        // 3a. 清格挡、遗物触发
        player.onTurnStart();

        if (!player.isAlive()) {
            gameOver = true;
            winner = "敌人";
            logList.add("💀 玩家倒下... 战斗失败。");
            return getCurrentState();
        }

        // 3b. 能量恢复 + 抽牌
        energy = 3;
        drawCards(5);

        // 3c. 推进敌人意图
        enemy.advanceIntent();

        return getCurrentState();
    }

    // =========================== 抽牌逻辑 ===========================

    /**
     * 抽牌：支持弃牌堆洗牌重组、手牌上限检查、状态提示
     */
    private void drawCards(int count) {
        int drawn = 0;
        for (int i = 0; i < count; i++) {
            // 手牌已满则停止
            if (hand.size() >= HAND_LIMIT) {
                logList.add("⚠️ 手牌已满（" + HAND_LIMIT + "张），停止抽牌");
                break;
            }

            // 抽牌堆为空 → 将弃牌堆洗入
            if (drawPile.isEmpty()) {
                if (discardPile.isEmpty()) {
                    logList.add("⚠️ 抽牌堆和弃牌堆均为空，无法继续抽牌");
                    break;
                }
                drawPile.addAll(discardPile);
                discardPile.clear();
                Collections.shuffle(drawPile);
                logList.add("🔄 弃牌堆洗入抽牌堆");
            }

            hand.add(drawPile.remove(0));
            drawn++;
        }
        if (drawn > 0) {
            logList.add(String.format("抽了 %d 张牌（手牌 %d/%d）", drawn, hand.size(), HAND_LIMIT));
        }
    }

    // =========================== 卡牌构建 ===========================

    private List<Card> buildDeckFromPlayerData(List<Map<String, Object>> playerDeck) {
        List<Card> deck = new ArrayList<>();
        if (playerDeck == null || playerDeck.isEmpty()) {
            List<String> starterIds = Arrays.asList("strike", "strike", "strike", "defend", "defend");
            for (String sid : starterIds) {
                CardTemplate tpl = dataRepo.getCardById(sid);
                if (tpl != null) deck.add(new Card(tpl));
            }
            return deck;
        }
        for (Map<String, Object> cardData : playerDeck) {
            Card card = null;
            String tplId = (String) cardData.get("id");
            if (tplId != null) {
                CardTemplate tpl = dataRepo.getCardById(tplId);
                if (tpl != null) {
                    card = new Card(tpl);
                    if (cardData.containsKey("damage") && cardData.get("damage") != null)
                        card.setDamage(((Number) cardData.get("damage")).intValue());
                    if (cardData.containsKey("block") && cardData.get("block") != null)
                        card.setBlock(((Number) cardData.get("block")).intValue());
                    if (cardData.containsKey("name") && cardData.get("name") != null)
                        card.setName((String) cardData.get("name"));
                }
            }
            if (card == null) {
                String name = (String) cardData.get("name");
                int cost = ((Number) cardData.get("cost")).intValue();
                int damage = cardData.get("damage") != null ? ((Number) cardData.get("damage")).intValue() : 0;
                int block = cardData.get("block") != null ? ((Number) cardData.get("block")).intValue() : 0;
                Card.CardType type = Card.CardType.valueOf((String) cardData.get("type"));
                card = new Card(name, cost, damage, block, type);
            }
            if (cardData.get("applyStatusType") != null)
                card.setApplyStatusType((String) cardData.get("applyStatusType"));
            if (cardData.get("applyStatusCount") != null)
                card.setApplyStatusCount(((Number) cardData.get("applyStatusCount")).intValue());
            if (cardData.get("applyStatusTarget") != null)
                card.setApplyStatusTarget((String) cardData.get("applyStatusTarget"));
            deck.add(card);
        }
        return deck;
    }

    // =========================== 状态序列化 ===========================

    private Map<String, Object> getCurrentState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("playerHp", player.getHp());
        state.put("playerMaxHp", player.getMaxHp());
        state.put("playerBlock", player.getBlock());

        // 玩家状态
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

        // 敌人信息
        state.put("enemyName", enemy.getEnemyName());
        state.put("enemyHp", enemy.getHp());
        state.put("enemyMaxHp", enemy.getMaxHp());
        state.put("enemyBlock", enemy.getBlock());
        state.put("enemyNextDamage", enemy.getNextDamage());
        state.put("enemyNextBlock", enemy.getNextBlock());
        state.put("enemyIntentDesc", enemy.getIntentDesc());

        IntentTemplate intent = enemy.getCurrentIntentTemplate();
        if (intent != null && intent.getApplyStatusType() != null) {
            state.put("enemyIntentStatusType", intent.getApplyStatusType());
            state.put("enemyIntentStatusCount", intent.getApplyStatusCount());
        } else {
            state.put("enemyIntentStatusType", null);
            state.put("enemyIntentStatusCount", 0);
        }

        // 敌人状态
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

        // 能量与牌堆信息
        state.put("energy", energy);
        state.put("drawPileSize", drawPile.size());
        state.put("discardPileSize", discardPile.size());
        state.put("exhaustPileSize", exhaustPile.size());
        state.put("handSize", hand.size());
        state.put("handLimit", HAND_LIMIT);

        // 手牌
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
            cardInfo.put("exhaust", c.isExhaust());
            cardInfo.put("retain", c.isRetain());
            cardInfo.put("ethereal", c.isEthereal());
            handCards.add(cardInfo);
        }
        state.put("hand", handCards);
        state.put("log", new ArrayList<>(logList));
        state.put("gameOver", gameOver);
        state.put("winner", winner);
        return state;
    }

    // =========================== 校验 ===========================

    private void validateBattleActive() {
        if (gameOver) throw new IllegalStateException("战斗已经结束");
    }

    private void validateCardIndex(int index) {
        if (index < 0 || index >= hand.size())
            throw new IllegalArgumentException("无效的卡牌编号");
    }
}
