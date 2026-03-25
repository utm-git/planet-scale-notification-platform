package com.systemdesign.notification.infrastructure;

public interface IdempotencyStore {
    boolean acquireLock(String idempotencyKey, long ttlSeconds);
}
