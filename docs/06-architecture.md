# 06 · Architecture

**Deliverable 3 of 6 — Design**

Two architectural styles, both required by the brief. This document says which
parts of the system are which, and — more importantly — **which requirement
forced each decision**.

---

## 6.1 The shape of the whole thing

Before the styles, the shape.

```
  40,000 people
       │
       ▼
  ┌─────────────────┐
  │  WAITING ROOM   │   absorbs the spike · issues tokens · reports positions
  │  (queue)        │   does nothing else
  └────────┬────────┘
           │  admits ~200 per second
           ▼
  ┌──────────────────────────────────────────────────────┐
  │  BOOKING ENGINE  =  the microkernel CORE SYSTEM      │
  │                                                       │
  │  its happy path  =  the PIPELINE                      │
  │                                                       │
  │  Producer → Tester → Transformer → Consumer           │
  │                          │                            │
  └──────────────────────────┼────────────────────────────┘
                             │  looks up by key
                    ┌────────┴────────┐
                    ▼                 ▼
             PricingRule        PaymentProvider
              plug-ins            plug-ins
```

**The one idea to take from this picture:** the waiting room exists so the
booking engine never experiences a spike. Everything expensive happens at 200
requests per second, not 40,000. That single move is what makes NFR-01
achievable without buying machines that idle for 364 days a year.

---

## 6.2 Style 1 — Pipeline  *(slide 8.1)*

### What the style is

Lecture 8.1 calls it **pipes and filters**.

- **Filters** are self-contained, independent of each other, generally
  stateless, and each **performs one task only**.
- **Pipes** are the channels between them: **unidirectional and
  point-to-point**, one source in, one destination out.
- The slides note that architects favour **small amounts of data** on the
  pipes, for performance.

There are exactly **four filter types**, and the slides define each one.

**Analogy.** A car wash. The car goes in one end and comes out the other. Wash,
then rinse, then dry, then polish. Each machine does one job and knows nothing
about the others. No car ever goes backwards.

### Why this style fits here

An order only ever moves forwards: admitted → checked and held → priced →
saved. It never needs to go back a stage. And because each filter does exactly
one job, **the inventory logic lives in exactly one place** — which is what
makes NFR-02 (no overselling) something we can actually prove rather than hope
for.

### The four filters

| Slide 8.1 type | The slide's definition | Our filter | What it does |
|---|---|---|---|
| **Producer** | *"The starting point of a process, outbound only, sometimes called the source"* | `QueueIntakeFilter` | A token is admitted. Creates an `OrderContext` and sends it down the pipe. The pipeline starts here and nowhere else. |
| **Tester** | *"Accepts input, tests one or more criteria, then optionally produces output, based on the test"* | `AvailabilityHoldFilter` | Checks the tier has enough seats and takes the **atomic hold**. Produces output **only if the hold succeeded**. |
| **Transformer** | *"Accepts input, optionally performs a transformation on some or all of the data, then forwards it"* | `PricingFilter` | Applies the tier price, then offers the order to every registered `PricingRule` plug-in. Changes `totalAmount`. Touches nothing else. |
| **Consumer** | *"The termination point... could persist the final result of the pipeline process to a database"* | `OrderPersistenceFilter` | Writes the `Reservation`. End of the line. |

```
 ┌───────────────┐   ┌─────────────────────┐   ┌───────────────┐   ┌──────────────────────┐
 │   PRODUCER    │   │       TESTER        │   │  TRANSFORMER  │   │      CONSUMER        │
 │ QueueIntake   │──►│ AvailabilityHold    │──►│   Pricing     │──►│  OrderPersistence    │
 │               │   │                     │   │               │   │                      │
 │ token admitted│   │ atomic hold: 2 or 0 │   │ tier price +  │   │ Reservation written  │
 │ → OrderContext│   │ held 10 → 12        │   │ pricing rules │   │ state = HELD         │
 └───────────────┘   └──────────┬──────────┘   └───────────────┘   └──────────────────────┘
                                │ hold failed
                                ▼
                     ┌─────────────────────┐
                     │   CONSUMER          │
                     │ RejectionConsumer   │   tells Kenji why · nothing written
                     └─────────────────────┘
```

### The thing on the pipes

Slide 8.1 asks for small payloads. One small record travels the whole
pipeline:

```java
record OrderContext(
    String  tokenId,
    String  tierId,
    int     quantity,
    Money   unitPrice,      // filled by PricingFilter
    Money   totalAmount,    // filled by PricingFilter
    String  reservationId   // filled by AvailabilityHoldFilter on success
) {}
```

No object graphs, no entity loaded and passed around. Each filter fills in its
own part and forwards.

### The four risks from slide 8.1, answered

The slides list four common risks for this style. A design that names them and
does not answer them has not used the style — it has only drawn it.

| Risk (slide 8.1) | Our answer |
|---|---|
| **Overloading filters with too much responsibility** | `AvailabilityHoldFilter` checks and holds — and does **not** price, persist or notify. `PricingFilter` changes money and nothing else. If a filter needs a second verb in its name, it is doing two jobs. |
| **Introducing bidirectional communication** | No filter calls the one before it. A failed hold does not send the order backwards; it leaves sideways through `RejectionConsumer`, which is a **second consumer**, not a return pipe. |
| **Handling error conditions** | Every failure ends the pipeline at a consumer. `RejectionConsumer` is a real, named terminal — errors are a path through the design, not an exception thrown out of it. |
| **Managing the contracts between filters** | One record, `OrderContext`, is the only contract. It is defined in the core and changing it is a deliberate, visible act. |

### Data topology

Slide 8.1 offers a choice: one database, or one per filter. **This design uses
a single database.**

The reason is INV-01. `heldQuantity` and `soldQuantity` must be correct at
every instant; splitting inventory across separate stores would make the
atomic hold a distributed problem, and a distributed atomic hold is a far
harder thing to get right than the one this project needs.

---

## 6.3 Style 2 — Microkernel  *(slide 8.2)*

### What the style is

Lecture 8.2 calls it the **plug-in architecture**. Two components:

- A **core system** — the slides give two definitions: *"the minimal
  functionality required to run the system"*, and *"the happy path: a general
  processing flow through the application that involves little or no custom
  processing."*
- **Plug-in components** — *"standalone, independent components that contain
  specialized processing... used to isolate highly volatile code"*. They must
  be **independent of each other**.

**Analogy.** A power drill. The motor and the handle are the core — they never
change. The drill bits are plug-ins. You swap a wood bit for a masonry bit
without opening the motor, and the bits do not need to know about each other.

### Why this style fits here

Payment methods and promotions are the **volatile** parts. PromptPay today,
another wallet next year. Early-bird this event, student discount the next,
fan-club presale the one after. The booking engine — queue, holds, order
lifecycle — does not change at all between those events.

Slide 8.2 describes exactly the code we are avoiding, with its recycling
example:

```java
// slide 8.2 — what NOT to write
public void assessDevice(String deviceID) {
    if (deviceID.equals("iPhone6s"))      { assessiPhone6s(); }
    else if (deviceID.equals("iPad1"))    { assessiPad1(); }
    else if (deviceID.equals("Galaxy5"))  { assessGalaxy5(); }
    ...
}
```

Here that same mistake would read `if (method.equals("stripe"))` inside the
booking engine, and every new payment method would mean editing the one class
that must never break.

### The core system

**`th.ac.kmitl.sda.ticketqueue.core`** — the booking engine.

It owns:
- queue admission,
- the atomic hold and the inventory counters,
- the reservation lifecycle (`HELD` → `CONFIRMED` / `EXPIRED` / `RELEASED`),
- the pipeline itself.

**Its happy path is the pipeline from §6.2.** That is not two styles bolted
together — it is exactly what slide 8.2 says a core system *is*: a general
processing flow with little or no custom processing in it.

The core does not contain the word "Stripe", "PromptPay" or "EarlyBird"
anywhere.

### The two plug-in seams

Slide 8.2 warns that plug-ins should be **independent of each other**. Both
contracts below are one-method interfaces, so a plug-in has nothing to talk to
except the core.

```java
package th.ac.kmitl.sda.ticketqueue.contract;

/** Seam 1 — how money is taken. One implementation per payment method. */
public interface PaymentProvider {
    String key();                                   // "promptpay", "stripe"
    PaymentResult authorise(PaymentRequest request); // carries the idempotency key
    void          capture(String providerReference);
    void          voidAuthorisation(String providerReference); // FR-17
}

/** Seam 2 — how a price is changed. One implementation per promotion. */
public interface PricingRule {
    String key();                                   // "early-bird"
    boolean appliesTo(OrderContext order);
    Money   apply(Money currentTotal, OrderContext order);
}
```

| Seam | Plug-ins | Used by |
|---|---|---|
| `PaymentProvider` | `PromptPayPlugin`, `StripePlugin` | Checkout (UC-03) |
| `PricingRule` | `EarlyBirdRule`, `StudentDiscountRule`, `FanClubPresaleRule` | `PricingFilter` (the Transformer) |

**These two are the only seams.** NFR-08 says so, and §2.4 explains why:
Lecture 3.1 warns that supporting too many characteristics complicates the
design for little benefit. A system where everything is pluggable is a system
nobody can read.

### The registry — how the core finds a plug-in

Slide 8.2's fix, applied directly:

```java
// core/registry/PluginRegistry.java  — inside the core
public PaymentResult pay(String providerKey, PaymentRequest request) {
    PaymentProvider provider = paymentProviders.get(providerKey);
    if (provider == null) {
        throw new UnknownPaymentProviderException(providerKey);
    }
    return provider.authorise(request);
}
```

The core holds a map from key to plug-in. It looks one up and calls it. There
is no `if` chain, and no class name of any payment method appears in the core.

**Adding PayPal** is then: write `PayPalPlugin`, add one line to the registry
wiring. Zero files changed in the core. That is the NFR-08 metric, and it is
checkable.

### Compile-based, not runtime

Slide 8.2 gives both options:

- **Runtime plug-ins** — added or removed without redeploying, usually managed
  by a framework such as Spring.
- **Compile-based plug-ins** — *"much simpler to manage but require the entire
  monolithic application to be redeployed when modified"*.

**This design uses compile-based plug-ins**, communicating point-to-point,
implemented as Java packages.

**Why.** Runtime loading buys the ability to add a payment method without a
restart. Nothing in the requirements asks for that — a ticketing platform
deploys between sales, not during one. Runtime loading costs class-loader
machinery and a framework, and would make the structural test in NFR-08 harder
to write, not easier. The architectural benefit is identical; the cost is not.

This is a deliberate trade, recorded here so it can be defended.

---

## 6.4 How the two styles fit together

They are not side by side. One is inside the other.

| Layer | Style | What it is |
|---|---|---|
| Waiting room | — | Absorbs the spike. Plain, small, and in front of everything. |
| Booking engine | **Microkernel core system** | Queue admission, holds, inventory, order lifecycle. |
| ↳ its happy path | **Pipeline** | Producer → Tester → Transformer → Consumer. |
| Around it | **Microkernel plug-ins** | `PaymentProvider` and `PricingRule`. |

The join is at one filter and one step:

- The **Transformer** (`PricingFilter`) asks the registry for every
  `PricingRule` and offers each one the order.
- **Checkout** (UC-03 step 5) asks the registry for one `PaymentProvider` by
  key.

Everywhere else, the two styles do not touch.

---

## 6.5 Java package layout

Package root: `th.ac.kmitl.sda.ticketqueue` (CON-04).

```
th.ac.kmitl.sda.ticketqueue
├── queue/                    the waiting room
│   ├── QueueToken.java
│   ├── QueueService.java             issue · position · admit
│   └── AdmissionScheduler.java       lets N through per second
│
├── core/                     ◄── THE CORE SYSTEM. Knows no plug-in by name.
│   ├── pipeline/
│   │   ├── Filter.java               the shared filter interface
│   │   ├── OrderContext.java         the only thing on the pipes
│   │   ├── QueueIntakeFilter.java        PRODUCER
│   │   ├── AvailabilityHoldFilter.java   TESTER
│   │   ├── PricingFilter.java            TRANSFORMER
│   │   ├── OrderPersistenceFilter.java   CONSUMER
│   │   └── RejectionConsumer.java        CONSUMER (failure terminal)
│   ├── inventory/
│   │   └── InventoryService.java     ◄── the ONLY writer of held/sold
│   ├── order/
│   │   ├── Reservation.java
│   │   ├── ReservationService.java   HELD → CONFIRMED / EXPIRED / RELEASED
│   │   └── HoldExpiryScheduler.java  ◄── UC-02 ext. 6a. Returns the seats.
│   └── registry/
│       └── PluginRegistry.java       key → plug-in
│
├── contract/                 ◄── THE SEAMS. Core and plug-ins both depend on these.
│   ├── PaymentProvider.java
│   └── PricingRule.java
│
├── plugin/                   ◄── PLUG-INS. Nothing in core/ may import this.
│   ├── payment/
│   │   ├── PromptPayPlugin.java      simulated (ASM-04)
│   │   └── StripePlugin.java         simulated (ASM-04)
│   └── pricing/
│       ├── EarlyBirdRule.java
│       ├── StudentDiscountRule.java
│       └── FanClubPresaleRule.java
│
├── ticket/
│   ├── DigitalTicket.java
│   └── TicketSigningService.java     signed, single-use payload (FR-21)
│
└── admin/
    ├── EventService.java             UC-05, UC-06
    └── SalesDashboardService.java    UC-07
```

### The one dependency rule

```
  plugin/  ──depends on──►  contract/  ◄──depends on──  core/

  core/  ──MUST NEVER depend on──►  plugin/
```

This is not a guideline. It is the NFR-08 fitness function: the build fails if
any class under `core/` imports anything under `plugin/`.

Lecture 5.1 calls this a **structural measure** — a check on the shape of the
code rather than its behaviour. It is also, in the words of that lecture, how
you *govern* a characteristic: make the rule automatic, so it cannot quietly
stop being true.

---

## 6.6 Where each requirement is enforced

The point of this table: every architectural decision has exactly one home,
and nothing important is enforced in two places.

| What must be true | Enforced by | One place? |
|---|---|---|
| **INV-01 · No overselling** | `InventoryService` — the only writer of `heldQuantity` and `soldQuantity` | **Yes.** This is the most important row in the document. |
| FR-08 · Holds are atomic | `InventoryService`, one conditional update | Yes |
| FR-12 · Expired holds return seats | `HoldExpiryScheduler` | Yes |
| FR-16 · Confirm only if still held | `ReservationService`, rule R-E04-6 | Yes |
| FR-17 · Void a late authorisation | `ReservationService` → `PaymentProvider.voidAuthorisation` | Yes |
| FR-18 · Charge once | Idempotency key, `PaymentRequest` → provider | Yes |
| FR-03 · One token per account per event | `QueueService` | Yes |
| FR-05 · First in, first admitted | `AdmissionScheduler` | Yes |
| FR-21 · Signed ticket | `TicketSigningService` | Yes |
| FR-26 · No price change while on sale | `EventService` | Yes |
| FR-28 · No reduction below sold + held | `EventService`, reading `InventoryService` | Yes |
| NFR-08 · Two seams only | `contract/` + the build rule in §6.5 | Yes |

---

## 6.7 The trade-offs, stated

An architecture document that lists only benefits has not made any choices.

| Decision | What it buys | What it costs |
|---|---|---|
| Inventory drops at **hold**, not payment | Overselling becomes structurally impossible | Seats sit unavailable during abandoned checkouts. Mitigated by the 10-minute limit — which is why FR-12 is not optional. |
| Single database for inventory | A genuinely atomic hold, easy to prove | Inventory is a single point of contention, and eventually a scaling ceiling. Acceptable: the queue caps the rate that reaches it. |
| Waiting room in front of everything | NFR-01 without over-provisioning | Every customer waits, including the one who arrives at 10:47 to an empty queue. |
| **Compile-based** plug-ins | Simple, readable, easy to govern with a build check | Adding a payment method needs a redeploy. Nothing requires otherwise. |
| Only two plug-in seams | The design stays readable | Anything that is neither a payment method nor a pricing rule means changing the core. |
| Server-owned 10-minute clock | One clock decides; no client can cheat it | Kenji's countdown and the server can disagree by a second or two at zero. |

---

## 6.8 What is not designed yet

Honest gaps, so they are not mistaken for oversights.

- **Deployment topology.** Slide 8.1 notes that the pipeline suits cloud
  deployment well, because of its modularity and separate filter types. How
  many instances of what, and where, belongs to Deliverable 5 (Operation).
- **The queue's own storage.** It must be fast and it must survive a restart
  without losing positions. The choice is an Operation concern.
- **Monitoring and alerting.** NFR-04 states 99.9% availability; how it is
  observed is Operation.
- **Load-test harness.** The NFR-01 and NFR-02 fitness functions are defined in
  `02-requirements.md`. Building them is part of Deliverable 4
  (Implementation), slice 1.
