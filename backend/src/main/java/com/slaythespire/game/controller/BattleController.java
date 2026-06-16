package com.slaythespire.game.controller;

import com.slaythespire.game.service.BattleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class BattleController {

    @Autowired
    private BattleService battleService;

    @PostMapping("/new")
    public Map<String, Object> newBattle(@RequestBody(required = false) Map<String, Object> payload) {
        List<Map<String, Object>> playerDeck = null;
        List<String> playerRelics = null;
        int playerHp = 70;
        int playerMaxHp = 70;

        if (payload != null) {
            if (payload.containsKey("deck")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> deckList = (List<Map<String, Object>>) payload.get("deck");
                playerDeck = deckList;
            }
            if (payload.containsKey("relics")) {
                @SuppressWarnings("unchecked")
                List<String> relicList = (List<String>) payload.get("relics");
                playerRelics = relicList;
            }
            if (payload.containsKey("playerHp")) {
                playerHp = ((Number) payload.get("playerHp")).intValue();
            }
            if (payload.containsKey("playerMaxHp")) {
                playerMaxHp = ((Number) payload.get("playerMaxHp")).intValue();
            }
        }
        return battleService.newBattle(playerDeck, playerRelics, playerHp, playerMaxHp);
    }

    @PostMapping("/play")
    public Map<String, Object> playCard(@RequestParam int index,
                                        @RequestParam(required = false) Integer targetIndex,
                                        @RequestParam(required = false) Integer exhaustHandIndex) {
        return battleService.playCard(index, targetIndex, exhaustHandIndex);
    }

    @PostMapping("/endTurn")
    public Map<String, Object> endTurn() {
        return battleService.endTurn();
    }
}