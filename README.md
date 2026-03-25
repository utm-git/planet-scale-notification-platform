# 🌍 Planet-Scale Notification Platform: Staff-Level LLD

A globally-distributed, event-driven notification nervous system designed to handle **billions of notifications per day** with near-perfect reliability, strictly-enforced idempotency, and cross-region active-active durability.

This repository contains the **Low-Level Design (LLD)** implemented as a Spring Boot application, engineered to specifically survive catastrophic failure modes (retry storms, thundering herds, celebrity fanout hotspots) required for a FAANG staff-level architecture.

---

## 🏗️ High-Level Architecture

The system relies on decoupled stream processing and durable event logs. It is explicitly designed to **degrade gracefully** under extreme load, prioritizing data integrity over immediate delivery.

1. **Ingestion Gateway (With Backpressure)**: The entry point. It utilizes **Resilience4j RateLimiters** to actively shed load (HTTP 429) if ingestion exceeds the Kafka/Datastore write capacity. It leverages an **Edge Bloom Filter** to drop duplicates instantly without a DB hit.
2. **Event Bus (`acks=all`)**: The absolute source of truth. The gateway explicitly awaits `acks=all` (incorporating In-Sync Replicas) before returning 200 OK. Decouples ingestion from delivery ensuring **Zero Data Loss**.
3. **Fanout Worker (Consistent Hashing MapReduce)**: Replaces naive synchronous iteration. Capable of evaluating chunk events representing 100k+ users. To prevent "Celebrity Hotspots" (where viral events nuke a single partition), chunked messages are immediately partitioned via **Consistent Hashing by User ID**, ensuring a flat distribution across the cluster brokers.
4. **Delivery Worker (Retry Budgets & Circuit Breakers)**: The final edge worker. It implements **Exponential Backoff with Full Jitter** to prevent retry wave synchronization. Crucially, it deploys a **Strict Retry Budget** (e.g., max 500 retries/min overall) and **Resilience4j Circuit Breakers** to physically sever the connection to downed downstream providers (APNS/FCM), preventing cascade amplification completely.
5. **State Stores (Cassandra)**: Stores long-term states for "Offline Sync" and actual datastore deduplication locks using composite keys `(notification_id, user_id)` with strict 72-hour TTLs to prevent exploding scale.

---

## ⚙️ The Codebase Geography

- `domain/`: Contains the foundational data structures (`Notification`, `Priority`, `Channel`, `Status`) including orchestrator-injected `TraceID` and `SpanID` for E2E Observability.
- `infrastructure/`: Defines the critical distributed system boundaries abstracted as Java Interfaces (`EventBus`, `IdempotencyStore`, `NotificationStateStore`).
- `ingestion/`: The `IngestionGateway` handling Fast-Path Bloom Filter Deduplication + Backpressure Load Shedding.
- `fanout/`: The `FanoutWorker` utilizing Partition Scaling and Consistent Hashing.
- `delivery/`: The `DeliveryWorker` interacting with the `ProviderClient` protected by Circuit Breakers and global Retry Budgets.

---

## 🚀 Key Engineering Concepts Displayed

* **Exactly-Once Illusion Engine**: Pairs Producer-side deduplication (Bloom Filters + Cassandra `SETNX`) with Client-side SQLite rolling windows to discard in-flight retried packets silently.
* **Traffic Shaping & Provider Protection**: Instead of blindly retrying, the system uses Circuit Breakers to fail-fast. When recovering, it applies Full Jitter against an exponential timeline, completely eliminating thundering herds.
* **True Zero Data Loss**: Synchronous `acks=all` validation on the ingestion edge before client confirmation.
* **End-to-End Observability**: Strict propagation of W3C standard OpenTelemetry `TraceID` throughout the distributed object lifecycle.

---
*Generated as a Staff-Level System Design exercise.*
