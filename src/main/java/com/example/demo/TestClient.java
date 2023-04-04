package com.example.demo;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient("testClient")
public interface TestClient {

    @GetMapping("/backend")
    String getBackend();
}
