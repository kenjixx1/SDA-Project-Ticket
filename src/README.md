# src — Implementation (Deliverable 4)

**Slices 1, 2 and 3 are built:**

- **Slice 1 — the booking engine:** atomic hold, 10-minute expiry, checkout
  with a simulated PromptPay, and the events that connect them.
- **Slice 2 — the waiting room:** join, place in line, FIFO admission in
  batches, and the hand-over from an admitted token to a hold.

- **Slice 3 — tickets and more plug-ins:** signed digital tickets, a card
  payment plug-in, the Early Bird pricing rule, and giving seats back early.

The organizer dashboard is not built yet.

**The presentation layer is built too** (`web/` and `resources/public/`). It
runs on a fake booking engine unless started with `ENGINE=core`; see `docs/09-web-api.md`. Run it with
`mvn -q compile exec:java` and open http://localhost:7070/ (Kenji, the customer)
or http://localhost:7070/organizer.html (Chai, the organizer). Add `?dev` to
either address for the demo controls.

## How to run

Needs only a JDK, version 17 or newer. No Maven, no Gradle, no libraries.

```bash
./run.sh test   # compile and run all tests
./run.sh demo   # Kenji queues, holds 2 × VIP with Early Bird, pays, gets 2 tickets; a fan's hold expires
```

Run from the repository root. Compiled classes go to `out/` (git-ignored).

## What is built

| Part | Class | Requirement |
|---|---|---|
| Event broker | `core/event/EventBus` + 8 events: `UserAdmitted`, `TicketHeld`, `PaymentCompleted`, `OrderConfirmed`, `TicketIssued`, `HoldExpired`, `HoldReleased`, `PaymentVoided` | §6.2 |
| Waiting room: join, place, reconnect | `core/queue/QueueService` | FR-01 … FR-04, FR-06 |
| FIFO admission in batches, idle expiry | `core/queue/AdmissionScheduler` | FR-05, FR-30, NFR-09, ASM-07 |
| Atomic hold, the only writer of held/sold; needs an admitted token | `core/inventory/InventoryService` | FR-08 … FR-11, INV-01, locked decision 3 |
| Reservation lifecycle, confirm-or-expire race, early release | `core/order/ReservationService` | FR-16, FR-17, R-E04-6 |
| Price at hold time: tier price, then every pricing rule | `core/order/PricingService` | FR-14 |
| 10-minute expiry that returns seats | `core/order/HoldExpiryScheduler` | FR-12 |
| Checkout, charge once | `core/order/CheckoutService` | FR-15, FR-18, FR-19 |
| Plug-in lookup by key | `core/registry/PluginRegistry` | NFR-08 |
| Simulated PromptPay and card payment | `plugin/payment/PromptPayPlugin`, `CardPlugin` | FR-15, ASM-04 |
| Early Bird −10% | `plugin/pricing/EarlyBirdRule` | FR-14, ASM-12 |
| One signed ticket per seat, and checking a ticket | `ticket/TicketService` | FR-20, FR-21, NFR-06 |
| Create event and tiers, open the sale, set the admission rate | `admin/EventService` | FR-23 … FR-25, FR-30 |
| Wiring (the only class that names a plug-in) | `EventFlow` | NFR-08 |

## What the tests prove

| Test | Proves |
|---|---|
| `NoOversellingTest` | 200 buyers race for the last 10 VIP seats: exactly 10 sold, 50 runs in a row (NFR-02) |
| `PaymentExpiryRaceTest` | Payment and expiry race for 200 holds: every hold ends paid **or** expired, never charged without seats |
| `HoldRulesTest` | 4-ticket limit, no partial holds, one token buys once, server-stamped 10 minutes |
| `HoldExpiryTest` | Seats return at exactly 10:00, not 9:59 |
| `CheckoutTest` | Happy path, double tap charged once, slow bank voided, declined card keeps the hold |
| `QueueTest` | Kenji behind 4,181 fans is at 4,182; ten tabs get one token; reconnect keeps the place; 10,000 fans admitted in exactly the order they joined (NFR-09); a waiting token cannot hold; idle tokens expire; join → pay end to end |
| `QueueLoadTest` | 40,000 fans join at once: every one gets a token, inside the NFR-03 time limits (NFR-01, measured in process) |
| `TicketTest` | Two seats paid → two tickets with their own codes; no tickets before paying; an edited ticket fails the check (NFR-06); paying twice issues tickets once |
| `PricingTest` | Early Bird −10% inside 30 minutes, full price after; a new promotion plugs in with no core change (NFR-08); PromptPay is asked for the discounted total |
| `ReleaseTest` | Seats given back go on sale at once and end the token; a released hold cannot be paid; a paid order cannot be released |
| `ArchitectureTest` | No class in `core/` imports `plugin/` (NFR-08) |

The overselling test has been checked against a broken hold: with the lock
removed, 42 buyers got the last 10 seats and the test failed.

## Not built yet

- `SalesDashboardService` (the organizer dashboard)
- A real database. Everything is in memory (ASM-08).

`QueueLoadTest` measures the code in one Java process, not over a network.

## Package layout

Matches `docs/06-architecture.md` §6.5.

```
eventflow
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
├── admin/          organizer: event management + dashboard
└── web/            presentation layer: routes, BookingGateway, the fake (docs/09)

src/main/resources/public/   the customer screens (plain HTML, CSS, JavaScript)
```

## The one rule the build must enforce

```
core/  ──MUST NEVER import──►  plugin/
core/  ──MUST NEVER import──►  web/
```

This is the NFR-08 fitness function. It is not a guideline — `ArchitectureTest`
(run by `./run.sh test`) and `ArchitectureRuleTest` (run by `mvn test`) fail the
build when it is broken. See `docs/06-architecture.md` §6.5.

## Conventions

- Java (CON-04), package root `eventflow`
- Plug-ins are **compile-based** — a registry map, not runtime loading
- Payment providers are **simulated**. No real API keys, no network calls.
