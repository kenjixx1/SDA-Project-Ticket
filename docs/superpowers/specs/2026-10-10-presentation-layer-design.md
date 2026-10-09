# Presentation layer — design spec

**Date:** 10 October 2026 · **Branch:** `feat/presentation-layer` · **Due:** 12 October 2026

**Goal.** Build the customer app (SCR-01 to SCR-18) as plain HTML, CSS and
JavaScript pages, served by a small Java web layer. A fake backend answers
today. The teammate's real `core/` replaces it later by changing 1 line.

---

## 1. Decisions already made

| # | Decision | Why |
|---|---|---|
| D-1 | Data lives **in memory**. No database. | No time for a real database. Recorded as a new assumption, ASM-07. |
| D-2 | Frontend is **plain HTML, CSS and JavaScript**. No React, no build step. | 1 project, 1 run command. The design file already holds the look. |
| D-3 | The Java web library is **Javalin**. | 1 dependency. A route is about 3 lines. |
| D-4 | No MVVM classes. 1 state object and 1 render function per screen. | MVVM pays off only with a framework that redraws for you. |
| D-5 | The teammate builds `core/` **in parallel**. | Answer from the brainstorm. |
| D-6 | Scope is **the customer app, all 18 screens**. The organizer console (SCR-30 to SCR-40) is a later spec. | Answer from the brainstorm. |
| D-7 | **Approach A.** The web layer talks to 1 Java interface, `BookingGateway`. A fake fills it today. The real one fills it later. | Our work never waits for `core/`, and the fake can act out every hard case on purpose. |

---

## 2. Where the files go

```
src/main/
├── java/th/ac/kmitl/sda/ticketqueue/
│   └── web/                         ★ NEW. The doorway.
│       ├── WebServer.java             starts Javalin, serves public/, picks the gateway (the 1-line switch)
│       ├── BookingGateway.java        the interface. Every method the page needs
│       ├── dto/                       small records that become JSON (EventView, QueueView, HoldView, ...)
│       ├── routes/                    1 class per area. Turn HTTP into gateway calls
│       │   ├── EventRoutes.java
│       │   ├── QueueRoutes.java
│       │   ├── HoldRoutes.java
│       │   ├── PaymentRoutes.java
│       │   └── MeRoutes.java
│       └── fake/                      ★ throw-away when core/ is ready
│           ├── FakeBookingGateway.java   in-memory story data + the rules the screens need
│           ├── FakeClock.java            a clock that can run faster (×1, ×10, ×30, ×100)
│           └── Scenario.java             forces a hard case: RACE_LOST, PROVIDER_DOWN, BANK_SLOW, ...
└── resources/public/                ★ NEW. The screens. Run in the browser.
    ├── index.html                   1 page. Screens are drawn into it.
    ├── css/app.css                  copied from design/TicketApp.dc.html
    ├── assets/                      event artwork, copied from design/assets/
    └── js/
        ├── api.js                   the ONLY file that calls Java (fetch)
        ├── app.js                   state, router, polling loop
        ├── parts.js                 shared parts C-01 to C-09 (live dot, countdown, banner, ...)
        └── screens/                 1 file per screen group
            ├── shell.js               SCR-01 to SCR-04 (tab bar screens)
            ├── event.js               SCR-05
            ├── queue.js               SCR-06, SCR-07
            ├── select.js              SCR-08
            ├── hold.js                SCR-09, SCR-10, SCR-11
            ├── pay.js                 SCR-12 to SCR-15, SCR-18
            └── tickets.js             SCR-16, SCR-17
```

**Dependency rule.** `web/` may import `core/`. `core/` must never import
`web/`, the same way it must never import `plugin/`.

**`BookingGateway` is not a plug-in seam.** Locked decision 4 says
`PaymentProvider` and `PricingRule` are the only plug-in seams. The gateway
is the presentation layer's own adapter, inside `web/`. It has exactly 2
implementations, fake and real. It never holds a business rule.

---

## 3. The message list (the API contract)

This is the "menu" both sides agree on. It also goes into a new document,
`docs/09-web-api.md`, so the teammate can build `CoreBookingGateway` against
it.

Every reply carries `serverNow` (the server's clock, as epoch milliseconds).
The page uses it to draw countdowns, so the phone's clock never decides
anything (locked decision 2).

The customer is picked by a header, `X-Customer: kenji`. There is no sign-in
screen (ASM-01). For the demo, opening `?as=mint` in another tab makes a
second buyer. Recorded as ASM-08.

| # | Message | Used by | Reply (main fields) |
|---|---|---|---|
| M-01 | `GET /api/events` | SCR-01 | list of `{eventId, name, venue, eventDateTime, saleOpensAt, state, fromPrice, artwork}` |
| M-02 | `GET /api/events/{eventId}` | SCR-05 | event fields + `tiers[{tierId, name, price}]` + `myToken` (or null) + `soldOut` |
| M-03 | `POST /api/events/{eventId}/queue` | SCR-05 Join, SCR-07/10/11/18 "join again" | `{token, position, isExisting}`. `isExisting=true` means SCR-06c (FR-03, FR-04) |
| M-04 | `GET /api/queue/{token}` | SCR-06 polling | `{state, position, moved, admitPerSecond}`. `state` is `WAITING`, `ADMITTED` or `EXPIRED` |
| M-05 | `GET /api/events/{eventId}/availability` | SCR-08 open, and after a refusal | `tiers[{tierId, name, price, remaining, total}]` |
| M-06 | `POST /api/holds` body `{token, tierId, quantity}` | SCR-08 Hold | **201** `HoldView`, or **409** `{reason, remaining}` where `reason` is `NOT_ENOUGH`, `RACE_LOST`, `SOLD_OUT` or `OVER_LIMIT` |
| M-07 | `GET /api/holds/{reservationId}` | SCR-09, SCR-12, on reload | `HoldView` = `{reservationId, state, event{eventId, name, eventDateTime}, tier, quantity, unitPrice, promotions[{name, amount}], totalAmount, expiresAt, remainingBefore, remainingAfter, paymentMethods[{key, label, line, available}]}` |
| M-08 | `DELETE /api/holds/{reservationId}` | SCR-09b Release | `{state: "RELEASED"}` |
| M-09 | `POST /api/holds/{reservationId}/payments` body `{providerKey}` | SCR-12 Pay | `{paymentId, providerKey, display}`. `display` is what the provider wants shown: a QR payload, or the simulated card page text |
| M-10 | `POST /api/payments/{paymentId}/answer` body `{approve: true or false}` | SCR-13 "I have paid", SCR-14 Approve or Decline | `{state: "PENDING"}`. Sending it twice is safe (FR-18) |
| M-11 | `GET /api/payments/{paymentId}` | SCR-15 polling | `{state}`: `PENDING`, `CAPTURED`, `DECLINED` or `VOIDED`. `CAPTURED` carries `orderId` |
| M-12 | `GET /api/me/orders` | SCR-02, SCR-03, SCR-16, SCR-17 | `orders[{orderId, event, tier, quantity, paidAmount, providerLabel, tickets[{ticketId, n, of, payload, state}]}]` |
| M-13 | `GET /api/me` | SCR-04 | `{name, maskedEmail}` |

**Refusal versus error.** A refused hold (M-06 **409**) is a normal answer
with a screen of its own (SCR-08e, 08f, 08g). A **5xx** or no answer at all is
an error. It shows C-09 *No connection* and keeps polling.

**Dev-only messages** (exist only while the fake is switched on):

| Message | Does |
|---|---|
| `POST /dev/scenario` body `{scenario}` | Forces the next hard case. Example `RACE_LOST` makes the next hold refuse with SCR-08f. |
| `POST /dev/speed` body `{times}` | Runs the fake clock 1, 10, 30 or 100 times faster, so a 10-minute hold runs out in 6 seconds at ×100. |
| `POST /dev/reset` | Puts the story data back to the start. |

A small dev panel, shown only with `?dev` in the address, calls these.

---

## 4. How the page works

**One state object.** `app.js` keeps everything the current screen needs, for
example `{ screen: "held", hold: {...}, serverOffset: 120 }`. A screen is a
function that takes the state and returns HTML.

**Routes in the address bar.** Each screen has an address after `#`, for
example `#/event/the-1975`, `#/queue/Q-123`, `#/hold/R-1042`. So the back
button and reload work. On reload, the page asks the server where things
stand (M-04, M-07 or M-11) and lands on the right screen. That covers SCR-15
"reopening comes back here".

**Live values by polling.** Polling means asking the server again on a timer.

| Screen | Asks | Every | Requirement |
|---|---|---|---|
| SCR-05a before sale | M-02 | 5 s, plus a local tick for "Opens in" | switch to 05b by itself at 10:00 |
| SCR-06 queue | M-04 | 2 s | ≤ 5 s (UC-01 step 5, NFR-03) |
| SCR-15 confirming | M-11 | 1 s | after 8 s, switch to SCR-15b |
| Countdowns C-02 | nothing, local tick | 1 s | drawn from `expiresAt` and `serverNow` |

When a countdown reaches 00:00, the page asks the server (M-07) and goes
where the server says: SCR-11 or SCR-18. The page never decides "expired" by
itself.

**The token.** The queue token from M-03 is kept in `localStorage` per
customer and event. It is sent with every purchase message (locked
decision 3). Reopening the event finds it and shows SCR-05c.

**The look.** Copied from `design/TicketApp.dc.html`: its CSS, its markup per
screen, and the artwork in `design/assets/`. That file's own logic is a
design-tool component and is **not** reused. The rules in
`docs/08-screen-requirements.md` win over the prototype where they differ.

---

## 5. The fake

`FakeBookingGateway` is a small in-memory booking engine with just enough
rules to drive every screen:

- **Story data** (Q-2): The 1975 Bangkok as the headline event at Impact Arena, 14
  November 2026, tiers VIP ฿6,500 × 500, Zone A ฿3,800 × 2,000, Zone B ฿1,900
  × 5,000. 3 more events for the SCR-01 list. Kenji owns 1 past order (SCR-02
  past card).
- **Queue.** A new token starts at position 4,182 and moves at the admission
  rate. Admitted when position reaches 0.
- **Hold.** Checks per-order limit 4 and remaining count. Stamps `expiresAt`
  = now + 10 minutes on the fake clock. Synchronised, so 2 tabs cannot both
  win the last seat.
- **Payment.** PromptPay and a simulated card provider. `approve=false` means
  declined (SCR-13b, 14b). Approval after `expiresAt` gives `VOIDED`
  (SCR-18).
- **Promotion.** Early Bird −10% while the fake clock is before 10:30 on sale
  day (SCR-12b).
- **Scenarios** force the rest: `RACE_LOST` (08f), `SOLD_OUT_ALL` (08g),
  `PROVIDER_DOWN` (12c), `ALL_PROVIDERS_DOWN` (12d), `BANK_SLOW` (15b),
  `TURN_PASSED` (07), `EVENT_CANCELLED` (05f, 17b).

The fake copies no code from `core/` and `core/` copies none from it. When
`CoreBookingGateway` is ready, the fake stays only for the demo dev panel or
is deleted.

---

## 6. Testing

| What | How | Proves |
|---|---|---|
| Message list | JUnit + Javalin's test tool. 1 test per message (M-01 to M-13) against the fake: status code and JSON fields. | The contract holds. The **same tests** later run against `CoreBookingGateway`. |
| Fake rules | JUnit. Limit 4, not enough left, expiry at 10:00 on the fake clock, double answer charged once. | The fake behaves like the requirements. |
| Screens | Playwright script walks the buy path SCR-01 → SCR-17 and takes a screenshot of each screen and variant (scenarios set via `/dev`). Screenshots compared by eye with the design. | Every screen renders from real replies. |
| Rule check | A test fails the build if any class in `core/` imports `web/`. | The dependency rule in §2. |

---

## 7. Documents this changes

Required by the traceability rule (`AGENTS.md` §6):

- `docs/02-requirements.md` — add **ASM-07** (data in memory, lost on restart)
  and **ASM-08** (customer picked by header, no sign-in, demo only).
- `docs/06-architecture.md` §6.5 — add `web/` and `resources/public/` to the
  layout, with the dependency rule.
- `src/README.md` — same tree update.
- `docs/07-glossary.md` — add **API**, **Endpoint**, **JSON**, **Polling**,
  **Gateway**, **Fake**.
- New `docs/09-web-api.md` — the message list from §3, for the teammate.
- `docs/08-screen-requirements.md` §8.3 — it still points at Neon Arena and
  `design/mockups/BRIEF.md`, which Redesign V1 deleted. Point it at
  `design/TicketApp.dc.html`.

---

## 8. Build order

The deadline is 2 days away, so the buy path goes first. Stopping early
still leaves a working demo.

1. Build setup (Maven, Java 17, Javalin, Jackson, JUnit) and `WebServer` serving an empty page.
2. `BookingGateway`, the records and the fake with story data. Message tests.
3. Page skeleton: `index.html`, CSS, `api.js`, `app.js` router, `parts.js`.
4. Buy path: SCR-05 → SCR-06 → SCR-08 → SCR-09 → SCR-12 → SCR-13 → SCR-15 → SCR-16.
5. Hard cases: SCR-07, 10, 11, 14, 18, and the 08, 12 and 15 variants, with scenarios and the dev panel.
6. Shell: SCR-01 to SCR-04 and SCR-17.
7. Document updates from §7.

---

## 9. Answered questions

Answered by Kenji on 10 October 2026.

| # | Question | Answer |
|---|---|---|
| Q-1 | Build tool | **Maven**, Java 17. The teammate's `core/` uses the same `pom.xml`. |
| Q-2 | Event name | **The new design's events**: The 1975 Bangkok (headline), Bruno Mars, Alan Walker, Marshmello, with their artwork. The 1975 uses the story's venue, date, tiers and prices. `AGENTS.md` §2 and every document were switched to The 1975 Bangkok on 10 October 2026. |
| Q-3 | Time on SCR-08 before SCR-07 | **5 minutes** in the fake. Reached in the demo through the `TURN_PASSED` scenario. |
| Q-4 | Message list agreed | **Yes.** `docs/09-web-api.md` is written in step 2 and sent to the teammate. |
