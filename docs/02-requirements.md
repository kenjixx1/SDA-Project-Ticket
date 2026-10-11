# 02 · Requirements

**Deliverable 2 of 6 — Requirements Model**

This is the engineering list. Every line is numbered and testable.
For the product view — who the users are and what counts as success — read
`03-prd.md` instead.

---

## 2.1 Actors

An **actor** is someone outside the system who uses it to reach a goal.

| Actor | Who they are | What they want |
|---|---|---|
| **Customer** | A member of the public buying tickets. In this document: **Kenji**. | Get the tickets they want, fairly, without being told "sold out" after they already paid. |
| **Organizer** | The person who runs the event and the sale. In this document: **Chai**. | Sell every seat, see what is happening while it happens, and never face a customer holding a fake ticket. |

**Only these two.** The system itself, the payment provider and the database
are *not* actors — they are parts of the solution, not people with goals. Time
is not an actor either, which is why the expiring hold is written as an
extension inside UC-02 rather than as its own use case.

---

## 2.2 How to read the requirement IDs

| Prefix | Means |
|---|---|
| `FR-nn` | Functional requirement — something the system **does**. |
| `NFR-nn` | Non-functional requirement — **how well** it must do it. |
| `CON-nn` | Constraint — something fixed that we did not choose. |
| `ASM-nn` | Assumption — something we decided because the brief does not say. |

Each functional requirement names the use case it comes from. Nothing appears
from nowhere.

---

## 2.3 Functional requirements

### From UC-01 · Join Waiting Room

| ID | The system shall… |
|---|---|
| FR-01 | Issue a **queue token** to a customer who asks to enter an event that is on sale. |
| FR-02 | Show the customer their current position in the queue and an estimated wait. |
| FR-03 | Allow at most **one active queue token per customer account per event**. |
| FR-04 | Restore the same position when a customer reconnects holding an existing token. |
| FR-05 | Admit tokens to the purchase flow **in controlled batches**, **in the order they were issued**. The batch size follows system capacity, set by the organizer as the admission rate (FR-30). |
| FR-06 | Refuse to issue new tokens once every tier of the event is sold out. |

> **FR-05 in the story.** Every second, the waiting room lets in the next
> batch of 200 tokens — positions 1–200, then 201–400, and so on. 200 is what
> the buying engine can serve safely. Chai can lower it if the engine slows.

> **FR-03 exists because of a real cheat.** Without it, Kenji opens the sale in
> ten browser tabs, gets ten positions, and uses whichever comes up first. Their
> neighbour with one tab is now tenth in line behind them. One token per
> account per event makes the queue mean something.

### From UC-02 · Select & Hold Tickets

| ID | The system shall… |
|---|---|
| FR-07 | Show an admitted customer every ticket tier with its price and remaining quantity. |
| FR-08 | Place a **hold** on the requested quantity in a **single atomic operation** (see NFR-02). |
| FR-09 | Reduce the tier's available quantity at the moment the hold succeeds — not at payment. |
| FR-10 | Reject a request for more tickets than the tier has remaining. |
| FR-11 | Reject a request that would take the customer over the per-order limit of **4 tickets**. |
| FR-12 | Expire a hold **10 minutes** after it was created, and return the held quantity to the tier. |
| FR-13 | Show the customer a countdown of the time left on the hold. |

> **FR-12 is the requirement the brief was missing.** The 10-minute limit is
> mentioned in the brief, but nothing says who gives the seats back. Without
> FR-12, every abandoned basket removes seats from the sale permanently.

### From UC-03 · Checkout & Pay

| ID | The system shall… |
|---|---|
| FR-14 | Calculate the total from the tier price and every applicable pricing rule. |
| FR-15 | Accept payment through any registered payment provider. |
| FR-16 | Turn a hold into a confirmed order **only when payment is authorised AND the hold is still alive**. |
| FR-17 | Void an authorised payment if the hold expired before the authorisation came back. |
| FR-18 | Treat a repeated submission of the same order as the first one, and charge once. |
| FR-19 | Keep the hold alive for its remaining time when a payment is declined, so the customer can try again. |

> **FR-16 and FR-17 close the worst race in the system.** Kenji's card takes
> eight seconds to authorise. During those eight seconds their hold can expire
> and their seats can be sold to someone else. FR-16 says the order is only
> confirmed if *both* facts are true. FR-17 says what happens to their money
> when they are not.

### From UC-04 · View Digital Ticket

| ID | The system shall… |
|---|---|
| FR-20 | Issue one digital ticket per ticket bought in a confirmed order. |
| FR-21 | Include a **signed, single-use payload** in each digital ticket. |
| FR-22 | Let a customer view every digital ticket they own. |

> **What "signed" means.** The system adds a code that only the system could
> have produced, calculated from the ticket's own details. Change one detail
> and the code no longer matches. It is the difference between a photocopied
> banknote and a real one — anyone can copy the picture, nobody can copy the
> watermark.

### From UC-05 · Create Event with Tiers

| ID | The system shall… |
|---|---|
| FR-23 | Let an organizer create an event with a name, venue, date and sale-open time. |
| FR-24 | Let an organizer define one or more ticket tiers, each with a name, price and quantity. |
| FR-25 | Refuse to open a sale for an event that has no tiers. |

### From UC-06 · Manage Event & Tiers

| ID | The system shall… |
|---|---|
| FR-26 | Let an organizer change a tier's price **before** the sale opens. |
| FR-27 | Let an organizer add quantity to a tier at any time. |
| FR-28 | Refuse to reduce a tier's quantity below the number already held or sold. |
| FR-29 | Let an organizer close a sale: no new admissions, live holds allowed to finish. |
| FR-30 | Let an organizer set the queue admission rate. |

> **FR-26 has a hard edge on purpose.** Once the sale is open, the price is
> frozen. Kenji must not watch ฿6,500 become ฿7,200 while their hold counts down.

### From UC-07 · Monitor Live Queue & Sales

| ID | The system shall… |
|---|---|
| FR-31 | Show the organizer the current queue length and admission rate. |
| FR-32 | Show, per tier, the quantity **held**, **sold** and **remaining**. |
| FR-33 | Show the gross revenue of confirmed orders. |
| FR-34 | Refresh these figures at least once every 5 seconds. |

---

## 2.4 Non-functional requirements — the architecture characteristics

### What these are, and why they matter more than usual here

Lecture 3.1 draws the line clearly:

> *Requirements specify what the application should do. Architecture
> characteristics specify operational and design criteria for success, how to
> implement the requirements, and why certain choices were made.*

Section 2.3 is the "what". This section is the "how well" — and in this
project it is the part that actually decides the design.

Lecture 5.1 adds the rule that keeps this section honest: each characteristic
needs **a precise metric**, and a **fitness function** — *any mechanism that
gives an objective assessment of a characteristic* — to check it. Every
characteristic below has both.

### Choosing few, on purpose

Lecture 3.1 warns that supporting too many characteristics *"complicates the
overall design to little or no benefit"*. So four characteristics **drive**
this design. Everything else is supporting.

| | Characteristic | Explicit or implicit |
|---|---|---|
| **Driving** | NFR-01 Elasticity | Explicit — the brief says "extreme traffic spikes" |
| **Driving** | NFR-02 Data integrity | Explicit — the brief says "eliminate overselling" |
| **Driving** | NFR-03 Performance | Implicit — a queue nobody believes is a queue nobody joins |
| **Driving** | NFR-09 Fairness | Explicit — the brief says admission must "strictly respect FIFO order via queue tokens" |
| Supporting | NFR-04 Availability | Implicit |
| Supporting | NFR-05 Fault tolerance | Implicit |
| Supporting | NFR-06 Security | Implicit |
| Supporting | NFR-07 Auditability | Implicit |
| Supporting | NFR-08 Extensibility (two seams only) | Explicit — the brief names payment and promotion plug-ins |

---

### NFR-01 · Elasticity  *(driving)*

**Plain meaning.** Stay responsive during a sudden, huge jump in users.

Lecture 3.1 separates this from scalability, and the separation is the whole
project:

- **Scalability** — staying responsive as load grows *gradually over time*.
- **Elasticity** — staying responsive during a *significantly high
  instantaneous spike*.

A ticket sale is all spike. There is no gradual growth to plan for.

**Precise metric.** Concurrent users go from ~100 to **40,000 within 60
seconds**. Every one of them receives a queue token and a position.

**Fitness function.** A load test that ramps 100 → 40,000 virtual users in 60
seconds. It passes when 100% of join requests return a token, and no request
takes longer than NFR-03's limit.

**Design response.** The virtual waiting room. The buying engine never sees
40,000 people — it sees whatever rate Chai set, for example 200 per second.
The queue absorbs the spike; the engine stays calm.

---

### NFR-02 · Data integrity  *(driving)*

**Plain meaning.** Tickets confirmed must never exceed tickets that exist. Not
"almost never". Never.

This is the requirement that cannot be fixed by apologising afterwards. A
refund does not put Kenji in a seat.

**Precise metric.** For every tier, at every moment:

```
sold + held  ≤  total quantity
```

**Fitness function.** A concurrency test: 200 threads each try to hold 2 seats
from a tier with exactly 10 remaining. It passes when exactly 5 threads
succeed, 195 are refused, and the remaining count is 0 — with no run of the
test ever producing a different answer.

**Design response.** Three things together:

1. The hold is taken **atomically** — in one indivisible step, so no two
   requests can both read "10 left" before either writes.
2. Inventory drops at **hold** time, not payment time (locked decision 1).
3. One service, `InventoryService`, owns this, and only that service touches
   inventory. Events announce a hold only after it is saved
   (see `06-architecture.md`).

> **Atomic** means the step cannot be split in half. Either it fully happened
> or it fully did not. Like a light switch: there is no halfway.

---

### NFR-03 · Performance  *(driving)*

**Plain meaning.** The system answers fast enough that people trust it.

If the queue position takes 30 seconds to update, Kenji refreshes the page,
which makes the load worse, which makes it slower. Slow does not stay slow —
it becomes collapse.

**Precise metric.** Lecture 5.1 asks for an average *and* a maximum, so
outliers are caught:

| Operation | Average | Maximum |
|---|---|---|
| Issue a queue token | ≤ 200 ms | ≤ 1 s |
| Refresh queue position | ≤ 1 s | ≤ 5 s |
| Place or refuse a hold | ≤ 300 ms | ≤ 2 s |
| Organizer dashboard refresh | ≤ 2 s | ≤ 5 s |

**Fitness function.** Response times recorded during the NFR-01 load test, and
checked against this table automatically.

---

### NFR-09 · Fairness  *(driving)*

**Plain meaning.** Whoever joined the queue first gets in first. No skipping.

**FIFO** means *first in, first out*. It is the rule of a normal shop queue.

*Like the ticket machine at a bank counter: number 41 is never called before
number 40, however loudly 41 complains.*

**Precise metric.** For any two tokens of the same event, if Kenji's token was
issued before another customer's token, Kenji is admitted **no later than**
that customer. Zero exceptions.

**Fitness function.** A test that issues 10,000 tokens, runs admission in
batches of 200, and checks the admitted order against the issued order. It
passes only when the two orders are identical.

**Design response.**

1. Every token is stamped with the server time it was issued (FR-01).
2. Admission walks the queue strictly by that stamp, in batches (FR-05).
3. One token per account per event, so nobody holds several places (FR-03).
4. Reconnecting gives back the same place, not a new one at the back (FR-04).

---

### NFR-04 · Availability  *(supporting)*

**Plain meaning.** The system is up when the sale opens.

**Precise metric.** 99.9% availability during the announced sale window. The
window is short and known in advance, which is what makes this achievable.

**Fitness function.** Uptime monitoring across the sale window.

---

### NFR-05 · Fault tolerance  *(supporting)*

**Plain meaning.** One broken part does not break everything.

**Precise metric.** If the payment provider stops answering, customers can
still join the queue, still hold seats, and are told clearly that payment is
unavailable. The sale is degraded, not dead.

**Fitness function.** A test that disables the simulated payment provider and
confirms UC-01 and UC-02 still complete.

---

### NFR-06 · Security  *(supporting)*

**Plain meaning.** Tickets cannot be forged. Payment details are not ours to
lose.

**Precise metric.**

- Every digital ticket carries a signature that fails verification if any
  field is altered.
- No card number is ever stored by this system (CON-03).

**Fitness function.** A test that alters one field of a signed ticket payload
and confirms verification fails.

---

### NFR-07 · Auditability  *(supporting)*

**Plain meaning.** Afterwards, we can prove what happened to every seat.

**Precise metric.** For any ticket, the full chain can be reconstructed:
queue token → hold → payment → order → ticket, with timestamps.

**Fitness function.** A test that picks a confirmed order at random and walks
the chain backwards to the queue token.

---

### NFR-08 · Extensibility — and its limits  *(supporting)*

**Plain meaning.** New payment methods and new promotions can be added without
touching the booking engine.

**Precise metric.** Adding a payment method or a pricing rule requires **zero
changes** to any class in the core booking engine.

**Fitness function.** A structural test (Lecture 5.1 calls these *structural
measures*) that fails the build if any core package imports a concrete
plug-in class.

**And here is the limit.** Extensibility applies to **exactly two seams**:
`PaymentProvider` and `PricingRule`. Nothing else is designed to be pluggable.
Lecture 3.1's warning about overengineering is the reason: a system where
everything is a plug-in is a system nobody can read.

---

### Deliberately not prioritised

Naming these protects the design from being judged on promises it never made.

| Not a driver | Why not |
|---|---|
| Internationalisation | One market, one language, one currency for this project. |
| Offline use | A ticket sale is inherently online. |
| Scalability (gradual growth) | Real, but secondary. Elasticity is the hard case, and a system that survives a spike survives growth. |
| Extensibility beyond the two seams | See NFR-08. |
| Portability across cloud vendors | Not a stated concern. |

---

## 2.5 Constraints

A **constraint** is something fixed that the design did not get to choose.

| ID | Constraint | Where it comes from |
|---|---|---|
| CON-01 | *Retired.* Was: "the architecture must use the pipeline style", from the group's original proposal (`SDA Project.pdf`). The course lets each group choose its design (slide 8.5). After Lecture 11.1, the group chose the event-driven style instead: expiry and late payment happen later, which a one-way pipeline cannot model. Traced in §2.7. | — |
| CON-02 | The architecture must use the **microkernel** style for payment and promotion plug-ins. | The project brief: payment methods and pricing rules must plug in "without altering the core booking engine" |
| CON-03 | Card details are handled by the payment provider, never stored here. | Standard practice; also keeps NFR-06 achievable |
| CON-04 | Implementation language is **Java**. | Course convention |
| CON-05 | Delivery date: **12 October 2026**. | Group Project 2 brief |
| CON-06 | Six people, of whom three are on this project; the other three are on Lab 9 patterns. | Team composition |

---

## 2.6 Assumptions

An **assumption** is something the brief does not say, which we decided so the
design could move. Each one is a place a grader may push, so each is written
down rather than hidden.

| ID | Assumption | If it turns out wrong |
|---|---|---|
| ASM-01 | A customer already has an account before the sale opens. Registration is out of scope. | FR-03 (one token per account) would need another way to identify a person. |
| ASM-02 | The per-order limit is **4 tickets**. The brief gives no number. | One number changes in FR-11. Cheap. |
| ASM-03 | Tiers are pools of seats. Customers do not pick individual seat numbers. | A seat-map feature would change the domain model substantially. |
| ASM-04 | Payment providers are **simulated** for this project, behind the real interface. | Only the plug-in bodies change; the architecture does not. |
| ASM-05 | The queue admits customers strictly first-in-first-out. No priority lanes, no presale windows in this version. | Fan-club presale is already anticipated as a `PricingRule` plug-in, but queue priority would be a new mechanism. |
| ASM-06 | One event's sale at a time is the case being designed for. Concurrent sales are possible but not the design target. | Capacity planning changes; the model does not. |
| ASM-07 | An admitted customer has **5 minutes** to place a hold. After that the token expires and they must rejoin at the back (UC-01 extension 6a). The brief gives no number. | One constant changes in `AdmissionScheduler`. Cheap. |
| ASM-07 | All data lives **in memory** for this project. Nothing is saved to a database, so a restart starts the sale again. | A database would be added behind `core/`. The pages and the web API do not change. |
| ASM-08 | The customer is named by a request header, `X-Customer`, for the demo. There is no sign-in. | A real sign-in would set the same customer id. The web API does not change. |
| ASM-09 | The 4-ticket limit is **per order** (ASM-02). After holding or buying, a customer may join the back of the line again and buy another order. | A per-person limit would need the queue to check earlier orders before issuing a new token. |
| ASM-10 | Customers buy from a desktop browser (1440 × 900 design frame, usable from 1024 px wide). There is no phone layout. Decided 11 Oct 2026. | A phone layout would need its own screens; the web API would not change. |

---

## 2.7 Traceability

Every functional requirement traces to a use case and to the entities it
touches. Entity IDs are defined in `05-domain-model.md`.

| Use case | Requirements | Entities touched | Driving NFRs |
|---|---|---|---|
| UC-01 Join Waiting Room | FR-01 … FR-06 | E-01 Event, E-03 QueueToken | NFR-01, NFR-03, **NFR-09** |
| UC-02 Select & Hold Tickets | FR-07 … FR-13 | E-02 TicketTier, E-03 QueueToken, E-04 Reservation | **NFR-02**, NFR-03 |
| UC-03 Checkout & Pay | FR-14 … FR-19 | E-04 Reservation, E-05 Payment | NFR-02, NFR-05, NFR-08 |
| UC-04 View Digital Ticket | FR-20 … FR-22 | E-04 Reservation, E-06 DigitalTicket | NFR-06 |
| UC-05 Create Event with Tiers | FR-23 … FR-25 | E-01 Event, E-02 TicketTier | — |
| UC-06 Manage Event & Tiers | FR-26 … FR-30 | E-01 Event, E-02 TicketTier | NFR-02 (FR-28) |
| UC-07 Monitor Live Queue & Sales | FR-31 … FR-34 | E-01, E-02, E-03, E-04, E-05 | NFR-03 |

### Which requirement forces which architecture decision

This is the column that makes the Design deliverable defensible.

| Decision in `06-architecture.md` | Forced by |
|---|---|
| Virtual waiting room in front of the booking engine | NFR-01 |
| Admission in FIFO batches, ordered by token issue time | NFR-09, FR-05 |
| Atomic hold, inventory reduced at hold time | NFR-02, FR-08, FR-09 |
| One and only one service touches inventory | NFR-02 |
| Event-driven, broker topology: core parts react to events | NFR-01, NFR-05, FR-12, FR-17 |
| Events published only after the change is saved; the database is the truth | NFR-02, NFR-07 |
| Microkernel: core engine + two plug-in seams | CON-02, NFR-08 |
| Compile-based plug-ins, registry lookup | NFR-08 with CON-04 (Java) and project size |
| Server-owned 10-minute clock | FR-12, NFR-02 |
| Signed single-use ticket payload | FR-21, NFR-06 |
| Idempotency key on checkout | FR-18, NFR-02 |
