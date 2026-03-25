package com.systemdesign.notification.delivery;

import com.systemdesign.notification.domain.Notification;
import com.systemdesign.notification.domain.Status;
import com.systemdesign.notification.infrastructure.EventBus;
import com.systemdesign.notification.infrastructure.NotificationStateStore;
import com.systemdesign.notification.infrastructure.ProviderClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class DeliveryWorker {
    private final ProviderClient providerClient;
    private final EventBus eventBus;
    private final NotificationStateStore stateStore;
    
    private static final int MAX_RETRIES = 5;
    private static final int BASE_BACKOFF_MS = 2000;

    public DeliveryWorker(ProviderClient providerClient, EventBus eventBus, NotificationStateStore stateStore) {
        this.providerClient = providerClient;
        this.eventBus = eventBus;
        this.stateStore = stateStore;
    }

    public void processDelivery(Notification notification) {
        try {
            boolean success = providerClient.send(notification);
            
            if (success) {
                stateStore.updateStatus(notification.getNotificationId(), Status.SENT);
                // Consumer commits Kafka offset here
            } else {
                handleRetry(notification);
            }
        } catch (ProviderException e) {
            handleRetry(notification);
        }
    }

    private void handleRetry(Notification notification) {
        notification.setRetryCount(notification.getRetryCount() + 1);
        
        if (notification.getRetryCount() > MAX_RETRIES) {
            stateStore.updateStatus(notification.getNotificationId(), Status.FAILED);
            eventBus.publish("dlq-notifications", notification.getUserId(), notification);
            return;
        }

        stateStore.updateStatus(notification.getNotificationId(), Status.RETRYING);
        
        long delayMs = calculateDelayWithJitter(notification.getRetryCount());
        
        eventBus.publishDelay("retry-topic", notification.getUserId(), notification, delayMs);
    }

    private long calculateDelayWithJitter(int attempt) {
        long exponentialBase = BASE_BACKOFF_MS * (long) Math.pow(2, attempt);
        long jitter = ThreadLocalRandom.current().nextLong(0, exponentialBase / 2);
        return exponentialBase + jitter;
    }
}
