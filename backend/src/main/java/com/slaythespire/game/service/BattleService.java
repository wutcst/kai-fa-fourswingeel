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

    public synchronized Map<String, Object> newBattle(List<Map<String, Object>> playerDeck, List<String> playerRelics, int playerHp, int playerMaxHp, String nodeType) {
        this.player = new Player(playerHp, playerMaxHp, dataRepo);
        if (playerRelics != null) {
            for (String relicId : playerRelics) {
                RelicTemplate tpl = dataRepo.getRelicById(relicId);
                if (tpl != null) player.addRelic(new GameRelic(tpl));
            }
        }

        List<EnemyGroupTemplate> groups = dataRepo.getAllEnemyGroups();
        if (groups.isEmpty()) throw new IllegalStateException("敌方阵容配置为空");
        String nodeTypeUpper = (nodeType != null) ? nodeType.toUpperCase() : null;
        if ("MONSTER".equals(nodeTypeUpper)) nodeTypeUpper = "NORMAL";
        List<EnemyGroupTemplate> filteredGroups = new ArrayList<>();
        for (EnemyGroupTemplate g : groups) {
            if (nodeTypeUpper != null && !g.getType().equalsIgnoreCase(nodeTypeUpper)) continue;
            filteredGroups.add(g);
        }
        if (filteredGroups.isEmpty()) filteredGroups = groups;
        EnemyGroupTemplate chosenGroup = filteredGroups.get(new Random().nextInt(filteredGroups.size()));

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
        this.hand = new ArrayList<>();
        this.discardPile = new ArrayList<>();
        this.exhaustPile = new ArrayList<>();
        this.logList = new ArrayList<>();
        this.energy = 0;
        this.gameOver = false;
        this.winner = null;

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
        if (RelicEffectHandler.hasEffect(player, "FIRST_DRAW_BONUS")) drawCount++;
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

        // 地精大块头被动
        if (card.getType() == Card.CardType.SKILL) {
            for (Enemy e : enemies) {
                for (StatusEffect s : e.getStatuses()) {
                    if ("ANGRY".equals(s.getId())) {
                        int angryPower = (int) (s.getCount() + 1);
                        StatusEffect strength = com.slaythespire.game.model.factory.StatusFactory.create("STRENGTH", angryPower, dataRepo);
                        if (strength != null) {
                            e.addStatus(strength);
                            logList.add("🔥 " + e.getEnemyName() + "因【激怒】获得 " + angryPower + " 点力量！");
                        }
                    }
                }
            }
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

        // ================= 伤害计算（包含力量倍率） =================
        if (card.getDamage() > 0) {
            int strengthMultiplier = card.getStrengthMultiplier();
            int strengthCount = 0;
            StatusEffect savedStrength = null;
            if (strengthMultiplier > 1) {
                // 记录并临时移除玩家的力量状态（防止 GameStatus 重复加成）
                for (StatusEffect s : player.getStatuses()) {
                    if ("STRENGTH".equals(s.getId())) {
                        strengthCount = s.getCount();
                        savedStrength = s;
                        player.getStatuses().remove(s);
                        break;
                    }
                }
                logList.add("💪 力量发挥 " + strengthMultiplier + " 倍效果，临时锁定力量值: " + strengthCount);
            }

            int baseDamage = card.getDamage() * xValue;
            if (strengthMultiplier > 1) {
                // 额外增加 (strengthMultiplier * strengthCount) 伤害（此时玩家身上已无力量，GameStatus 不会再加）
                baseDamage += strengthCount * strengthMultiplier;
                logList.add("💪 额外增加 " + (strengthCount * strengthMultiplier) + " 伤害");
            }

            if (card.isAoe()) {
                for (Enemy e : aliveEnemies) {
                    int actualDmg = e.takeDamage(baseDamage, player);
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
                    int actualDmg = target.takeDamage(baseDamage, player);
                    logList.addAll(target.getLastCombatLogs());
                    if (hitCount > 1) {
                        logList.add(String.format("对 %s 造成 %d 点伤害 (%d/%d段)，HP: %d", target.getEnemyName(), actualDmg, i + 1, hitCount, target.getHp()));
                    } else {
                        logList.add(String.format("对 %s 造成 %d 点伤害，HP: %d", target.getEnemyName(), actualDmg, target.getHp()));
                    }
                }
            }

            // 恢复力量状态（如果有）
            if (savedStrength != null) {
                player.addStatus(savedStrength);
            }
        }

        // ================= 多效果系统 =================
        List<CardEffect> effects = card.getEffects();
        if (effects != null && !effects.isEmpty()) {
            for (CardEffect effect : effects) {
                if (effect.getType() == null || effect.getType().isEmpty()) continue;
                StatusEffect status = StatusFactory.create(effect.getType(), effect.getCount() * xValue, dataRepo);
                if (status == null) continue;

                String targetStr = effect.getTarget();
                if (targetStr == null || targetStr.isEmpty()) {
                    targetStr = (card.getType() == Card.CardType.ATTACK) ? "ENEMY" : "SELF";
                }

                if ("ENEMY".equals(targetStr)) {
                    if (card.isAoe()) {
                        for (Enemy e : aliveEnemies) e.addStatus(StatusFactory.create(effect.getType(), effect.getCount() * xValue, dataRepo));
                        logList.add(String.format("给所有敌人施加了 %d 层 %s", effect.getCount() * xValue, status.getName()));
                    } else {
                        target.addStatus(StatusFactory.create(effect.getType(), effect.getCount() * xValue, dataRepo));
                        logList.add(String.format("给 %s 施加了 %d 层 %s", target.getEnemyName(), effect.getCount() * xValue, status.getName()));
                    }
                } else {
                    player.addStatus(StatusFactory.create(effect.getType(), effect.getCount() * xValue, dataRepo));
                    logList.add(String.format("给自己施加了 %d 层 %s", effect.getCount() * xValue, status.getName()));
                }
            }
        }

        removeDeadEnemies();

        int playedCardIndex = index;

        if (card.isDrawFirst()) {
            if (card.getDrawCount() > 0) {
                drawCards(card.getDrawCount());
            }
        }

        if (card.getExhaustHandCount() > 0) {
            int count = Math.min(card.getExhaustHandCount(), hand.size() - 1);
            for (int i = 0; i < count; i++) {
                int targetIdx = resolveHandInteractionIndex(i, exhaustHandIndex, playedCardIndex, hand.size(), "消耗");
                Card exhaustedCard = hand.remove(targetIdx);
                exhaustPile.add(exhaustedCard);
                logList.add("🔥 消耗了手中的 " + exhaustedCard.getName());
                triggerDrawOnExhaust();
                if (targetIdx < playedCardIndex) {
                    playedCardIndex--;
                }
            }
        }

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
            // 🆕 愤怒效果：在弃牌堆添加一张复制品
            if (finalCard.isCopyToDiscard()) {
                Card copy = new Card(finalCard);
                discardPile.add(copy);
                logList.add("🔥 " + finalCard.getName() + "的复制品加入了弃牌堆");
            }
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

        player.onTurnEnd();
        logList.addAll(player.getLastTurnEndLogs());
        logList.addAll(player.getLastCombatLogs());

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

        for (Enemy enemy : enemies) {
            enemy.onTurnStart();
            logList.addAll(enemy.getLastTurnStartLogs());

            int actualDmg = enemy.executeCurrentIntent(player);
            logList.addAll(player.getLastCombatLogs());

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

        decrementIntangible(player);

        player.onTurnStart();
        logList.addAll(player.getLastTurnStartLogs());
        if (!player.isAlive()) { gameOver = true; winner = "敌人"; logList.add("💀 玩家倒下..."); return getCurrentState(); }

        energy = 3;
        int turnDrawCount = 5 - retained.size();
        if (RelicEffectHandler.hasEffect(player, "FIRST_DRAW_BONUS")) turnDrawCount++;
        drawCards(Math.max(0, turnDrawCount));
        for (Enemy enemy : enemies) enemy.advanceIntent();
        return getCurrentState();
    }

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
                    if (cardData.containsKey("innate") && cardData.get("innate") != null) card.setInnate((Boolean) cardData.get("innate"));
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

            if (cardData.containsKey("effects") && cardData.get("effects") != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> effectsData = (List<Map<String, Object>>) cardData.get("effects");
                List<CardEffect> effects = new ArrayList<>();
                for (Map<String, Object> eMap : effectsData) {
                    String eType = (String) eMap.get("type");
                    int eCount = eMap.containsKey("count") ? ((Number) eMap.get("count")).intValue() : 1;
                    String eTarget = (String) eMap.get("target");
                    effects.add(new CardEffect(eType, eCount, eTarget));
                }
                card.setEffects(effects);
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
            if (cardData.containsKey("discardMode") && cardData.get("discardMode") != null) {
                card.setDiscardMode((String) cardData.get("discardMode"));
            }
            if (cardData.containsKey("xCost")) card.setXCost((Boolean) cardData.get("xCost"));
            if (cardData.containsKey("aoe")) card.setAoe((Boolean) cardData.get("aoe"));

            if (cardData.containsKey("drawFirst")) {
                card.setDrawFirst((Boolean) cardData.get("drawFirst"));
            }

            // 🆕 读取 copyToDiscard 字段
            if (cardData.containsKey("copyToDiscard")) {
                card.setCopyToDiscard((Boolean) cardData.get("copyToDiscard"));
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
            eMap.put("index", i); eMap.put("name", e.getEnemyName()); eMap.put("hp", e.getHp()); eMap.put("maxHp", e.getMaxHp()); eMap.put("enemyType", e.getEnemyType());
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

        state.put("energy", energy); state.put("drawPileSize", drawPile.size()); state.put("discardPileSize", discardPile.size()); state.put("exhaustPileSize", exhaustPile.size()); state.put("handSize", hand.size()); state.put("handLimit", HAND_LIMIT);

        List<Map<String, Object>> handCards = new ArrayList<>();
        for (int i = 0; i < hand.size(); i++) {
            Card c = hand.get(i);
            Map<String, Object> cardInfo = new LinkedHashMap<>();
            cardInfo.put("index", i); cardInfo.put("name", c.getName()); cardInfo.put("cost", c.getCost());
            cardInfo.put("damage", c.getDamage()); cardInfo.put("block", c.getBlock()); cardInfo.put("type", c.getType().name());
            cardInfo.put("effects", effectsToStateList(c.getEffects()));
            cardInfo.put("exhaust", c.isExhaust()); cardInfo.put("retain", c.isRetain()); cardInfo.put("ethereal", c.isEthereal());
            cardInfo.put("drawCount", c.getDrawCount()); cardInfo.put("charId", c.getCharId()); cardInfo.put("rarity", c.getRarity());
            cardInfo.put("selfDamage", c.getSelfDamage()); cardInfo.put("energyGain", c.getEnergyGain());
            cardInfo.put("multiHitCount", c.getMultiHitCount());
            cardInfo.put("exhaustHandCount", c.getExhaustHandCount());
            cardInfo.put("exhaustHandMode", c.getExhaustHandMode());

            cardInfo.put("unplayable", c.isUnplayable());
            cardInfo.put("innate", c.isInnate());
            cardInfo.put("discardCount", c.getDiscardCount());
            cardInfo.put("discardMode", c.getDiscardMode());
            cardInfo.put("xCost", c.isXCost());
            cardInfo.put("aoe", c.isAoe());
            cardInfo.put("drawFirst", c.isDrawFirst());

            // 🆕 新增字段传递给前端
            cardInfo.put("copyToDiscard", c.isCopyToDiscard());
            cardInfo.put("strengthMultiplier", c.getStrengthMultiplier());

            handCards.add(cardInfo);
        }
        state.put("hand", handCards);
        state.put("drawPile", cardsToStateList(drawPile));
        state.put("discardPile", cardsToStateList(discardPile));
        state.put("exhaustPile", cardsToStateList(exhaustPile));
        state.put("log", new ArrayList<>(logList)); state.put("gameOver", gameOver); state.put("winner", winner);
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

    private List<Map<String, Object>> cardsToStateList(List<Card> cards) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Card c : cards) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", c.getName()); info.put("cost", c.getCost());
            info.put("damage", c.getDamage()); info.put("block", c.getBlock()); info.put("type", c.getType().name());
            info.put("effects", effectsToStateList(c.getEffects()));
            info.put("exhaust", c.isExhaust()); info.put("retain", c.isRetain()); info.put("ethereal", c.isEthereal());
            info.put("drawCount", c.getDrawCount()); info.put("charId", c.getCharId()); info.put("rarity", c.getRarity());
            info.put("selfDamage", c.getSelfDamage()); info.put("energyGain", c.getEnergyGain());
            info.put("multiHitCount", c.getMultiHitCount());
            info.put("exhaustHandCount", c.getExhaustHandCount());
            info.put("exhaustHandMode", c.getExhaustHandMode());
            info.put("innate", c.isInnate()); info.put("aoe", c.isAoe());
            info.put("unplayable", c.isUnplayable());
            info.put("discardCount", c.getDiscardCount());
            info.put("discardMode", c.getDiscardMode());
            info.put("xCost", c.isXCost());
            info.put("drawFirst", c.isDrawFirst());
            info.put("upgraded", c.isUpgraded());

            // 🆕 新增字段
            info.put("copyToDiscard", c.isCopyToDiscard());
            info.put("strengthMultiplier", c.getStrengthMultiplier());

            list.add(info);
        }
        return list;
    }

    private List<Map<String, Object>> effectsToStateList(List<CardEffect> effects) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (effects == null) return list;
        for (CardEffect e : effects) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", e.getType());
            m.put("count", e.getCount());
            m.put("target", e.getTarget());
            list.add(m);
        }
        return list;
    }

}
