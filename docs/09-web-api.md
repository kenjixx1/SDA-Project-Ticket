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
  "error": { "code": "NOT_ENOUGH", "message": "Not enough seats left in this tier", "remaining": 1 } }
```

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
| M-12 | `GET /api/me/orders` | | list of `{orderId, event, eventState, tierId, tierName, quantity, paidAmount, providerLabel, past, tickets[{ticketId, n, of, payload, state}]}` | SCR-02, 03, 16, 17 |
| M-13 | `GET /api/me` | | `{customerId, name, maskedEmail}` | SCR-04, SCR-17 |

`HoldView` = `{reservationId, token, state, event{eventId, name, shortName,
venue, eventDateTime}, tierId, tierName, quantity, unitPrice,
promotions[{name, amount}], totalAmount, expiresAt, remainingBefore,
remainingAfter, paymentMethods[{key, tile, label, line, available}],
paymentId}`. `state` is `HELD`, `CONFIRMED`, `EXPIRED` or `RELEASED`.

`PaymentStatus` = `{paymentId, reservationId, providerKey, state, answered, orderId}`. Answering a payment that a newer one replaced is refused with **400** `PAYMENT_REPLACED`.

**Story values.** Kenji holds 2 VIP at 10:06:20: `unitPrice` 6500,
`promotions` `[{"name":"Early Bird −10%","amount":1300}]`, `totalAmount`
11700, `remainingBefore` 16, `remainingAfter` 14, `expiresAt` 10:16:20.

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
