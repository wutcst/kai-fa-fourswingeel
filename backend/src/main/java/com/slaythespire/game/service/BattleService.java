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
    private static final int HAND_LIMIT = 10;

    @Autowired
    private GameDataRepository dataRepo;

    private Player player;
    private List<Enemy> enemies;
    private List<Card> drawPile, hand, discardPile, exhaustPile;
    private int energy;
    private List<String> logList;
    private boolean gameOver;
    private String winner;

    public synchronized Map<String, Object> newBattle(List<Map<String, Object>> playerDeck, List<String> playerRelics, int playerHp, int playerMaxHp) {
        this.player = new Player(playerHp, playerMaxHp, dataRepo);
        if (playerRelics != null) {
            for (String relicId : playerRelics) {
                RelicTemplate tpl = dataRepo.getRelicById(relicId);
                if (tpl != null) player.addRelic(new GameRelic(tpl));
            }
        }

        List<EnemyGroupTemplate> groups = dataRepo.getAllEnemyGroups();
        if (groups.isEmpty()) throw new IllegalStateException("敌方阵容配置为空");
        EnemyGroupTemplate chosenGroup = groups.get(new Random().nextInt(groups.size()));
        
        this.enemies = new ArrayList<>();
        for (String eid : chosenGroup.getEnemies()) {
            EnemyTemplate tpl = dataRepo.getEnemyById(eid);
            if (tpl != null) enemies.add(new Enemy(tpl, dataRepo));
        }
        if (enemies.isEmpty()) {
            List<EnemyTemplate> all = dataRepo.getAllEnemies();
            if (!all.isEmpty()) enemies.add(new Enemy(all.get(0), dataRepo));
        }

        this.drawPile = buildDeckFromPlayerData(playerDeck);
        Collections.shuffle(drawPile);
        this.hand = new ArrayList<>(); this.discardPile = new ArrayList<>(); this.exhaustPile = new ArrayList<>();
        this.energy = 0; this.logList = new ArrayList<>(); this.gameOver = false; this.winner = null;

        player.onTurnStart();
        logList.addAll(player.getLastTurnStartLogs());
        energy = 3;
        drawCards(5);
        return getCurrentState();
    }

    public synchronized Map<String, Object> playCard(int index, Integer targetIndex) {
        validateBattleActive();
        validateCardIndex(index);

        Card card = hand.get(index);
        if (card.getCost() > energy) throw new IllegalStateException("能量不足");

        energy -= card.getCost();
        logList.clear();
        logList.add("🃏 使用: " + card.getName());

        List<Enemy> aliveEnemies = getAliveEnemies();
        if (aliveEnemies.isEmpty()) {
            gameOver = true; winner = "玩家"; logList.add("所有敌人已死亡");
            return getCurrentState();
        }
        Enemy target = (targetIndex != null && targetIndex >= 0 && targetIndex < aliveEnemies.size()) ? aliveEnemies.get(targetIndex) : aliveEnemies.get(0);

        if (card.getBlock() > 0) {
            player.gainBlock(card.getBlock());
            logList.add(String.format("🛡️ 获得 %d 点格挡，当前格挡: %d", card.getBlock(), player.getBlock()));
        }
        if (card.getSelfDamage() > 0) {
            player.takeDamage(card.getSelfDamage(), null, true);
            logList.add(String.format("💔 失去 %d 点生命，当前 HP: %d", card.getSelfDamage(), player.getHp()));
            if (!player.isAlive()) { gameOver = true; winner = "自身"; logList.add("💀 玩家因自身伤害倒下..."); return getCurrentState(); }
        }
        if (card.getEnergyGain() > 0) {
            energy += card.getEnergyGain();
            logList.add(String.format("⚡ 获得 %d 点能量，当前能量: %d", card.getEnergyGain(), energy));
        }

        // ================= ⚔️ 处理对敌伤害（支持多段攻击及目标切换） =================
        if (card.getDamage() > 0) {
            int hitCount = Math.max(1, card.getMultiHitCount());
            for (int i = 0; i < hitCount; i++) {
                if (!target.isAlive()) {
                    List<Enemy> currentAlive = getAliveEnemies();
                    if (currentAlive.isEmpty()) break;
                    target = currentAlive.get(0); // 自动寻找下一个目标
                }
                int actualDmg = target.takeDamage(card.getDamage(), player);
                if (hitCount > 1) {
                    logList.add(String.format("对 %s 造成 %d 点伤害 (%d/%d段)，HP: %d", target.getEnemyName(), actualDmg, i + 1, hitCount, target.getHp()));
                } else {
                    logList.add(String.format("对 %s 造成 %d 点伤害，HP: %d", target.getEnemyName(), actualDmg, target.getHp()));
                }
            }
        }

        if (card.getApplyStatusType() != null && !card.getApplyStatusType().isEmpty()) {
            StatusEffect status = StatusFactory.create(card.getApplyStatusType(), card.getApplyStatusCount(), dataRepo);
            if (status != null) {
                String targetStr = card.getApplyStatusTarget();
                if (targetStr == null || targetStr.isEmpty()) targetStr = (card.getType() == Card.CardType.ATTACK) ? "ENEMY" : "SELF";
                if ("ENEMY".equals(targetStr)) {
                    target.addStatus(status);
                    logList.add(String.format("给 %s 施加了 %d 层 %s", target.getEnemyName(), card.getApplyStatusCount(), status.getName()));
                } else {
                    player.addStatus(status);
                    logList.add(String.format("给自己施加了 %d 层 %s", card.getApplyStatusCount(), status.getName()));
                }
            }
        }

        removeDeadEnemies();
        if (card.getDrawCount() > 0) drawCards(card.getDrawCount());

        hand.remove(index);
        if (card.getType() == Card.CardType.POWER) logList.add(card.getName() + "被使用（能力牌）");
        else if (card.isExhaust()) { exhaustPile.add(card); logList.add(card.getName() + "被消耗"); }
        else discardPile.add(card);

        if (getAliveEnemies().isEmpty()) { gameOver = true; winner = "玩家"; logList.add("🎉 所有敌人被击败！"); }
        return getCurrentState();
    }

    public synchronized Map<String, Object> endTurn() {
        validateBattleActive();
        player.onTurnEnd();
        logList.addAll(player.getLastTurnEndLogs());

        List<Card> retained = new ArrayList<>();
        for (Card card : hand) {
            if (card.isEthereal()) { exhaustPile.add(card); logList.add(card.getName() + "因【虚无】被消耗"); }
            else if (card.isRetain()) retained.add(card);
            else discardPile.add(card);
        }
        hand.clear(); hand.addAll(retained);
        removeDeadEnemies();

        for (Enemy enemy : enemies) {
            enemy.onTurnStart();
            logList.addAll(enemy.getLastTurnStartLogs());
            int actualDmg = enemy.executeCurrentIntent(player);
            if (actualDmg > 0) logList.add(String.format("⚔️ %s %s，造成 %d 点伤害。玩家 HP: %d | 格挡: %d", enemy.getEnemyName(), enemy.getIntentDesc(), actualDmg, player.getHp(), player.getBlock()));
            else logList.add(String.format("🛡️ %s %s", enemy.getEnemyName(), enemy.getIntentDesc()));

            IntentTemplate intent = enemy.getCurrentIntentTemplate();
            if (intent != null && intent.getApplyStatusType() != null) {
                StatusEffect status = StatusFactory.create(intent.getApplyStatusType(), intent.getApplyStatusCount(), dataRepo);
                if (status != null) {
                    if ("ENEMY".equals(intent.getApplyStatusTarget())) enemy.addStatus(status);
                    else player.addStatus(status);
                }
            }
            enemy.onTurnEnd();
            logList.addAll(enemy.getLastTurnEndLogs());
            if (!enemy.isAlive()) logList.add(String.format("💀 %s 被击败！", enemy.getEnemyName()));
        }
        removeDeadEnemies();

        player.onTurnStart();
        logList.addAll(player.getLastTurnStartLogs());
        if (!player.isAlive()) { gameOver = true; winner = "敌人"; logList.add("💀 玩家倒下..."); return getCurrentState(); }

        energy = 3;
        drawCards(5);
        for (Enemy enemy : enemies) enemy.advanceIntent();
        return getCurrentState();
    }

    private void removeDeadEnemies() { if (enemies != null) enemies.removeIf(e -> !e.isAlive()); }
    private List<Enemy> getAliveEnemies() {
        List<Enemy> alive = new ArrayList<>();
        if (enemies != null) for (Enemy e : enemies) if (e.isAlive()) alive.add(e);
        return alive;
    }

    private void drawCards(int count) {
        int drawn = 0;
        for (int i = 0; i < count; i++) {
            if (hand.size() >= HAND_LIMIT) { logList.add("⚠️ 手牌已满"); break; }
            if (drawPile.isEmpty()) {
                if (discardPile.isEmpty()) break;
                drawPile.addAll(discardPile); discardPile.clear(); Collections.shuffle(drawPile);
                logList.add("🔄 弃牌堆洗入抽牌堆");
            }
            hand.add(drawPile.remove(0)); drawn++;
        }
        if (drawn > 0) logList.add(String.format("抽了 %d 张牌（手牌 %d/%d）", drawn, hand.size(), HAND_LIMIT));
    }

    private List<Card> buildDeckFromPlayerData(List<Map<String, Object>> playerDeck) {
        List<Card> deck = new ArrayList<>();
        if (playerDeck == null || playerDeck.isEmpty()) {
            for (String sid : Arrays.asList("strike", "strike", "strike", "defend", "defend")) {
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
                    if (cardData.containsKey("damage") && cardData.get("damage") != null) card.setDamage(((Number) cardData.get("damage")).intValue());
                    if (cardData.containsKey("block") && cardData.get("block") != null) card.setBlock(((Number) cardData.get("block")).intValue());
                    if (cardData.containsKey("name") && cardData.get("name") != null) card.setName((String) cardData.get("name"));
                    if (cardData.containsKey("rarity") && cardData.get("rarity") != null) card.setRarity((String) cardData.get("rarity"));
                    if (cardData.containsKey("selfDamage") && cardData.get("selfDamage") != null) card.setSelfDamage(((Number) cardData.get("selfDamage")).intValue());
                    if (cardData.containsKey("energyGain") && cardData.get("energyGain") != null) card.setEnergyGain(((Number) cardData.get("energyGain")).intValue());
                    if (cardData.containsKey("multiHitCount") && cardData.get("multiHitCount") != null) card.setMultiHitCount(((Number) cardData.get("multiHitCount")).intValue());
                }
            }
            if (card == null) {
                String name = (String) cardData.get("name");
                int cost = ((Number) cardData.get("cost")).intValue();
                int damage = cardData.get("damage") != null ? ((Number) cardData.get("damage")).intValue() : 0;
                int block = cardData.get("block") != null ? ((Number) cardData.get("block")).intValue() : 0;
                Card.CardType type = Card.CardType.valueOf((String) cardData.get("type"));
                card = new Card(name, cost, damage, block, type);
                card.setRarity("COMMON");
            }
            if (cardData.containsKey("applyStatusType")) card.setApplyStatusType((String) cardData.get("applyStatusType"));
            if (cardData.containsKey("applyStatusCount")) card.setApplyStatusCount(((Number) cardData.get("applyStatusCount")).intValue());
            if (cardData.containsKey("applyStatusTarget")) card.setApplyStatusTarget((String) cardData.get("applyStatusTarget"));
            if (cardData.containsKey("drawCount")) card.setDrawCount(((Number) cardData.get("drawCount")).intValue());
            if (cardData.containsKey("charId")) card.setCharId((String) cardData.get("charId"));
            if (cardData.containsKey("rarity")) card.setRarity((String) cardData.get("rarity"));
            if (cardData.containsKey("selfDamage") && cardData.get("selfDamage") != null) card.setSelfDamage(((Number) cardData.get("selfDamage")).intValue());
            if (cardData.containsKey("energyGain") && cardData.get("energyGain") != null) card.setEnergyGain(((Number) cardData.get("energyGain")).intValue());
            if (cardData.containsKey("multiHitCount") && cardData.get("multiHitCount") != null) card.setMultiHitCount(((Number) cardData.get("multiHitCount")).intValue());
            
            deck.add(card);
        }
        return deck;
    }

    private Map<String, Object> getCurrentState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("playerHp", player.getHp()); state.put("playerMaxHp", player.getMaxHp()); state.put("playerBlock", player.getBlock());

        List<Map<String, Object>> playerStatusList = new ArrayList<>();
        for (StatusEffect s : player.getStatuses()) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", s.getId()); info.put("count", s.getCount()); info.put("name", s.getName());
            StatusTemplate tpl = dataRepo.getStatusById(s.getId());
            if (tpl != null) info.put("color", tpl.getColor());
            playerStatusList.add(info);
        }
        state.put("playerStatuses", playerStatusList);

        List<Map<String, Object>> enemyList = new ArrayList<>();
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            Map<String, Object> eMap = new LinkedHashMap<>();
            eMap.put("index", i); eMap.put("name", e.getEnemyName()); eMap.put("hp", e.getHp()); eMap.put("maxHp", e.getMaxHp());
            eMap.put("block", e.getBlock()); eMap.put("nextDamage", e.getNextDamage()); eMap.put("nextBlock", e.getNextBlock()); eMap.put("intentDesc", e.getIntentDesc());
            
            IntentTemplate intent = e.getCurrentIntentTemplate();
            if (intent != null && intent.getApplyStatusType() != null) {
                eMap.put("intentStatusType", intent.getApplyStatusType()); eMap.put("intentStatusCount", intent.getApplyStatusCount());
                StatusTemplate statusTpl = dataRepo.getStatusById(intent.getApplyStatusType());
                eMap.put("intentStatusName", statusTpl != null ? statusTpl.getName() : intent.getApplyStatusType());
            } else {
                eMap.put("intentStatusType", null); eMap.put("intentStatusCount", 0); eMap.put("intentStatusName", null);
            }
            
            List<Map<String, Object>> statuses = new ArrayList<>();
            for (StatusEffect s : e.getStatuses()) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("id", s.getId()); info.put("count", s.getCount()); info.put("name", s.getName());
                StatusTemplate tpl = dataRepo.getStatusById(s.getId());
                if (tpl != null) info.put("color", tpl.getColor());
                statuses.add(info);
            }
            eMap.put("statuses", statuses);
            enemyList.add(eMap);
        }
        state.put("enemies", enemyList);

        if (!enemies.isEmpty()) {
            Enemy first = enemies.get(0);
            state.put("enemyName", first.getEnemyName()); state.put("enemyHp", first.getHp()); state.put("enemyMaxHp", first.getMaxHp());
            state.put("enemyBlock", first.getBlock()); state.put("enemyNextDamage", first.getNextDamage()); state.put("enemyNextBlock", first.getNextBlock());
            state.put("enemyIntentDesc", first.getIntentDesc());
            IntentTemplate intent = first.getCurrentIntentTemplate();
            state.put("enemyIntentStatusType", intent != null ? intent.getApplyStatusType() : null);
            state.put("enemyIntentStatusCount", intent != null ? intent.getApplyStatusCount() : 0);
            if (intent != null && intent.getApplyStatusType() != null) {
                StatusTemplate tpl = dataRepo.getStatusById(intent.getApplyStatusType());
                state.put("enemyIntentStatusName", tpl != null ? tpl.getName() : intent.getApplyStatusType());
            } else state.put("enemyIntentStatusName", null);
        } else {
            state.put("enemyName", ""); state.put("enemyHp", 0); state.put("enemyMaxHp", 0); state.put("enemyStatuses", new ArrayList<>());
        }

        state.put("energy", energy); state.put("drawPileSize", drawPile.size()); state.put("discardPileSize", discardPile.size());
        state.put("exhaustPileSize", exhaustPile.size()); state.put("handSize", hand.size()); state.put("handLimit", HAND_LIMIT);

        List<Map<String, Object>> handCards = new ArrayList<>();
        for (int i = 0; i < hand.size(); i++) {
            Card c = hand.get(i);
            Map<String, Object> cardInfo = new LinkedHashMap<>();
            cardInfo.put("index", i); cardInfo.put("name", c.getName()); cardInfo.put("cost", c.getCost());
            cardInfo.put("damage", c.getDamage()); cardInfo.put("block", c.getBlock()); cardInfo.put("type", c.getType().name());
            cardInfo.put("applyStatusType", c.getApplyStatusType()); cardInfo.put("applyStatusCount", c.getApplyStatusCount());
            cardInfo.put("exhaust", c.isExhaust()); cardInfo.put("retain", c.isRetain()); cardInfo.put("ethereal", c.isEthereal());
            cardInfo.put("drawCount", c.getDrawCount()); cardInfo.put("charId", c.getCharId()); cardInfo.put("rarity", c.getRarity());
            cardInfo.put("selfDamage", c.getSelfDamage()); cardInfo.put("energyGain", c.getEnergyGain());
            cardInfo.put("multiHitCount", c.getMultiHitCount()); // 🆕 传给前端
            handCards.add(cardInfo);
        }
        state.put("hand", handCards); state.put("log", new ArrayList<>(logList)); state.put("gameOver", gameOver); state.put("winner", winner);
        return state;
    }

    private void validateBattleActive() { if (gameOver) throw new IllegalStateException("战斗已经结束"); }
    private void validateCardIndex(int index) { if (index < 0 || index >= hand.size()) throw new IllegalArgumentException("无效的卡牌编号"); }
}