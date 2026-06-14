package com.slaythespire.game.model;

import com.slaythespire.repository.CardTemplate;

public class Card {
    private String name;
    private int cost;
    private int damage;
    private int block;
    private CardType type;
    private String applyStatusType;
    private int applyStatusCount;
    private String applyStatusTarget;
    private boolean exhaust;
    private boolean retain;
    private boolean ethereal;
    private int drawCount;
    private boolean upgraded;
    private String charId;
    private String rarity;

    public enum CardType { ATTACK, SKILL, POWER }

    public Card(CardTemplate template) {
        this.name = template.getName();
        this.cost = template.getCost();
        this.damage = template.getDamage();
        this.block = template.getBlock();
        this.type = template.getType();
        this.applyStatusType = template.getApplyStatusType();
        this.applyStatusCount = template.getApplyStatusCount();
        this.applyStatusTarget = template.getApplyStatusTarget();
        this.exhaust = template.isExhaust();
        this.retain = template.isRetain();
        this.ethereal = template.isEthereal();
        this.drawCount = template.getDrawCount();
        this.upgraded = template.isUpgraded();
        this.charId = template.getCharId();
        this.rarity = template.getRarity();
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
    }

    // ================= Getter =================
    public String getName() { return name; }
    public int getCost() { return cost; }
    public int getDamage() { return damage; }
    public int getBlock() { return block; }
    public CardType getType() { return type; }
    public String getApplyStatusType() { return applyStatusType; }
    public int getApplyStatusCount() { return applyStatusCount; }
    public String getApplyStatusTarget() { return applyStatusTarget; }
    public boolean isExhaust() { return exhaust; }
    public boolean isRetain() { return retain; }
    public boolean isEthereal() { return ethereal; }
    public int getDrawCount() { return drawCount; }
    public boolean isUpgraded() { return upgraded; }
    public String getCharId() { return charId; }
    public String getRarity() { return rarity; }

    // ================= Setter =================
    public void setName(String name) { this.name = name; }
    public void setDamage(int damage) { this.damage = damage; }
    public void setBlock(int block) { this.block = block; }
    public void setApplyStatusType(String applyStatusType) { this.applyStatusType = applyStatusType; }
    public void setApplyStatusCount(int applyStatusCount) { this.applyStatusCount = applyStatusCount; }
    public void setApplyStatusTarget(String applyStatusTarget) { this.applyStatusTarget = applyStatusTarget; }
    public void setExhaust(boolean exhaust) { this.exhaust = exhaust; }
    public void setRetain(boolean retain) { this.retain = retain; }
    public void setEthereal(boolean ethereal) { this.ethereal = ethereal; }
    public void setDrawCount(int drawCount) { this.drawCount = drawCount; }
    public void setUpgraded(boolean upgraded) { this.upgraded = upgraded; }
    public void setCharId(String charId) { this.charId = charId; }
    public void setRarity(String rarity) { this.rarity = rarity; }
}
