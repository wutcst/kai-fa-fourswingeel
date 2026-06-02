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

    // 开始新交互战斗
    @PostMapping("/new")
    public Map<String, Object> newBattle() {
        return battleService.newBattle();
    }

    // 玩家出牌
    @PostMapping("/play")
    public Map<String, Object> playCard(@RequestParam int index) {
        return battleService.playCard(index);
    }
}
