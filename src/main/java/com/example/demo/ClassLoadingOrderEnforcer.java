package com.example.demo;

import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;

public class ClassLoadingOrderEnforcer implements ApplicationListener<ApplicationStartingEvent> {

    @Override
    public void onApplicationEvent(ApplicationStartingEvent event) {
//         not ok:
        loadClass(TestNonController.class.getName());
        loadClass(TestController.class.getName());

//         ok:
//         loadClass(TestController.class.getName());
//         loadClass(TestNonController.class.getName());
    }

    private void loadClass(String name) {
        System.out.println("Loading class " + name);
        try {
            Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}

