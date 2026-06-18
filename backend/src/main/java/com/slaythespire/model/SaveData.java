package com.slaythespire.model;



import java.util.List;

import java.util.Map;



public class SaveData {

    private String charId;

    private int playerHp;

    private int maxHp;

    private int gold;

    private List<Map<String, Object>> deck;

    private List<String> relics;

    private List<String> visitedNodes;

    private String currentNode;



    // 🆕 新增：地图数据持久化字段

    private List<Map<String, Object>> mapNodes;

    private List<Map<String, String>> mapEdges;



    // 🆕 新增：当前阶段（act）

    private int act = 1;

    private List<String> seenEvents;

    public SaveData() {}



    // --- Getters & Setters ---

    public String getCharId() { return charId; }

    public void setCharId(String charId) { this.charId = charId; }



    public int getPlayerHp() { return playerHp; }

    public void setPlayerHp(int playerHp) { this.playerHp = playerHp; }



    public int getMaxHp() { return maxHp; }

    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }



    public int getGold() { return gold; }

    public void setGold(int gold) { this.gold = gold; }



    public List<Map<String, Object>> getDeck() { return deck; }

    public void setDeck(List<Map<String, Object>> deck) { this.deck = deck; }



    public List<String> getRelics() { return relics; }

    public void setRelics(List<String> relics) { this.relics = relics; }



    public List<String> getVisitedNodes() { return visitedNodes; }

    public void setVisitedNodes(List<String> visitedNodes) { this.visitedNodes = visitedNodes; }



    public String getCurrentNode() { return currentNode; }

    public void setCurrentNode(String currentNode) { this.currentNode = currentNode; }



    public List<Map<String, Object>> getMapNodes() { return mapNodes; }

    public void setMapNodes(List<Map<String, Object>> mapNodes) { this.mapNodes = mapNodes; }



    public List<Map<String, String>> getMapEdges() { return mapEdges; }

    public void setMapEdges(List<Map<String, String>> mapEdges) { this.mapEdges = mapEdges; }



    // 🆕 新增 act 的 getter/setter

    public int getAct() { return act; }

    public void setAct(int act) { this.act = act; }

    public List<String> getSeenEvents() { return seenEvents; }
    public void setSeenEvents(List<String> seenEvents) { this.seenEvents = seenEvents; }

}