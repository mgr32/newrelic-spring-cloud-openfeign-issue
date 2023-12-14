package com.example.demo;

import com.newrelic.api.agent.NewRelic;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/hello")
    public String sayHello() {
        return "hello";
    }

    @GetMapping("/hello-secured")
    public String sayHelloSecured() {
        return "hello-secured";
    }

    @GetMapping("/hello-error")
    public String sayHelloWithError() {
        throw new TestException("test");
    }

    @GetMapping("/hello-with-manual-instrumentation")
    public String sayHelloWithManualInstrumentation() {
        return "hello";
    }
}
