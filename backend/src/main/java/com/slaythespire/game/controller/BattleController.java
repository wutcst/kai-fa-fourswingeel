package com.slaythespire.game.controller;

import com.slaythespire.game.service.BattleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class BattleController {

    @Autowired
    private BattleService battleService;

    @PostMapping("/new")
    public Map<String, Object> newBattle() {
        return battleService.newBattle();
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
