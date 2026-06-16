package com.slaythespire.game.controller;

import com.slaythespire.game.service.BattleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 战斗接口控制器
 * 负责接收前端传来的卡组、遗物、血量等数据，并初始化战斗
 */
@RestController
@RequestMapping("/api/game")
public class BattleController {

    @Autowired
    private BattleService battleService;

    /**
     * 开启新战斗
     * 支持接收前端传来的玩家当前卡组、遗物列表、血量数据
     */
    @PostMapping("/new")
    public Map<String, Object> newBattle(@RequestBody(required = false) Map<String, Object> payload) {
        List<Map<String, Object>> playerDeck = null;
        List<String> playerRelics = null;
        int playerHp = 70;
        int playerMaxHp = 70;
        String nodeType = null;

        if (payload != null) {
            // 解析卡组
            if (payload.containsKey("deck")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> deckList = (List<Map<String, Object>>) payload.get("deck");
                playerDeck = deckList;
            }
            
            // 解析遗物 ID 列表
            if (payload.containsKey("relics")) {
                @SuppressWarnings("unchecked")
                List<String> relicList = (List<String>) payload.get("relics");
                playerRelics = relicList;
            }
            
            // 解析血量
            if (payload.containsKey("playerHp")) {
                playerHp = ((Number) payload.get("playerHp")).intValue();
            }
            if (payload.containsKey("playerMaxHp")) {
                playerMaxHp = ((Number) payload.get("playerMaxHp")).intValue();
            }
            if (payload.containsKey("nodeType")) {
                nodeType = (String) payload.get("nodeType");
            }
        }
        
        // 将数据传递给 Service
        return battleService.newBattle(playerDeck, playerRelics, playerHp, playerMaxHp, nodeType);
    }

    /**
     * 打出卡牌
     * @param index 打出的手牌索引
     * @param targetIndex 攻击目标敌人索引（可选，多目标时使用）
     * @param exhaustHandIndex 要消耗的手牌索引（可选，用于“知识渴求”等卡牌手动选牌）
     * @param discardHandIndex 要丢弃的手牌索引（可选，用于“杂技”等卡牌手动选牌）
     */
    @PostMapping("/play")
    public Map<String, Object> playCard(@RequestParam int index,
                                        @RequestParam(required = false) Integer targetIndex,
                                        @RequestParam(required = false) Integer exhaustHandIndex,
                                        @RequestParam(required = false) Integer discardHandIndex) {
        return battleService.playCard(index, targetIndex, exhaustHandIndex, discardHandIndex);
    }

    /**
     * 结束回合
     */
    @PostMapping("/endTurn")
    public Map<String, Object> endTurn() {
        return battleService.endTurn();
    }
}