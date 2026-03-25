package com.systemdesign.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NotificationPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationPlatformApplication.class, args);
        System.out.println("🚀 Planet-Scale Notification Platform LLD initialized successfully.");
    }
}
