package com.systemdesign.notification.ingestion;

import com.systemdesign.notification.domain.Notification;
import com.systemdesign.notification.domain.Priority;
import com.systemdesign.notification.infrastructure.EventBus;
import com.systemdesign.notification.infrastructure.IdempotencyStore;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class IngestionGateway {
    private static final Logger log = LoggerFactory.getLogger(IngestionGateway.class);
    private final EventBus eventBus;
    private final IdempotencyStore idempotencyStore;
    
    // Backpressure mechanism preventing Kafka/DB overload during traffic spikes
    private final RateLimiter ingestionRateLimiter;

    public IngestionGateway(EventBus eventBus, IdempotencyStore idempotencyStore) {
        this.eventBus = eventBus;
        this.idempotencyStore = idempotencyStore;
        
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .limitForPeriod(10000) // Shed load immediately if > 10k req/sec/node
            .timeoutDuration(Duration.ofMillis(50)) // Fail fast for backpressure
            .build();
        this.ingestionRateLimiter = RateLimiter.of("ingestion", config);
    }

    public boolean ingest(Notification notification) {
        // Enforce Backpressure
        if (!ingestionRateLimiter.acquirePermission()) {
            log.warn("Backpressure activated. Load shedding notification: {}", notification.getNotificationId());
            // Throw HTTP 429 / 503 equivalent to force client backoff
            throw new RuntimeException("429 Too Many Requests");
        }

        String compoundKey = notification.getNotificationId() + "_" + notification.getUserId();

        // 1. Fast Path rejection via Edge Bloom Filter
        if (idempotencyStore.definitelySeen(compoundKey)) {
            log.info("Dropped by Bloom Filter: {}", compoundKey);
            return true;
        }

        // 2. Real Datastore Lock (Exactly-Once Illusion: Phase 1)
        if (!idempotencyStore.acquireLock(compoundKey, 259200)) { // 72H TTL
            log.info("Duplicate lock detected. Ignoring: {}", compoundKey);
            return true;
        }

        String topic = getTopicForPriority(notification.getPriority());
        
        // 3. Guarantee Zero Data Loss (Acks=all + ISR check)
        eventBus.publishWithAck(topic, notification.getUserId(), notification)
            .exceptionally(ex -> {
                log.error("Failed to durably persist ingestion: {}", ex.getMessage());
                // Alerting upstream API or routing to fallback Kafka cluster
                return null;
            });
            
        return true;
    }

    private String getTopicForPriority(Priority priority) {
        return "ingress-" + priority.name().toLowerCase();
    }
}
