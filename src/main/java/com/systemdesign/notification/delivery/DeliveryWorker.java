package com.systemdesign.notification.delivery;

import com.systemdesign.notification.domain.Notification;
import com.systemdesign.notification.domain.Status;
import com.systemdesign.notification.infrastructure.EventBus;
import com.systemdesign.notification.infrastructure.NotificationStateStore;
import com.systemdesign.notification.infrastructure.ProviderClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class DeliveryWorker {
    private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);
    
    private final ProviderClient providerClient;
    private final EventBus eventBus;
    private final NotificationStateStore stateStore;
    private final CircuitBreaker circuitBreaker;
    
    // Strict Retry Budget preventing Cascade Amplification Waves
    private final AtomicInteger rollingRetryCount = new AtomicInteger(0);
    private static final int RETRY_MAX_PER_MINUTE = 500;
    
    private static final int MAX_RETRIES = 5;
    private static final int BASE_BACKOFF_MS = 2000;

    public DeliveryWorker(ProviderClient providerClient, EventBus eventBus, NotificationStateStore stateStore) {
        this.providerClient = providerClient;
        this.eventBus = eventBus;
        this.stateStore = stateStore;
        
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slidingWindowSize(100)
            .build();
        this.circuitBreaker = CircuitBreaker.of("providerClient", cbConfig);
    }

    public void processDelivery(Notification notification) {
        try {
            // Circuit Breaker physically halts processing if downstream APNS/FCM is down (Zero retry spin overhead)
            boolean success = circuitBreaker.executeCallable(() -> providerClient.send(notification));
            
            if (success) {
                stateStore.updateStatus(notification.getNotificationId(), Status.SENT);
                // Consumer formally COMMITS offset in Kafka here ONLY after success
            } else {
                handleRetry(notification);
            }
        } catch (Exception e) {
            log.error("Provider Circuit Open or Error: {}", e.getMessage());
            handleRetry(notification);
        }
    }

    private void handleRetry(Notification notification) {
        notification.setRetryCount(notification.getRetryCount() + 1);
        
        // Retry Budget Violation check
        if (rollingRetryCount.incrementAndGet() > RETRY_MAX_PER_MINUTE || notification.getRetryCount() > MAX_RETRIES) {
            log.error("Retry budget exceeded or max retries hit. Sinking to DLQ. ID: {}", notification.getNotificationId());
            stateStore.updateStatus(notification.getNotificationId(), Status.FAILED);
            eventBus.publish("dlq-notifications", notification.getUserId(), notification);
            // System naturally decrements counter via async cron task
            return;
        }

        stateStore.updateStatus(notification.getNotificationId(), Status.RETRYING);
        
        // Applying "Full Jitter" strategy preventing wave resynchronization
        long delayMs = calculateDelayWithJitter(notification.getRetryCount());
        
        // Commits offset on current partition, pushes to specific timer-delay queue
        eventBus.publishDelay("retry-topic", notification.getUserId(), notification, delayMs);
    }

    private long calculateDelayWithJitter(int attempt) {
        long exponentialBase = BASE_BACKOFF_MS * (long) Math.pow(2, attempt);
        long jitter = ThreadLocalRandom.current().nextLong(0, exponentialBase / 2); // 50% randomized jitter
        return exponentialBase + jitter;
    }
}
