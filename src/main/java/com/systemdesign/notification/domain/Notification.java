package com.systemdesign.notification.domain;

import lombok.Data;
import java.util.UUID;

@Data
public class Notification {
    private String notificationId;
    private String idempotencyKey;
    private String userId;
    private Channel channel;
    private Priority priority;
    private NotificationPayload payload;
    private Status status;
    private int retryCount;

    // Orchestrated Observability (OpenTelemetry simulation)
    private String traceId;
    private String spanId;

    public Notification(String idempotencyKey, String userId, Priority priority) {
        this.notificationId = UUID.randomUUID().toString();
        this.idempotencyKey = idempotencyKey;
        this.userId = userId;
        this.priority = priority;
        this.status = Status.PENDING;
        this.retryCount = 0;
        
        // At ingestion, we would typically extract X-B3-TraceId from headers
        // but here we initialize a fresh sequence for the simulation
        this.traceId = UUID.randomUUID().toString();
        this.spanId = UUID.randomUUID().toString();
    }
}
