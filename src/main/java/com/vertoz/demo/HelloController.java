package com.vertoz.demo;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HelloController {

    @GetMapping("/hello")
    public Map<String, String> hello() {

        return Map.of(
            "message", "Hello from Spring Boot!",
            "application", "CI/CD Demo",
            "status", "SUCCESS"
        );
    }

    @GetMapping("/version")
    public Map<String, String> version() {

        return Map.of(
            "version", "1.0.0",
            "environment", "Kubernetes"
        );
    }
}