package com.slaythespire.controller;

import com.slaythespire.model.SaveData;
import com.slaythespire.service.SaveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // 允许跨域
public class SaveController {

    @Autowired
    private SaveService saveService;

    /**
     * 保存游戏
     */
    @PostMapping("/save")
    public ResponseEntity<String> saveGame(@RequestBody SaveData data) {
        try {
            if (data.getCharId() == null || data.getCharId().isEmpty()) {
                return ResponseEntity.badRequest().body("角色ID不能为空");
            }
            saveService.saveGame(data.getCharId(), data);
            return ResponseEntity.ok("保存成功");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("保存失败: " + e.getMessage());
        }
    }

    /**
     * 加载游戏
     */
    @GetMapping("/load")
    public ResponseEntity<?> loadGame(@RequestParam String charId) {
        try {
            SaveData data = saveService.loadGame(charId);
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
            if (data.getCharId() == null || data.getCharId().isEmpty()) {
                return ResponseEntity.badRequest().body("角色ID不能为空");
            }
            // 设置默认值
            if (data.getVisitedNodes() == null) {
                data.setVisitedNodes(java.util.Arrays.asList("start"));
            }
            if (data.getCurrentNode() == null) {
                data.setCurrentNode("start");
            }
            if (data.getRelics() == null) {
                data.setRelics(new java.util.ArrayList<>());
            }
            
            saveService.saveGame(data.getCharId(), data);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("初始化失败: " + e.getMessage());
        }
    }

    /**
     * 删除存档
     */
    @DeleteMapping("/save")
    public ResponseEntity<String> deleteSave(@RequestParam String charId) {
        try {
            saveService.deleteSave(charId);
            return ResponseEntity.ok("删除成功");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("删除失败: " + e.getMessage());
        }
    }
}