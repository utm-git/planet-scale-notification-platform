package com.systemdesign.notification.infrastructure;

import com.systemdesign.notification.domain.Notification;
import java.util.concurrent.CompletableFuture;

public interface EventBus {
    // Legacy fire-and-forget
    void publish(String topic, String partitionKey, Notification notification);
    
    // Explicit ACK-based publish enforcing acks=all and ISR validation
    // Guarantees Zero Data Loss. Failure returned via Exception in future.
    CompletableFuture<Void> publishWithAck(String topic, String partitionKey, Notification notification);

    // Delayed processing
    void publishDelay(String topic, String partitionKey, Notification notification, long delayMs);
}
