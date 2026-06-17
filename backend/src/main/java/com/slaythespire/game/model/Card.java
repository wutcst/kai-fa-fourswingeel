package com.slaythespire.game.model;

import com.slaythespire.repository.CardTemplate;

import java.util.ArrayList;
import java.util.List;

public class Card {

    private String name; 
    private int cost; 
    private int damage; 
    private int block; 
    private CardType type;

    private List<CardEffect> effects = new ArrayList<>();

    private boolean exhaust; 
    private boolean retain; 
    private boolean ethereal; 
    private int drawCount;

    private boolean upgraded; 
    private String charId; 
    private String rarity;

    private int selfDamage; 
    private int energyGain; 
    private int multiHitCount;

    private int exhaustHandCount; 
    private String exhaustHandMode;

    private boolean unplayable; 
    private boolean innate; 
    private int discardCount;

    private String discardMode;

    private boolean xCost; 
    private boolean aoe;

    private boolean drawFirst;

    private boolean copyToDiscard;

    private int strengthMultiplier = 1;

    private boolean randomTarget = false;

    // 🆕 新增字段：支持特殊状态牌机制
    private int endOfTurnDamage;
    private int energyLossOnDraw;
    // 🆕 新增字段：特殊卡牌机制
    private int exhaustNonAttackBlock;
    private int addWoundCount;
    private boolean blockToDamage;

    // 🆕 修改：枚举增加 STATUS 类型
    public enum CardType { 
        ATTACK, SKILL, POWER, STATUS 
    }

    // ==================== 构造函数 ====================

    public Card(CardTemplate template) {
        this.name = template.getName(); 
        this.cost = template.getCost(); 
        this.damage = template.getDamage();
        this.block = template.getBlock(); 
        this.type = template.getType();

        this.effects = new ArrayList<>(template.getEffects());

        this.exhaust = template.isExhaust(); 
        this.retain = template.isRetain(); 
        this.ethereal = template.isEthereal();
        this.drawCount = template.getDrawCount(); 
        this.upgraded = template.isUpgraded();
        this.charId = template.getCharId(); 
        this.rarity = template.getRarity();

        this.selfDamage = template.getSelfDamage(); 
        this.energyGain = template.getEnergyGain();
        this.multiHitCount = template.getMultiHitCount(); 
        this.exhaustHandCount = template.getExhaustHandCount();
        this.exhaustHandMode = template.getExhaustHandMode();

        this.unplayable = template.isUnplayable(); 
        this.innate = template.isInnate();
        this.discardCount = template.getDiscardCount(); 
        this.discardMode = template.getDiscardMode();

        this.xCost = template.isXCost(); 
        this.aoe = template.isAoe();
        this.drawFirst = template.isDrawFirst();

        this.copyToDiscard = template.isCopyToDiscard();
        this.strengthMultiplier = template.getStrengthMultiplier();
        this.randomTarget = template.isRandomTarget();

        // 🆕 新增字段赋值
        this.endOfTurnDamage = template.getEndOfTurnDamage();
        this.energyLossOnDraw = template.getEnergyLossOnDraw();
        this.exhaustNonAttackBlock = template.getExhaustNonAttackBlock();
        this.addWoundCount = template.getAddWoundCount();
        this.blockToDamage = template.isBlockToDamage();
    }

    public Card(String name, int cost, int damage, int block, CardType type) {
        this.name = name; 
        this.cost = cost; 
        this.damage = damage; 
        this.block = block; 
        this.type = type;

        this.drawCount = 0; 
        this.upgraded = false; 
        this.charId = "1"; 
        this.rarity = "COMMON";

        this.selfDamage = 0; 
        this.energyGain = 0; 
        this.multiHitCount = 1;

        this.exhaustHandCount = 0; 
        this.exhaustHandMode = "RANDOM";

        this.unplayable = false; 
        this.innate = false; 
        this.discardCount = 0; 
        this.discardMode = "RANDOM";

        this.xCost = false; 
        this.aoe = false; 
        this.drawFirst = false;

        this.copyToDiscard = false;
        this.strengthMultiplier = 1;
        this.randomTarget = false;

        // 🆕 新增字段默认赋值
        this.endOfTurnDamage = 0;
        this.energyLossOnDraw = 0;
        this.exhaustNonAttackBlock = 0;
        this.addWoundCount = 0;
        this.blockToDamage = false;
    }

    // copy constructor for Anger copy
    public Card(Card original) {
        this.name = original.name;
        this.cost = original.cost;
        this.damage = original.damage;
        this.block = original.block;
        this.type = original.type;

        this.effects = new ArrayList<>(original.effects);

        this.exhaust = original.exhaust;
        this.retain = original.retain;
        this.ethereal = original.ethereal;
        this.drawCount = original.drawCount;

        this.upgraded = original.upgraded;
        this.charId = original.charId;
        this.rarity = original.rarity;

        this.selfDamage = original.selfDamage;
        this.energyGain = original.energyGain;
        this.multiHitCount = original.multiHitCount;
        this.exhaustHandCount = original.exhaustHandCount;
        this.exhaustHandMode = original.exhaustHandMode;

        this.unplayable = original.unplayable;
        this.innate = original.innate;
        this.discardCount = original.discardCount;
        this.discardMode = original.discardMode;

        this.xCost = original.xCost;
        this.aoe = original.aoe;
        this.drawFirst = original.drawFirst;

        this.copyToDiscard = original.copyToDiscard;
        this.strengthMultiplier = original.strengthMultiplier;
        this.randomTarget = original.randomTarget;

        // 🆕 新增字段拷贝
        this.endOfTurnDamage = original.endOfTurnDamage;
        this.energyLossOnDraw = original.energyLossOnDraw;
        this.exhaustNonAttackBlock = original.exhaustNonAttackBlock;
        this.addWoundCount = original.addWoundCount;
        this.blockToDamage = original.blockToDamage;
    }

    // ==================== Getter 方法 ====================

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

    // 🆕 新增 Getter 方法
    public int getEndOfTurnDamage() { return endOfTurnDamage; }
    public int getEnergyLossOnDraw() { return energyLossOnDraw; }
    public int getExhaustNonAttackBlock() { return exhaustNonAttackBlock; }
    public int getAddWoundCount() { return addWoundCount; }
    public boolean isBlockToDamage() { return blockToDamage; }

    // ==================== Setter 方法 ====================

    public void setRandomTarget(boolean randomTarget) { this.randomTarget = randomTarget; }
    public void setName(String name) { this.name = name; } 
    public void setDamage(int damage) { this.damage = damage; }
    public void setBlock(int block) { this.block = block; }
    public void setEffects(List<CardEffect> effects) { this.effects = effects != null ? effects : new ArrayList<>(); }

    public void setExhaust(boolean exhaust) { this.exhaust = exhaust; } 
    public void setRetain(boolean retain) { this.retain = retain; }
    public void setEthereal(boolean ethereal) { this.ethereal = ethereal; } 
    public void setDrawCount(int drawCount) { this.drawCount = drawCount; }

    public void setUpgraded(boolean upgraded) { this.upgraded = upgraded; } 
    public void setCharId(String charId) { this.charId = charId; }
    public void setRarity(String rarity) { this.rarity = rarity; } 
    public void setSelfDamage(int selfDamage) { this.selfDamage = selfDamage; }
    public void setEnergyGain(int energyGain) { this.energyGain = energyGain; } 
    public void setMultiHitCount(int multiHitCount) { this.multiHitCount = multiHitCount; }
    public void setExhaustHandCount(int exhaustHandCount) { this.exhaustHandCount = exhaustHandCount; }
    public void setExhaustHandMode(String exhaustHandMode) { this.exhaustHandMode = exhaustHandMode; }

    public void setUnplayable(boolean unplayable) { this.unplayable = unplayable; }
    public void setInnate(boolean innate) { this.innate = innate; }
    public void setDiscardCount(int discardCount) { this.discardCount = discardCount; }
    public void setDiscardMode(String discardMode) { this.discardMode = discardMode; }

    public void setXCost(boolean xCost) { this.xCost = xCost; }
    public void setAoe(boolean aoe) { this.aoe = aoe; }
    public void setDrawFirst(boolean drawFirst) { this.drawFirst = drawFirst; }
    public void setCopyToDiscard(boolean copyToDiscard) { this.copyToDiscard = copyToDiscard; }
    public void setStrengthMultiplier(int strengthMultiplier) { this.strengthMultiplier = strengthMultiplier; }

    // 🆕 新增 Setter 方法
    public void setEndOfTurnDamage(int endOfTurnDamage) { this.endOfTurnDamage = endOfTurnDamage; }
    public void setEnergyLossOnDraw(int energyLossOnDraw) { this.energyLossOnDraw = energyLossOnDraw; }
    public void setExhaustNonAttackBlock(int exhaustNonAttackBlock) { this.exhaustNonAttackBlock = exhaustNonAttackBlock; }
    public void setAddWoundCount(int addWoundCount) { this.addWoundCount = addWoundCount; }
    public void setBlockToDamage(boolean blockToDamage) { this.blockToDamage = blockToDamage; }

    // ==================== 辅助方法 (状态效果相关) ====================

    public String getApplyStatusType() { 
        return effects.isEmpty() ? null : effects.get(0).getType(); 
    }

    public int getApplyStatusCount() { 
        return effects.isEmpty() ? 0 : effects.get(0).getCount(); 
    }

    public String getApplyStatusTarget() { 
        return effects.isEmpty() ? null : effects.get(0).getTarget(); 
    }

    public void setApplyStatusType(String type) {
        if (!effects.isEmpty()) { 
            effects.set(0, new CardEffect(type, effects.get(0).getCount(), effects.get(0).getTarget())); 
        } else if (type != null) { 
            effects.add(new CardEffect(type, 1, "ENEMY")); 
        }
    }

    public void setApplyStatusCount(int count) {
        if (!effects.isEmpty()) {
            effects.set(0, new CardEffect(effects.get(0).getType(), count, effects.get(0).getTarget()));
        } else if (count > 0) {
            effects.add(new CardEffect("VULNERABLE", count, "ENEMY"));
        }
    }

    public void setApplyStatusTarget(String target) {
        if (!effects.isEmpty()) {
            effects.set(0, new CardEffect(effects.get(0).getType(), effects.get(0).getCount(), target));
        } else if (target != null && !target.isEmpty()) {
            effects.add(new CardEffect("VULNERABLE", 1, target));
        }
    }
}