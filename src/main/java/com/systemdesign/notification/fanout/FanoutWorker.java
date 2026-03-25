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
            eventBus.publish(channelTopic, userId, individualizedNotif);
        }
    }

    private Channel resolveUserChannelPreference(String userId) {
        // Mock DB lookup
        return Channel.APNS;
    }
}
