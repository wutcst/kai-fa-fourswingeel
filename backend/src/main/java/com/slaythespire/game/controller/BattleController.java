package com.slaythespire.game.controller;

import com.slaythespire.game.service.BattleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class BattleController {

    @Autowired
    private BattleService battleService;

    @PostMapping("/new")
    public ResponseEntity<?> newBattle() {
        try {
            return ResponseEntity.ok(battleService.newBattle());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createError("无法开始战斗：" + e.getMessage()));
        }
    }

    @PostMapping("/play")
    public ResponseEntity<?> playCard(@RequestParam int index) {
        try {
            return ResponseEntity.ok(battleService.playCard(index));
        } catch (IllegalStateException e) {
            // 处理业务异常（能量不足、战斗结束等）
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(createError(e.getMessage()));
        } catch (IllegalArgumentException e) {
            // 处理参数异常
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(createError(e.getMessage()));
        } catch (Exception e) {
            // 处理其他异常
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createError("服务器内部错误：" + e.getMessage()));
        }
    }

    @PostMapping("/endTurn")
    public ResponseEntity<?> endTurn() {
        try {
            return ResponseEntity.ok(battleService.endTurn());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(createError(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createError("服务器内部错误：" + e.getMessage()));
        }
    }

    /**
     * 创建统一的错误响应格式
     */
    private Map<String, Object> createError(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        return error;
    }
}