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
        
        // 🛠️ 提前初始化所有列表，防止 NullPointerException
        this.hand = new ArrayList<>();
        this.discardPile = new ArrayList<>();
        this.exhaustPile = new ArrayList<>();
        this.logList = new ArrayList<>();
        this.energy = 0; 
        this.gameOver = false; 
        this.winner = null;

        // 🆕 【核心机制：固有牌处理】
        List<Card> innateCards = new ArrayList<>();
        Iterator<Card> iterator = drawPile.iterator();
        while (iterator.hasNext()) {
            Card c = iterator.next();
            if (c.isInnate()) {
                innateCards.add(c);
                iterator.remove(); 
            }
        }
        hand.addAll(innateCards);
        if (!innateCards.isEmpty()) {
            logList.add("🌟 固有牌加入手牌: " + innateCards.size() + " 张");
        }

        Collections.shuffle(drawPile);
        
        player.onTurnStart();
        logList.addAll(player.getLastTurnStartLogs());
        energy = 3;
        if (RelicEffectHandler.hasEffect(player, "ENERGY_FIRST_TURN")) {
            int val = RelicEffectHandler.getEffectValue(player, "ENERGY_FIRST_TURN");
            energy += val;
            logList.add("🏮 灯笼使初始能量 +" + val);
        }
        
        int drawCount = Math.max(0, 5 - innateCards.size());
        drawCards(drawCount);
        
        return getCurrentState();
    }

    public synchronized Map<String, Object> playCard(int index, Integer targetIndex, Integer exhaustHandIndex, Integer discardHandIndex) {
        validateBattleActive();
        validateCardIndex(index);

        Card card = hand.get(index);
        
        if (card.isUnplayable()) throw new IllegalStateException("此牌无法被主动打出！");
        if (card.isXCost() && energy <= 0) throw new IllegalStateException("X耗能卡牌需要至少1点能量才能打出！");
        if (!card.isXCost() && card.getCost() > energy) throw new IllegalStateException("能量不足");

        logList.clear();
        logList.add("🃏 使用: " + card.getName());

        List<Enemy> aliveEnemies = getAliveEnemies();
        if (aliveEnemies.isEmpty()) {
            gameOver = true; winner = "玩家"; logList.add("所有敌人已死亡");
            return getCurrentState();
        }
        Enemy target = (targetIndex != null && targetIndex >= 0 && targetIndex < aliveEnemies.size()) ? aliveEnemies.get(targetIndex) : aliveEnemies.get(0);

        int xValue = 1;
        if (card.isXCost()) {
            xValue = energy;
            logList.add("⚡ X耗能: 消耗了 " + xValue + " 点能量");
            energy = 0;
        } else {
            energy -= card.getCost();
        }

        if (card.getBlock() > 0) {
            int actualBlock = card.getBlock() * xValue;
            player.gainBlock(actualBlock);
            logList.add(String.format("🛡️ 获得 %d 点格挡，当前格挡: %d", actualBlock, player.getBlock()));
        }
        
        if (card.getSelfDamage() > 0) {
            player.takeDamage(card.getSelfDamage(), null, true);
            logList.addAll(player.getLastCombatLogs());
            logList.add(String.format("💔 失去 %d 点生命，当前 HP: %d", card.getSelfDamage(), player.getHp()));
            if (!player.isAlive()) { gameOver = true; winner = "自身"; logList.add("💀 玩家因自身伤害倒下..."); return getCurrentState(); }
        }
        
        if (card.getEnergyGain() > 0) {
            energy += card.getEnergyGain();
            logList.add(String.format("⚡ 获得 %d 点能量，当前能量: %d", card.getEnergyGain(), energy));
        }

        if (card.getDamage() > 0) {
            if (card.isAoe()) {
                for (Enemy e : aliveEnemies) {
                    int actualDmg = e.takeDamage(card.getDamage() * xValue, player);
                    logList.addAll(e.getLastCombatLogs());
                    logList.add(String.format("💥 AOE对 %s 造成 %d 点伤害，HP: %d", e.getEnemyName(), actualDmg, e.getHp()));
                }
            } else {
                int hitCount = Math.max(1, card.getMultiHitCount());
                for (int i = 0; i < hitCount; i++) {
                    if (!target.isAlive()) {
                        List<Enemy> currentAlive = getAliveEnemies();
                        if (currentAlive.isEmpty()) break;
                        target = currentAlive.get(0);
                    }
                    int actualDmg = target.takeDamage(card.getDamage() * xValue, player);
                    logList.addAll(target.getLastCombatLogs());
                    if (hitCount > 1) {
                        logList.add(String.format("对 %s 造成 %d 点伤害 (%d/%d段)，HP: %d", target.getEnemyName(), actualDmg, i + 1, hitCount, target.getHp()));
                    } else {
                        logList.add(String.format("对 %s 造成 %d 点伤害，HP: %d", target.getEnemyName(), actualDmg, target.getHp()));
                    }
                }
            }
        }

        if (card.getApplyStatusType() != null && !card.getApplyStatusType().isEmpty()) {
            StatusEffect status = StatusFactory.create(card.getApplyStatusType(), card.getApplyStatusCount() * xValue, dataRepo);
            if (status != null) {
                String targetStr = card.getApplyStatusTarget();
                if (targetStr == null || targetStr.isEmpty()) targetStr = (card.getType() == Card.CardType.ATTACK) ? "ENEMY" : "SELF";
                
                if ("ENEMY".equals(targetStr)) {
                    if (card.isAoe()) {
                        for (Enemy e : aliveEnemies) e.addStatus(status);
                        logList.add(String.format("给所有敌人施加了 %d 层 %s", card.getApplyStatusCount() * xValue, status.getName()));
                    } else {
                        target.addStatus(status);
                        logList.add(String.format("给 %s 施加了 %d 层 %s", target.getEnemyName(), card.getApplyStatusCount() * xValue, status.getName()));
                    }
                } else {
                    player.addStatus(status);
                    logList.add(String.format("给自己施加了 %d 层 %s", card.getApplyStatusCount() * xValue, status.getName()));
                }
            }
        }

        removeDeadEnemies();

        // ================= ⚙️ 核心结算顺序：根据 drawFirst 决定抽牌时机 =================
        int playedCardIndex = index;

        // 1. 如果配置为先抽牌（如：杂技），则先执行抽牌
        if (card.isDrawFirst()) {
            if (card.getDrawCount() > 0) {
                drawCards(card.getDrawCount());
            }
        }

        // 2. 处理消耗手牌 (手中无其他牌时 count=0，安全跳过，不会报错)
        if (card.getExhaustHandCount() > 0) {
            int count = Math.min(card.getExhaustHandCount(), hand.size() - 1);
            for (int i = 0; i < count; i++) {
                int targetIdx = resolveHandInteractionIndex(i, exhaustHandIndex, playedCardIndex, hand.size(), "消耗");
                hand.remove(targetIdx);
                if (targetIdx < playedCardIndex) {
                    playedCardIndex--;
                }
            }
        }

        // 3. 处理丢弃手牌 (手中无其他牌时 count=0，安全跳过，不会报错)
        if (card.getDiscardCount() > 0) {
            int count = Math.min(card.getDiscardCount(), hand.size() - 1);
            for (int i = 0; i < count; i++) {
                int targetIdx = resolveHandInteractionIndex(i, discardHandIndex, playedCardIndex, hand.size(), "丢弃");
                hand.remove(targetIdx);
                if (targetIdx < playedCardIndex) {
                    playedCardIndex--;
                }
            }
        }

        // 4. 如果配置为后抽牌（如：知识渴求），则后执行抽牌
        if (!card.isDrawFirst()) {
            if (card.getDrawCount() > 0) {
                drawCards(card.getDrawCount());
            }
        }

        // 5. 最后移除打出的牌
        Card finalCard = hand.remove(playedCardIndex);
        if (finalCard.getType() == Card.CardType.POWER) {
            logList.add(finalCard.getName() + "被使用（能力牌）");
        } else if (finalCard.isExhaust()) {
            exhaustPile.add(finalCard);
            logList.add(finalCard.getName() + "被消耗");
            triggerDrawOnExhaust();
        } else {
            discardPile.add(finalCard);
        }

        if (getAliveEnemies().isEmpty()) { gameOver = true; winner = "玩家"; logList.add("🎉 所有敌人被击败！"); }
        return getCurrentState();
    }

    private int resolveHandInteractionIndex(int loopIndex, Integer providedIndex, int playedCardIndex, int currentHandSize, String actionName) {
        int targetIdx;
        if (loopIndex == 0 && providedIndex != null && providedIndex >= 0 && providedIndex < currentHandSize) {
            if (providedIndex == playedCardIndex) {
                targetIdx = (playedCardIndex == 0) ? 1 : 0; 
                logList.add(String.format("⚠️ 无法%s正在打出的牌，随机%s了其他牌", actionName, actionName));
            } else {
                targetIdx = providedIndex;
                logList.add(String.format("🔥 你选择%s了手中的 %s", actionName, hand.get(targetIdx).getName()));
            }
        } else {
            List<Integer> validIndices = new ArrayList<>();
            for (int j = 0; j < currentHandSize; j++) {
                if (j != playedCardIndex) validIndices.add(j);
            }
            targetIdx = validIndices.get(new Random().nextInt(validIndices.size()));
            logList.add(String.format("🎲 随机%s了手中的 %s", actionName, hand.get(targetIdx).getName()));
        }
        return targetIdx;
    }

    public synchronized Map<String, Object> endTurn() {
        validateBattleActive();
        
        // ================= 1. 玩家回合结束 =================
        player.onTurnEnd();
        logList.addAll(player.getLastTurnEndLogs());
        logList.addAll(player.getLastCombatLogs()); 

        // 🆕 【关键修复】玩家回合结束，衰减敌人身上的无实体（因为玩家回合结束了）
        for (Enemy e : enemies) {
            decrementIntangible(e);
        }

        boolean hasRunePyramid = RelicEffectHandler.hasEffect(player, "RUNE_PYRAMID");
        List<Card> retained = new ArrayList<>();
        for (Card card : hand) {
            if (card.isEthereal()) {
                exhaustPile.add(card);
                logList.add(card.getName() + "因【虚无】被消耗");
            triggerDrawOnExhaust();
            } else if (hasRunePyramid || card.isRetain()) {
                retained.add(card);
            } else {
                discardPile.add(card);
            }
        }
        hand.clear(); 
        hand.addAll(retained);
        removeDeadEnemies();

        // ================= 2. 敌人回合行动 =================
        for (Enemy enemy : enemies) {
            enemy.onTurnStart();
            logList.addAll(enemy.getLastTurnStartLogs());
            
            int actualDmg = enemy.executeCurrentIntent(player);
            logList.addAll(player.getLastCombatLogs()); // 获取玩家受击时的无实体日志
            
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
            logList.addAll(enemy.getLastCombatLogs());
            
            if (!enemy.isAlive()) logList.add(String.format("💀 %s 被击败！", enemy.getEnemyName()));
        }
        removeDeadEnemies();

        // ================= 3. 敌人回合结束 =================
        // 🆕 【关键修复】所有敌人行动完毕后，衰减玩家身上的无实体（持续了一整回合，现在消失）
        decrementIntangible(player);

        // ================= 4. 玩家新回合开始 =================
        player.onTurnStart();
        logList.addAll(player.getLastTurnStartLogs());
        if (!player.isAlive()) { gameOver = true; winner = "敌人"; logList.add("💀 玩家倒下..."); return getCurrentState(); }

        energy = 3;
        drawCards(5);
        for (Enemy enemy : enemies) enemy.advanceIntent();
        return getCurrentState();
    }

    // 🆕 辅助方法：精确控制无实体的衰减（持续一回合逻辑）
    private void decrementIntangible(Combatant c) {
        if (c == null || !c.isAlive()) return;
        for (StatusEffect s : new ArrayList<>(c.getStatuses())) {
            if ("INTANGIBLE".equals(s.getId())) {
                s.decrement();
                String name = (c instanceof Player) ? "玩家" : ((Enemy) c).getEnemyName();
                if (s.getCount() <= 0) {
                    c.getStatuses().remove(s);
                    logList.add(String.format("💨 %s 的【无实体】持续一回合结束，效果消失", name));
                } else {
                    logList.add(String.format("💨 %s 的【无实体】持续一回合结束，剩余层数: %d", name, s.getCount()));
                }
                break; 
            }
        }
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
            
            if (cardData.containsKey("exhaust")) card.setExhaust((Boolean) cardData.get("exhaust"));
            if (cardData.containsKey("retain")) card.setRetain((Boolean) cardData.get("retain"));
            if (cardData.containsKey("ethereal")) card.setEthereal((Boolean) cardData.get("ethereal"));
            
            if (cardData.containsKey("selfDamage") && cardData.get("selfDamage") != null) card.setSelfDamage(((Number) cardData.get("selfDamage")).intValue());
            if (cardData.containsKey("energyGain") && cardData.get("energyGain") != null) card.setEnergyGain(((Number) cardData.get("energyGain")).intValue());
            if (cardData.containsKey("multiHitCount") && cardData.get("multiHitCount") != null) card.setMultiHitCount(((Number) cardData.get("multiHitCount")).intValue());
            
            if (cardData.containsKey("exhaustHandCount") && cardData.get("exhaustHandCount") != null) {
                card.setExhaustHandCount(((Number) cardData.get("exhaustHandCount")).intValue());
            }
            if (cardData.containsKey("exhaustHandMode") && cardData.get("exhaustHandMode") != null) {
                card.setExhaustHandMode((String) cardData.get("exhaustHandMode"));
            }
            
            if (cardData.containsKey("unplayable")) card.setUnplayable((Boolean) cardData.get("unplayable"));
            if (cardData.containsKey("innate")) card.setInnate((Boolean) cardData.get("innate"));
            if (cardData.containsKey("discardCount") && cardData.get("discardCount") != null) {
                card.setDiscardCount(((Number) cardData.get("discardCount")).intValue());
            }
            if (cardData.containsKey("xCost")) card.setXCost((Boolean) cardData.get("xCost"));
            if (cardData.containsKey("aoe")) card.setAoe((Boolean) cardData.get("aoe"));
            
            // 🆕 读取 drawFirst 字段
            if (cardData.containsKey("drawFirst")) {
                card.setDrawFirst((Boolean) cardData.get("drawFirst"));
            }
            
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
            cardInfo.put("multiHitCount", c.getMultiHitCount()); 
            cardInfo.put("exhaustHandCount", c.getExhaustHandCount()); 
            cardInfo.put("exhaustHandMode", c.getExhaustHandMode()); 
            
            cardInfo.put("unplayable", c.isUnplayable());
            cardInfo.put("innate", c.isInnate());
            cardInfo.put("discardCount", c.getDiscardCount());
            cardInfo.put("xCost", c.isXCost());
            cardInfo.put("aoe", c.isAoe());
            cardInfo.put("drawFirst", c.isDrawFirst()); // 🆕 传递给前端
            
            handCards.add(cardInfo);
        }
        state.put("hand", handCards); state.put("log", new ArrayList<>(logList)); state.put("gameOver", gameOver); state.put("winner", winner);
        return state;
    }

    private void triggerDrawOnExhaust() {
        if (RelicEffectHandler.hasEffect(player, "DRAW_ON_EXHAUST")) {
            drawCards(1);
            logList.add("📄 金纸触发，抽了一张牌");
        }
    }

    private void validateBattleActive() { if (gameOver) throw new IllegalStateException("战斗已经结束"); }
    private void validateCardIndex(int index) { if (index < 0 || index >= hand.size()) throw new IllegalArgumentException("无效的卡牌编号"); }
}