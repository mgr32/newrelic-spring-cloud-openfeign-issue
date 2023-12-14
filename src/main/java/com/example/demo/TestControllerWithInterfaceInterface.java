package com.example.demo;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/hello-new")
public interface TestControllerWithInterfaceInterface {

    @GetMapping("/with-interface")
    String sayHelloWithInterface();
}
