# 06 · Architecture

**Deliverable 3 of 6 — Design**

Two architectural styles: **event-driven** (slide 11.1) and **microkernel**
(slide 8.2). This document says which parts of the system are which, and —
more importantly — **which requirement forced each decision**.

---

## 6.1 The shape of the whole thing

Before the styles, the shape.

```
  40,000 people
       │
       ▼
  ┌───────────────────────────────────────────────────────────┐
  │  CORE SYSTEM  (microkernel)                                │
  │                                                            │
  │   WAITING ROOM ── UserAdmitted ──► booking: hold · pay     │
  │   admits ~200/s                    · confirm · expire      │
  │                                                            │
  │   the parts talk by EVENTS through one event broker        │
  │   TicketHeld · HoldExpired · PaymentCompleted · ...        │
  └──────────────────────────────┬────────────────────────────┘
                                 │  looks up by key
                        ┌────────┴────────┐
                        ▼                 ▼
                 PricingRule        PaymentProvider
                  plug-ins            plug-ins
```

**The one idea to take from this picture:** the waiting room exists so the
booking part never experiences a spike. Everything expensive happens at 200
requests per second, not 40,000. That single move is what makes NFR-01
achievable without buying machines that idle for 364 days a year.

**The second idea:** inside the core, one part never calls the next part
directly to say "now do your job". It announces what just happened — an
**event** — and the parts that care react.

---

## 6.2 Style 1 — Event-driven  *(slide 11.1)*

### What the style is

Lecture 11.1 describes **event-driven architecture (EDA)** as a style that
combines **asynchronous communication** with **parallel processing** — doing
many things at once without waiting — to build very fast systems.

The slides name **four components** in the broker topology:

| Slide 11.1 component | What it is | In this system |
|---|---|---|
| **Initiating event** | The event that *"starts the entire event flow"*. | Kenji presses **Hold** or **Pay**; the clock passes a hold's deadline. |
| **Event broker** | Holds all the **event channels** for one domain. | `EventBus`. Each event type is one channel. |
| **Event processor** | Listens to a channel, does its business logic, publishes a new event. | `InventoryService`, `ReservationService`, `CheckoutService`, `AdmissionScheduler`, … |
| **Processing event** (also *derived event*) | An event *"generated in response to the initiating event"*. | `TicketHeld`, `PaymentCompleted`, `OrderConfirmed`, `HoldExpired`, … |

- An **event** says that something has **already happened**. Slide 11.1:
  events are *broadcast* to many processors, while *messages* request
  something from one service.
- **Example:** `TicketHeld` — "2 VIP seats were held for Kenji at 10:04:40,
  reservation R-1001".
- The publisher does not know who is listening.

**Analogy.** A restaurant kitchen. The waiter pins an order ticket on the rail
and walks away. The grill cook takes what is theirs, the salad cook takes what
is theirs. Nobody phones anybody. Adding a dessert station means one more
cook reading the rail — the waiter does not change.

Slide 11.1 gives two **topologies** (ways of wiring it):

| Topology | Slide 11.1: use it when you need… | Used here? |
|---|---|---|
| **Broker** | *"a high degree of responsiveness and dynamic control over the processing of an event"*. No central controller; each processor publishes what it did. | **Yes** |
| **Mediator** | *"control over the workflow of an event process"*. One **event mediator** receives the initiating event and directs every step. | No — see below |

### Why this style fits here

The booking flow is full of things that happen **later**, on their own
schedule:

- Kenji is **admitted** whenever the queue reaches him.
- His hold **expires** 10 minutes after it was made — or it doesn't.
- PromptPay says **"paid"** whenever Kenji finishes scanning.

A straight line of steps cannot wait 10 minutes for something that may never
come. An event can simply arrive when it arrives. That is the whole fit.

Slide 11.1 lists the broker topology's advantages. Each one maps to a
characteristic this project needs:

| Advantage (slide 11.1) | Characteristic it serves |
|---|---|
| High responsiveness, high performance | NFR-03 Performance — the queue screen and the hold answer fast |
| High scalability | NFR-01 Elasticity — events queue up and are handled at a steady pace |
| High fault tolerance | NFR-05 — if payment is down, the waiting room and the hold still work |
| Highly decoupled event processors | NFR-08 — a new listener needs no change to the publisher |

### Why broker, not mediator

- The workflow is short — five steps — and each reaction is independent.
- The one place where order truly matters (expire **or** confirm, never both)
  is already decided by a single atomic step inside `ReservationService`
  (rule R-E04-6).
- A mediator would be a controller that mostly passes events along, and a
  single point of failure during the spike.

The cost is that no single class shows the whole workflow. The event table
below is that map instead.

### The events — Kenji's purchase, end to end

Kenji buys 2 × VIP at ฿6,500. Every event carries his `tokenId` and, once it
exists, his `reservationId` (locked decision 3), so the whole chain can be
traced afterwards (NFR-07).

**Initiating events:** Kenji's **Hold** request, Kenji's **Pay** request, and
the clock reaching a hold's `expiresAt`.

**Processing events:**

| # | Processing event | Published by | Who listens | What they do |
|---|---|---|---|---|
| 1 | **`UserAdmitted`** | `AdmissionScheduler` | Kenji's session · `SalesDashboardService` | Show "your turn" · count one more admitted |
| 2 | **`TicketHeld`** | `InventoryService`, after the atomic hold succeeds | `SalesDashboardService` | VIP held goes 10 → 12 on the dashboard. The 10-minute deadline is already saved on the hold; `HoldExpiryScheduler` finds it by scanning, not by listening. |
| 3 | **`PaymentCompleted`** | `CheckoutService`, after the `PaymentProvider` authorises | `ReservationService` | Confirm the order if the hold is still alive (FR-16). Otherwise void the payment (FR-17). |
| 4 | **`OrderConfirmed`** | `ReservationService` | `InventoryService` · `TicketService` · `SalesDashboardService` | Move 2 seats from held to sold · issue 2 tickets · add ฿13,000 revenue |
| 5 | **`TicketIssued`** | `TicketService` | Kenji's session | Show the two tickets |
| — | **`HoldExpired`** | `ReservationService`, when expiry wins | `InventoryService` · `SalesDashboardService` · Kenji's session | Return the 2 seats (FR-12) · held goes back down · show "You have not been charged" |
| — | **`PaymentVoided`** | `ReservationService`, when a payment arrived too late | `SalesDashboardService` · Kenji's session | Record it · tell Kenji the money is released |

Slide 11.1 recommends that each processor **advertise what it has done**,
*"regardless of whether or not any other event processor cares"*. That is why
`TicketIssued` and `PaymentVoided` exist even though only Kenji's screen
listens to them today.

```
 AdmissionScheduler ──UserAdmitted──►  Kenji picks 2 × VIP
                                            │  (direct call, answer needed now)
                                            ▼
                                     InventoryService ──TicketHeld──► dashboard
                                     (saves expiresAt)             HoldExpiryScheduler
                                                                   scans · 10 min pass
 CheckoutService ──PaymentCompleted──►  ReservationService  ◄───────────────┘
                                        HELD → CONFIRMED   or   HELD → EXPIRED
                                             │                        │
                                     OrderConfirmed               HoldExpired
                                       │       │                      │
                                InventoryService  TicketService     InventoryService
                                 held → sold     ──TicketIssued──►   seats returned
```

### Events versus requests

Slide 11.1 separates the two: *"events always broadcast something that has
already happened, whereas messages request something that needs to be done"*.

Some steps need an answer **right now**, so they stay direct requests:

- **Issuing a queue token.** Kenji must see his position immediately.
- **The atomic hold.** Kenji must know at once whether he got the seats.
- **Calling the payment provider.** Checkout waits for "authorised" or
  "declined".

**The rule:** a request that needs an answer is a call. A fact that others may
care about is an event. Slide 11.1's own example works the same way: the
`OrderPlacement` processor saves the order and *returns an order ID to the
customer*, then advertises `order-created`.

### Events are news, not decisions

An event is published **only after** the change it describes has been saved.
The database is the truth; events are the announcements.

This matters for INV-01. No event ever decides who gets the last VIP seat.
`InventoryService` decides that with one atomic update, and only then says
`TicketHeld`.

### The risks of this style, answered

A design that names a style's risks and does not answer them has not used the
style — it has only drawn it.

**The broker topology's disadvantages (slide 11.1):**

| Disadvantage | Our answer |
|---|---|
| **Workflow control** — no one place runs the flow | The event table above is the map. The one ordering rule that matters — confirm **or** expire — is one conditional update in `ReservationService` (R-E04-6). |
| **Error handling** — a failure inside one processor | Failures are events too: `HoldExpired`, `PaymentVoided`. A declined payment changes nothing, so the hold keeps its time (FR-19). |
| **Recoverability** — an event is lost, e.g. a crash after saving but before publishing | The hold's expiry time is stored with the hold (locked decision 2). `HoldExpiryScheduler` scans the database, so a lost `TicketHeld` cannot leave seats stuck. |
| **Restart capabilities** — the system stops mid-flow | Nothing lives only inside an event. On restart, every reservation's state is read back from the database, and the next sweep finishes any expiry that was due. |
| **Data inconsistency** — counters and records drift apart | Only `InventoryService` changes `heldQuantity` and `soldQuantity`. Every processor is **idempotent** — handling the same event twice has the same effect as once. INV-02 and INV-03 are re-checked by the tests. |

**The common risks of the style (slide 11.1):**

| Risk | Our answer |
|---|---|
| **Nondeterministic event processing** — the order events arrive in can differ run to run | `PaymentCompleted` and `HoldExpired` can race for the same hold. Who wins may differ; the result is always one of two valid endings. `PaymentExpiryRaceTest` runs this race 4,000 times. |
| **Too much static coupling** — processors depending on each other's code | Processors share only the small event records in `core/event`. No processor calls another processor's class. |
| **Too much synchronous communication between event processors** | Exactly three direct calls, listed in *Events versus requests*, each because Kenji is waiting for the answer. |
| **Overall state management** — who knows the state of an order? | One place: the `Reservation`. Its state machine (`HELD → CONFIRMED / EXPIRED`) is the single answer. |

### Data topology — monolithic database

Slide 11.1 gives three choices: a **monolithic database** shared by all
processors, a **domain database** per group of processors, or a **dedicated
database** per processor.

**This design uses the monolithic database topology.**

- **Why:** INV-01. `heldQuantity` and `soldQuantity` must be correct at every
  instant. Splitting inventory across separate stores would make the atomic
  hold a distributed problem, which is far harder to get right.
- **The cost, as slide 11.1 lists it:** weaker fault tolerance, scalability
  and change control, and the whole system becomes one architectural quantum
  (one unit that is deployed and fails together).
- **Why the cost is acceptable:** the waiting room caps the rate that reaches
  the database at about 200 requests per second, not 40,000.

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
- queue management and admission (the waiting room),
- ticket inventory: the atomic hold and the inventory counters,
- reservations: the lifecycle `HELD` → `CONFIRMED` / `EXPIRED` / `RELEASED`,
- booking: checkout and pricing,
- the event broker that connects all of the above.

**Its happy path is the event flow from §6.2.** That is exactly what slide 8.2
says a core system *is*: a general processing flow with little or no custom
processing in it.

The core does not contain the word "Stripe", "PromptPay" or "EarlyBird"
anywhere.

### The two plug-in seams

Slide 8.2 warns that plug-ins should be **independent of each other**. Both
contracts below are small interfaces, so a plug-in has nothing to talk to
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
    String  key();                                  // "early-bird"
    boolean appliesTo(PricingRequest request);
    Money   apply(Money currentTotal, PricingRequest request);
}

/** What a pricing rule may look at. Small on purpose. */
public record PricingRequest(String eventId, String tierId,
                             int quantity, String customerId) {}
```

| Seam | Plug-ins | Used by |
|---|---|---|
| `PaymentProvider` | `PromptPayPlugin`, `StripePlugin` | `CheckoutService` (UC-03) |
| `PricingRule` | `EarlyBirdRule`, `StudentDiscountRule`, `FanClubPresaleRule` | `PricingService` (UC-03, FR-14) |

**These two are the only seams.** NFR-08 says so, and §2.4 explains why:
Lecture 3.1 warns that supporting too many characteristics complicates the
design for little benefit. A system where everything is pluggable is a system
nobody can read.

**Plug-ins never publish or listen to events.** The core calls them and gets
an answer back. This keeps every plug-in a plain class with one job, and keeps
them independent of each other — they cannot even see each other's events.

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
| Booking engine | **Microkernel core system** | Queue, inventory, reservations, booking. |
| ↳ how its parts talk | **Event-driven** (broker topology) | `UserAdmitted` → `TicketHeld` → `PaymentCompleted` → `OrderConfirmed` → `TicketIssued`, plus `HoldExpired`. |
| Around it | **Microkernel plug-ins** | `PaymentProvider` and `PricingRule`. Called directly, never by event. |

The join is at three calls, all through the registry:

- **`PricingService`** asks for every `PricingRule` and offers each one the
  order (FR-14).
- **`CheckoutService`** asks for one `PaymentProvider` by key, then publishes
  `PaymentCompleted` (UC-03 step 5).
- **`ReservationService`** asks the same provider to void a late payment
  (FR-17).

Everywhere else, the two styles do not touch.

---

## 6.5 Java package layout

Package root: `th.ac.kmitl.sda.ticketqueue` (CON-04).

```
th.ac.kmitl.sda.ticketqueue
├── core/                     ◄── THE CORE SYSTEM. Knows no plug-in by name.
│   ├── event/
│   │   ├── DomainEvent.java          the shared event interface
│   │   ├── EventBus.java             the event broker: publish · subscribe
│   │   ├── UserAdmitted.java
│   │   ├── TicketHeld.java
│   │   ├── PaymentCompleted.java
│   │   ├── OrderConfirmed.java
│   │   ├── TicketIssued.java
│   │   ├── HoldExpired.java
│   │   └── PaymentVoided.java
│   ├── queue/                        the waiting room
│   │   ├── QueueToken.java               WAITING → ADMITTED → SPENT / EXPIRED
│   │   ├── QueueService.java             join · position · reconnect · claim for a hold
│   │   └── AdmissionScheduler.java       admits a FIFO batch per second → UserAdmitted
│   ├── inventory/
│   │   └── InventoryService.java     ◄── the ONLY writer of held/sold → TicketHeld
│   ├── order/
│   │   ├── Reservation.java
│   │   ├── ReservationService.java   HELD → CONFIRMED / EXPIRED / RELEASED
│   │   ├── HoldExpiryScheduler.java  ◄── UC-02 ext. 6a. Asks for expiry.
│   │   ├── CheckoutService.java      calls PaymentProvider → PaymentCompleted
│   │   └── PricingService.java       applies every PricingRule (FR-14)
│   └── registry/
│       └── PluginRegistry.java       key → plug-in
│
├── EventFlow.java            ◄── composition root: wires core + plug-ins. Outside core/.
│
├── contract/                 ◄── THE SEAMS. Core and plug-ins both depend on these.
│   ├── Money.java
│   ├── PaymentProvider.java
│   ├── PricingRule.java
│   └── PricingRequest.java
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
│   └── TicketService.java            listens to OrderConfirmed → TicketIssued (FR-20, FR-21)
│
└── admin/
    ├── EventService.java             UC-05, UC-06
    └── SalesDashboardService.java    UC-07 · listens to every event
```

**Built so far.** Slices 1 and 2 of `03-prd.md` §3.6: `core/event`,
`core/queue`, `core/inventory`, `core/order` (without `PricingService`),
`core/registry`, `contract/` (payment only), the simulated `PromptPayPlugin`,
`admin/EventService` and `EventFlow`. `src/README.md` lists what is built and
what the tests prove.

**How the queue hands over to the hold.** `InventoryService` asks
`QueueService.claimForHold` inside its atomic hold: the token must be
`ADMITTED` for this event, and the hold moves it to `SPENT` (locked
decision 3). This is a direct request, not an event — the hold needs the
answer before it can continue.

**Event broker implementation.** `EventBus` is a small in-process class: a
map from event type (the channel) to its listeners. No message-broker product
is needed to demonstrate the style.

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
| FR-12 · Expired holds return seats | `HoldExpiryScheduler` → `HoldExpired` → `InventoryService` | Yes — the seats move in `InventoryService` only |
| FR-16 · Confirm only if still held | `ReservationService`, rule R-E04-6 | Yes |
| FR-17 · Void a late authorisation | `ReservationService` → `PaymentProvider.voidAuthorisation` | Yes |
| FR-18 · Charge once | Idempotency key, `PaymentRequest` → provider | Yes |
| FR-03 · One token per account per event | `QueueService` | Yes |
| FR-05 · First in, first admitted, in batches (NFR-09) | `AdmissionScheduler` | Yes |
| FR-21 · Signed ticket | `TicketService` | Yes |
| FR-26 · No price change while on sale | `EventService` | Yes |
| FR-28 · No reduction below sold + held | `EventService`, reading `InventoryService` | Yes |
| NFR-08 · Two seams only | `contract/` + the build rule in §6.5 | Yes |

---

## 6.7 The trade-offs, stated

An architecture document that lists only benefits has not made any choices.

| Decision | What it buys | What it costs |
|---|---|---|
| Inventory drops at **hold**, not payment | Overselling becomes structurally impossible | Seats sit unavailable during abandoned checkouts. Mitigated by the 10-minute limit — which is why FR-12 is not optional. |
| **Event-driven**, broker topology (slide 11.1) | Late and timed things (expiry, payment) fit naturally; parts fail independently (NFR-05) | Slide 11.1's broker disadvantages: workflow control, error handling, recoverability, restart, data inconsistency — each answered in §6.2. |
| Monolithic database topology (slide 11.1) | A genuinely atomic hold, easy to prove | Inventory is a single point of contention, and eventually a scaling ceiling. Acceptable: the queue caps the rate that reaches it. |
| Waiting room in front of everything | NFR-01 without over-provisioning | Every customer waits, including the one who arrives at 10:47 to an empty queue. |
| **Compile-based** plug-ins | Simple, readable, easy to govern with a build check | Adding a payment method needs a redeploy. Nothing requires otherwise. |
| Only two plug-in seams | The design stays readable | Anything that is neither a payment method nor a pricing rule means changing the core. |
| Server-owned 10-minute clock | One clock decides; no client can cheat it | Kenji's countdown and the server can disagree by a second or two at zero. |

---

## 6.8 Limits of this design

Stated plainly, so they are not mistaken for oversights.

- **One process.** The whole system runs as a single Java program on one
  machine. It is not split across servers.
- **Data in memory.** Queue positions, holds and orders are kept in memory and
  are lost when the program stops.
- **No monitoring.** NFR-04 sets 99.9% availability; this design does not
  include a way to observe it.
