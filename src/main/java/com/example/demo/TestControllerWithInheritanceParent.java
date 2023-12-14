package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/hello-new")
public abstract class TestControllerWithInheritanceParent {

    @GetMapping("/with-inheritance")
    public abstract String sayHelloWithInheritance();
}
