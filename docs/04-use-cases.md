# 04 · Use Cases

**Part of Deliverable 2 — Requirements Model**

Seven use cases. Each one is a full specification, not a name.

---

## 4.1 What a use case is, and how to read one

**A use case is one complete story of one actor reaching one goal.** It
includes the ordinary path *and* every way the story can go wrong.

**Analogy.** A recipe tells you how to make the dish. A good recipe also tells
you what to do when the sauce splits. The first part is the *main success
scenario*. The second part is the *extensions*. A recipe without the second
part is where most kitchens go wrong — and most software.

Each specification below has these parts:

| Part | What it means |
|---|---|
| **Actor** | The person with the goal. |
| **Goal** | What they want, in their own words. |
| **Preconditions** | What must already be true before the story can start. |
| **Trigger** | The thing that starts it. |
| **Main success scenario** | The story when everything works. |
| **Extensions** | Every other way it can go. Numbered against the main step where it branches — `4a` branches from step 4. |
| **Postconditions** | What is true afterwards, on success and on failure. |
| **Traces to** | The requirements, entities and characteristics involved. |

---

## 4.2 Actors

| Actor | Who | In this document |
|---|---|---|
| **Customer** | A member of the public buying tickets | **Kenji**, buying 2 × VIP for BLACKPINK — Bangkok |
| **Organizer** | The person running the event and its sale | **Chai** |

**There are only two actors, and both are people.**

The database, the payment provider and the clock are not actors. They are
parts of the solution. A use case diagram with an "API" bubble in the actor
column describes the implementation, not the goals.

This is why the expiring hold — the most important behaviour in the system —
is written as extension **6a inside UC-02** rather than as its own use case.
Nobody performs it. Time does.

---

## 4.3 The use case diagram

To be drawn by Member 3. The content is fixed here so the drawing has nothing
to invent.

```
                ┌─────────────────────────────────────────┐
                │  Event Ticket & Queue Management System  │
                │                                          │
   Customer ────┼──  UC-01  Join Waiting Room              │
   (Kenji)       │                                          │
            ────┼──  UC-02  Select & Hold Tickets          │
                │                                          │
            ────┼──  UC-03  Checkout & Pay                 │
                │                                          │
            ────┼──  UC-04  View Digital Ticket            │
                │                                          │
                │                                          │
   Organizer ───┼──  UC-05  Create Event with Tiers        │
   (Chai)       │                                          │
            ────┼──  UC-06  Manage Event & Tiers           │
                │                                          │
            ────┼──  UC-07  Monitor Live Queue & Sales     │
                └─────────────────────────────────────────┘
```

Two actors. Seven bubbles. No `<<include>>` or `<<extend>>` arrows — the
sequence UC-01 → UC-02 → UC-03 is a workflow, and workflows belong in the
activity diagram, not in the use case diagram.

---

# UC-01 · Join Waiting Room

| | |
|---|---|
| **Actor** | Customer (Kenji) |
| **Goal** | *"Get a place in line for the BLACKPINK sale, and know where I stand."* |
| **Preconditions** | Kenji has an account (ASM-01). The event exists and the sale-open time has passed. |
| **Trigger** | Kenji opens the event's sale page. |

### Main success scenario

1. Kenji asks to join the sale for **BLACKPINK — Bangkok**.
2. The system checks they have no active queue token for this event.
3. The system issues a **queue token** and puts it at the back of the queue.
4. The system shows their **position 4,182** and an estimated wait of ~6 minutes.
5. The system refreshes their position at least every 5 seconds while they wait.
6. Their token reaches the front. The admission rate allows another customer in,
   so the system admits them. **UC-02 begins.**

### Extensions

| # | Situation | What happens |
|---|---|---|
| **1a** | The sale has not opened yet | The opening time is shown. No token is issued. |
| **1b** | Every tier is sold out | They are told so. No token is issued. (FR-06) |
| **1c** | The organizer has closed the sale (FR-29) | No new tokens. Tokens already in the queue continue. |
| **2a** | They already have an active token for this event | The **existing** token and its current position are returned — not a new one. (FR-03) |
| **2b** | Their previous token exists but has expired | Treated as new. Continue from step 3. |
| **5a** | They close the page and come back | Extension 2a applies. **Same position.** (FR-04) |
| **6a** | They are admitted but do nothing | Their token expires. They must rejoin at the back to try again. |

> **Why 2a matters.** Without it, Kenji opens the sale in ten tabs, collects ten
> positions, and uses whichever comes up first. Their neighbour with one tab is
> now behind all ten. One token per account per event is what makes the queue
> fair rather than decorative.

### Postconditions

- **Success:** a `QueueToken` exists for Kenji and this event, in state
  `WAITING`, and later `ADMITTED`.
- **Failure:** no token exists. Kenji has been told the reason.

### Traces to

**Requirements** FR-01 … FR-06 · **Entities** E-01 Event, E-03 QueueToken ·
**Characteristics** NFR-01 Elasticity, NFR-03 Performance

---

# UC-02 · Select & Hold Tickets

| | |
|---|---|
| **Actor** | Customer (Kenji) |
| **Goal** | *"Lock the 2 VIP seats I want, so nobody can take them while I pay."* |
| **Preconditions** | Kenji holds a queue token in state `ADMITTED`. The sale is open. |
| **Trigger** | The system admits their token. |

### Main success scenario

1. The system shows every tier with its price and what is left:
   **VIP ฿6,500 — 16 left** · Zone A ฿3,800 — 350 left · Zone B ฿1,900 — 2,020 left.
2. Kenji chooses **VIP, quantity 2**.
3. The system checks 2 against the per-order limit of **4**. It passes.
4. The system attempts an **atomic hold** of 2 seats from VIP.
5. The hold succeeds. VIP remaining drops **16 → 14 immediately** —
   before any payment is discussed.
6. The system creates a `Reservation` in state `HELD`, stamped to expire
   **10 minutes** from now **by the server's clock**.
7. The system shows their selection, the total, and a countdown starting at
   **10:00**.
8. Kenji continues to checkout. **UC-03 begins.**

> **Step 5 is the whole design.** Inventory goes down at the *hold*, not at the
> payment. This is why two people racing for the last seats cannot both
> succeed — the seats are already gone from the pool by the time either of
> them reaches for a card.

### Extensions

| # | Situation | What happens |
|---|---|---|
| **2a** | They ask for more than remain | Refused. Fresh remaining quantity shown. They may pick a smaller quantity or another tier. (FR-10) |
| **3a** | The request would take them over 4 tickets | Refused, with the limit stated. (FR-11) |
| **4a** | The atomic hold fails — the seats went in the milliseconds between showing and choosing | Refused. Fresh numbers shown. Back to step 1. **No partial holds**: they get 2 or they get 0, never 1. |
| **6a** | **The hold expires.** Ten minutes pass with no confirmed payment. | · The seats return to the pool: VIP remaining **14 → 16**.<br>· The reservation moves to `EXPIRED`.<br>· The queue token is spent — they must rejoin to try again.<br>· Nothing is charged. (FR-12) |
| **7a** | They change their mind before checkout | They may release the hold. The quantity returns to the tier immediately. |

> **Extension 6a is the requirement the brief left out.**
>
> The brief says holds last ten minutes. It never says who gives the seats
> back. Without 6a, every abandoned basket removes seats from the sale
> permanently — 2,000 people wandering off mid-purchase would quietly destroy
> a third of the inventory.
>
> It lives here, as an extension, rather than as UC-08, because **nobody
> performs it**. It is triggered by the clock. Giving it its own bubble would
> put a clock in the actor column of the use case diagram.

### Postconditions

- **Success:** a `Reservation` in `HELD` exists; the tier's remaining quantity
  is reduced by the held amount; an expiry timestamp is set.
- **Failure:** no reservation exists; the tier's quantity is untouched.
- **Always true:** `sold + held ≤ total` for every tier. (INV-01)

### Traces to

**Requirements** FR-07 … FR-13 · **Entities** E-02 TicketTier, E-03 QueueToken,
E-04 Reservation · **Characteristics** **NFR-02 Data integrity**, NFR-03 Performance

---

# UC-03 · Checkout & Pay

| | |
|---|---|
| **Actor** | Customer (Kenji) |
| **Goal** | *"Pay for my held seats and be sure they are mine."* |
| **Preconditions** | A `Reservation` in state `HELD` that has not expired. |
| **Trigger** | Kenji starts checkout. |

### Main success scenario

1. The system calculates the total: 2 × ฿6,500 = **฿13,000**. Every registered
   pricing rule is offered the order. None apply today, so the total stands.
2. The system shows the total, the tier, the quantity and the time remaining.
3. Kenji chooses **PromptPay**.
4. The system creates a `Payment` in state `PENDING`, with an **idempotency
   key** taken from the reservation.
5. The system sends the request to the PromptPay plug-in.
6. The plug-in returns `AUTHORISED`.
7. The system checks that the reservation is **still `HELD` and not expired**.
8. Both are true. The `Reservation` becomes `CONFIRMED`. The `Payment` becomes
   `CAPTURED`.
9. The system issues one digital ticket per seat. **UC-04 becomes available.**

> **Idempotency key** — a label attached to the payment request, made from the
> reservation. If the same request arrives twice, the provider recognises the
> label and returns the first answer instead of charging again. Like writing a
> cheque number on a cheque: presenting the same cheque twice does not move
> money twice.

### Extensions

| # | Situation | What happens |
|---|---|---|
| **1a** | A pricing rule applies — say Early Bird −10% | Total becomes **฿11,700**. Everything else is unchanged. The core engine does not know the rule's name; see `06-architecture.md`. |
| **3a** | Kenji submits the same order twice — double tap, or a refresh | The idempotency key matches the existing `Payment`. The second submission returns the first one's result. **Charged once.** (FR-18) |
| **6a** | Payment is **declined** | The `Payment` becomes `DECLINED`. The reservation **stays `HELD`** for the time it has left. Kenji can try another method. (FR-19) |
| **6b** | The provider does not answer in time | Treated as unknown, not as failure. The system asks for the status again using the **same** idempotency key. If it is still unknown when the hold expires, extension 7a applies. |
| **7a** | **The hold expired while the payment was in flight** | · The authorisation is **voided** through the provider. (FR-17)<br>· **No ticket is issued.**<br>· Kenji is told plainly: seats released, nothing charged.<br>· The reservation is already `EXPIRED` — UC-02 extension 6a returned the seats when the clock ran out. |

> **Extension 7a is the race the brief never mentions.**
>
> Kenji's bank takes eight seconds. During those eight seconds their ten minutes
> can run out and their seats can be sold to someone else. Two answers are
> possible and only one is acceptable:
>
> - *Keep the money, no seat* — the customer has been robbed.
> - *Void the payment, no seat* — the customer is disappointed but whole.
>
> FR-16 makes confirmation depend on **both** facts being true. FR-17 says what
> happens to the money when they are not. Written down here, the
> implementation has no choice left to make.

### Postconditions

- **Success:** `Reservation` = `CONFIRMED`, `Payment` = `CAPTURED`, one
  `DigitalTicket` per seat.
- **Failure:** no ticket. Either the money never left (declined), or it was
  returned (voided).
- **Never possible:** charged with no seat, or a seat with no payment.

### Traces to

**Requirements** FR-14 … FR-19 · **Entities** E-04 Reservation, E-05 Payment ·
**Characteristics** NFR-02, NFR-05 Fault tolerance, NFR-08 Extensibility

---

# UC-04 · View Digital Ticket

| | |
|---|---|
| **Actor** | Customer (Kenji) |
| **Goal** | *"Have something at the gate that proves the seat is mine."* |
| **Preconditions** | Kenji owns at least one `CONFIRMED` reservation. |
| **Trigger** | Kenji opens their tickets. |

### Main success scenario

1. The system lists every digital ticket Kenji owns.
2. They open one.
3. The system shows: event, venue, date, tier, seat count, and a QR code.
4. The QR code carries a **signed, single-use payload**.

> **What "verifiable" actually means.** The brief says the ticket is
> verifiable but never says how. Two properties make it so:
>
> - **Signed** — the payload carries a code only this system could produce,
>   calculated from the ticket's own contents. Change the tier from Zone B to
>   VIP and the code no longer matches. It is the watermark on a banknote:
>   anyone can copy the picture, nobody can copy the watermark.
> - **Single-use** — the payload is good for one admission. A screenshot
>   forwarded to a friend gets whoever arrives second turned away.

### Extensions

| # | Situation | What happens |
|---|---|---|
| **1a** | They have no confirmed orders | An empty state explaining they have no tickets yet. |
| **3a** | The organizer cancelled the event | The ticket is shown marked `CANCELLED`. Its signature will not verify. |

### Postconditions

- The ticket is displayed. **Nothing changes** — this use case only reads.

### Out of scope, on purpose

**Checking the ticket at the venue gate is not part of this system.** It needs
a third actor (gate staff) and venue hardware. This system's responsibility
ends at *issuing* a ticket that can be verified. See `03-prd.md` §3.5.

### Traces to

**Requirements** FR-20 … FR-22 · **Entities** E-04 Reservation, E-06 DigitalTicket ·
**Characteristics** NFR-06 Security

---

# UC-05 · Create Event with Tiers

| | |
|---|---|
| **Actor** | Organizer (Chai) |
| **Goal** | *"Put my event on sale with the right prices."* |
| **Preconditions** | Chai is signed in as an organizer. |
| **Trigger** | Chai creates a new event. |

### Main success scenario

1. Chai enters name, venue, date and sale-open time:
   *BLACKPINK — Bangkok · Impact Arena · 14 Nov 2026 · on sale 1 Oct 2026, 10:00.*
2. The system creates the `Event` in state `DRAFT`.
3. Chai adds the tiers:

   | Tier | Price | Quantity |
   |---|---|---|
   | VIP | ฿6,500 | 500 |
   | Zone A | ฿3,800 | 2,000 |
   | Zone B | ฿1,900 | 5,000 |

4. The system validates each tier: a name, a price of 0 or more, a quantity
   above 0.
5. Chai publishes. The system checks at least one tier exists.
6. The event becomes `SCHEDULED`. At 10:00 on 1 October it becomes `ON_SALE`.

### Extensions

| # | Situation | What happens |
|---|---|---|
| **1a** | Sale-open time is in the past | Refused, with the reason. |
| **4a** | A tier has quantity 0 or a negative price | Refused, naming the tier and the field. |
| **5a** | No tiers were defined | Publishing is refused. (FR-25) An event with nothing to sell cannot open. |

### Postconditions

- **Success:** `Event` in `SCHEDULED` with at least one `TicketTier`.
- **Nothing is on sale** until the sale-open time arrives.

### Traces to

**Requirements** FR-23 … FR-25 · **Entities** E-01 Event, E-02 TicketTier

---

# UC-06 · Manage Event & Tiers

| | |
|---|---|
| **Actor** | Organizer (Chai) |
| **Goal** | *"Change what I need to, without breaking a sale that is already running."* |
| **Preconditions** | The event exists and Chai owns it. |
| **Trigger** | Chai opens the event. |

### Main success scenario

1. The system shows the event, its tiers and its current state.
2. Chai chooses a change.
3. The system checks the change is allowed **in the event's current state**.
4. The change is applied and recorded.

### What is allowed, and when

This table is the real content of this use case.

| Change | `DRAFT` / `SCHEDULED` | `ON_SALE` | `CLOSED` |
|---|---|---|---|
| Edit name, venue, date | yes | yes | no |
| **Change a tier price** | yes | **no** (FR-26) | no |
| Add quantity to a tier | yes | yes (FR-27) | no |
| Reduce a tier's quantity | yes, not below sold + held | yes, not below sold + held (FR-28) | no |
| Add a new tier | yes | yes | no |
| Set the admission rate | yes | yes (FR-30) | — |
| Close the sale | — | yes (FR-29) | — |

### Extensions

| # | Situation | What happens |
|---|---|---|
| **3a** | A price change is attempted while `ON_SALE` | Refused. Kenji must not watch ฿6,500 become ฿7,200 while their countdown runs. |
| **3b** | A quantity reduction would go below sold + held | Refused, showing the numbers: *"VIP: 476 sold and 12 held. Cannot reduce below 488."* (FR-28) |
| **4a** | Chai closes the sale | No new admissions. Live holds **keep their remaining time** and may still complete. When the last hold resolves, the event becomes `CLOSED`. |

> **Extension 4a is a kindness with teeth.** Closing a sale must not cancel
> Kenji's hold three minutes into their ten. They were admitted fairly; they keep
> their window.

### Postconditions

- The change is applied, or clearly refused with the reason.
- **No existing reservation is ever invalidated by a management action.**

### Traces to

**Requirements** FR-26 … FR-30 · **Entities** E-01 Event, E-02 TicketTier ·
**Characteristics** NFR-02 (FR-28 protects the invariant)

---

# UC-07 · Monitor Live Queue & Sales

| | |
|---|---|
| **Actor** | Organizer (Chai) |
| **Goal** | *"See whether the sale is working, right now."* |
| **Preconditions** | The event is `ON_SALE`, or recently closed. |
| **Trigger** | Chai opens the dashboard. |

### Main success scenario

1. The system shows the **queue**: how many are waiting, and the admission rate.
2. The system shows **each tier**: total, held, sold, remaining.
3. The system shows **gross revenue** of confirmed orders.
4. The figures refresh at least every **5 seconds**. (FR-34)

### What Chai sees at 10:06:20

```
QUEUE     31,208 waiting          admitting 200 / second

TIER      TOTAL     HELD    SOLD    REMAINING
VIP         500       12     476           12
Zone A    2,000       40   1,610          350
Zone B    5,000       96   2,884        2,020

REVENUE   ฿14,691,600   (confirmed orders only)
```

> **Why "held" is its own column.** Those 12 VIP seats are not sold and not
> available. They are in somebody's ten minutes. If Chai only saw *sold* and
> *remaining*, the numbers would not add up to 500 and he would think the
> system had lost seats. Naming the third state is what makes the dashboard
> trustworthy. Note that every row adds up: 12 + 476 + 12 = 500.

### Extensions

| # | Situation | What happens |
|---|---|---|
| **1a** | The sale is not open | The last known figures are shown, marked as final. |
| **2a** | A refresh failed and the numbers are stale | The **age of the data** is shown. A stale number presented as fresh is worse than no number. |

### Postconditions

- Read only. **Nothing in the system changes.**

### Traces to

**Requirements** FR-31 … FR-34 · **Entities** E-01 … E-05 ·
**Characteristics** NFR-03 Performance

---

## 4.4 What changed from the brief, and why

The brief listed six use case **names**. This document has seven
**specifications**. Here is every change, so the difference is a decision on
the record rather than a drift.

| # | Change | Why |
|---|---|---|
| 1 | **Split** "Create / Manage Event & Tiers" into UC-05 and UC-06 | Two goals joined by a slash. Creating needs no event to exist; managing needs one, and its rules depend on what state that event is in. Seven total still sits inside the brief's stated range of 5–7. |
| 2 | **Added the expiring hold** as UC-02 extension 6a | The single mechanism that prevents overselling had no owner. Written as an extension, not a use case, because time is not an actor. |
| 3 | **Added the payment/hold race** as UC-03 extension 7a | The brief does not say what happens when authorisation outlives the hold. Both possible answers are reachable from the brief, and one of them charges a customer for nothing. |
| 4 | **Added declined payment** as UC-03 extension 6a | A decline is not the end of the story — the hold still has time left on it. |
| 5 | **Added duplicate submission** as UC-03 extension 3a | Double-tapping a button on a phone during a countdown is not an edge case. It is what people do. |
| 6 | **Added one-token-per-account** to UC-01 | Without it the queue can be gamed with browser tabs, and a fair queue is the product. |
| 7 | **Defined "verifiable"** in UC-04 | The brief uses the word without meaning. Now it means signed and single-use. |
| 8 | **Named the figures** in UC-07 | "Monitor sales" is not testable. Queue length, admission rate, held/sold/remaining per tier, and revenue are. |
| 9 | **Declared gate scanning out of scope** in UC-04 | It was implied by "verifiable" and would have added a third actor. Better excluded loudly than assumed quietly. |

### The alternative that was considered and rejected

Keeping "Create / Manage" merged, and spending the seventh slot on **Validate
Ticket at Gate**.

**Rejected because** it adds a third actor — gate staff — to the use case
diagram, and the venue side of ticketing (hardware, offline scanning, turnstile
integration) is a system of its own. Issuing a verifiable ticket is this
system's job. Checking it at the door is not.
