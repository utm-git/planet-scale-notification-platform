package com.systemdesign.notification.infrastructure;

import com.systemdesign.notification.domain.Notification;

public interface EventBus {
    void publish(String topic, String partitionKey, Notification notification);
    void publishDelay(String topic, String partitionKey, Notification notification, long delayMs);
}
