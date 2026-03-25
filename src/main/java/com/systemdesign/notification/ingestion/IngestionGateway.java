package com.systemdesign.notification.ingestion;

import com.systemdesign.notification.domain.Notification;
import com.systemdesign.notification.domain.Priority;
import com.systemdesign.notification.infrastructure.EventBus;
import com.systemdesign.notification.infrastructure.IdempotencyStore;
import org.springframework.stereotype.Service;

@Service
public class IngestionGateway {
    private final EventBus eventBus;
    private final IdempotencyStore idempotencyStore;

    public IngestionGateway(EventBus eventBus, IdempotencyStore idempotencyStore) {
        this.eventBus = eventBus;
        this.idempotencyStore = idempotencyStore;
    }

    public boolean ingest(Notification notification) {
        // 1. Deduplication Check (Exactly-Once Illusion: Phase 1)
        if (!idempotencyStore.acquireLock(notification.getIdempotencyKey(), 86400)) {
            System.out.println("Duplicate detected. Ignoring: " + notification.getIdempotencyKey());
            return true;
        }

        // 2. Static Routing to relevant priority topic
        String topic = getTopicForPriority(notification.getPriority());
        
        // 3. Publish to Durable Log (Kafka) partitioned by User ID
        eventBus.publish(topic, notification.getUserId(), notification);
        return true;
    }

    private String getTopicForPriority(Priority priority) {
        return "ingress-" + priority.name().toLowerCase();
    }
}
