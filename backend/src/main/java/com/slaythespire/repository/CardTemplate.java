package com.slaythespire.repository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.slaythespire.game.model.Card.CardType;
import com.slaythespire.game.model.CardEffect;

import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CardTemplate {

    private final String id;
    private final String name;
    private final int cost;
    private final int damage;
    private final int block;
    private final CardType type;
    private final List<CardEffect> effects;
    private final boolean exhaust;
    private final boolean retain;
    private final boolean ethereal;
    private final int drawCount;
    private final boolean upgraded;
    private final String charId;
    private final String rarity;
    private final int selfDamage;
    private final int energyGain;
    private final int multiHitCount;
    private final int exhaustHandCount;
    private final String exhaustHandMode;
    private final boolean unplayable;
    private final boolean innate;
    private final int discardCount;
    private final String discardMode;
    private final boolean xCost;
    private final boolean aoe;
    private final boolean drawFirst;
    private final boolean copyToDiscard;
    private final int strengthMultiplier;
    private final boolean randomTarget;
    
    // === 新增字段：支持特殊状态牌机制 ===
    private final int endOfTurnDamage;
    private final int energyLossOnDraw;
    // === 新增字段：特殊卡牌机制 ===
    private final int exhaustNonAttackBlock;
    private final int addWoundCount;
    private final boolean blockToDamage;
    private final int blockPerAttack;
    // 🆕 静默猎手专属字段
    private final int energyGainIfDiscarded;
    private final String discardAllForCards;
    private final boolean discardAllForDraw;
    private final String buffCardName;
    private final int buffDamageAmount;
    private final boolean doublePoison;
    private final int drawPoisonAll;
    private final boolean extraPoisonTick;
    private final String addCardId;
    private final int addCardCount;
    private final int upgradeHandCount;
    private final String upgradeHandMode;
    private final boolean upgradeAllInHand;
    private final boolean requiresEmptyDrawPile;

    @JsonCreator
    public CardTemplate(
            @JsonProperty("id") String id, 
            @JsonProperty("name") String name,
            @JsonProperty("cost") int cost, 
            @JsonProperty("damage") int damage,
            @JsonProperty("block") int block, 
            @JsonProperty("type") CardType type,
            @JsonProperty("effects") List<CardEffect> effects,
            @JsonProperty("exhaust") Boolean exhaust, 
            @JsonProperty("retain") Boolean retain,
            @JsonProperty("ethereal") Boolean ethereal, 
            @JsonProperty("drawCount") Integer drawCount,
            @JsonProperty("upgraded") Boolean upgraded, 
            @JsonProperty("charId") String charId,
            @JsonProperty("rarity") String rarity,
            @JsonProperty("selfDamage") Integer selfDamage, 
            @JsonProperty("energyGain") Integer energyGain,
            @JsonProperty("multiHitCount") Integer multiHitCount,
            @JsonProperty("exhaustHandCount") Integer exhaustHandCount,
            @JsonProperty("exhaustHandMode") String exhaustHandMode,
            @JsonProperty("unplayable") Boolean unplayable,
            @JsonProperty("innate") Boolean innate,
            @JsonProperty("discardCount") Integer discardCount,
            @JsonProperty("discardMode") String discardMode,
            @JsonProperty("xCost") Boolean xCost,
            @JsonProperty("aoe") Boolean aoe,
            @JsonProperty("drawFirst") Boolean drawFirst,
            @JsonProperty("copyToDiscard") Boolean copyToDiscard,
            @JsonProperty("strengthMultiplier") Integer strengthMultiplier,
            @JsonProperty("randomTarget") Boolean randomTarget,
            // === 新增构造函数参数 ===
            @JsonProperty("endOfTurnDamage") Integer endOfTurnDamage,
            @JsonProperty("energyLossOnDraw") Integer energyLossOnDraw,
            @JsonProperty("exhaustNonAttackBlock") Integer exhaustNonAttackBlock,
            @JsonProperty("addWoundCount") Integer addWoundCount,
            @JsonProperty("blockToDamage") Boolean blockToDamage,
            @JsonProperty("blockPerAttack") Integer blockPerAttack,
            @JsonProperty("energyGainIfDiscarded") Integer energyGainIfDiscarded,
            @JsonProperty("discardAllForCards") String discardAllForCards,
            @JsonProperty("discardAllForDraw") Boolean discardAllForDraw,
            @JsonProperty("buffCardName") String buffCardName,
            @JsonProperty("buffDamageAmount") Integer buffDamageAmount,
            @JsonProperty("doublePoison") Boolean doublePoison,
            @JsonProperty("drawPoisonAll") Integer drawPoisonAll,
            @JsonProperty("extraPoisonTick") Boolean extraPoisonTick,
            @JsonProperty("addCardId") String addCardId,
            @JsonProperty("addCardCount") Integer addCardCount,
            @JsonProperty("upgradeHandCount") Integer upgradeHandCount,
            @JsonProperty("upgradeHandMode") String upgradeHandMode,
            @JsonProperty("upgradeAllInHand") Boolean upgradeAllInHand,
            @JsonProperty("requiresEmptyDrawPile") Boolean requiresEmptyDrawPile) {

        this.id = id; 
        this.name = name; 
        this.cost = cost; 
        this.damage = damage; 
        this.block = block;
        this.type = type;
        this.effects = effects != null ? effects : Collections.emptyList();
        this.exhaust = exhaust != null ? exhaust : false; 
        this.retain = retain != null ? retain : false;
        this.ethereal = ethereal != null ? ethereal : false; 
        this.drawCount = drawCount != null ? drawCount : 0;
        this.upgraded = upgraded != null ? upgraded : false; 
        this.charId = charId != null ? charId : "1";
        this.rarity = rarity != null ? rarity : "COMMON";
        this.selfDamage = selfDamage != null ? selfDamage : 0; 
        this.energyGain = energyGain != null ? energyGain : 0;
        this.multiHitCount = multiHitCount != null && multiHitCount > 0 ? multiHitCount : 1;
        this.exhaustHandCount = exhaustHandCount != null ? exhaustHandCount : 0;
        this.exhaustHandMode = exhaustHandMode != null ? exhaustHandMode.toUpperCase() : "RANDOM";
        this.unplayable = unplayable != null ? unplayable : false;
        this.innate = innate != null ? innate : false;
        this.discardCount = discardCount != null ? discardCount : 0;
        this.discardMode = discardMode != null ? discardMode.toUpperCase() : "RANDOM";
        this.xCost = xCost != null ? xCost : false;
        this.aoe = aoe != null ? aoe : false;
        this.drawFirst = drawFirst != null ? drawFirst : false;
        this.copyToDiscard = copyToDiscard != null ? copyToDiscard : false;
        this.strengthMultiplier = strengthMultiplier != null ? strengthMultiplier : 1;
        this.randomTarget = randomTarget != null ? randomTarget : false;
        
        // === 新增字段初始化 ===
        this.endOfTurnDamage = endOfTurnDamage != null ? endOfTurnDamage : 0;
        this.energyLossOnDraw = energyLossOnDraw != null ? energyLossOnDraw : 0;
        this.exhaustNonAttackBlock = exhaustNonAttackBlock != null ? exhaustNonAttackBlock : 0;
        this.addWoundCount = addWoundCount != null ? addWoundCount : 0;
        this.blockToDamage = blockToDamage != null ? blockToDamage : false;
        this.blockPerAttack = blockPerAttack != null ? blockPerAttack : 0;
        this.energyGainIfDiscarded = energyGainIfDiscarded != null ? energyGainIfDiscarded : 0;
        this.discardAllForCards = discardAllForCards;
        this.discardAllForDraw = discardAllForDraw != null ? discardAllForDraw : false;
        this.buffCardName = buffCardName;
        this.buffDamageAmount = buffDamageAmount != null ? buffDamageAmount : 0;
        this.doublePoison = doublePoison != null ? doublePoison : false;
        this.drawPoisonAll = drawPoisonAll != null ? drawPoisonAll : 0;
        this.extraPoisonTick = extraPoisonTick != null ? extraPoisonTick : false;
        this.addCardId = addCardId;
        this.addCardCount = addCardCount != null ? addCardCount : 0;
        this.upgradeHandCount = upgradeHandCount != null ? upgradeHandCount : 0;
        this.upgradeHandMode = upgradeHandMode != null ? upgradeHandMode.toUpperCase() : "RANDOM";
        this.upgradeAllInHand = upgradeAllInHand != null ? upgradeAllInHand : false;
        this.requiresEmptyDrawPile = requiresEmptyDrawPile != null ? requiresEmptyDrawPile : false;
    }

    // ==================== Getter 方法 ====================

    public String getId() { return id; }
    public String getName() { return name; }
    public int getCost() { return cost; }
    public int getDamage() { return damage; }
    public int getBlock() { return block; }
    public CardType getType() { return type; }
    public List<CardEffect> getEffects() { return effects; }
    
    public boolean isExhaust() { return exhaust; }
    public boolean isRetain() { return retain; }
    public boolean isEthereal() { return ethereal; }
    public int getDrawCount() { return drawCount; }
    public boolean isUpgraded() { return upgraded; }
    public String getCharId() { return charId; }
    public String getRarity() { return rarity; }
    
    public int getSelfDamage() { return selfDamage; }
    public int getEnergyGain() { return energyGain; }
    public int getMultiHitCount() { return multiHitCount; }
    public int getExhaustHandCount() { return exhaustHandCount; }
    public String getExhaustHandMode() { return exhaustHandMode; }
    
    public boolean isUnplayable() { return unplayable; }
    public boolean isInnate() { return innate; }
    public int getDiscardCount() { return discardCount; }
    public String getDiscardMode() { return discardMode; }
    
    public boolean isXCost() { return xCost; }
    public boolean isAoe() { return aoe; }
    public boolean isDrawFirst() { return drawFirst; }
    public boolean isCopyToDiscard() { return copyToDiscard; }
    public int getStrengthMultiplier() { return strengthMultiplier; }
    public boolean isRandomTarget() { return randomTarget; }

    // === 新增 Getter 方法 ===
    public int getEndOfTurnDamage() { return endOfTurnDamage; }
    public int getEnergyLossOnDraw() { return energyLossOnDraw; }
    public int getExhaustNonAttackBlock() { return exhaustNonAttackBlock; }
    public int getAddWoundCount() { return addWoundCount; }
    public boolean isBlockToDamage() { return blockToDamage; }
    public int getBlockPerAttack() { return blockPerAttack; }
    public int getEnergyGainIfDiscarded() { return energyGainIfDiscarded; }
    public String getDiscardAllForCards() { return discardAllForCards; }
    public boolean isDiscardAllForDraw() { return discardAllForDraw; }
    public String getBuffCardName() { return buffCardName; }
    public int getBuffDamageAmount() { return buffDamageAmount; }
    public boolean isDoublePoison() { return doublePoison; }
    public int getDrawPoisonAll() { return drawPoisonAll; }
    public boolean isExtraPoisonTick() { return extraPoisonTick; }
    public String getAddCardId() { return addCardId; }
    public int getAddCardCount() { return addCardCount; }
    public int getUpgradeHandCount() { return upgradeHandCount; }
    public String getUpgradeHandMode() { return upgradeHandMode; }
    public boolean isUpgradeAllInHand() { return upgradeAllInHand; }
    public boolean isRequiresEmptyDrawPile() { return requiresEmptyDrawPile; }

    // ==================== 辅助方法 ====================

    public String getApplyStatusType() { 
        return effects.isEmpty() ? null : effects.get(0).getType(); 
    }

    public int getApplyStatusCount() { 
        return effects.isEmpty() ? 0 : effects.get(0).getCount(); 
    }

    public String getApplyStatusTarget() { 
        return effects.isEmpty() ? null : effects.get(0).getTarget(); 
    }
}