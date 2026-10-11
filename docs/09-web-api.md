# 09 · Web API

**Part of Deliverable 4 — Implementation**

**The web API is the list of messages the customer page can send to the Java
server, and the answer it gets back for each one.** Both the page and the
booking engine are built against this list. Change it here first.

**Analogy.** A cafe menu. The customer can only order what is on it, and the
kitchen only has to cook what is on it.

**Real example.** Kenji taps **Hold these seats** for 2 VIP. The page sends
M-06 and gets back reservation **R-1042**, which expires at **10:16:20**.

---

## 9.1 Words this document uses

| Word | Plain meaning |
|---|---|
| **Endpoint** | One address on the server that answers one kind of message, like `/api/holds`. |
| **JSON** | A plain text format for data: names and values in curly brackets. |
| **Envelope** | The fixed outer shape every answer has, so the page always knows where to look. |
| **Gateway** | The one Java interface, `BookingGateway`, that the endpoints call. Today a fake fills it. Later `core/` fills it. |
| **Epoch milliseconds** | A time written as one number: milliseconds since 1 January 1970, UTC. `1790823980000` is 10:06:20 on 1 October 2026 in Bangkok. |
| **HoldView** | The shape of the answer that describes one hold: tier, quantity, prices, end time and ways to pay. |
| **PaymentStatus** | The shape of the answer that describes one payment attempt and how it ended. |
| **C-09** | The "No connection." banner, a shared part described in `08-screen-requirements.md` §8.4. |

---

## 9.2 Who is asking

The page sends a header `X-Customer: kenji` with every message. There is no
sign-in screen (ASM-01, ASM-08). Opening the page as `?as=mint` makes a second
buyer for the demo.

## 9.3 The envelope

Every answer has the server's clock, `serverNow`, in epoch milliseconds. The
page draws countdowns from it, never from the phone's clock (locked decision 2).

```json
{ "serverNow": 1790823980000, "clockRate": 1, "data": { "...": "..." } }
```

`clockRate` is how fast the server clock runs. The real system always sends 1.
The demo fake can send 0 (paused) up to 100.

A refusal or error:

```json
{ "serverNow": 1790823980000, "clockRate": 1,
  "error": { "code": "NOT_ENOUGH", "message": "Not enough seats left in this tier", "remaining": 1, "field": null } }
```

**`field` names the form field the page must mark**, or `null` when the
refusal is not about one field. The organizer's tier rows name both the row
and the field: `tiers[1].total` is the seats box of the second tier row
(SCR-32b). `remaining` is filled only where it means something, like the
seats left when a hold is refused.

| Status | Means | Page shows |
|---|---|---|
| 200, 201 | Done | The next screen |
| 400 | The request does not fit the current state | Depends on `code` |
| 404 | Not found, or not this customer's | "This page is not available" |
| 409 | A hold was refused. A normal answer with its own screen | SCR-08e, 08f, 08g |
| no answer, 5xx | The server could not be reached | C-09 *No connection*, keep polling |

## 9.4 The messages

| # | Method and path | Body | Answer `data` | Used by |
|---|---|---|---|---|
| M-01 | `GET /api/events` | | list of `{eventId, name, shortName, venue, eventDateTime, saleOpensAt, state, fromPrice, artwork, soldOut}` | SCR-01 |
| M-02 | `GET /api/events/{eventId}` | | event fields + `tiers[{tierId, name, price}]` + `myToken` + `myPosition` | SCR-05 |
| M-03 | `POST /api/events/{eventId}/queue` | | `{token, eventId, position, isExisting}` | SCR-05, "join again" |
| M-04 | `GET /api/queue/{token}` | | `{token, eventId, state, position, moved, admitPerSecond, reservationId}`. `state` is `WAITING`, `ADMITTED`, `SPENT` or `EXPIRED`. When SPENT, reservationId names the hold | SCR-06 every 2 s |
| M-05 | `GET /api/events/{eventId}/availability` | | list of `{tierId, name, price, remaining, total}` | SCR-08 |
| M-06 | `POST /api/holds` | `{token, tierId, quantity}` | **201** `HoldView`, or **409** with `code` `NOT_ENOUGH`, `RACE_LOST`, `SOLD_OUT`, `OVER_LIMIT` and `remaining` | SCR-08 |
| M-07 | `GET /api/holds/{reservationId}` | | `HoldView` | SCR-09 to SCR-14, SCR-18 |
| M-08 | `DELETE /api/holds/{reservationId}` | | `HoldView` with `state` `RELEASED` | SCR-09b |
| M-09 | `POST /api/holds/{reservationId}/payments` | `{providerKey}` | **201** `{paymentId, reservationId, providerKey, providerLabel, amount, display}` | SCR-12, 13b, 14b |
| M-10 | `POST /api/payments/{paymentId}/answer` | `{approve}` | `PaymentStatus`. Sending it twice changes nothing (FR-18) | SCR-13, SCR-14 |
| M-11 | `GET /api/payments/{paymentId}` | | `PaymentStatus` = `{paymentId, reservationId, providerKey, state, answered, orderId}`. `state` is `PENDING`, `CAPTURED`, `DECLINED` or `VOIDED` | SCR-15 every 1 s |
| M-12 | `GET /api/me/orders` | | list of `{orderId, event (same fields as in HoldView), eventState, tierId, tierName, quantity, paidAmount, providerLabel, past, tickets[{ticketId, n, of, payload, state}]}` | SCR-02, 03, 16, 17 |
| M-13 | `GET /api/me` | | `{customerId, name, maskedEmail}` | SCR-04, SCR-17 |

`HoldView` = `{reservationId, token, state, event{eventId, name, shortName,
venue, eventDateTime, artwork}, tierId, tierName, quantity, unitPrice,
promotions[{name, amount}], totalAmount, expiresAt, remainingBefore,
remainingAfter, paymentMethods[{key, tile, label, line, available}],
paymentId}`. `state` is `HELD`, `CONFIRMED`, `EXPIRED` or `RELEASED`.

`PaymentStatus` = `{paymentId, reservationId, providerKey, state, answered, orderId}`. Answering a payment that a newer one replaced is refused with **400** `PAYMENT_REPLACED`.

**Story values.** Kenji holds 2 VIP at 10:06:20: `unitPrice` 6500,
`promotions` `[{"name":"Early Bird −10%","amount":1300}]`, `totalAmount`
11700, `remainingBefore` 16, `remainingAfter` 14, `expiresAt` 10:16:20.

## Organizer messages (O-01 to O-11)

**The organizer console sends these to the same server, in the same envelope,
with the header `X-Customer: chai`.** As for the customer, the header is the
whole of "who is asking"; there is no organizer sign-in (ASM-08). What Chai
sends must match the event's `organizerId` (E-01). An event that belongs to
someone else answers **404**, exactly like an event that does not exist (UC-06).

**Analogy.** The same cafe counter with a second menu behind it. Customers
order from the front menu; the organizer changes what is on it.

**Real example.** Chai lists his events (O-01). Then, at 10:06:20, he reads the
live figures for The 1975 Bangkok (O-11): 31,208 waiting, VIP 484 sold and 16
left.

| # | Method and path | Body | Answer `data` | Status | Use case | Requirements |
|---|---|---|---|---|---|---|
| O-01 | `GET /api/org/events` | — | `OrgEventRow[]` | 200 | UC-05, UC-06 trigger | FR-23 |
| O-02 | `POST /api/org/events` | `EventDetails` | `OrgEvent` | 201 | UC-05 steps 1–2 | FR-23 |
| O-03 | `GET /api/org/events/{eventId}` | — | `OrgEvent` | 200 | UC-06 step 1 | FR-23, FR-24 |
| O-04 | `PATCH /api/org/events/{eventId}` | `EventDetails` | `OrgEvent` | 200 | UC-06 | FR-23, FR-26 |
| O-05 | `PUT /api/org/events/{eventId}/tiers` | `TierList` | `OrgEvent` | 200 | UC-05 steps 3–4 | FR-24 |
| O-06 | `POST /api/org/events/{eventId}/tiers` | `TierInput` | `OrgEvent` | 201 | UC-06 | FR-24, FR-27 |
| O-07 | `PATCH /api/org/events/{eventId}/tiers/{tierId}` | `TierInput` | `OrgEvent` | 200 | UC-06 | FR-26, FR-27, FR-28 |
| O-08 | `POST /api/org/events/{eventId}/publish` | — | `OrgEvent` | 200 | UC-05 steps 5–6 | FR-25 |
| O-09 | `PUT /api/org/events/{eventId}/rate` | `RateInput` | `OrgEvent` | 200 | UC-06 | FR-30 |
| O-10 | `POST /api/org/events/{eventId}/close` | — | `OrgEvent` | 200 | UC-06, ext 4a | FR-29 |
| O-11 | `GET /api/org/events/{eventId}/dashboard` | — | `Dashboard` | 200 | UC-07 | FR-31 – FR-34 |

**The four request shapes.**

- `EventDetails` = `{name, venue, eventDateTime, saleOpensAt, admitPerSecond}`.
- `TierInput` = `{name, price, total}`. `price` is whole baht: `6500` is ฿6,500.
- `TierList` = `{tiers: [TierInput, ...]}`. O-05 replaces every tier at once.
- `RateInput` = `{admitPerSecond}`.

A field left out means "not given": on O-04 and O-07 what is not sent keeps its
current value. Times are epoch milliseconds in Bangkok time, as everywhere else.

**The three reply shapes.**

- `OrgEventRow` = `{eventId, name, venue, eventDateTime, saleOpensAt, state, sold, total}`.
- `OrgEvent` = `{eventId, name, venue, eventDateTime, saleOpensAt, state, admitPerSecond, tiers, heldNow, waiting, closedAt}`.
- `Dashboard` = `{eventId, name, state, waiting, admitPerSecond, tiers, all, revenue, closedAt}`.

`tiers` is a list of `OrgTier` = `{tierId, name, price, total, held, sold,
remaining}`. In every tier `held + sold + remaining == total`, always, and
`held + sold ≤ total` — that is INV-01, the no-overselling rule (FR-28 protects
it). `held` is not sold and not available: it is inside somebody's 10 minutes.
`waiting` is the number of people in the queue right now, `heldNow` is the sum
of `held`. In `Dashboard`, `all` is the sum row — `tierId` `"all"`, `name`
`"All"`, `price` 0 — and `revenue` counts confirmed orders only, in baht.
`closedAt` is `null` until the event is `CLOSED`.

**The six states.** `DRAFT → SCHEDULED → ON_SALE → CLOSING → CLOSED`, plus
`CANCELLED` in place of any of them.

| State | Plain meaning | What a customer sees |
|---|---|---|
| `DRAFT` | Chai is still building it. | Nothing. A draft answers **404**, like a missing event |
| `SCHEDULED` | Published; the sale time has not come. | The event, priced, not yet on sale |
| `ON_SALE` | The sale is open. | Selling, or sold out |
| `CLOSING` | No new buyers; the holds already made are finishing. | Read as closed (SCR-05e) |
| `CLOSED` | No seat is held any more. `closedAt` is stamped. | SALE CLOSED |
| `CANCELLED` | The event was called off. | Hidden from the list; its tickets read CANCELLED |

**Every refusal names the field that caused it.** `i` in a field name is the
row, counted from 0; the second tier row is `tiers[1]`.

| Status | Code | Field | Message | When |
|---|---|---|---|---|
| 400 | `REQUIRED` | `name`, `venue`, `eventDateTime` or `saleOpensAt` | `Required` | The field is missing or empty (O-02, O-04) |
| 400 | `TOO_LONG` | `name` | `Up to 40 characters` | Longer than 40 (O-02, O-04) |
| 400 | `TOO_LONG` | `venue` | `Up to 30 characters` | Longer than 30 (O-02, O-04) |
| 400 | `SALE_IN_PAST` | `saleOpensAt` | `The sale must open in the future.` | The sale time is not in the future (O-02, O-04) |
| 400 | `SALE_AFTER_EVENT` | `saleOpensAt` | `The sale must open before the event.` | The sale time is after the event (O-02, O-04) |
| 400 | `RATE_TOO_LOW` | `admitPerSecond` | `Must be at least 1.` | The rate is missing or below 1 (O-02, O-09) |
| 400 | `TOO_MANY_TIERS` | `tiers` | `Up to 8 tiers.` | A ninth tier (O-05, O-06) |
| 400 | `TIER_NAME_REQUIRED` | `tiers[i].name`, or `name` on O-06 and O-07 | `Tier 2 · Name is required.` | The row has no name. A nameless row is named by its number |
| 400 | `PRICE_NEGATIVE` | `tiers[i].price`, or `price` | `VIP · Price cannot be below ฿0.` | The price is missing or below 0 |
| 400 | `SEATS_ZERO` | `tiers[i].total`, or `total` | `Zone B · Seats must be more than 0.` | The seats are missing or 0 |
| 404 | `NOT_FOUND` | — | `Event not found` | Another organizer's event, or a missing one |
| 404 | `NOT_FOUND` | — | `Tier not found` | No such tier in the event |
| 409 | `NOT_DRAFT` | — | `Only a draft can do this.` | O-05 or O-08 after the event was published |
| 409 | `NO_TIERS` | — | `An event needs at least one tier before it can be published.` | O-08 with no tier defined (FR-25) |
| 409 | `EVENT_LOCKED` | — | `This event can no longer be changed.` | O-04, O-06, O-07 or O-09 on a closing or closed event |
| 409 | `EVENT_LOCKED` | `saleOpensAt` | `The sale time cannot change once the sale is on.` | O-04 changes the sale time while `ON_SALE` |
| 409 | `PRICE_FROZEN` | `price` | `Prices are frozen while the sale is on, so no customer sees a price change mid-purchase.` | O-07 changes a price while `ON_SALE` (FR-26, ext 3a) |
| 409 | `BELOW_HELD_SOLD` | `total` | `VIP: 484 sold and 2 held. Cannot reduce below 486.` `remaining` is 486 | O-07 lowers a tier below held + sold (FR-28, ext 3b) |
| 409 | `NOT_ON_SALE` | — | `Only a sale that is on can be closed.` | O-10 before the sale opens, or twice (FR-29) |

## 9.5 Demo-only messages

These exist only while the fake is switched on (`WebServer.main`).

| Path | Body | Does |
|---|---|---|
| `POST /dev/scenario` | `{scenario}` | Forces a hard case: `RACE_LOST`, `SOLD_OUT_ALL`, `PROVIDER_DOWN`, `ALL_PROVIDERS_DOWN`, `BANK_SLOW`, `TURN_PASSED`, `EVENT_CANCELLED`, `SALE_CLOSED` |
| `POST /dev/speed` | `{times}` | Runs the fake clock 0 (paused), 1, 10, 30 or 100 times faster |
| `POST /dev/advance` | `{seconds}` | Jumps the fake clock forward |
| `POST /dev/reset` | | Back to 09:59:30 on 1 October 2026, story data restored |

## 9.6 For the teammate building `core/`

Write `web/CoreBookingGateway.java` that implements `BookingGateway` by
calling the `core/` services. Then change the 2 lines marked `THE SWITCH` in
`WebServer.main`. `ApiContractTest` holds the checks the real gateway must also
pass.
