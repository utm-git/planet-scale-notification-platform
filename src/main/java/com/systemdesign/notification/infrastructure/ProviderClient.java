package com.systemdesign.notification.infrastructure;

import com.systemdesign.notification.domain.Notification;
import com.systemdesign.notification.delivery.ProviderException;

public interface ProviderClient {
    boolean send(Notification notification) throws ProviderException;
}
