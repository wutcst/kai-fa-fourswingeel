package com.slaythespire.game.controller;

import com.slaythespire.game.service.BattleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 战斗接口控制器
 */
@RestController
@RequestMapping("/api/game")
public class BattleController {

    @Autowired
    private BattleService battleService;

    /**
     * 开启新战斗
     * 支持接收前端传来的玩家当前卡组、血量数据
     */
    @PostMapping("/new")
    public Map<String, Object> newBattle(@RequestBody(required = false) Map<String, Object> payload) {
        List<Map<String, Object>> playerDeck = null;
        int playerHp = 70;
        int playerMaxHp = 70;

        if (payload != null) {
            if (payload.containsKey("deck")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> deckList = (List<Map<String, Object>>) payload.get("deck");
                playerDeck = deckList;
            }
            if (payload.containsKey("playerHp")) {
                playerHp = ((Number) payload.get("playerHp")).intValue();
            }
            if (payload.containsKey("playerMaxHp")) {
                playerMaxHp = ((Number) payload.get("playerMaxHp")).intValue();
            }
        }
        return battleService.newBattle(playerDeck, playerHp, playerMaxHp);
    }

    @PostMapping("/play")
    public Map<String, Object> playCard(@RequestParam int index) {
        return battleService.playCard(index);
    }

    @PostMapping("/endTurn")
    public Map<String, Object> endTurn() {
        return battleService.endTurn();
    }
}