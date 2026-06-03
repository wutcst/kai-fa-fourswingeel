package com.slaythespire.controller;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        // 转发到 cover.html（仍为静态资源，浏览器地址栏不会改变）
        return "forward:/cover.html";
    }
}