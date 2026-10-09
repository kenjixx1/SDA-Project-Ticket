# 05 · Domain Model

**Part of Deliverable 2 — Requirements Model**

The six things the system stores, what each one means in plain English, and
the rules that must never be broken.

---

## 5.1 What a domain model is

**A domain model is the list of real things the system has to keep track of,
and how they relate to each other.**

**Analogy.** Before opening a restaurant you write down what you must keep
track of: tables, bookings, orders, bills. Not the kitchen layout, not the
software — the *things*. Get that list wrong and everything built on top is
wrong too.

**In this system.** Six things. The brief names all six; this document gives
each one fields, states and rules.

---

## 5.2 The six entities at a glance

| ID | Entity | In one sentence |
|---|---|---|
| **E-01** | `Event` | The concert being sold. |
| **E-02** | `TicketTier` | One price band within an event, with a fixed number of seats. |
| **E-03** | `QueueToken` | A customer's numbered place in the waiting room. |
| **E-04** | `Reservation` | Seats taken out of the pool for one customer, with a deadline. |
| **E-05** | `Payment` | One attempt to pay for one reservation. |
| **E-06** | `DigitalTicket` | The thing Kenji shows at the gate. |

### How they connect

```
   Event  (E-01)
     │  "has many"
     ├──────────────► TicketTier  (E-02)
     │                    │
     │                    │ "seats are drawn from"
     │                    ▼
     │  "queues for"   Reservation  (E-04) ──── "paid by" ───► Payment  (E-05)
     ├──► QueueToken ──────┘                                      (0..n attempts)
     │      (E-03)         │
     │    one per          │ "when confirmed, issues one per seat"
     │    customer         ▼
     │    per event   DigitalTicket  (E-06)
```

Read it as a sentence:

> An **Event** has several **TicketTiers**. A customer takes a **QueueToken**
> for the Event. When admitted, their **Reservation** draws seats from one
> **TicketTier**. They make one or more **Payment** attempts against it. When
> one succeeds, the Reservation issues one **DigitalTicket** per seat.

---

## E-01 · Event

**The concert being sold.**

### Attributes

| Field | Type | Meaning |
|---|---|---|
| `eventId` | id | Unique. Never reused. |
| `name` | text | *The 1975 Bangkok* |
| `venue` | text | *Impact Arena* |
| `eventDateTime` | timestamp | *14 Nov 2026, 19:00* |
| `saleOpensAt` | timestamp | *1 Oct 2026, 10:00:00* |
| `state` | enum | See below |
| `admissionRatePerSecond` | integer | How many customers the queue lets through each second. Set by Chai. |
| `organizerId` | id | Chai |

### States

```
  DRAFT ──publish──► SCHEDULED ──saleOpensAt reached──► ON_SALE ──close──► CLOSED
    │                                                       │
    └───────────────────── cancel ──────────────────────────┴──────► CANCELLED
```

| State | Meaning |
|---|---|
| `DRAFT` | Being set up. Not visible to customers. |
| `SCHEDULED` | Published, with at least one tier. Waiting for the clock. |
| `ON_SALE` | The queue is open and seats are selling. |
| `CLOSED` | No new admissions. Reached when the last live hold resolves. |
| `CANCELLED` | Called off. Existing digital tickets stop verifying. |

### Rules

- **R-E01-1** — cannot leave `DRAFT` without at least one `TicketTier`. (FR-25)
- **R-E01-2** — `saleOpensAt` must be in the future when set.
- **R-E01-3** — while `ON_SALE`, no tier price may change. (FR-26)

---

## E-02 · TicketTier

**One price band within an event, holding a fixed number of seats.**

**Analogy.** A cinema does not sell "chair 14, row F". It sells *front*,
*middle* and *back*. You get a seat in the band you paid for. A tier is that
band.

### Attributes

| Field | Type | Meaning |
|---|---|---|
| `tierId` | id | Unique |
| `eventId` | id | Which event it belongs to |
| `name` | text | *VIP* |
| `price` | money | *฿6,500* |
| `totalQuantity` | integer | *500* — seats that exist |
| `heldQuantity` | integer | *12* — inside somebody's ten minutes |
| `soldQuantity` | integer | *476* — paid for |

`remaining` is not stored. It is calculated:

```
remaining = totalQuantity − heldQuantity − soldQuantity
          = 500 − 12 − 476
          = 12
```

> **Why three numbers instead of one.** A single "remaining" counter cannot
> tell you *why* a seat is unavailable. Chai needs to know whether those 12
> seats are gone forever or coming back in nine minutes. Held is a real state,
> not a rounding error, so it gets its own column — on the dashboard and here.

### Rules

- **R-E02-1** — `price ≥ 0`, `totalQuantity > 0`.
- **R-E02-2** — `totalQuantity` may be increased at any time. (FR-27)
- **R-E02-3** — `totalQuantity` may never be reduced below
  `heldQuantity + soldQuantity`. (FR-28)
- **R-E02-4** — `heldQuantity` and `soldQuantity` only ever change through the
  one component that owns inventory. Nothing else writes to them. See
  `06-architecture.md`.

---

## E-03 · QueueToken

**A customer's numbered place in the waiting room.**

**Analogy.** The paper slip from the machine at a bank. It carries a number.
It only means something in that bank, on that day, for that person.

### Attributes

| Field | Type | Meaning |
|---|---|---|
| `tokenId` | id | Unique. Also the identity for the whole purchase — see below. |
| `eventId` | id | Which sale |
| `customerId` | id | Kenji |
| `issuedAt` | timestamp | Sets the place in line. First issued, first admitted. |
| `position` | integer | *4,182* — derived from `issuedAt` |
| `state` | enum | See below |
| `admittedAt` | timestamp | Null until admitted |

### States

```
  WAITING ──admitted──► ADMITTED ──hold created──► SPENT
     │                     │
     └──── EXPIRED ◄───────┘   (abandoned, or its reservation expired)
```

| State | Meaning |
|---|---|
| `WAITING` | In line. Position is shown and refreshed. |
| `ADMITTED` | Through the door. May now select and hold. |
| `SPENT` | Used for a reservation. Cannot be used again. |
| `EXPIRED` | Abandoned, or its reservation ran out of time. |

### Rules

- **R-E03-1** — **at most one token in `WAITING` or `ADMITTED` per
  (customerId, eventId).** (FR-03) This is what stops ten browser tabs from
  buying ten places in line.
- **R-E03-2** — reconnecting returns the existing token, never a new one.
  (FR-04)
- **R-E03-3** — admission order is strictly by `issuedAt`. (FR-05, ASM-05)
- **R-E03-4** — one token permits **one** reservation. (Locked decision 3)

---

## E-04 · Reservation

**Seats taken out of the pool for one customer, with a deadline.**

This is the most important entity in the system. It is where overselling is
prevented.

### Attributes

| Field | Type | Meaning |
|---|---|---|
| `reservationId` | id | Unique |
| `tokenId` | id | The queue token that earned it |
| `tierId` | id | Which tier the seats came from |
| `quantity` | integer | *2* |
| `unitPrice` | money | *฿6,500* — copied at hold time, so a later price edit cannot change a live order |
| `totalAmount` | money | *฿13,000* after pricing rules |
| `state` | enum | See below |
| `heldAt` | timestamp | *10:04:40* |
| `expiresAt` | timestamp | *10:14:40* — **stamped by the server**, exactly 10 minutes later |
| `idempotencyKey` | text | Derived from `reservationId`. Makes a repeated checkout safe. |

### States

```
                  ┌── payment authorised AND still held ──► CONFIRMED
   HELD ──────────┤
     │            └── customer releases ──────────────────► RELEASED
     │
     └── expiresAt passes with no confirmation ──────────► EXPIRED
```

| State | Meaning | Seats are… |
|---|---|---|
| `HELD` | Inside the ten minutes | counted in `heldQuantity` |
| `CONFIRMED` | Paid for | moved to `soldQuantity` |
| `EXPIRED` | Ran out of time | returned to the pool |
| `RELEASED` | Given up on purpose | returned to the pool |

### Rules

- **R-E04-1** — creating a `HELD` reservation and increasing
  `tier.heldQuantity` happen **atomically**: in one indivisible step, or not
  at all. This is the single rule that makes NFR-02 achievable.
- **R-E04-2** — **no partial holds.** A request for 2 gets 2 or gets 0.
- **R-E04-3** — `expiresAt = heldAt + 10 minutes`, set by the **server's**
  clock. The countdown Kenji sees is display only. (Locked decision 2)
- **R-E04-4** — on `HELD → CONFIRMED`: `heldQuantity` decreases and
  `soldQuantity` increases by the same amount, in one step.
- **R-E04-5** — on `HELD → EXPIRED` or `HELD → RELEASED`: `heldQuantity`
  decreases and nothing else changes. The seats are back on sale.
- **R-E04-6** — a reservation may **only** become `CONFIRMED` while its state
  is still `HELD` and `now < expiresAt`. (FR-16) This is the rule that decides
  the race in UC-03 extension 7a.

> **Read R-E04-6 once more.** It is one sentence, and it is why nobody can be
> charged for a seat they do not have.

---

## E-05 · Payment

**One attempt to pay for one reservation.**

Note *attempt*. A declined card is not the end of the story — the hold still
has time left, so there can be several payments against one reservation.

### Attributes

| Field | Type | Meaning |
|---|---|---|
| `paymentId` | id | Unique |
| `reservationId` | id | What is being paid for |
| `providerKey` | text | *"promptpay"* or *"stripe"* — a **key**, never a class name |
| `amount` | money | *฿13,000* |
| `state` | enum | See below |
| `idempotencyKey` | text | Copied from the reservation |
| `providerReference` | text | Whatever the provider calls this transaction |
| `requestedAt` / `settledAt` | timestamp | For auditing (NFR-07) |

### States

```
   PENDING ──► AUTHORISED ──► CAPTURED      (money taken, ticket issued)
      │             │
      │             └──────► VOIDED         (hold expired first — FR-17)
      │
      └──────► DECLINED                     (hold stays alive — FR-19)
```

| State | Meaning |
|---|---|
| `PENDING` | Sent to the provider, no answer yet |
| `AUTHORISED` | The provider says the money is good, not yet taken |
| `CAPTURED` | Money taken. Only reachable when R-E04-6 holds. |
| `VOIDED` | Authorised, then cancelled because the hold had expired |
| `DECLINED` | Refused. The reservation keeps its remaining time. |

### Rules

- **R-E05-1** — `providerKey` is a lookup key. The core booking engine never
  names a payment class. (NFR-08, see `06-architecture.md`)
- **R-E05-2** — two payments with the same `idempotencyKey` are the same
  payment. The second returns the first one's result. (FR-18)
- **R-E05-3** — `AUTHORISED → CAPTURED` requires R-E04-6 to hold at that
  moment. Otherwise `AUTHORISED → VOIDED`.
- **R-E05-4** — **no card number is ever stored here.** (CON-03, NFR-06)

---

## E-06 · DigitalTicket

**The thing Kenji shows at the gate.**

### Attributes

| Field | Type | Meaning |
|---|---|---|
| `ticketId` | id | Unique |
| `reservationId` | id | Where it came from |
| `eventId`, `tierId` | id | Printed on the face |
| `holderName` | text | *Kenji* |
| `issuedAt` | timestamp | |
| `payload` | text | The contents encoded in the QR code |
| `signature` | text | Proof this system produced it |
| `state` | enum | `VALID` · `CANCELLED` |

### Rules

- **R-E06-1** — **one ticket per seat.** A reservation for 2 issues 2 tickets,
  not one ticket saying "×2". Two people go through two gates.
- **R-E06-2** — tickets are only issued from a `CONFIRMED` reservation.
- **R-E06-3** — the `signature` is calculated from the `payload`. Change any
  field and it stops matching. (FR-21, NFR-06)
- **R-E06-4** — the payload is **single-use**. Whoever arrives second with the
  same code is turned away — by the gate system, which is out of scope here.
- **R-E06-5** — cancelling the event sets every ticket to `CANCELLED`, and
  they no longer verify.

---

## 5.3 Invariants — the rules that must always be true

An **invariant** is a statement that is true before an operation and true
after it, no matter what. If one of these is ever false, the system is broken,
not merely wrong.

| ID | Invariant | Why it matters |
|---|---|---|
| **INV-01** | For every tier: `heldQuantity + soldQuantity ≤ totalQuantity` | **This is "no overselling", written as arithmetic.** It is the most important line in this document. |
| **INV-02** | For every tier: `heldQuantity` equals the total quantity of its `HELD` reservations | The counter and the reservations cannot drift apart. |
| **INV-03** | For every tier: `soldQuantity` equals the total quantity of its `CONFIRMED` reservations | Same, for sold seats. |
| **INV-04** | At most one `WAITING`/`ADMITTED` token per customer per event | A fair queue. (FR-03) |
| **INV-05** | Every `CONFIRMED` reservation has exactly one `CAPTURED` payment | No seat without money. |
| **INV-06** | Every `CAPTURED` payment has exactly one `CONFIRMED` reservation | No money without a seat. |
| **INV-07** | Number of `VALID` tickets for a reservation equals its `quantity` | Two seats, two tickets. |

> **INV-05 and INV-06 are a pair on purpose.** One alone would allow the other
> failure. Together they say: money and seats move as one, or not at all.

### How they get checked

INV-01 is the fitness function in `02-requirements.md` §NFR-02: 200 threads
race for the last 10 seats, and exactly 10 are sold, every run. INV-02 and
INV-03 are checked by a reconciliation test that recounts from the
reservations and compares against the stored counters.

---

## 5.4 The worked example, end to end

Kenji's purchase, with every entity as it changes. Times are from the story.

| Time | What happens | Entity state |
|---|---|---|
| 10:00:03 | Kenji joins | `QueueToken` **WAITING**, position 4,182 |
| 10:04:12 | Admitted | `QueueToken` **ADMITTED** |
| 10:04:40 | Picks 2 × VIP; hold taken | `Reservation` **HELD**, expires 10:14:40 · VIP `heldQuantity` 10 → 12 · remaining 16 → 14 |
| 10:05:02 | Chooses PromptPay | `Payment` **PENDING**, ฿13,000 |
| 10:05:10 | Provider answers | `Payment` **AUTHORISED** |
| 10:05:10 | R-E04-6 checked: still `HELD`, 9m30s left → passes | `Reservation` **CONFIRMED** · `Payment` **CAPTURED** · VIP held 12 → 10, sold 474 → 476 |
| 10:05:11 | Tickets issued | 2 × `DigitalTicket` **VALID** |

**And the version where it goes wrong.** Same story, but the bank is slow:

| Time | What happens | Entity state |
|---|---|---|
| 10:14:40 | The ten minutes run out | `Reservation` **EXPIRED** · VIP held 12 → 10, remaining 14 → 16 · seats back on sale |
| 10:14:46 | Authorisation finally arrives | `Payment` **AUTHORISED** |
| 10:14:46 | R-E04-6 checked: reservation is `EXPIRED` → **fails** | `Payment` **VOIDED** · no ticket issued |

Kenji does not get their seats. **Kenji is not charged.** Both facts are
consequences of one rule, R-E04-6, and both are tested.
