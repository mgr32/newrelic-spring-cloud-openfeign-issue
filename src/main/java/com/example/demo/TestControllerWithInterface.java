package com.example.demo;

import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestControllerWithInterface implements TestControllerWithInterfaceInterface {
    @Override
    public String sayHelloWithInterface() {
        return "hello";
    }
}
