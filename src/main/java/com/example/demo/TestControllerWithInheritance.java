package com.example.demo;

import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestControllerWithInheritance extends TestControllerWithInheritanceParent {

    @Override
    public String sayHelloWithInheritance() {
        return "hello";
    }

}
