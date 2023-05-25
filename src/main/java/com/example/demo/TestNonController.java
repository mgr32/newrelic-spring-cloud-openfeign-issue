package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;

public class TestNonController {

    @GetMapping("/doesnotmatter")
    public void doesnotmatter() {

    }
}
