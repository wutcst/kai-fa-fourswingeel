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
 * 采用数据驱动架构，支持遗物加载、复合卡牌（铁斩波）、毒无视格挡、状态日志收集等高级特性。
 */
@Service
public class BattleService {
    private static final Logger log = LoggerFactory.getLogger(BattleService.class);
    
    @Autowired 
    private GameDataRepository dataRepo;

    private Player player;
    private Enemy enemy;
    private List<Card> drawPile, hand, discardPile;
    private int energy;
    private List<String> logList;
    private boolean gameOver;
    private String winner;

    /**
     * 初始化并开启一场新战斗
     * @param playerDeck   前端传入的玩家当前卡组
     * @param playerRelics ✅ 新增：前端传入的玩家携带的遗物 ID 列表
     * @param playerHp     玩家当前剩余血量
     * @param playerMaxHp  玩家最大血量
     */
    public synchronized Map<String, Object> newBattle(List<Map<String, Object>> playerDeck, List<String> playerRelics, int playerHp, int playerMaxHp) {
        this.player = new Player(playerHp, playerMaxHp, dataRepo);
        
        // ✅ 核心新增：加载玩家携带的遗物并处理被动属性
        if (playerRelics != null && !playerRelics.isEmpty()) {
            for (String relicId : playerRelics) {
                RelicTemplate tpl = dataRepo.getRelicById(relicId);
                if (tpl != null) {
                    GameRelic relic = new GameRelic(tpl);
                    this.player.addRelic(relic);
                    
                    // 处理被动属性（例如：MAX_HP 增加最大生命值）
                    if ("MAX_HP".equals(relic.getEffectType())) {
                        // 注意：请确保 Player 或 Combatant 类中有 setMaxHp 方法
                        // 如果没有，请在 Combatant.java 中添加：public void setMaxHp(int maxHp) { this.maxHp = maxHp; }
                        this.player.setMaxHp(this.player.getMaxHp() + relic.getValue());
                        this.player.heal(relic.getValue()); // 当前血量同步增加
                    }
                }
            }
        }

        // 加载怪物
        List<EnemyTemplate> enemies = dataRepo.getAllEnemies();
        if (enemies.isEmpty()) throw new IllegalStateException("怪物配置为空");
        this.enemy = new Enemy(enemies.get(0), dataRepo);
        
        // 构建牌堆
        this.drawPile = buildDeckFromPlayerData(playerDeck);
        Collections.shuffle(drawPile);
        
        // 初始化回合上下文
        this.hand = new ArrayList<>();
        this.discardPile = new ArrayList<>();
        this.energy = 3;
        this.logList = new ArrayList<>();
        this.gameOver = false;
        this.winner = null;
        
        // 玩家回合开始（清空格挡等）并抽初始手牌
        player.onTurnStart(); 
        drawCards(5);
        
        return getCurrentState();
    }

    /**
     * 根据玩家存档数据构建牌堆，兜底逻辑从配置读取初始牌组
     */
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
            // 兜底：从配置读取初始牌组
            List<String> starterIds = Arrays.asList("strike", "strike", "strike", "defend", "defend");
            for (String id : starterIds) {
                CardTemplate tpl = dataRepo.getCardById(id);
                if (tpl != null) {
                    deck.add(new Card(tpl)); 
                }
            }
        }
        return deck;
    }

    /**
     * 玩家打出指定索引的卡牌
     * ✅ 核心修复：解耦伤害与格挡逻辑，支持“攻击牌带格挡”（如铁斩波）
     */
    public synchronized Map<String, Object> playCard(int index) {
        validateBattleActive();
        validateCardIndex(index);

        Card card = hand.get(index);
        if (card.getCost() > energy) throw new IllegalStateException("能量不足");

        energy -= card.getCost();
        logList.clear();
        logList.add("🃏 玩家使用: " + card.getName());

        // 1. 处理伤害（无论卡牌类型，只要有伤害就执行）
        if (card.getDamage() > 0) {
            int actualDmg = enemy.takeDamage(card.getDamage(), player);           
            logList.add(String.format("造成 %d 点伤害，敌人 HP: %d", actualDmg, enemy.getHp()));
        }

        // 2. 处理格挡（无论卡牌类型，只要有格挡就执行）
        if (card.getBlock() > 0) {
            player.gainBlock(card.getBlock());
            logList.add(String.format("获得 %d 点格挡，当前格挡: %d", card.getBlock(), player.getBlock()));
        }

        // 3. 施加状态效果
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

    /**
     * 结束玩家回合，触发怪物行动与回合结算
     * ✅ 核心修复：严格按时序执行，收集毒/再生等状态产生的日志
     */
    public synchronized Map<String, Object> endTurn() {
        validateBattleActive();

        // ================= 阶段 1：玩家回合结束 =================
        player.onTurnEnd(); 
        logList.addAll(player.getLastTurnEndLogs()); // ✅ 收集玩家身上的毒/再生/遗物日志
        
        discardPile.addAll(hand);
        hand.clear();
        energy = 3;
        drawCards(5); 
        
        // ================= 阶段 2：怪物回合开始 =================
        enemy.onTurnStart(); 

        // ================= 阶段 3：怪物执行当前意图 =================
        int actualDmg = enemy.executeCurrentIntent(player); 
        
        if (actualDmg > 0) {
            logList.add(String.format("⚔️ %s %s，造成 %d 点伤害。玩家 HP: %d | 格挡: %d", 
                                    enemy.getEnemyName(), enemy.getIntentDesc(), actualDmg, player.getHp(), player.getBlock()));
        } else {
            logList.add(String.format("🛡️ %s %s", enemy.getEnemyName(), enemy.getIntentDesc()));
        }

        // 怪物施加意图附带的状态
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

        // ================= 阶段 4：怪物回合结束 =================
        enemy.onTurnEnd(); 
        logList.addAll(enemy.getLastTurnEndLogs()); // ✅ 收集怪物身上的毒/再生/遗物日志

        // ================= 阶段 5：玩家回合开始 =================
        player.onTurnStart(); 

        if (!player.isAlive()) {
            gameOver = true; winner = "敌人";
            logList.add("💀 玩家倒下... 战斗失败。");
            return getCurrentState();
        }

        // ================= 阶段 6：推进怪物意图到下一轮 =================
        enemy.advanceIntent();
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
            if (!drawPile.isEmpty()) hand.add(drawPile.remove(0));
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

    // ================= 辅助校验方法 =================
    private void validateBattleActive() { 
        if (gameOver) throw new IllegalStateException("战斗已经结束"); 
    }
    
    private void validateCardIndex(int index) { 
        if (index < 0 || index >= hand.size()) throw new IllegalArgumentException("无效的卡牌编号"); 
    }
}