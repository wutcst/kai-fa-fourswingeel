package com.slaythespire.controller;

import com.slaythespire.model.EventTemplate;
import com.slaythespire.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 未知事件接口控制器
 */
@RestController
@RequestMapping("/api/event")
public class EventController {

    @Autowired
    private EventService eventService;

    /**
     * 随机获取一个事件（含选项，不含效果内部逻辑）
     * 前端仅用于展示
     */
    @GetMapping("/roll")
    public ResponseEntity<EventTemplate> rollEvent() {
        EventTemplate event = eventService.rollEvent();
        if (event == null) {
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok(event);
    }

    /**
     * 执行选项并保存
     * @param eventId 事件 ID
     * @param optionIndex 选项索引
     * @param charId 角色 ID
     * @return 执行结果描述
     */
    @PostMapping("/choose")
    public ResponseEntity<String> chooseOption(@RequestParam String eventId,
                                               @RequestParam int optionIndex,
                                               @RequestParam String charId,
                                               @RequestParam(required = false) List<Integer> cardIndices) {
        String result = eventService.executeOption(eventId, optionIndex, charId, cardIndices);
        return ResponseEntity.ok(result);
    }
}
