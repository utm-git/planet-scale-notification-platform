package com.systemdesign.notification.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPayload {
    private String templateId;
    private Map<String, String> dynamicData;
}
