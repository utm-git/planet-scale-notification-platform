package com.systemdesign.notification.infrastructure;

public interface IdempotencyStore {
    /**
     * Highly scalable fast-path duplicate rejection utilizing edge Bloom Filters.
     * Prevents hotspots at scale before ever reaching the datastore.
     */
    boolean definitelySeen(String compoundIdempotencyKey);

    /**
     * Acquires real deduplication persistence utilizing scalable bucketing.
     * Storage should be Cassandra (or strictly modeled Key-Value), avoiding single-shard Redis limits.
     * @param compoundIdempotencyKey Format: {notification_id}_{user_id}
     * @param ttlSeconds Bounded TTL (e.g. 72 hours) matching platform limits
     */
    boolean acquireLock(String compoundIdempotencyKey, long ttlSeconds);
}
