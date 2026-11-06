package com.vertoz.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HelloController {

    // Set by Kubernetes (APP_VERSION env var = Jenkins build number), "dev" locally.
    @Value("${app.version}")
    private String version;

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of("message", "Hello from hello-app v2", "version", version);
    }

    @GetMapping("/hello")
    public Map<String, String> hello(@RequestParam(defaultValue = "World") String name) {
        return Map.of("message", "Hello, " + name + "!");
    }
}
