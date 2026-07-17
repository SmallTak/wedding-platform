package com.wedding.platform.platform.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicStatusController {

    private final String applicationName;

    public PublicStatusController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "application", applicationName,
                "status", "UP",
                "time", Instant.now()
        );
    }
}

