# 03 · Product Requirements Document (PRD)

**The product view.** Who this is for, what success looks like, what we build
first and what we leave out.

This is **not** the engineering list. That is `02-requirements.md`. The
difference in one line:

- **Requirements** answer *"what must it do, and how do we test that?"*
- **PRD** answers *"why does this exist, who is it for, and what do we build first?"*

---

## 3.1 One-paragraph summary

**Event Ticket & Queue Management System** sells tickets for events where
demand is far larger than supply. It puts every buyer through a virtual
waiting room, admits them a few at a time, and holds their seats for ten
minutes while they pay. Because seats are taken out of stock the moment they
are held — not when money arrives — the same seat can never be sold twice, no
matter how many people click at once.

---

## 3.2 The problem and the opportunity

### The problem people actually have

Three groups suffer when a big sale goes wrong.

| Who | What goes wrong today | What it costs them |
|---|---|---|
| **The fan** | Site freezes. Refreshes for twenty minutes. Finally gets in — sold out. Or worse: pays, then gets an email saying the order was cancelled. | Money held on a card for days. A show missed. No trust left. |
| **The organizer** | Sales stall during the only hour that matters. Support is flooded with duplicate-sale complaints for weeks afterwards. | Lost revenue, refund costs, reputation. |
| **The venue** | Two people arrive with a valid-looking ticket for the same seat. | A scene at the door, staff dealing with it instead of running the show. |

### Why now, why this shape

The fix is not "a bigger server". Traffic during a ticket sale is not high —
it is *instantaneously* high, for about ninety seconds, and then it is over.
Buying enough machines to serve 40,000 simultaneous checkouts means buying
machines that sit idle 99.9% of the year.

The opportunity is to **change the shape of the traffic** instead of the size
of the machine. A queue turns a wall of 40,000 people into a steady stream of
200 per second. The expensive part of the system then only ever has to be
built for 200 per second.

---

## 3.3 Who this is for

### Kenji — the fan

- Has the page open at 09:59. Phone in hand, not a laptop.
- Does not care how it works. Cares about two things: **am I getting in**, and
  **how long**.
- Will refresh the page if they are not told what is happening. Every refresh
  makes the problem worse for everyone.
- **Their job to be done:** *"Tell me honestly whether I have a chance, and if I
  do, don't lose my place."*

### Chai — the organizer

- Set up the event weeks ago. On the day, he is watching, not clicking.
- Needs to know within seconds whether the sale is working.
- Will need to add a payment method or run a promotion for the next event, and
  does not want a rebuild each time.
- **His job to be done:** *"Sell everything, see it happening, and never have
  to apologise to a customer at the gate."*

---

## 3.4 Goals and success metrics

### Product goals

1. **Nobody is ever sold a seat that does not exist.**
2. **Everybody knows where they stand** — a real position, a real estimate,
   updated while they wait.
3. **The sale completes** — the system is still answering at the end of the
   ninety seconds, not just at the start.
4. **Adding a payment method or a promotion is a small job**, not a project.

### How we would know it worked

| Goal | Measure | Target |
|---|---|---|
| No overselling | Tickets confirmed vs. seats that exist | Exactly equal. Any difference at all is a failure. |
| Everyone knows where they stand | Customers who get a queue position | 100% of people who join |
| The sale completes | Requests answered across the sale window | ≥ 99.9% |
| Fans are not left guessing | Queue position age when displayed | ≤ 5 seconds old |
| Seats are not lost to abandoned baskets | Held seats returned after expiry | 100%, within 10 s of expiry |
| Extension is cheap | Files changed to add a payment method | Only the new plug-in and one registry line |

The engineering versions of these numbers, with the tests that check them, are
NFR-01 to NFR-09 in `02-requirements.md`.

---

## 3.5 Scope

### In scope

| Area | What is included |
|---|---|
| Waiting room | Join, position, estimated wait, reconnect, fair first-in-first-out admission |
| Buying | Browse tiers, hold seats for 10 minutes, countdown, automatic release |
| Paying | Price calculation with promotions, payment through a plug-in, confirmed order |
| Ticket | One signed, single-use digital ticket per seat bought |
| Organizing | Create an event and its tiers; edit, restock, close |
| Watching | Live queue length, admission rate, held / sold / remaining per tier, revenue |

### Out of scope — and why

| Not building | Reason |
|---|---|
| Scanning tickets at the gate | Needs a third actor and venue hardware. We *issue* a verifiable ticket; verifying it at the door is another system. |
| Refunds and cancellations | Real money movement with real policy. Large enough to be its own project. |
| Seat maps — picking chair A-14 | Tiers are pools. A seat map changes the domain model and the hold mechanism. |
| Real Stripe / PromptPay integration | Simulated behind the real interface (ASM-04). The architecture is demonstrated without live keys. |
| Ticket resale or transfer | Out of scope for this project. |
| Account registration and login | Assumed to exist already (ASM-01). |
| Email and SMS notifications | Nice to have. Not what the project is about. |

> **Saying no in writing is part of the design.** An unbounded scope is the
> fastest way to a system that does everything badly.

---

## 3.6 What gets built first

Three slices. Each one is useful on its own and each proves something the next
one depends on.

### Slice 1 — Prove the seats are safe

**The point:** show that overselling is impossible, before anything is pretty.

- Create an event with tiers (UC-05)
- Hold seats atomically, with the 10-minute expiry that really releases them
  (UC-02)
- Confirm an order with a simulated payment that always succeeds (UC-03, happy
  path only)

**Done when:** the NFR-02 concurrency test passes — 200 threads race for 10
seats and exactly 10 are sold, every single run.

### Slice 2 — Prove the spike is absorbed

**The point:** show the queue does its job.

- Join the waiting room, get a position, see it update (UC-01)
- Organizer sets the admission rate (FR-30)
- The customer queue screen

**Done when:** the NFR-01 load test passes — 100 → 40,000 users in 60 seconds,
everyone gets a token, response times inside the NFR-03 table.

### Slice 3 — Prove the seams are real

**The point:** show the microkernel is not just a diagram.

- Second payment provider plug-in
- A pricing rule plug-in (early bird)
- Payment failure paths (FR-17, FR-19)
- Digital ticket with its signature (UC-04)
- Organizer dashboard (UC-07)

**Done when:** a new payment method is added without editing a single file in
the core booking engine.

---

## 3.7 Risks

| Risk | How likely | How bad | What we do about it |
|---|---|---|---|
| The atomic hold is implemented as read-then-write by mistake | Medium — it is the easy way to write it | **Severe** — overselling returns, silently | The concurrency test runs on every build. It is the one test that must never be skipped. |
| Payment authorisation is slower than the 10-minute hold | Low | Severe — charged, no seat | FR-16 and FR-17 make the outcome explicit and tested |
| The queue becomes the bottleneck it was meant to remove | Medium | High | The queue does the least work possible: issue a token, report a position. No pricing, no inventory. |
| Scope creep into seat maps or refunds | **High** — both sound small | Medium | Written into "out of scope" above. Changing that list is a decision, not a drift. |
| Three members, one project, one month | Certain | Medium | Slices in 3.6 are ordered so that stopping after slice 1 or 2 still leaves something that demonstrates the point. |
| Plug-in contracts change after code depends on them | Medium | High | The two interfaces are locked in `AGENTS.md` before implementation starts. |

---

## 3.8 Dependencies

| Depends on | Status |
|---|---|
| Customer accounts already exist | Assumed (ASM-01) |
| A database that supports an atomic conditional update | Required by NFR-02. Any mainstream relational database does this. |
| Payment provider | Simulated for this project (ASM-04) |
| Java toolchain | CON-04 |

---

## 3.9 Open product questions

Not blocking. Worth deciding before slice 3.

1. **What does Kenji see when their hold expires while they are paying?** FR-17
   says the payment is voided. The wording of that screen matters more than
   most of this document — it is the moment a customer decides whether the
   system cheated them.
2. **Should the queue show a position, an estimated time, or both?** A
   position is honest. An estimate is useful. An estimate that turns out wrong
   is worse than no estimate. Currently: both, with the estimate clearly
   marked as approximate.
3. **What happens to a customer who is admitted but never chooses anything?**
   Currently their token simply expires with no hold ever made. An explicit
   timeout on the selection screen may be fairer to the people behind them.
