# 🌍 Planet-Scale Notification Platform: Staff-Level LLD

A globally-distributed, event-driven notification nervous system designed to handle **billions of notifications per day** with near-perfect reliability, exactly-once delivery illusions, and cross-region active-active durability.

This repository contains the **Low-Level Design (LLD)** implemented as a Spring Boot application, simulating the core abstractions required for a FAANG staff-level architecture.

---

## 🏗️ High-Level Architecture

The system relies on decoupled stream processing and durable event logs to gracefully absorb massive scale, provider outages, and network partitioning.

1. **Ingestion Gateway**: The entry point. Handles `Idempotency` checks to drop duplicates instantly, checks priorities, and routes messages to the main durable event bus.
2. **Event Bus (Kafka Abstraction)**: The absolute source of truth. Decouples ingestion from delivery ensuring **Zero Message Loss**.
3. **Fanout Worker (MapReduce Pattern)**: Replaces naive synchronous iteration. Capable of evaluating a "chunk event" representing 100k+ users, querying their individual channel preferences, and spawning personalized delivery messages asynchronously.
4. **Delivery Worker (With Adaptive Jitter)**: The final edge worker. Interfaces with APNS/FCM/Email APIs. It implements **Exponential Backoff with Jitter** to protect external provider SLAs and avoid thundering herds against our own retry queues.
5. **State Stores (ScyllaDB / Cassandra / Redis Abstraction)**: Stores long-term states for "Offline Sync" (devices pulling missed notifications upon waking up) and deduplication locks.

---

## 📂 Codebase Geography

- `domain/`: Contains the foundational data structures (`Notification`, `Priority`, `Channel`, `Status`).
- `infrastructure/`: Defines the critical distributed system boundaries abstracted as Java Interfaces (`EventBus`, `IdempotencyStore`, `NotificationStateStore`).
- `ingestion/`: The `IngestionGateway` handling deduplication (Exactly-Once Phase 1).
- `fanout/`: The `FanoutWorker` resolving massive segments of targets down to individualized routed messages.
- `delivery/`: The `DeliveryWorker` interacting with the `ProviderClient` and managing the retry logic onto deferred-execution queues.

---

## 🚀 Key Engineering Concepts Displayed

* **Exactly-Once Illusion Engine**: True exactly-once across the internet is mathematically impossible. We pair Producer-side deduplication (via Redis `SETNX`) with a recommended Client-side SQLite rolling window to discard in-flight retried packets silently.
* **Streaming MapReduce Fanout**: Avoiding Out-Of-Memory (OOM) errors and blocking threads by refusing to loop over 100M users synchronously.
* **Traffic Shaping (Jitter)**: Delivery workers dynamically delay retries using randomized jitter against an exponential scale, preventing synchronized retry waves when a provider recovers from an outage.

---
*Generated as a System Design exercise.*
