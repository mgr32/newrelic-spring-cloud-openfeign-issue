package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;

@CustomRestControllerAnnotation
public class TestControllerWithCustomAnnotation {

    @GetMapping("/hello-custom")
    public String sayHello() {
        return "hello-custom";
    }
}
