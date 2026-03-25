package com.systemdesign.notification.fanout;

import com.systemdesign.notification.domain.Channel;
import com.systemdesign.notification.domain.Notification;
import com.systemdesign.notification.domain.Priority;
import com.systemdesign.notification.infrastructure.EventBus;
import com.systemdesign.notification.infrastructure.NotificationStateStore;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FanoutWorker {
    private final EventBus eventBus;
    private final NotificationStateStore stateStore;
    
    // Constant configuring partition distribution logic
    private static final int NUM_KAFKA_PARTITIONS = 1000;

    public FanoutWorker(EventBus eventBus, NotificationStateStore stateStore) {
        this.eventBus = eventBus;
        this.stateStore = stateStore;
    }

    public void processFanoutChunk(String baseIdempotencyKey, List<String> targetUserIds, Priority priority) {
        for (String userId : targetUserIds) {
            String childIdempotency = baseIdempotencyKey + ":" + userId;
            
            Notification individualizedNotif = new Notification(childIdempotency, userId, priority);
            individualizedNotif.setChannel(resolveUserChannelPreference(userId));
            
            stateStore.save(individualizedNotif);

            String channelTopic = "deliver-" + individualizedNotif.getChannel().name().toLowerCase();
            
            // Consistent Hashing partition sizing avoiding Celebrity hotspots
            String deterministicPartitionKey = calculateConsistentHashPartition(userId);
            eventBus.publish(channelTopic, deterministicPartitionKey, individualizedNotif);
        }
    }

    /**
     * Prevents single partition bottlenecks during viral 100M fanouts.
     * By sharding strictly on User ID rather than Campaign ID, 
     * messages naturally spread across the cluster.
     */
    private String calculateConsistentHashPartition(String userId) {
        int hash = Math.abs(userId.hashCode());
        int partition = hash % NUM_KAFKA_PARTITIONS;
        return "partition-" + partition;
    }

    private Channel resolveUserChannelPreference(String userId) {
        // Mock DB lookup
        return Channel.APNS;
    }
}
