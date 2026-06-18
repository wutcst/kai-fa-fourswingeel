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
    private int blockPerAttackThisTurn = 0;
    private boolean hasDiscardedThisTurn = false;
    private int drawCountThisTurn = 0;
    private CardTemplate buffedShivTemplate = null;
    private int shivBuffDamage = 0;
    private boolean playerExtraPoisonTick = false;
    private int poisonAllPerCardThisTurn = 0;
    private boolean poisonSkipFirstDraw = false;
    private List<String> logList;
    private boolean gameOver;
    private String winner;
    private int attackCountThisTurn; // 本场战斗累计攻击牌计数（黄金戒指，跨回合累计）
    private int skillCountCombat;    // 本场战斗累计技能牌计数（黄金项链，跨回合累计）
    private boolean hasDealtDamageThisTurn; // 魔力花：本回合是否已造成过伤害
    private boolean exhaustedThisAction;   // 本次操作是否消耗了卡牌（安东尼之怒）

    // 🔥 简单状态牌模板（晕眩、灼伤）
    private static class StatusCardTemplate {
        String name;
        int cost;
        boolean ethereal;
        boolean unplayable;
        boolean exhaust;
        int endOfTurnDamage;
        StatusCardTemplate(String name, int cost, boolean ethereal, boolean unplayable, boolean exhaust, int endOfTurnDamage) {
            this.name = name; this.cost = cost; this.ethereal = ethereal; this.unplayable = unplayable; this.exhaust = exhaust; this.endOfTurnDamage = endOfTurnDamage;
        }
    }
    private static final Map<String, StatusCardTemplate> STATUS_CARD_TEMPLATES = new HashMap<>();
    static {
        STATUS_CARD_TEMPLATES.put("burn",  new StatusCardTemplate("灼伤", 1, true,  true,  true,  2));
        STATUS_CARD_TEMPLATES.put("dazed", new StatusCardTemplate("晕眩", 0, true,  true,  false, 0));
        STATUS_CARD_TEMPLATES.put("wound", new StatusCardTemplate("伤口", 1, false, true,  false, 0));
        STATUS_CARD_TEMPLATES.put("slime", new StatusCardTemplate("黏液", 1, false, false, true,  0));
    }

    /** 向抽牌堆中塞入一张状态牌 */
    public void addStatusCardToDrawPile(String type) {
        StatusCardTemplate tpl = STATUS_CARD_TEMPLATES.get(type);
        if (tpl == null) return;
        // 构造一张简单卡片对象
        Card card = new Card(tpl.name, tpl.cost, 0, 0, Card.CardType.STATUS);
        if (tpl.ethereal) card.setEthereal(true);
        if (tpl.unplayable) card.setUnplayable(true);
        if (tpl.exhaust) card.setExhaust(true);
        if (tpl.endOfTurnDamage > 0) card.setEndOfTurnDamage(tpl.endOfTurnDamage);
        drawPile.add(new Random().nextInt(drawPile.size() + 1), card); // 随机位置洗入
        logList.add("🃏 一张【" + tpl.name + "】被洗入抽牌堆");
    }

    public synchronized Map<String, Object> newBattle(List<Map<String, Object>> playerDeck, List<String> playerRelics, int playerHp, int playerMaxHp, String nodeType) {
        this.player = new Player(playerHp, playerMaxHp, dataRepo);
        this.player.resetBattleFlags();
        // 🆕 凤凰之羽全局重置：如果新开一局（遗物列表不含 phoenix_feather），重置重生标记
        if (playerRelics == null || !playerRelics.contains("phoenix_feather")) {
            RelicEffectHandler.resetDeathSaveInRun();
        }
        if (playerRelics != null) {
            for (String relicId : playerRelics) {
                RelicTemplate tpl = dataRepo.getRelicById(relicId);
                if (tpl != null) player.addRelic(new GameRelic(tpl));
            }
        }

        List<EnemyGroupTemplate> groups = dataRepo.getAllEnemyGroups();
        if (groups.isEmpty()) throw new IllegalStateException("敌方阵容配置为空");
        // 从传入参数中提取act和类型（前端格式："monster_act1"、"elite_act2"、"boss_act1"等）
        int currentAct = 1;
        String pureNodeType = null;
        if (nodeType != null) {
            String upper = nodeType.toUpperCase();
            if (upper.contains("_ACT")) {
                try {
                    String[] parts = upper.split("_ACT");
                    pureNodeType = parts[0];
                    if (parts.length > 1) currentAct = Integer.parseInt(parts[1]);
                } catch (Exception e) { currentAct = 1; }
            } else {
                pureNodeType = upper;
            }
        }
        if ("MONSTER".equals(pureNodeType)) pureNodeType = "NORMAL";
        List<EnemyGroupTemplate> filteredGroups = new ArrayList<>();
        for (EnemyGroupTemplate g : groups) {
            if (pureNodeType != null && !g.getType().equalsIgnoreCase(pureNodeType)) continue;
            if (g.getMinAct() > currentAct || g.getMaxAct() < currentAct) continue;
            filteredGroups.add(g);
        }
        if (filteredGroups.isEmpty()) {
            // 保底：只按类型不过滤层数
            for (EnemyGroupTemplate g : groups) {
                if (pureNodeType != null && !g.getType().equalsIgnoreCase(pureNodeType)) continue;
                filteredGroups.add(g);
            }
        }
        if (filteredGroups.isEmpty()) {
            filteredGroups = groups;
        }
        EnemyGroupTemplate chosenGroup = filteredGroups.get(new Random().nextInt(filteredGroups.size()));

        this.enemies = new ArrayList<>();
        for (String eid : chosenGroup.getEnemies()) {
            EnemyTemplate tpl = dataRepo.getEnemyById(eid);
            if (tpl != null) {
                Enemy e = new Enemy(tpl, dataRepo);
                // 绑定塞牌回调
                e.setDrawer(cardType -> addStatusCardToDrawPile(cardType));
                enemies.add(e);
            }
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
        this.logList.add("━━━ 战斗开始 ━━━");
        log.info("New battle started: hp={}/{}, deck={}, relics={}, nodeType={}", playerHp, playerMaxHp, playerDeck.size(), playerRelics != null ? playerRelics.size() : 0, nodeType);
        this.energy = 0;
        this.gameOver = false;
        this.winner = null;
        this.attackCountThisTurn = 0;
        this.skillCountCombat = 0;
        this.hasDealtDamageThisTurn = false;
        this.exhaustedThisAction = false;
        this.blockPerAttackThisTurn = 0;
        this.hasDiscardedThisTurn = false;
        this.drawCountThisTurn = 0;
        this.buffedShivTemplate = null;
        this.shivBuffDamage = 0;
        this.playerExtraPoisonTick = false;
        this.poisonAllPerCardThisTurn = 0;
        this.poisonSkipFirstDraw = false;

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
            logList.add("🏮 灯笼/战场号角使初始能量 +" + val);
        }
        if (RelicEffectHandler.hasEffect(player, "ENERGY_PER_TURN")) {
            int val = RelicEffectHandler.getEffectValue(player, "ENERGY_PER_TURN");
            energy += val;
            logList.add("✳️ 能量方块使初始能量 +" + val);
        }
        // 🆕 音叉：每场战斗第一回合获得格挡
        if (RelicEffectHandler.hasEffect(player, "BLOCK_FIRST_TURN")) {
            int val = RelicEffectHandler.getEffectValue(player, "BLOCK_FIRST_TURN");
            player.gainBlock(val);
            logList.add("🔔 音叉触发，获得 " + val + " 点格挡");
        }
        // 🆕 佩尔之眼：第一回合开始给所有敌人施加虚弱
        if (RelicEffectHandler.hasEffect(player, "WEAKEN_FIRST_TURN")) {
            int weakCount = RelicEffectHandler.getEffectValue(player, "WEAKEN_FIRST_TURN");
            List<Enemy> alive = getAliveEnemies();
            for (Enemy e : alive) {
                StatusEffect weak = StatusFactory.create("WEAK", weakCount, dataRepo);
                if (weak != null) e.addStatus(weak);
            }
            logList.add("👁️ 佩尔之眼触发，所有敌人获得 " + weakCount + " 层虚弱");
        }
        // 🆕 佩尔之泪：第一回合开始给所有敌人施加易伤
        if (RelicEffectHandler.hasEffect(player, "VULNERABLE_FIRST_TURN")) {
            int vulnCount = RelicEffectHandler.getEffectValue(player, "VULNERABLE_FIRST_TURN");
            for (Enemy e : getAliveEnemies()) {
                StatusEffect vuln = StatusFactory.create("VULNERABLE", vulnCount, dataRepo);
                if (vuln != null) e.addStatus(vuln);
            }
            logList.add("💧 佩尔之泪触发，所有敌人获得 " + vulnCount + " 层易伤");
        }
        // 🆕 节日拉炮：第一回合开始对所有敌人造成伤害
        if (RelicEffectHandler.hasEffect(player, "AOE_FIRST_TURN")) {
            int dmg = RelicEffectHandler.getEffectValue(player, "AOE_FIRST_TURN");
            for (Enemy e : getAliveEnemies()) {
                int actual = e.takeDamage(dmg, player);
                logList.addAll(e.getLastCombatLogs());
                logList.add("🧨 节日拉炮对 " + e.getEnemyName() + " 造成 " + actual + " 点伤害");
            }
        }

        int drawCount = Math.max(0, 5 - innateCards.size());
        if (RelicEffectHandler.hasEffect(player, "FIRST_DRAW_BONUS")) drawCount++;
        if (RelicEffectHandler.hasEffect(player, "DRAW_PER_TURN")) drawCount++;
        drawCards(drawCount);

        // 🆕 Boss 遗物：混沌之核 — 战斗开始获得力量
        int strengthVal = RelicEffectHandler.getEffectValue(player, "STRENGTH_START_BATTLE");
        if (strengthVal > 0) {
            StatusEffect strength = StatusFactory.create("STRENGTH", strengthVal, dataRepo);
            if (strength != null) {
                player.addStatus(strength);
                logList.add("🌀 混沌之核触发，获得 " + strengthVal + " 层力量");
            }
        }

        // 🆕 Boss 遗物：暗影斗篷 — 战斗开始获得无实体
        int intangibleVal = RelicEffectHandler.getEffectValue(player, "INTANGIBLE_START");
        if (intangibleVal > 0) {
            StatusEffect intangible = StatusFactory.create("INTANGIBLE", intangibleVal, dataRepo);
            if (intangible != null) {
                player.addStatus(intangible);
                logList.add("🌑 暗影斗篷触发，获得 " + intangibleVal + " 层无实体");
            }
        }

        return getCurrentState();
    }

    public synchronized Map<String, Object> playCard(int index, Integer targetIndex, Integer exhaustHandIndex, Integer discardHandIndex) {
        return playCard(index, targetIndex, exhaustHandIndex, discardHandIndex, null, null);
    }
    public synchronized Map<String, Object> playCard(int index, Integer targetIndex, Integer exhaustHandIndex, Integer discardHandIndex, Integer upgradeHandIndex) {
        return playCard(index, targetIndex, exhaustHandIndex, discardHandIndex, upgradeHandIndex, null);
    }
    public synchronized Map<String, Object> playCard(int index, Integer targetIndex, Integer exhaustHandIndex, Integer discardHandIndex, Integer upgradeHandIndex, List<Integer> discardHandIndices) {
        validateBattleActive();
        validateCardIndex(index);

        Card card = hand.get(index);

        if (card.isUnplayable()) throw new IllegalStateException("此牌无法被主动打出！");
        if (card.isRequiresEmptyDrawPile() && !drawPile.isEmpty()) throw new IllegalStateException("抽牌堆不为空，无法打出此牌！");
        if (card.isXCost() && energy <= 0) throw new IllegalStateException("X耗能卡牌需要至少1点能量才能打出！");
        if (!card.isXCost() && card.getCost() > energy) throw new IllegalStateException("能量不足");

        exhaustedThisAction = false;
        logList.add("━━━ 🃏 使用: " + card.getName() + " ━━━");

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

        // 🆕 熔火之怒：每打出一张牌，随机敌人受2点伤害
        if (RelicEffectHandler.hasEffect(player, "MOLTEN_FURY")) {
            List<Enemy> alive = getAliveEnemies();
            if (!alive.isEmpty()) {
                Enemy randomTarget = alive.get(new Random().nextInt(alive.size()));
                int dmg = randomTarget.takeDamage(2, player);
                logList.addAll(randomTarget.getLastCombatLogs());
                logList.add("🔥 熔火之怒对 " + randomTarget.getEnemyName() + " 造成 " + dmg + " 点伤害");
            }
        }

        // 🆕 地精大块头被动：玩家打出技能牌时，激怒敌人
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
        // 🆕 狂怒：每打出一张攻击牌获得格挡
        if (card.getType() == Card.CardType.ATTACK && blockPerAttackThisTurn > 0) {
            player.gainBlock(blockPerAttackThisTurn);
            logList.add("💢 狂怒！获得 " + blockPerAttackThisTurn + " 点格挡（当前: " + player.getBlock() + "）");
        }

        // 🆕 黄金戒指：每打出3张攻击牌获得1层敏捷（跨回合累计）
        if (card.getType() == Card.CardType.ATTACK) {
            attackCountThisTurn++;
            if (attackCountThisTurn % 3 == 0 && RelicEffectHandler.hasEffect(player, "ATTACK_DEXTERITY")) {
                StatusEffect dex = StatusFactory.create("DEXTERITY", RelicEffectHandler.getEffectValue(player, "ATTACK_DEXTERITY"), dataRepo);
                if (dex != null) {
                    player.addStatus(dex);
                    logList.add("💍 黄金戒指触发，获得 " + dex.getCount() + " 层敏捷（已打出 " + attackCountThisTurn + " 张攻击牌）");
                }
            }
        }
        // 🆕 黄金项链：每打出3张技能牌获得1层力量（跨回合累计）
        if (card.getType() == Card.CardType.SKILL) {
            skillCountCombat++;
            if (skillCountCombat % 3 == 0 && RelicEffectHandler.hasEffect(player, "SKILL_STRENGTH")) {
                StatusEffect str = StatusFactory.create("STRENGTH", RelicEffectHandler.getEffectValue(player, "SKILL_STRENGTH"), dataRepo);
                if (str != null) {
                    player.addStatus(str);
                    logList.add("📿 黄金项链触发，获得 " + str.getCount() + " 层力量（已打出 " + skillCountCombat + " 张技能牌）");
                }
            }
        }

        if (card.getBlock() > 0) {
            int actualBlock = card.getBlock() * xValue;
            player.gainBlock(actualBlock);
            logList.add(String.format("🛡️ 获得 %d 点格挡，当前格挡: %d", actualBlock, player.getBlock()));
        }

        // 🆕 狂怒效果：本回合每打出一张攻击牌获得格挡
        if (card.getBlockPerAttack() > 0) {
            blockPerAttackThisTurn = card.getBlockPerAttack();
            logList.add("💢 进入狂怒状态！本回合每打出攻击牌获得 " + blockPerAttackThisTurn + " 点格挡");
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

        // 🆕 隐秘打击：本回合丢弃过牌获得能量
        if (card.getEnergyGainIfDiscarded() > 0 && hasDiscardedThisTurn) {
            energy += card.getEnergyGainIfDiscarded();
            logList.add(String.format("⚡ 隐秘打击！获得 %d 点能量，当前: %d", card.getEnergyGainIfDiscarded(), energy));
        }

        // 🆕 腐蚀波：本回合之后每抽1张牌，给所有敌人施加毒
        if (card.getPoisonAllPerCard() > 0) {
            poisonAllPerCardThisTurn = card.getPoisonAllPerCard();
            poisonSkipFirstDraw = true; // 跳过本牌的抽牌
            logList.add("☠️ 腐蚀波！本回合之后每抽1张牌给所有敌人施加 " + poisonAllPerCardThisTurn + " 层毒");
        }

        // ================= 全身撞击：伤害=当前格挡 =================
        int actualCardDamage = card.getDamage();
        if (card.isBlockToDamage()) {
            actualCardDamage = player.getBlock();
            logList.add(String.format("💥 全身撞击！当前格挡 %d 转化为伤害", actualCardDamage));
        }

        // ================= 伤害计算 =================
        if (actualCardDamage > 0) {
            // 🆕 X耗费卡牌：每点能量算一段，与 multiHitCount 叠加
            int hitCount = Math.max(1, card.getMultiHitCount()) * xValue;
            String hitDesc = xValue > 1 ? ("X耗费×" + xValue + "段") : "";

            // 力量加成（每次命中都会加成，在循环内处理）
            int strengthMultiplier = card.getStrengthMultiplier();
            int strengthCount = 0;
            StatusEffect savedStrength = null;
            if (strengthMultiplier > 1) {
                for (StatusEffect s : player.getStatuses()) {
                    if ("STRENGTH".equals(s.getId())) {
                        strengthCount = s.getCount();
                        savedStrength = s;
                        break;
                    }
                }
                if (strengthCount > 0) {
                    player.getStatuses().remove(savedStrength);
                    logList.add("💪 力量发挥 " + strengthMultiplier + " 倍效果，锁定力量值: " + strengthCount);
                }
            }
            // 全身撞击的力量加成（每段1x）
            int blockDamageStrength = 0;
            if (card.isBlockToDamage()) {
                for (StatusEffect s : player.getStatuses()) {
                    if ("STRENGTH".equals(s.getId()) && s.getCount() > 0) {
                        blockDamageStrength = s.getCount();
                        break;
                    }
                }
            }

            if (card.isAoe()) {
                for (Enemy e : aliveEnemies) {
                    for (int seg = 0; seg < hitCount; seg++) {
                        int hitDmg = actualCardDamage + blockDamageStrength;
                        if (seg == 0 && strengthMultiplier > 1) hitDmg += strengthCount * strengthMultiplier;
                        int actualDmg = e.takeDamage(hitDmg, player);
                        logList.addAll(e.getLastCombatLogs());
                        if (hitCount > 1) {
                            logList.add(String.format("💥 AOE第%d段对 %s 造成 %d 点伤害，HP: %d", seg + 1, e.getEnemyName(), actualDmg, e.getHp()));
                        } else {
                            logList.add(String.format("💥 AOE对 %s 造成 %d 点伤害，HP: %d", e.getEnemyName(), actualDmg, e.getHp()));
                        }
                        if (actualDmg > 0) triggerManaFlower();
                        if (!e.isAlive()) { triggerGoblinHorn(); break; }
                    }
                }
            } else {
                Random random = new Random();
                List<Enemy> currentAlive = new ArrayList<>(aliveEnemies);
                for (int i = 0; i < hitCount; i++) {
                    if (currentAlive.isEmpty()) {
                        currentAlive = new ArrayList<>(getAliveEnemies());
                        if (currentAlive.isEmpty()) break;
                    }
                    Enemy currentTarget;
                    if (card.isRandomTarget()) {
                        currentTarget = currentAlive.get(random.nextInt(currentAlive.size()));
                    } else {
                        if (!target.isAlive()) {
                            currentAlive = new ArrayList<>(getAliveEnemies());
                            if (currentAlive.isEmpty()) break;
                            target = currentAlive.get(0);
                        }
                        currentTarget = target;
                    }
                    int hitDmg = actualCardDamage + blockDamageStrength;
                    if (strengthMultiplier > 1) hitDmg += strengthCount * strengthMultiplier;
                    int actualDmg = currentTarget.takeDamage(hitDmg, player);
                    logList.addAll(currentTarget.getLastCombatLogs());
                    if (hitCount > 1) {
                        logList.add(String.format("对 %s 造成 %d 点伤害 (%d/%d段)%s，HP: %d", currentTarget.getEnemyName(), actualDmg, i + 1, hitCount, hitDesc, currentTarget.getHp()));
                    } else {
                        logList.add(String.format("对 %s 造成 %d 点伤害，HP: %d", currentTarget.getEnemyName(), actualDmg, currentTarget.getHp()));
                    }
                    if (!currentTarget.isAlive()) {
                        currentAlive.remove(currentTarget);
                    }
                    if (actualDmg > 0) triggerManaFlower();
                    if (!currentTarget.isAlive()) triggerGoblinHorn();
                }
            }

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
                        for (Enemy e : aliveEnemies) {
                            StatusEffect s = StatusFactory.create(effect.getType(), effect.getCount() * xValue, dataRepo);
                            if (s != null) e.addStatus(s);
                        }
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

        // ================= 硬撑：增加伤口到手牌 =================
        if (card.getAddWoundCount() > 0) {
            for (int i = 0; i < card.getAddWoundCount(); i++) {
                Card wound = new Card("伤口", 1, 0, 0, Card.CardType.STATUS);
                wound.setUnplayable(true);
                wound.setEthereal(true);
                wound.setRarity("COMMON");
                hand.add(playedCardIndex + 1 + i, wound);
            }
            logList.add(String.format("🩹 增加了 %d 张伤口到手牌", card.getAddWoundCount()));
        }

        // 🆕 武装/武装+：临时升级手牌（仅本场战斗有效）
        if (card.isUpgradeAllInHand()) {
            int upgraded = 0;
            for (int i = 0; i < hand.size(); i++) {
                if (i == playedCardIndex || hand.get(i).isUpgraded()) continue;
                Card old = hand.get(i);
                CardTemplate tpl = dataRepo.getCardById(old.getName().replaceAll("\\+\\d*$", "") + "+");
                if (tpl != null) { hand.set(i, new Card(tpl)); upgraded++; }
            }
            if (upgraded > 0) logList.add("🔨 武装+！升级了手中 " + upgraded + " 张牌");
        } else if (card.getUpgradeHandCount() > 0) {
            int count = Math.min(card.getUpgradeHandCount(), hand.size() - 1);
            for (int i = 0; i < count; i++) {
                int ti = resolveHandInteractionIndex(i, upgradeHandIndex, playedCardIndex, hand.size(), "升级");
                Card old = hand.get(ti);
                if (!old.isUpgraded()) {
                    CardTemplate tpl = dataRepo.getCardById(old.getName().replaceAll("\\+\\d*$", "") + "+");
                    if (tpl != null) {
                        hand.set(ti, new Card(tpl));
                        logList.add("🔨 武装！" + old.getName() + " → " + hand.get(ti).getName());
                    }
                }
            }
        }

        // 🆕 通用：增加指定卡牌到手牌（如刀刃之舞、斗篷与匕首）
        if (card.getAddCardId() != null && !card.getAddCardId().isEmpty() && card.getAddCardCount() > 0) {
            for (int i = 0; i < card.getAddCardCount(); i++) {
                Card gen = createCardFromId(card.getAddCardId());
                if (gen != null) {
                    hand.add(playedCardIndex + 1 + i, gen);
                }
            }
            logList.add(String.format("🃏 增加了 %d 张%s到手牌", card.getAddCardCount(),
                dataRepo.getCardById(card.getAddCardId()) != null ? dataRepo.getCardById(card.getAddCardId()).getName() : card.getAddCardId()));
        }

        // 🆕 精准：本场战斗小刀伤害增加
        if (card.getBuffCardName() != null && !card.getBuffCardName().isEmpty() && card.getBuffDamageAmount() > 0) {
            if (buffedShivTemplate == null) {
                buffedShivTemplate = dataRepo.getCardById(card.getBuffCardName());
            }
            if (buffedShivTemplate != null) {
                // store the buff amount for future shiv creations
                shivBuffDamage += card.getBuffDamageAmount();
                logList.add("🎯 精准！小刀伤害增加 " + card.getBuffDamageAmount() + "（当前加成: " + shivBuffDamage + "）");
            }
        }

        // 🆕 催化剂：将目标敌人中毒翻倍
        if (card.isDoublePoison() && target != null && target.isAlive()) {
            for (StatusEffect s : target.getStatuses()) {
                if ("POISON".equals(s.getId())) {
                    int oldCount = s.getCount();
                    s.setCount(oldCount * 2);
                    logList.add("☠️ 催化剂！中毒 " + oldCount + " → " + s.getCount());
                    break;
                }
            }
        }

        // 🆕 触媒：回合结束中毒额外结算1次
        if (card.isExtraPoisonTick()) {
            playerExtraPoisonTick = true;
            logList.add("⚗️ 触媒：本场战斗回合结束时中毒额外结算1次");
        }

        // ================= 重振精神：消耗手牌中所有非攻击牌，每张获得格挡 =================
        if (card.getExhaustNonAttackBlock() > 0) {
            int exhaustedCount = 0;
            for (int i = hand.size() - 1; i >= 0; i--) {
                if (i == playedCardIndex) continue;
                Card hc = hand.get(i);
                if (hc.getType() != Card.CardType.ATTACK) {
                    hand.remove(i);
                    exhaustPile.add(hc);
                    exhaustedCount++;
                    if (i < playedCardIndex) playedCardIndex--;
                    logList.add("🔥 重振精神消耗了 " + hc.getName());
                    triggerDrawOnExhaust();
                }
            }
            if (exhaustedCount > 0) {
                int bonusBlock = card.getExhaustNonAttackBlock() * exhaustedCount;
                player.gainBlock(bonusBlock);
                logList.add(String.format("🛡️ 重振精神消耗 %d 张牌，额外获得 %d 格挡（当前: %d）", exhaustedCount, bonusBlock, player.getBlock()));
            }
        }

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
            List<Integer> usedIndices = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                // 支持多选：传入的 discardHandIndex 是一个索引，后续用列表跟踪已用过的索引
                Integer requestedIdx = null;
                if (discardHandIndices != null && i < discardHandIndices.size()) {
                    requestedIdx = discardHandIndices.get(i);
                } else if (i == 0 && discardHandIndex != null) {
                    requestedIdx = discardHandIndex;
                }
                int targetIdx = resolveHandInteractionIndex(i, requestedIdx, playedCardIndex, hand.size(), "丢弃");
                usedIndices.add(targetIdx);
                Card discarded = hand.remove(targetIdx);
                discardPile.add(discarded);
                if (targetIdx < playedCardIndex) {
                    playedCardIndex--;
                }
            }
            hasDiscardedThisTurn = true;
        }

        // 🆕 钢铁风暴：丢弃所有手牌，获得对应数量的小刀
        if (card.getDiscardAllForCards() != null && !card.getDiscardAllForCards().isEmpty()) {
            int discarded = 0;
            while (hand.size() > 1) {
                for (int i = 0; i < hand.size(); i++) {
                    if (i != playedCardIndex) {
                        discardPile.add(hand.remove(i));
                        if (i < playedCardIndex) playedCardIndex--;
                        discarded++;
                        break;
                    }
                }
            }
            int created = 0;
            for (int i = 0; i < discarded; i++) {
                Card shiv = createCardFromId(card.getDiscardAllForCards());
                if (shiv != null) { hand.add(playedCardIndex + 1 + i, shiv); created++; }
            }
            if (created > 0) logList.add("🔪 钢铁风暴：丢弃所有手牌，获得 " + created + " 张小刀");
            hasDiscardedThisTurn = true;
        }

        // 🆕 计算下注：丢弃所有手牌，抽对应数量
        if (card.isDiscardAllForDraw()) {
            int discarded = 0;
            while (hand.size() > 1) {
                for (int i = 0; i < hand.size(); i++) {
                    if (i != playedCardIndex) {
                        discardPile.add(hand.remove(i));
                        if (i < playedCardIndex) playedCardIndex--;
                        discarded++;
                        break;
                    }
                }
            }
            if (discarded > 0) {
                drawCards(discarded);
                logList.add("🎰 计算下注：丢弃 " + discarded + " 张，抽 " + discarded + " 张");
            }
            hasDiscardedThisTurn = true;
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
        exhaustedThisAction = false;
        logList.add("━━━ 结束回合 ━━━");
        validateBattleActive();

        player.onTurnEnd();
        logList.addAll(player.getLastTurnEndLogs());
        logList.addAll(player.getLastCombatLogs());

        for (Enemy e : enemies) {
            decrementIntangible(e);
        }

        // 🆕 【新增逻辑】处理手牌中的“灼烧”等回合结束伤害效果
        for (Card card : hand) {
            int endTurnDmg = card.getEndOfTurnDamage();
            if (endTurnDmg > 0) {
                // 灼烧通常是真实伤害，无视格挡，所以第三个参数 ignoreBlock 传 true
                player.takeDamage(endTurnDmg, null, true); 
                logList.addAll(player.getLastCombatLogs());
                logList.add(String.format("🔥 受到【%s】的灼烧，失去 %d 点生命，当前 HP: %d", card.getName(), endTurnDmg, player.getHp()));
                
                if (!player.isAlive()) {
                    gameOver = true;
                    winner = "灼烧";
                    logList.add("💀 玩家因灼烧倒下...");
                    return getCurrentState();
                }
            }
        }

        boolean hasRunePyramid = RelicEffectHandler.hasEffect(player, "RUNE_PYRAMID");
        List<Card> retained = new ArrayList<>();
        // ⚠️ 遍历 hand 副本，防止 triggerDrawOnExhaust->drawCards->hand.add() 导致 ConcurrentModificationException
        for (Card card : new ArrayList<>(hand)) {
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

            // 🆕 BUFF/DEBUFF意图处理
            IntentTemplate intent = enemy.getCurrentIntentTemplate();
            if (intent != null) {
                // 🆕 召唤匕首：如果意图是召唤，生成新匕首加入战斗
                if (intent.getSummonCount() > 0) {
                    EnemyTemplate daggerTpl = dataRepo.getEnemyById("dagger");
                    if (daggerTpl != null) {
                        for (int i = 0; i < intent.getSummonCount(); i++) {
                            Enemy newDagger = new Enemy(daggerTpl, dataRepo);
                            newDagger.setDrawer(cardType -> addStatusCardToDrawPile(cardType));
                            enemies.add(newDagger);
                            logList.add("🗡️ " + enemy.getEnemyName() + "召唤了一把匕首！");
                        }
                    }
                }
                if (intent.getHealAmount() > 0) {
                    // 🆕 群体治疗（神秘术士的治愈）vs 单体治疗（虱虫的吮吸）
                    if (intent.getType() == IntentType.BUFF) {
                        // BUFF类型治疗 = 群体治疗
                        for (Enemy ally : enemies) {
                            if (ally.isAlive()) ally.heal(intent.getHealAmount());
                        }
                        logList.add("💚 " + enemy.getEnemyName() + "为所有敌人恢复了 " + intent.getHealAmount() + " 点生命");
                    } else {
                        // 非BUFF类型治疗 = 单体治疗（吮吸等，已在Enemy.java中处理）
                    }
                }
                if (intent.getApplyStatusType() != null) {
                    StatusEffect status = StatusFactory.create(intent.getApplyStatusType(), intent.getApplyStatusCount(), dataRepo);
                    if (status != null) {
                        if ("ALL_ENEMY".equals(intent.getApplyStatusTarget())) {
                            // 给所有敌方加buff（甜圈的力量之环、神秘术士的治愈）
                            for (Enemy ally : enemies) {
                                ally.addStatus(StatusFactory.create(intent.getApplyStatusType(), intent.getApplyStatusCount(), dataRepo));
                            }
                            logList.add("✨ " + enemy.getEnemyName() + "为所有敌人施加了 " + intent.getApplyStatusCount() + " 层" + status.getName());
                        } else if ("SELF".equals(intent.getApplyStatusTarget())) {
                            // 只给自己加buff
                            enemy.addStatus(StatusFactory.create(intent.getApplyStatusType(), intent.getApplyStatusCount(), dataRepo));
                            logList.add("💪 " + enemy.getEnemyName() + "给自己施加了 " + intent.getApplyStatusCount() + " 层" + status.getName());
                        } else {
                            // PLAYER或其他默认→给玩家施加（debuff）
                            player.addStatus(StatusFactory.create(intent.getApplyStatusType(), intent.getApplyStatusCount(), dataRepo));
                            logList.add("☠️ " + enemy.getEnemyName() + "对玩家施加了 " + intent.getApplyStatusCount() + " 层" + status.getName());
                        }
                    }
                }
            }

            enemy.onTurnEnd();
            logList.addAll(enemy.getLastTurnEndLogs());
            logList.addAll(enemy.getLastCombatLogs());

            if (!enemy.isAlive()) logList.add(String.format("💀 %s 被击败！", enemy.getEnemyName()));
        }
        removeDeadEnemies();

        // 🆕 【反甲反死修复】如果所有敌人在敌人回合被反伤/荆棘等击杀，立即结束战斗
        if (getAliveEnemies().isEmpty()) {
            gameOver = true;
            winner = "玩家";
            logList.add("🎉 所有敌人被击败！");
            return getCurrentState();
        }

        // ================= 3. 敌人回合结束 =================
        // 🆕 【关键修复】所有敌人行动完毕后，衰减玩家身上的无实体（持续了一整回合，现在消失）
        decrementIntangible(player);

        // ================= 4. 玩家新回合开始 =================
        hasDealtDamageThisTurn = false;
        player.onTurnStart();
        logList.addAll(player.getLastTurnStartLogs());
        if (!player.isAlive()) { gameOver = true; winner = "敌人"; logList.add("💀 玩家倒下..."); return getCurrentState(); }

        energy = 3;
        blockPerAttackThisTurn = 0;
        hasDiscardedThisTurn = false;
        drawCountThisTurn = 0;
        poisonAllPerCardThisTurn = 0;
        poisonSkipFirstDraw = false;
        if (RelicEffectHandler.hasEffect(player, "ENERGY_PER_TURN")) {
            energy += RelicEffectHandler.getEffectValue(player, "ENERGY_PER_TURN");
        }
        int turnDrawCount = 5 - retained.size();
        if (RelicEffectHandler.hasEffect(player, "FIRST_DRAW_BONUS")) turnDrawCount++;
        if (RelicEffectHandler.hasEffect(player, "DRAW_PER_TURN")) turnDrawCount++;
        drawCards(Math.max(0, turnDrawCount));

        // 🆕 触媒：回合结束时中毒额外结算一次
        if (playerExtraPoisonTick) {
            for (Enemy e : enemies) {
                if (!e.isAlive()) continue;
                for (StatusEffect s : new ArrayList<>(e.getStatuses())) {
                    if ("POISON".equals(s.getId())) {
                        int poisonDmg = s.getCount();
                        e.takeDamage(poisonDmg, null, true);
                        logList.add("⚗️ 触媒额外结算！" + e.getEnemyName() + " 毒发 " + poisonDmg + " 点伤害");
                        log.debug("Extra poison tick on {}: {} damage", e.getEnemyName(), poisonDmg);
                        break;
                    }
                }
            }
        }

        for (Enemy enemy : enemies) enemy.advanceIntent();

        // 🆕 战斗关键事件日志
        if (gameOver) log.info("战斗结束，胜利者: {}", winner);
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
                if (discardPile.isEmpty()) { logList.add("⚠️ 抽牌堆和弃牌堆均为空，无法抽牌"); break; }
                drawPile.addAll(discardPile); discardPile.clear(); Collections.shuffle(drawPile);
                logList.add("🔄 弃牌堆洗入抽牌堆");
            }
            
            Card drawnCard = drawPile.remove(0);
            hand.add(drawnCard); 
            drawn++;
            
            // 🆕 【新增逻辑】处理“虚空”等抽牌时失去能量的效果
            int energyLoss = drawnCard.getEnergyLossOnDraw();
            if (energyLoss > 0) {
                energy = Math.max(0, energy - energyLoss); // 扣除能量，最低为0
                logList.add(String.format("🌑 抽到了【%s】，失去了 %d 点能量，当前能量: %d", drawnCard.getName(), energyLoss, energy));
            }
        }
        if (drawn > 0) {
            logList.add(String.format("抽了 %d 张牌（手牌 %d/%d）", drawn, hand.size(), HAND_LIMIT));
            drawCountThisTurn += drawn;
            // 🆕 腐蚀波：抽牌时施加毒
            if (poisonAllPerCardThisTurn > 0) {
                if (poisonSkipFirstDraw) {
                    poisonSkipFirstDraw = false;
                } else {
                    int totalPoison = poisonAllPerCardThisTurn * drawn;
                    List<Enemy> alive = getAliveEnemies();
                    for (Enemy e : alive) {
                        StatusEffect ps = StatusFactory.create("POISON", totalPoison, dataRepo);
                        if (ps != null) e.addStatus(ps);
                    }
                    logList.add("☠️ 腐蚀波！抽了 " + drawn + " 张，所有敌人 +" + totalPoison + " 毒");
                    log.debug("Corrosive wave: {} enemies got {} poison from {} draws", alive.size(), totalPoison, drawn);
                }
            }
        }
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
                int cost = cardData.containsKey("cost") && cardData.get("cost") != null
                        ? ((Number) cardData.get("cost")).intValue() : 1;
                int damage = cardData.get("damage") != null ? ((Number) cardData.get("damage")).intValue() : 0;
                int block = cardData.get("block") != null ? ((Number) cardData.get("block")).intValue() : 0;
                Card.CardType type = Card.CardType.ATTACK;
                if (cardData.containsKey("type") && cardData.get("type") != null) {
                    try { type = Card.CardType.valueOf((String) cardData.get("type")); }
                    catch (IllegalArgumentException ignored) {}
                }
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

            if (cardData.containsKey("copyToDiscard")) {
                card.setCopyToDiscard((Boolean) cardData.get("copyToDiscard"));
            }

            if (cardData.containsKey("randomTarget")) {
                card.setRandomTarget((Boolean) cardData.get("randomTarget"));
            }

            // 🆕 【新增】读取特殊状态牌字段
            if (cardData.containsKey("endOfTurnDamage") && cardData.get("endOfTurnDamage") != null) {
                card.setEndOfTurnDamage(((Number) cardData.get("endOfTurnDamage")).intValue());
            }
            if (cardData.containsKey("energyLossOnDraw") && cardData.get("energyLossOnDraw") != null) {
                card.setEnergyLossOnDraw(((Number) cardData.get("energyLossOnDraw")).intValue());
            }
            if (cardData.containsKey("exhaustNonAttackBlock") && cardData.get("exhaustNonAttackBlock") != null) {
                card.setExhaustNonAttackBlock(((Number) cardData.get("exhaustNonAttackBlock")).intValue());
            }
            if (cardData.containsKey("addWoundCount") && cardData.get("addWoundCount") != null) {
                card.setAddWoundCount(((Number) cardData.get("addWoundCount")).intValue());
            }
            if (cardData.containsKey("blockToDamage") && cardData.get("blockToDamage") != null) {
                card.setBlockToDamage((Boolean) cardData.get("blockToDamage"));
            }
            if (cardData.containsKey("blockPerAttack") && cardData.get("blockPerAttack") != null) {
                card.setBlockPerAttack(((Number) cardData.get("blockPerAttack")).intValue());
            }
            if (cardData.containsKey("energyGainIfDiscarded")) card.setEnergyGainIfDiscarded(((Number) cardData.get("energyGainIfDiscarded")).intValue());
            if (cardData.containsKey("discardAllForCards")) card.setDiscardAllForCards((String) cardData.get("discardAllForCards"));
            if (cardData.containsKey("discardAllForDraw")) card.setDiscardAllForDraw((Boolean) cardData.get("discardAllForDraw"));
            if (cardData.containsKey("buffCardName")) card.setBuffCardName((String) cardData.get("buffCardName"));
            if (cardData.containsKey("buffDamageAmount")) card.setBuffDamageAmount(((Number) cardData.get("buffDamageAmount")).intValue());
            if (cardData.containsKey("doublePoison")) card.setDoublePoison((Boolean) cardData.get("doublePoison"));
            if (cardData.containsKey("poisonAllPerCard")) card.setPoisonAllPerCard(((Number) cardData.get("poisonAllPerCard")).intValue());
            if (cardData.containsKey("extraPoisonTick")) card.setExtraPoisonTick((Boolean) cardData.get("extraPoisonTick"));
            if (cardData.containsKey("addCardId")) card.setAddCardId((String) cardData.get("addCardId"));
            if (cardData.containsKey("addCardCount")) card.setAddCardCount(((Number) cardData.get("addCardCount")).intValue());
            if (cardData.containsKey("upgradeHandCount")) card.setUpgradeHandCount(((Number) cardData.get("upgradeHandCount")).intValue());
            if (cardData.containsKey("upgradeHandMode")) card.setUpgradeHandMode((String) cardData.get("upgradeHandMode"));
            if (cardData.containsKey("upgradeAllInHand")) card.setUpgradeAllInHand((Boolean) cardData.get("upgradeAllInHand"));
            if (cardData.containsKey("requiresEmptyDrawPile")) card.setRequiresEmptyDrawPile((Boolean) cardData.get("requiresEmptyDrawPile"));

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
        state.put("drawCountThisTurn", drawCountThisTurn);

        List<Map<String, Object>> handCards = new ArrayList<>();
        for (int i = 0; i < hand.size(); i++) {
            Card c = hand.get(i);
            Map<String, Object> cardInfo = new LinkedHashMap<>();
            cardInfo.put("index", i); cardInfo.put("name", c.getName()); cardInfo.put("cost", c.getCost());
            cardInfo.put("damage", c.getDamage()); cardInfo.put("block", c.getBlock()); cardInfo.put("type", c.getType().name());
            cardInfo.put("applyStatusType", c.getApplyStatusType()); cardInfo.put("applyStatusCount", c.getApplyStatusCount()); cardInfo.put("applyStatusTarget", c.getApplyStatusTarget());
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
            cardInfo.put("upgraded", c.isUpgraded());

            cardInfo.put("copyToDiscard", c.isCopyToDiscard());
            cardInfo.put("strengthMultiplier", c.getStrengthMultiplier());
            cardInfo.put("randomTarget", c.isRandomTarget());

            // 🆕 传递特殊状态牌数值给前端
            cardInfo.put("endOfTurnDamage", c.getEndOfTurnDamage());
            cardInfo.put("energyLossOnDraw", c.getEnergyLossOnDraw());
            cardInfo.put("exhaustNonAttackBlock", c.getExhaustNonAttackBlock());
            cardInfo.put("addWoundCount", c.getAddWoundCount());
            cardInfo.put("blockToDamage", c.isBlockToDamage());
            cardInfo.put("blockPerAttack", c.getBlockPerAttack());
            cardInfo.put("energyGainIfDiscarded", c.getEnergyGainIfDiscarded());
            cardInfo.put("discardAllForCards", c.getDiscardAllForCards());
            cardInfo.put("discardAllForDraw", c.isDiscardAllForDraw());
            cardInfo.put("buffCardName", c.getBuffCardName());
            cardInfo.put("buffDamageAmount", c.getBuffDamageAmount());
            cardInfo.put("doublePoison", c.isDoublePoison());
            cardInfo.put("poisonAllPerCard", c.getPoisonAllPerCard());
            cardInfo.put("extraPoisonTick", c.isExtraPoisonTick());
            cardInfo.put("addCardId", c.getAddCardId());
            cardInfo.put("addCardCount", c.getAddCardCount());
            cardInfo.put("upgradeHandCount", c.getUpgradeHandCount());
            cardInfo.put("upgradeHandMode", c.getUpgradeHandMode());
            cardInfo.put("upgradeAllInHand", c.isUpgradeAllInHand());
            cardInfo.put("requiresEmptyDrawPile", c.isRequiresEmptyDrawPile());

            handCards.add(cardInfo);
        }
        state.put("hand", handCards);
        state.put("drawPile", cardsToStateList(drawPile));
        state.put("discardPile", cardsToStateList(discardPile));
        state.put("exhaustPile", cardsToStateList(exhaustPile));
        state.put("exhaustHappened", exhaustedThisAction); // 🆕 安东尼之怒
        state.put("log", new ArrayList<>(logList)); state.put("gameOver", gameOver); state.put("winner", winner);
        return state;
    }

    /** 🆕 魔力花：每回合首次造成伤害时获得能量 */
    private void triggerManaFlower() {
        if (!hasDealtDamageThisTurn && RelicEffectHandler.hasEffect(player, "FIRST_HIT_ENERGY")) {
            hasDealtDamageThisTurn = true;
            int val = RelicEffectHandler.getEffectValue(player, "FIRST_HIT_ENERGY");
            energy += val;
            logList.add("🌸 魔力花触发，获得 " + val + " 点能量（当前能量: " + energy + "）");
        }
    }

    /** 🆕 地精之角：击杀敌人时获得能量+抽牌+力量+敏捷 */
    private void triggerGoblinHorn() {
        if (!RelicEffectHandler.hasEffect(player, "GOBLIN_HORN")) return;
        energy += 1;
        drawCards(2);
        StatusEffect str = StatusFactory.create("STRENGTH", 1, dataRepo);
        if (str != null) player.addStatus(str);
        StatusEffect dex = StatusFactory.create("DEXTERITY", 1, dataRepo);
        if (dex != null) player.addStatus(dex);
        logList.add("🦴 地精之角触发！获得1能量、抽2牌、+1力量、+1敏捷");
    }

    private void triggerDrawOnExhaust() {
        exhaustedThisAction = true; // 🆕 安东尼之怒标记
        if (RelicEffectHandler.hasEffect(player, "DRAW_ON_EXHAUST")) {
            drawCards(1);
            logList.add("📄 金纸触发，抽了一张牌");
        }
    }

    private void validateBattleActive() { if (gameOver) throw new IllegalStateException("战斗已经结束"); }
    private void validateCardIndex(int index) { if (index < 0 || index >= hand.size()) throw new IllegalArgumentException("无效的卡牌编号"); }

    private Card createCardFromId(String cardId) {
        CardTemplate tpl = dataRepo.getCardById(cardId);
        if (tpl == null) return null;
        Card c = new Card(tpl);
        if (shivBuffDamage > 0 && "shiv".equals(cardId)) {
            c.setDamage(c.getDamage() + shivBuffDamage);
        }
        return c;
    }

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

            info.put("copyToDiscard", c.isCopyToDiscard());
            info.put("strengthMultiplier", c.getStrengthMultiplier());
            info.put("randomTarget", c.isRandomTarget());

            // 🆕 传递特殊状态牌数值给前端
            info.put("endOfTurnDamage", c.getEndOfTurnDamage());
            info.put("energyLossOnDraw", c.getEnergyLossOnDraw());
            info.put("exhaustNonAttackBlock", c.getExhaustNonAttackBlock());
            info.put("addWoundCount", c.getAddWoundCount());
            info.put("blockToDamage", c.isBlockToDamage());
            info.put("blockPerAttack", c.getBlockPerAttack());
            info.put("energyGainIfDiscarded", c.getEnergyGainIfDiscarded());
            info.put("discardAllForCards", c.getDiscardAllForCards());
            info.put("discardAllForDraw", c.isDiscardAllForDraw());
            info.put("buffCardName", c.getBuffCardName());
            info.put("buffDamageAmount", c.getBuffDamageAmount());
            info.put("doublePoison", c.isDoublePoison());
            info.put("poisonAllPerCard", c.getPoisonAllPerCard());
            info.put("extraPoisonTick", c.isExtraPoisonTick());
            info.put("addCardId", c.getAddCardId());
            info.put("addCardCount", c.getAddCardCount());
            info.put("upgradeHandCount", c.getUpgradeHandCount());
            info.put("upgradeHandMode", c.getUpgradeHandMode());
            info.put("upgradeAllInHand", c.isUpgradeAllInHand());
            info.put("requiresEmptyDrawPile", c.isRequiresEmptyDrawPile());

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