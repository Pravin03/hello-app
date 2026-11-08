
package com.vertoz.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HelloController.class)
class HelloControllerTest {

    @Autowired
    private MockMvc mvc;

    // Test 1: Hello API
    @Test
    void helloReturnsSuccessMessage() throws Exception {

        mvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Hello from Spring Boot!"))
                .andExpect(jsonPath("$.application")
                        .value("CI/CD Demo"))
                .andExpect(jsonPath("$.status")
                        .value("SUCCESS"));
    }

    // Test 2: Version API
    @Test
    void versionReturnsApplicationVersion() throws Exception {

        mvc.perform(get("/api/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version")
                        .value("1.0.0"))
                .andExpect(jsonPath("$.environment")
                        .value("Kubernetes"));
    }
}
