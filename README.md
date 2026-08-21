# Donation Matching System

[![CI](https://github.com/tirth-sohagiya/donation-matching-system/actions/workflows/ci.yml/badge.svg)](https://github.com/tirth-sohagiya/donation-matching-system/actions/workflows/ci.yml)

Event-driven backend that matches surplus food/goods donations to shelter requests. Built as three independently deployable Spring Boot services communicating over Kafka, with no direct database access between them.

The core problem it solves: donations are perishable and arrive in arbitrary quantities, requests rarely line up 1:1 with what's available, and a shelter that reserves a donation but never picks it up shouldn't permanently take that donation out of circulation. The matching engine allocates the soonest-expiring inventory first (FEFO), supports a single request drawing from multiple donations over time, and automatically releases and re-matches allocations that go unclaimed past their pickup window.

## Architecture

```
                    ┌────────────────────┐
   POST /lots       │  donation-service   │
   ───────────────► │     (:8081)         │
                    └─────────┬──────────┘
                              │ DonationLotCreatedEvent
                              ▼
                    ┌────────────────────┐
                    │  matching-service   │
   POST /requests   │     (:8083)         │
   ───────────────► │  FEFO allocation    │
                    │  pessimistic locking│
                    └─────────┬──────────┘
                              │ AllocationLifecycleEvent
                    ┌─────────┴──────────┐
                    ▼                    ▼
          ┌──────────────────┐  ┌──────────────────┐
          │ donation-service  │  │ request-service   │
          │ updates quantity  │  │ updates quantity  │
          └──────────────────┘  └──────────────────┘
```

Each service owns its own Postgres database (`donation_db`, `request_db`, `matching_db`). There are no foreign keys or queries across service boundaries - all cross-service communication happens through Kafka, and each service deserializes events into its own local copy of the event shape rather than relying on the producer's Java classes.

### Allocation lifecycle

1. A lot or request is created via REST and published to Kafka.
2. `matching-service` consumes the event, stores a local read-model copy, and immediately searches for a match (soonest-expiring lot for a new request; oldest-waiting request for a new lot).
3. A match creates an `Allocation` with a 24-hour pickup window and publishes an `AllocationLifecycleEvent` (`CREATED`).
4. `donation-service` and `request-service` each consume that event to update their own quantity fields, deduplicated against redelivery via a `ProcessedAllocationRelease` table per service.
5. A scheduled poller in `matching-service` expires any allocation whose pickup window has passed, publishes an `EXPIRED` event, and re-runs matching against the now-freed lot.

## Tech stack

- Java 21, Spring Boot 4.1
- Apache Kafka 4.2 (KRaft mode, no ZooKeeper)
- PostgreSQL, one instance with three separate databases
- Micrometer for metrics, exposed via Spring Actuator
- JUnit 5 + Testcontainers for integration tests against real Postgres
- Docker / Docker Compose

## Running locally

```
docker compose up --build
```

This builds and starts all three services along with Postgres, Kafka, and Kafka UI (`localhost:8090`). Health checks:

```
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

## API

**POST /lots** (donation-service, `:8081`)
```json
{
  "donorId": "uuid",
  "itemType": "CANNED_VEGETABLES",
  "quantityTotal": 10,
  "expiryDate": "2026-12-01T00:00:00Z"
}
```

**POST /requests** (request-service, `:8082`)
```json
{
  "shelterId": "uuid",
  "itemType": "CANNED_VEGETABLES",
  "quantityRequested": 5
}
```

`itemType` is a fixed enum shared across services rather than free text, since matching relies on exact equality between a lot and a request.

**POST /allocations/{id}/confirm** (matching-service, `:8083`) - marks an allocation as picked up, taking it out of the expiry poller's scope.

Both `donation-service` and `request-service` also expose paginated `GET /lots` / `GET /requests` and `GET /lots/{id}` / `GET /requests/{id}`. `matching-service` exposes `GET /allocations?lotId=...`.

## Testing

28 tests across the three services, run against real Postgres via Testcontainers rather than mocks:

- FEFO ordering, partial fulfillment, and capacity-cap correctness for the core allocation algorithm
- Pessimistic-locking concurrency test proving two simultaneous allocation attempts against the same lot cannot oversell its capacity
- Idempotent event handling in `donation-service` and `request-service`, including a regression test for the release-then-rematch ordering bug the lifecycle event model fixed

```
mvn test
```
from each service directory.

## Metrics

Each service exposes Micrometer metrics via `/actuator/metrics`, including:

- `matching.allocation.duration` - timer around the core allocation transaction
- `matching.allocations.created` / `.rejected` (tagged by rejection reason) / `.confirmed` / `.expired`
- `donation.allocation.events` / `request.allocation.events` - tagged by event type and idempotency outcome

## Benchmarks

- **In-process concurrency:** 100 threads racing to allocate against a single shared lot sized to exactly their combined demand - 1000/1000 units allocated correctly with zero oversell, at 301.6 allocations/sec.
- **End-to-end throughput:** real HTTP → Kafka → matching pipeline against the fully containerized system, measured via the `matching.allocations.created` metric - 7.58 matches/sec sustained.

## Project structure

```
services/
  donation-service/   owns donation_db, exposes /lots
  request-service/     owns request_db, exposes /requests
  matching-service/    owns matching_db, runs the allocation engine
benchmark/
  end_to_end_throughput.py
docker-compose.yml
```
