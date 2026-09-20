# Diagrams

Member 3's deliverables: the use case diagram, the System Sequence Diagram
(SSD), the Activity Diagram, and the UI mockups.

**Nothing here has to be invented.** Every diagram's content is specified
below, taken from `docs/04-use-cases.md` and `docs/05-domain-model.md`. Draw
what is written; if something is missing, it belongs in the document first,
then here.

## Status

| Diagram | Status | File |
|---|---|---|
| Use case diagram | To draw | — |
| System Sequence Diagram | **Built** | [`ssd-booking-payment.html`](ssd-booking-payment.html) |
| Activity diagram | **Built** | [`activity-booking-flow.html`](activity-booking-flow.html) |
| UI mockups | To draw — prompt ready | [`ui-mockup-prompt.md`](ui-mockup-prompt.md) |

The two built diagrams are interactive standalone HTML: open the file in a
browser. They carry light/dark themes, pan and zoom, guided views, and a
PNG / SVG export button for dropping into the report.

Each one is generated from a small JSON source next to it
(`*.sequence.json`, `*.workflow.json`). **Edit the JSON and regenerate** —
never hand-edit the HTML. To regenerate:

```
node ~/.claude/skills/archify/bin/archify.mjs deliver sequence \
  ssd-booking-payment.sequence.json ssd-booking-payment.html --quality showcase

node ~/.claude/skills/archify/bin/archify.mjs deliver workflow \
  activity-booking-flow.workflow.json activity-booking-flow.html --quality showcase
```

### One known limitation

The activity diagram does **not** draw the two return arrows (hold refused →
choose again, payment declined → retry). The renderer could not route them
without crossing other edges. Both loops are stated on the nodes themselves
("fresh numbers, choose again" / "hold keeps its time, retry") and in the
cards below the diagram. If the report needs the arrows drawn, they have to
be added by hand afterwards.

---

## 1 · Use case diagram

**File:** `use-case-diagram.png` (or `.svg`)
**Source:** `docs/04-use-cases.md` §4.3

### Content

Two actors, seven use cases, one system boundary box.

| Actor | Use cases |
|---|---|
| **Customer** | UC-01 Join Waiting Room · UC-02 Select & Hold Tickets · UC-03 Checkout & Pay · UC-04 View Digital Ticket |
| **Organizer** | UC-05 Create Event with Tiers · UC-06 Manage Event & Tiers · UC-07 Monitor Live Queue & Sales |

System boundary label: **Event Ticket & Queue Management System**

### Rules for this diagram

- **Only two actors, and both are people.** No "Database" actor, no "Payment
  Gateway" actor, no "Timer" actor. Those are parts of the solution, not
  people with goals.
- **No `<<include>>` or `<<extend>>` arrows.** UC-01 → UC-02 → UC-03 is a
  workflow, and workflows belong in the activity diagram.
- Clarity beats UML completeness. If a notation would need explaining, leave
  it out.

---

## 2 · System Sequence Diagram (SSD)

**File:** `ssd-booking-payment.png`
**Source:** `docs/04-use-cases.md` UC-01 → UC-02 → UC-03

### What an SSD is

A picture of the messages between the actor and the system **as a black box**.
It shows *what* is asked and *what* comes back — never what happens inside.

### Participants

```
   Ploy : Customer                    : System
```

Two lifelines. The system is one box — do not draw filters, services or the
database. Those are design, and this diagram is requirements.

### Messages, in order

| # | Direction | Message | Returns |
|---|---|---|---|
| 1 | Ploy → System | `joinQueue(customerId, eventId)` | `tokenId, position` |
| 2 | Ploy → System | `getQueuePosition(tokenId)` *(loop, every 5 s until admitted)* | `position, estimatedWait` |
| 3 | System → Ploy | `admitted(tokenId)` | — |
| 4 | Ploy → System | `listTiers(eventId)` | `tiers[ name, price, remaining ]` |
| 5 | Ploy → System | `holdTickets(tokenId, tierId, quantity)` | `reservationId, expiresAt` |
| 6 | Ploy → System | `checkout(reservationId, providerKey)` | `totalAmount` |
| 7 | System → Ploy | `paymentConfirmed(reservationId)` | — |
| 8 | Ploy → System | `getDigitalTickets(reservationId)` | `tickets[ ticketId, qrPayload ]` |

### Fragments to show

Three UML fragments make this diagram worth more than a straight list:

- **`loop`** around message 2 — *"every 5 s until admitted"*
- **`alt`** around messages 5 and 6:
  - `[hold acquired]` → continue
  - `[hold refused]` → `holdRefused(reason, remaining)` and stop
- **`alt`** around message 7:
  - `[authorised AND hold still alive]` → `paymentConfirmed`
  - `[hold expired]` → `paymentVoided(reason)` — **no ticket, nothing charged**

> The second `alt` is the point of this diagram. It is UC-03 extension 7a, and
> it is the behaviour the brief never specified. A drawing that shows only the
> happy path shows the easy half.

### Optional

A third lifeline `: PaymentProvider` may be added for messages 6–7 if the
external call should be visible. **Recommended: leave it out.** An SSD is
about the boundary between the actor and the system; the provider is behind
that boundary, and `docs/06-architecture.md` already shows it.

### What the built version does

`ssd-booking-payment.html` shows 10 messages rather than the 16 the table
above implies, because the brief asks for the **core booking and payment
flow**. Two compressions were made so the whole diagram stays readable on one
screen:

- `listTiers` and its reply are folded into one message: *admitted + tiers:
  VIP 16 left*.
- `getDigitalTickets` (UC-04) is left out. The flow ends at the payment
  outcome, which is where "booking and payment" ends.

Both `alt` fragments and the `loop` are present. If the report needs the two
dropped messages, add them back in `ssd-booking-payment.sequence.json` and
raise `meta.viewBox` height.

---

## 3 · Activity diagram — the primary workflow

**File:** `activity-booking-flow.png`
**Source:** the brief's Primary Workflow, plus UC-01/02/03 extensions

### Two swimlanes

`Customer` and `System`.

### The flow

```
 ● start
 │
 ├─[Customer]  Open sale page
 ├─[System]    Issue queue token            ─── decision: already has an active token?
 │                                               yes → return existing token and position
 ├─[System]    Show position, refresh every 5 s
 │
 ◆ [System]    Is it this token's turn?
 │   no  → loop back to "Show position"
 │   yes ↓
 ├─[System]    Admit customer
 ├─[System]    Show tiers with prices and remaining
 ├─[Customer]  Choose tier and quantity
 │
 ◆ [System]    Atomic hold — did it succeed?
 │   no  → Show reason and fresh numbers → back to "Show tiers"
 │   yes ↓
 ├─[System]    Reduce remaining · create reservation · stamp expiry = now + 10 min
 ├─[System]    Show countdown
 │
 ├─[Customer]  Enter payment details
 ├─[System]    Request authorisation from the provider
 │
 ◆ [System]    Authorised?
 │   no  → Show decline · hold KEEPS its remaining time → back to "Enter payment details"
 │   yes ↓
 │
 ◆ [System]    Is the hold STILL alive?          ◄── the critical decision
 │   no  → Void the payment · no ticket · tell the customer  → ● end (failure)
 │   yes ↓
 ├─[System]    Confirm reservation · capture payment · issue digital tickets
 └─● end (success)
```

### The timeout — draw it as an interrupting event

Attach a **timer event** to the region that covers *Show countdown* through
*Request authorisation*:

```
   ⏱ 10 minutes elapse
        │
        ▼
   [System] Return seats to the tier · reservation → EXPIRED · token spent
        │
        ▼
        ● end (expired)
```

> **This is the part most drawings miss.** The expiry is not a step in the
> flow — it can fire at any moment during the countdown, from outside. Drawing
> it as a normal decision node in the sequence would say something false about
> how it works.

### Two decisions that must both be visible

1. **Did the atomic hold succeed?** — this is where overselling is prevented.
2. **Is the hold still alive when the payment comes back?** — this is where a
   customer is protected from being charged for nothing.

A diagram missing either one has missed the design.

---

## 4 · UI mockups

**Files:** `ui-01-queue.png`, `ui-02-selection.png`, `ui-03-confirmation.png`
**Named in the brief as the UI mockup target:** the Customer Queue screen.

Design for a **phone**. Ploy is standing up with one hand on a phone, not
sitting at a laptop.

### Screen 1 — Queue *(the required one)*

Must show:

- The event name and date — *BLACKPINK — Bangkok · Impact Arena, 14 Nov 2026*
- **Her position, as the largest thing on the screen** — *4,182*
- A progress bar showing movement
- An estimated wait — *~6 min* — clearly marked as approximate
- One line telling her what will happen next, and that her place is safe

A first draft of this screen is in
[`../plans/sda-project-docs/plan.html`](../plans/sda-project-docs/plan.html)
(the "Customer Queue screen" figure) and can be used as a starting point.

> **The hardest thing about this screen is that nothing happens on it.** Ploy
> stares at it for six minutes. If it does not visibly move, she refreshes —
> and every refresh makes the spike worse for everyone. Movement is a feature,
> not decoration.

### Screen 2 — Selection and countdown

Must show:

- Every tier with price and remaining: *VIP ฿6,500 — 412 left*
- A quantity picker, capped at **4** (FR-11)
- **The countdown**, prominent, once a hold exists — starting at `10:00`
- The total

Show **two states** if there is time: before the hold (no countdown yet) and
after the hold (countdown running).

### Screen 3 — Confirmation

Must show:

- What she bought, the total paid
- The QR code
- **One ticket per seat** — 2 tickets for 2 seats, not one card saying "×2"
  (INV-07, R-E06-1)

### Worth drawing if there is time — the failure screen

What Ploy sees when her hold expires **while she is paying** (UC-03 ext. 7a).

Two facts have to land, in this order:

1. **You have not been charged.**
2. The seats went back on sale.

This is the moment a customer decides whether the system cheated them. It is
listed as an open product question in `docs/03-prd.md` §3.9 for exactly that
reason.

---

## Checklist before submitting

- [ ] Use case diagram — two actors only, no system or API actors
- [ ] SSD — system drawn as **one** black box
- [ ] SSD — the `alt` fragment for *hold expired during payment* is present
- [ ] Activity diagram — the 10-minute timeout is an **interrupting** event,
      not a step in the sequence
- [ ] Activity diagram — both critical decisions visible
- [ ] Queue screen — position is the largest element
- [ ] Selection screen — countdown visible, quantity capped at 4
- [ ] Confirmation — one ticket per seat
- [ ] Every name in every diagram matches `docs/04-use-cases.md` exactly
