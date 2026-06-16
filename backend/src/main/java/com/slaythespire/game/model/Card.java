package com.slaythespire.game.model;

import com.slaythespire.repository.CardTemplate;

public class Card {
    private String name; private int cost; private int damage; private int block; private CardType type;
    private String applyStatusType; private int applyStatusCount; private String applyStatusTarget;
    private boolean exhaust; private boolean retain; private boolean ethereal; private int drawCount;
    private boolean upgraded; private String charId; private String rarity;
    private int selfDamage; private int energyGain; private int multiHitCount; 
    private int exhaustHandCount; private String exhaustHandMode;
    private boolean unplayable; private boolean innate; private int discardCount;
    private String discardMode; // 🆕 新增
    private boolean xCost; private boolean aoe;
    private boolean drawFirst;

    public enum CardType { ATTACK, SKILL, POWER }

    public Card(CardTemplate template) {
        this.name = template.getName(); this.cost = template.getCost(); this.damage = template.getDamage();
        this.block = template.getBlock(); this.type = template.getType();
        this.applyStatusType = template.getApplyStatusType(); this.applyStatusCount = template.getApplyStatusCount();
        this.applyStatusTarget = template.getApplyStatusTarget();
        this.exhaust = template.isExhaust(); this.retain = template.isRetain(); this.ethereal = template.isEthereal();
        this.drawCount = template.getDrawCount(); this.upgraded = template.isUpgraded();
        this.charId = template.getCharId(); this.rarity = template.getRarity();
        this.selfDamage = template.getSelfDamage(); this.energyGain = template.getEnergyGain();
        this.multiHitCount = template.getMultiHitCount(); this.exhaustHandCount = template.getExhaustHandCount();
        this.exhaustHandMode = template.getExhaustHandMode();
        this.unplayable = template.isUnplayable(); this.innate = template.isInnate();
        this.discardCount = template.getDiscardCount(); this.discardMode = template.getDiscardMode(); // 🆕
        this.xCost = template.isXCost(); this.aoe = template.isAoe();
        this.drawFirst = template.isDrawFirst();
    }

    public Card(String name, int cost, int damage, int block, CardType type) {
        this.name = name; this.cost = cost; this.damage = damage; this.block = block; this.type = type;
        this.drawCount = 0; this.upgraded = false; this.charId = "1"; this.rarity = "COMMON";
        this.selfDamage = 0; this.energyGain = 0; this.multiHitCount = 1;
        this.exhaustHandCount = 0; this.exhaustHandMode = "RANDOM";
        this.unplayable = false; this.innate = false; this.discardCount = 0; this.discardMode = "RANDOM"; // 🆕
        this.xCost = false; this.aoe = false;
        this.drawFirst = false;
    }

    public String getName() { return name; } public int getCost() { return cost; } public int getDamage() { return damage; }
    public int getBlock() { return block; } public CardType getType() { return type; }
    public String getApplyStatusType() { return applyStatusType; } public int getApplyStatusCount() { return applyStatusCount; }
    public String getApplyStatusTarget() { return applyStatusTarget; } public boolean isExhaust() { return exhaust; }
    public boolean isRetain() { return retain; } public boolean isEthereal() { return ethereal; } public int getDrawCount() { return drawCount; }
    public boolean isUpgraded() { return upgraded; } public String getCharId() { return charId; } public String getRarity() { return rarity; }
    public int getSelfDamage() { return selfDamage; } public int getEnergyGain() { return energyGain; }
    public int getMultiHitCount() { return multiHitCount; } public int getExhaustHandCount() { return exhaustHandCount; }
    public String getExhaustHandMode() { return exhaustHandMode; }
    public boolean isUnplayable() { return unplayable; } public boolean isInnate() { return innate; }
    public int getDiscardCount() { return discardCount; } public String getDiscardMode() { return discardMode; } // 🆕
    public boolean isXCost() { return xCost; } public boolean isAoe() { return aoe; }
    public boolean isDrawFirst() { return drawFirst; }

    public void setName(String name) { this.name = name; } public void setDamage(int damage) { this.damage = damage; }
    public void setBlock(int block) { this.block = block; } public void setApplyStatusType(String applyStatusType) { this.applyStatusType = applyStatusType; }
    public void setApplyStatusCount(int applyStatusCount) { this.applyStatusCount = applyStatusCount; }
    public void setApplyStatusTarget(String applyStatusTarget) { this.applyStatusTarget = applyStatusTarget; }
    public void setExhaust(boolean exhaust) { this.exhaust = exhaust; } public void setRetain(boolean retain) { this.retain = retain; }
    public void setEthereal(boolean ethereal) { this.ethereal = ethereal; } public void setDrawCount(int drawCount) { this.drawCount = drawCount; }
    public void setUpgraded(boolean upgraded) { this.upgraded = upgraded; } public void setCharId(String charId) { this.charId = charId; }
    public void setRarity(String rarity) { this.rarity = rarity; } public void setSelfDamage(int selfDamage) { this.selfDamage = selfDamage; }
    public void setEnergyGain(int energyGain) { this.energyGain = energyGain; } public void setMultiHitCount(int multiHitCount) { this.multiHitCount = multiHitCount; }
    public void setExhaustHandCount(int exhaustHandCount) { this.exhaustHandCount = exhaustHandCount; }
    public void setExhaustHandMode(String exhaustHandMode) { this.exhaustHandMode = exhaustHandMode; }
    public void setUnplayable(boolean unplayable) { this.unplayable = unplayable; }
    public void setInnate(boolean innate) { this.innate = innate; }
    public void setDiscardCount(int discardCount) { this.discardCount = discardCount; }
    public void setDiscardMode(String discardMode) { this.discardMode = discardMode; } // 🆕
    public void setXCost(boolean xCost) { this.xCost = xCost; }
    public void setAoe(boolean aoe) { this.aoe = aoe; }
    public void setDrawFirst(boolean drawFirst) { this.drawFirst = drawFirst; }
}
