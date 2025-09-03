package com.example.logwatcher.controller;

import com.example.logwatcher.service.LogTailService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LogController {
    
    private final LogTailService logTailService;
    
    public LogController(LogTailService logTailService) {
        this.logTailService = logTailService;
    }
    
    @GetMapping("/log")
    public String logPage() {
        return "log.html";
    }
    
    @MessageMapping("/getInitialLogs")
    public void getInitialLogs() {
        logTailService.sendInitialLines();
    }
}
