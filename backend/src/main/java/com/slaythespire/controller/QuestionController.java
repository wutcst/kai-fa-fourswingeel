package com.slaythespire.controller;

import com.slaythespire.service.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/question")
public class QuestionController {

    @Autowired
    private QuestionService questionService;

    /**
     * 随机决定未知房间的结果，返回类型字符串
     */
    @GetMapping("/roll")
    public String roll(@RequestParam String charId) {
        QuestionService.QuestionResult result = questionService.roll(charId);
        return result.name().toLowerCase(); // 返回: enemy, chest, shop, event
    }
}
