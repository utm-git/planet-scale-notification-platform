package com.systemdesign.notification.infrastructure;

import com.systemdesign.notification.domain.Notification;
import com.systemdesign.notification.domain.Status;

public interface NotificationStateStore {
    void save(Notification notification);
    void updateStatus(String notificationId, Status status);
}
