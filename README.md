# Donation Matching System

Event-driven backend that matches surplus food/goods donations to shelter requests. Built as three independently deployable Spring Boot services communicating over Kafka, with no direct database access between them.

The core problem it solves: donations are perishable and arrive in arbitrary quantities, requests rarely line up 1:1 with what's available, and a shelter that reserves a donation but never picks it up shouldn't permanently take that donation out of circulation. The matching logic allocates the soonest-expiring inventory first (FEFO) and supports a single request drawing from multiple donations over time.

## Services

**donation-service** (`:8081`) - donors submit lots (a physical batch of donated goods). Owns `donation_db`.

**request-service** (`:8082`) - shelters submit requests for a quantity of some item type. Owns `request_db`.

**matching-service** (`:8083`) - consumes events from both services, maintains its own read-only view of active lots and requests, and runs the allocation algorithm. Owns `matching_db`.

Each service has its own database. There are no foreign keys or queries across service boundaries - all cross-service communication happens through Kafka.

## Event flow

```
donation-service --publishes--> donation-lot-created --> matching-service
request-service  --publishes--> request-created      --> matching-service
```

When matching-service receives either event, it saves a local copy of the lot/request and immediately tries to match it: a new lot is checked against open requests (oldest first), a new request is checked against available lots (soonest-to-expire first). Matches are recorded as `Allocation` rows with a 48-hour pickup window. A single request can be partially fulfilled from several lots as they arrive.

Kafka messages carry no producer-specific type information - each service defines its own local copy of the event shape and deserializes based on the agreed JSON contract, not the sender's Java classes.

## Stack

- Java 21, Spring Boot 4.1
- Apache Kafka (KRaft mode, no ZooKeeper)
- PostgreSQL (one instance, three separate databases)
- Docker Compose for local infrastructure

## Running locally

```
docker compose up -d
```

This starts Postgres, Kafka, and Kafka UI (`localhost:8090`). Then run each service from IntelliJ or `mvn spring-boot:run` inside `services/donation-service`, `services/request-service`, and `services/matching-service`.

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

`itemType` is a fixed set of categories (see `ItemType` in each service) rather than free text, since the matching algorithm relies on exact equality between a lot and a request.

Both services also expose `GET /lots`, `GET /lots/{id}`, `GET /requests`, `GET /requests/{id}`.

## Known gaps

- Allocations in matching-service don't yet propagate back to update `donation-service`/`request-service`'s own records (their `quantityAvailable`/`quantityFulfilled` stay at creation-time values).
- No handling yet for expired pickup windows or expired lots.
- Kafka consumers aren't idempotent against redelivered messages.
- No outbox pattern - a crash between saving an allocation and publishing its event is a small, currently-accepted gap.

Design rationale for the above and everything else is in `design_decisions_log.md`.
