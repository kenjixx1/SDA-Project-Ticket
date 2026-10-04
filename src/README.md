# src — Implementation (Deliverable 4)

**Slice 1 is built:** the core booking engine — atomic hold, 10-minute
expiry, checkout with a simulated PromptPay, and the events that connect them.
Slices 2 (queue) and 3 (more plug-ins, tickets, dashboard) are not built yet.

## How to run

Needs only a JDK, version 17 or newer. No Maven, no Gradle, no libraries.

```bash
./run.sh test   # compile and run all tests
./run.sh demo   # Kenji buys 2 × VIP; another fan's hold expires
```

Run from the repository root. Compiled classes go to `out/` (git-ignored).

## What is built

| Part | Class | Requirement |
|---|---|---|
| Event channel | `core/event/EventBus` + 5 events: `TicketHeld`, `PaymentCompleted`, `OrderConfirmed`, `HoldExpired`, `PaymentVoided` | §6.2 |
| Atomic hold, the only writer of held/sold | `core/inventory/InventoryService` | FR-08 … FR-11, INV-01 |
| Reservation lifecycle, confirm-or-expire race | `core/order/ReservationService` | FR-16, FR-17, R-E04-6 |
| 10-minute expiry that returns seats | `core/order/HoldExpiryScheduler` | FR-12 |
| Checkout, charge once | `core/order/CheckoutService` | FR-15, FR-18, FR-19 |
| Plug-in lookup by key | `core/registry/PluginRegistry` | NFR-08 |
| Simulated PromptPay | `plugin/payment/PromptPayPlugin` | ASM-04 |
| Create event and tiers | `admin/EventService` | FR-23 … FR-25 |
| Wiring (the only class that names a plug-in) | `EventFlow` | NFR-08 |

## What the tests prove

| Test | Proves |
|---|---|
| `NoOversellingTest` | 200 buyers race for the last 10 VIP seats: exactly 10 sold, 50 runs in a row (NFR-02) |
| `PaymentExpiryRaceTest` | Payment and expiry race for 200 holds: every hold ends paid **or** expired, never charged without seats |
| `HoldRulesTest` | 4-ticket limit, no partial holds, one token buys once, server-stamped 10 minutes |
| `HoldExpiryTest` | Seats return at exactly 10:00, not 9:59 |
| `CheckoutTest` | Happy path, double tap charged once, slow bank voided, declined card keeps the hold |
| `ArchitectureTest` | No class in `core/` imports `plugin/` (NFR-08) |

The overselling test has been checked against a broken hold: with the lock
removed, 42 buyers got the last 10 seats and the test failed.

## Not built yet

- `core/queue/` — waiting room and `UserAdmitted` (Slice 2)
- `PricingRule` plug-ins and `PricingService` (Slice 3)
- `ticket/` — digital tickets and `TicketIssued` (Slice 3)
- `SalesDashboardService` (Slice 3)
- A real database and a web API. Everything is in memory.

## Package layout

Matches `docs/06-architecture.md` §6.5.

```
th.ac.kmitl.sda.ticketqueue
├── core/           THE CORE SYSTEM — knows no plug-in by name
│   ├── event/      the event channel (EventBus) + every event type
│   ├── queue/      the waiting room
│   ├── inventory/  the ONLY writer of heldQuantity / soldQuantity
│   ├── order/      reservation lifecycle, hold expiry, checkout, pricing
│   └── registry/   key → plug-in lookup
├── contract/       THE SEAMS: PaymentProvider, PricingRule, PricingRequest
├── plugin/         plug-ins. core/ must never import this.
│   ├── payment/    PromptPay, Stripe  (simulated — ASM-04)
│   └── pricing/    EarlyBird, StudentDiscount, FanClubPresale
├── ticket/         issuing digital tickets
└── admin/          organizer: event management + dashboard
```

## The one rule the build must enforce

```
core/  ──MUST NEVER import──►  plugin/
```

This is the NFR-08 fitness function. It is not a guideline — `ArchitectureTest`
fails the build when it is broken. See `docs/06-architecture.md` §6.5.

## Conventions

- Java (CON-04), package root `th.ac.kmitl.sda.ticketqueue`
- Plug-ins are **compile-based** — a registry map, not runtime loading
- Payment providers are **simulated**. No real API keys, no network calls.
