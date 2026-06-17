package com.slaythespire.controller;

import com.slaythespire.model.SaveData;
import com.slaythespire.service.QuestionService;
import com.slaythespire.service.SaveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SaveController {

    @Autowired
    private SaveService saveService;

    @Autowired
    private QuestionService questionService;

    /**
     * 保存游戏 (全局唯一存档)
     */
    @PostMapping("/save")
    public ResponseEntity<String> saveGame(@RequestBody SaveData data) {
        try {
            // 🆕 直接保存，不再校验 charId
            saveService.saveGame(data);
            return ResponseEntity.ok("保存成功");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("保存失败: " + e.getMessage());
        }
    }

    /**
     * 加载游戏 (全局唯一存档)
     */
    @GetMapping("/load")
    public ResponseEntity<?> loadGame() {
        try {
            // 🆕 移除 @RequestParam String charId
            SaveData data = saveService.loadGame();
            if (data == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("加载失败: " + e.getMessage());
        }
    }

    /**
     * 初始化新游戏（创建初始存档）
     */
    @PostMapping("/init")
    public ResponseEntity<?> initGame(@RequestBody SaveData data) {
        try {
            if (data.getVisitedNodes() == null) {
                data.setVisitedNodes(java.util.Arrays.asList("start"));
            }
            if (data.getCurrentNode() == null) {
                data.setCurrentNode("start");
            }
            if (data.getRelics() == null) {
                data.setRelics(new java.util.ArrayList<>());
            }
            
            // 如果 QuestionService 仍按角色区分概率，保留此逻辑
            if (data.getCharId() != null && !data.getCharId().isEmpty()) {
                questionService.resetChar(data.getCharId());
            }
            
            // 🆕 直接保存全局存档
            saveService.saveGame(data);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("初始化失败: " + e.getMessage());
        }
    }

    /**
     * 删除存档 (全局唯一存档)
     */
    @DeleteMapping("/save")
    public ResponseEntity<String> deleteSave() {
        try {
            // 🆕 移除 @RequestParam String charId
            saveService.deleteSave();
            return ResponseEntity.ok("删除成功");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("删除失败: " + e.getMessage());
        }
    }
}