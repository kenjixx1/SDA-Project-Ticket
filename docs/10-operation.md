# 10 · Operation

**Deliverable 5 of 6 — Operation**

**This document says how EventFlow is run, checked and looked after.** It covers
the day before the sale, the ten minutes around 10:00 on 1 October, and what to
do when something goes wrong.

*Like a café's opening checklist: switch on the machines, test one coffee,
know what to do if the milk runs out.*

**Real example.** At 09:59:30 Chai has The 1975 Bangkok published. At 10:00
the sale opens, 23,768 people are still waiting at 10:01:00, the queue lets in 200
per second, and every VIP, Zone A and Zone B seat is held or sold without one seat
being sold twice. This document is how that morning is run.

---

## 10.1 Words this document uses

- **Operation** — running the system for real users and keeping it healthy.
- **Runbook** — a short list of "when you see this, do that" steps, written
  before anything goes wrong. See `07-glossary.md`.
- **Fitness function** — an automatic check that proves a quality target is
  met (`07-glossary.md`).
- **Degraded** — part of the system has stopped, but the rest still works. See
  `07-glossary.md`.
- **Fake engine** — the in-memory stand-in for `core/` that the pages use today
  (`07-glossary.md`, *Fake*).

---

## 10.2 What runs, and where

**One Java program on one machine.** It serves the pages and answers them.

```
 Browser                         One Java program (port 7070)
 ┌────────────────────┐          ┌───────────────────────────────────────┐
 │ index.html · Kenji │ ──HTTP──►│ web/  routes ─► the switch ─► fake    │
 │ organizer.html·Chai│ ──HTTP──►│                      (later: core/)   │
 └────────────────────┘          └───────────────────────────────────────┘
```

| Part | Built with | Started by | Tested by |
|---|---|---|---|
| Booking engine, `core/` | Plain Java 17, no libraries | `./run.sh demo` | `./run.sh test` |
| Pages and web API, `web/` + `public/` | Java 17, Javalin, Maven | `mvn -q compile exec:java` | `mvn test` |

- The two builds sit side by side on purpose. `./run.sh` skips `web/`, so
  `core/` stays library-free (`AGENTS.md` §8).
- All data lives in memory (ASM-08). Stopping the program ends the sale.

---

## 10.3 How to run it

**Needs:** JDK 17 or newer. Maven 3.9 or newer for the web part.

1. Get the code: `git clone https://github.com/kenjixx1/SDA-Project-Ticket.git`
2. Check the booking engine: `./run.sh test` → **31 passed, 0 failed**.
3. Check the web part: `mvn test` → **86 tests, 0 failures**.
4. Start the app: `mvn -q compile exec:java`.
5. Open the two pages:

| Who | Address |
|---|---|
| Kenji, the customer | `http://localhost:7070/?dev` |
| Chai, the organizer | `http://localhost:7070/organizer.html?dev` |

To run the pages on the real booking engine instead: `ENGINE=core mvn -q compile exec:java`
(see §10.7).

`?dev` shows the **demo controls** in the corner. They exist only for the fake
engine.

| Control | What it does |
|---|---|
| Pause, ×1, ×10, ×30, ×100 | How fast the demo clock runs |
| +1 min, +6 min, +10 min | Jump the clock forward |
| Scenario | Force a hard case: `RACE_LOST`, `SOLD_OUT_ALL`, `PROVIDER_DOWN`, `ALL_PROVIDERS_DOWN`, `BANK_SLOW`, `TURN_PASSED`, `EVENT_CANCELLED`, `SALE_CLOSED` |
| Reset | Back to 09:59:30 on 1 October, story data restored |

To run on another port: `PORT=7071 mvn -q compile exec:java`.

---

## 10.4 Runbook — sale day

**The order of the morning, using the story.** Times are the demo clock.

| Time | Who | Step | Screen |
|---|---|---|---|
| Day before | Chai | Create The 1975 Bangkok, add VIP ฿6,500 × 500, Zone A ฿3,800 × 2,000, Zone B ฿1,900 × 5,000, keep 200 per second, publish | SCR-30 → SCR-33 |
| Day before | Team | Run both test suites. Both must pass before the sale | — |
| 09:59:30 | Chai | Open the event page. State reads `SCHEDULED` | SCR-34b |
| 09:59:30 | Kenji | Event page shows the countdown to 10:00 | SCR-05a |
| 10:00:00 | System | State turns `ON SALE`. The Join button turns on | SCR-05b |
| 10:00:30 | Kenji | Joins the queue. Position 4,182, about 21 seconds | SCR-06a |
| 10:00 onward | Chai | Opens the live dashboard and watches it | SCR-40 |
| 10:00:51 | Kenji | Admitted. Holds 2 × VIP. 10 minutes start | SCR-08 → SCR-09 |
| 10:07 | Kenji | Pays by PromptPay. Gets 2 tickets | SCR-12 → SCR-17 |
| Sold out, or Chai decides | Chai | Close the sale. People inside their 10 minutes keep them | SCR-39 → SCR-34d |
| Last hold ends | System | State turns `CLOSED`. Dashboard reads *Final* | SCR-34e, SCR-40c |

---

## 10.5 Runbook — when something goes wrong

**Each row: what you see, why it happens, what to do.**

| You see | Why | Do |
|---|---|---|
| Dashboard: **"These figures are 41 seconds old."** | The page lost contact with the server | Nothing to fix in the page; it retries by itself. Check the Java program is still running |
| Customers: the card method reads **"Not available right now"** | The card provider stopped answering (try it: scenario `PROVIDER_DOWN`) | Nothing. PromptPay still works. The sale is **degraded**, not stopped (NFR-05) |
| Customers: **"Payment is not available right now."** on every method | No provider answers (`ALL_PROVIDERS_DOWN`) | Queue and holds still work. Holds expire after 10 minutes and seats go back. Close the sale if it lasts |
| Customers stuck on **"Confirming your payment"** | The bank is slow (`BANK_SLOW`) | Nothing. If the bank answers after the 10 minutes end, the payment is voided and the customer is not charged (FR-17) |
| Waiting number not going down | Admission rate too low | Raise it in SCR-38 |
| People admitted, then **"those seats just went"** | Admission rate too high: people get in only to find every seat held | Lower it in SCR-38. The careful number is seats ÷ 600 s: 7,500 ÷ 600 ≈ 12 per second |
| A price is wrong after 10:00 | Prices are frozen while the sale is on (FR-26) | Cannot change. Add seats or a new tier instead (FR-27) |
| Need fewer VIP seats | Allowed only down to sold + held (FR-28) | The dialog shows the lowest allowed number, e.g. *"VIP: 476 sold and 12 held. Cannot reduce below 488."* |
| The Java program stopped | Crash or restart | **The sale is lost**, because data is in memory (ASM-08). Restart and re-create the event. A database behind `core/` removes this risk (`06-architecture.md`, Data topology) |

---

## 10.6 How we check it works — fitness functions

**Every driving quality has an automatic test, run on every build.** Numbers
below are from the run on 11 October 2026.

| Quality | Target | Test | Result |
|---|---|---|---|
| **NFR-02** No overselling | `sold + held ≤ total`, always | `NoOversellingTest`: 200 buyers race for the last 10 VIP seats, 50 runs | ✅ exactly 10 sold every run. With the lock removed, 42 got 10 seats and the test failed |
| **NFR-02** Paid or expired, never both | — | `PaymentExpiryRaceTest`: 200 holds race payment against expiry | ✅ every hold ends paid **or** expired |
| **NFR-09** Fairness | Admitted in the order they joined | `QueueTest`: 10,000 fans | ✅ same order, zero exceptions |
| **NFR-01** Elasticity | 40,000 join at once, all get a token | `QueueLoadTest` | ✅ all 40,000 got a token |
| **NFR-03** Performance — issue a token | avg ≤ 200 ms, max ≤ 1 s | `QueueLoadTest` | ✅ avg **0.033 ms**, max **3.3 ms** |
| **NFR-03** Performance — refresh position | avg ≤ 1 s, max ≤ 5 s | `QueueLoadTest` | ✅ avg **0.001 ms**, max **0.1 ms** |
| **NFR-08** Two seams only | `core/` never names a plug-in | `ArchitectureTest`, `ArchitectureRuleTest` | ✅ |
| Hold rules | 4 per order, 10 minutes, one token buys once | `HoldRulesTest`, `HoldExpiryTest` | ✅ seats return at exactly 10:00, not 9:59 |
| Web API contract | Every message keeps its shape | `ApiContractTest`, `OrgApiContractTest` | ✅ |
| Organizer rules (UC-05, UC-06) | Frozen prices, no cut below sold + held, close keeps holds | `FakeOrganizerTest`, 32 tests | ✅ |

**What these numbers do not prove.** `QueueLoadTest` runs inside one Java
program, without a network. A real load test sends 40,000 requests over HTTP
from other machines. That test is not built yet.

### Not measured yet

| Quality | Why not | What it needs |
|---|---|---|
| NFR-04 Availability 99.9% | No monitoring exists (`06-architecture.md` §6.8) | Uptime checks during the sale window |
| NFR-06 Security, signed tickets | Ticket signing is Slice 3, not built in `core/` yet | The tamper test in NFR-06 |
| NFR-07 Auditability | The chain token → hold → payment → order → ticket stops at order until tickets exist | The walk-back test in NFR-07 |
| NFR-05 Fault tolerance, in `core/` | Shown with the fake engine's scenarios only | The disable-provider test against `core/` |

---

## 10.7 Switching the pages to the real engine

**The pages talk to the fake engine unless `ENGINE=core` is set. One place decides that.**

- In `WebServer.main`, the lines marked `THE SWITCH` read `ENGINE`.
- `ENGINE=core` starts `RealEngine`: the real `core/` plus the gateway's own
  records for what core does not keep yet (drafts, pictures, event state,
  payments, orders). Both doors sit on it: `CoreBookingGateway` (Kenji's pages)
  and `CoreOrganizerGateway` (Chai's pages).
- Core runs on the demo clock, so speed and +10 min still work. A scenario is
  refused with `NOT_IN_CORE`, because core has nothing to fake.
- `core/` itself is not changed by any of this.

| On `ENGINE=core` | Calls |
|---|---|
| Answered by core | M-03 join · M-04 place in line · M-05 seats left · M-06 hold, with Early Bird · M-08 give seats back early · O-06 add a tier · O-09 admission rate |
| Core, finished by the gateway | M-02 one event · M-07 my hold · M-09, M-10, M-11 pay in 3 steps over core's one-step checkout · O-02 create · O-03 one event · O-05 set tiers · O-08 publish |
| Stand-in made by the gateway | M-01 events list · M-12 orders (the tickets inside are core's signed ones) · M-13 who am I · O-01 my events · O-11 dashboard |
| Refused, `409 NOT_IN_CORE` | O-04 and O-07 after publishing · O-10 close the sale |

- **Slice 3 on core:** card payment (`card` plug-in), the *Early Bird −10%*
  line and total, signed tickets (`TicketService`), and giving seats back early.
- When core adds a missing piece, one gateway method switches from refusing
  to calling core.

---

## 10.8 Limits of operation today

Stated plainly, so they are not mistaken for oversights.

- **One machine, one program.** If it stops, the sale stops.
- **Data in memory.** A restart loses every queue place, hold and order (ASM-08).
- **No monitoring.** Nobody is told automatically when the program stops.
- **Fake engine in the pages.** The pages show the full flow, but on the
  stand-in engine, not on `core/` yet.
- **Simulated payments.** No real money moves (ASM-04). This stays true by design.

---

## Traces to

**Requirements** NFR-01 … NFR-09, FR-17, FR-26 … FR-30 ·
**Assumptions** ASM-04, ASM-08 · **Use cases** UC-01 … UC-07 ·
**Architecture** `06-architecture.md` §6.6, §6.8 · **Screens** `08-screen-requirements.md`
