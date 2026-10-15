# 08 · Screen Requirements

**Part of Deliverable 3 — Design**

Every screen the system has, and exactly what each one must show. It is
written so it can be handed to a designer, or to a design AI, without
anything having to be invented.

---

## 8.1 What this document is

**A screen requirement is the list of everything one screen must show and
do.** It says *what* is on the screen, not how it looks. How it looks lives
in the design system (see 8.3).

**Analogy.** A screen requirement is the list of dishes and ingredients for a
restaurant menu. The design system is the plating style. A cook with only the
plating style serves pretty plates with the wrong food.

**Real example.** SCR-06 Queue must show Kenji's position, **4,182**, as the
largest thing on the screen, and must never show a refresh button. Any design
that misses either of those is wrong, however good it looks.

### Words this document uses

Each one is also in `07-glossary.md`.

| Word | Plain meaning |
|---|---|
| **Screen** | One full page the user sees. It has an ID, `SCR-nn`. |
| **Variant** | The same screen in a different situation. `SCR-08e` is SCR-08 when Kenji asked for more seats than are left. A variant changes some parts; everything not mentioned stays as in the default. |
| **Static content** `[S]` | Fixed words or parts. They are the same for every customer, every event and every moment. Example: the button label **Join the queue**. |
| **Dynamic content** `[D]` | Filled in from stored data. It changes per event, per customer or over time. Example: the event name **THE 1975 BANGKOK**, which is a different act for a different concert. |
| **Dynamic list** `[D-list]` | A dynamic part that repeats once per item, and can hold 0, 1 or many items. Example: the Tickets list, one card per concert Kenji has tickets for. |
| **Live content** `[D-live]` | Dynamic content that updates **by itself while the screen is open**, without the user doing anything. Example: the queue position counting down. |
| **Takeover** | A full-screen flow with no tab bar, so the user cannot wander off. The purchase flow is a takeover. |
| **Sheet** | A panel that slides up from the bottom of a phone screen, over the current screen, and is dismissed by a button. |
| **Dialog** | A small window in the middle of a desktop screen that asks one question. |
| **Banner** | A short message strip across the top of the content, that stays until the situation changes. |
| **Empty state** | What a list screen shows when the list has nothing in it. |
| **Stepper** | A minus button, a number and a plus button, for choosing a small quantity. |

### How to read a dynamic tag

Every dynamic tag names where the value comes from, using the field names in
`05-domain-model.md`. The value written after it is the **example from the
story**, not fixed text.

> `[D] Event.name` — *THE 1975 BANGKOK*
>
> means: show the event's name here. For our story it reads THE 1975 BANGKOK.
> For another concert it reads that concert's name.

A design must work for **every** value a dynamic field can hold, not only the
story value. Section 8.4 gives the ranges to design for.

---

## 8.2 The full list of screens

**29 screens, plus their variants. 18 customer screens and 11 organizer
screens, all on a desktop browser.** Every use case extension in
`04-use-cases.md` lands on at least one of them (see 8.10).

### Customer app — desktop web

| ID | Screen | Variants | Tab bar | Traces to | Prototype file no. |
|---|---|---|---|---|---|
| SCR-01 | Events (home) | a list, b no events | yes | supporting, no use case | 00 |
| SCR-02 | Tickets | a with tickets | yes | UC-04 step 1, FR-22 | 09 |
| SCR-03 | Tickets, empty | — | yes | UC-04 ext 1a | 10 |
| SCR-04 | Account | — | yes | supporting, no use case | 11 |
| SCR-05 | Event | a before sale, b on sale, c already in queue, d sold out, e sale closed, f cancelled | no | UC-01, ext 1a, 1b, 1c, 2a | 01 |
| SCR-06 | Queue | a waiting, b almost your turn, c welcome back, d reconnecting, e joining | no | UC-01, FR-02, FR-04 | 02 |
| SCR-07 | Your turn passed | — | no | UC-01 ext 6a | new |
| SCR-08 | Select tickets | a default, b holding, c at the limit of 4, d tier sold out, e more than remain, f seats just went, g everything sold out | no | UC-02, ext 2a, 3a, 4a | 03 |
| SCR-09 | Seats held | a running, b release sheet | no | UC-02 steps 5–7, ext 7a, FR-13 | 04 |
| SCR-10 | Seats released | — | no | UC-02 ext 7a | new |
| SCR-11 | Hold ran out | — | no | UC-02 ext 6a | new |
| SCR-12 | Checkout | a default, b promotion applied, c one method down, d payment unavailable | no | UC-03 steps 1–3, ext 1a, NFR-05 | part of 05 |
| SCR-13 | Pay by PromptPay | a waiting for scan, b declined | no | UC-03 steps 4–5, ext 6a | 05 |
| SCR-14 | Pay by card (simulated provider) | a waiting, b declined | no | UC-03, FR-15, CON-03 | new |
| SCR-15 | Confirming payment | a normal, b bank is slow | no | UC-03 steps 6–8, ext 3a, 6b | new |
| SCR-16 | Confirmed | — | no | UC-03 step 9, FR-20, INV-07 | 06 |
| SCR-17 | Ticket | a valid, b event cancelled, c past event | no | UC-04 steps 2–4, ext 3a, FR-21 | 07 |
| SCR-18 | Expired while paying | — | no | UC-03 ext 7a, FR-17 | 08 |

### Organizer console — desktop

| ID | Screen | Variants | Traces to |
|---|---|---|---|
| SCR-30 | My events | a list, b no events | UC-05 trigger, UC-06 trigger |
| SCR-31 | Create event · 1 Details | a default, b sale time in the past | UC-05 steps 1–2, ext 1a, FR-23 |
| SCR-32 | Create event · 2 Tiers | a default, b invalid tier | UC-05 steps 3–4, ext 4a, FR-24 |
| SCR-33 | Create event · 3 Review & publish | a ready, b no tiers | UC-05 steps 5–6, ext 5a, FR-25 |
| SCR-34 | Event overview | a draft, b scheduled, c on sale, d closing, e closed, f cancelled | UC-06 steps 1–4 |
| SCR-35 | Edit event details (dialog) | a editable, b closed | UC-06 rule table row 1 |
| SCR-36 | Edit tier (dialog) | a before sale, b on sale, c reduce refused | UC-06 ext 3a, 3b, FR-26, FR-27, FR-28 |
| SCR-37 | Add tier (dialog) | a default, b invalid | UC-06 rule table, FR-24 |
| SCR-38 | Admission rate (dialog) | — | UC-06, FR-30 |
| SCR-39 | Close the sale (dialog) | — | UC-06 ext 4a, FR-29 |
| SCR-40 | Live dashboard | a live, b data is stale, c final | UC-07, ext 1a, 2a, FR-31 – FR-34 |

IDs `SCR-19` to `SCR-29` are left free on purpose, for customer screens added
later. Never renumber an ID; retire it instead.

### Not designed, on purpose

A design AI will often add these by itself. **Do not.**

| Not a screen | Why not |
|---|---|
| Sign up, sign in, forgot password | Accounts already exist (ASM-01). Out of scope in `03-prd.md` §3.5. |
| Seat map, picking a seat number | Tiers are pools of seats (ASM-03). |
| Refunds, cancelling an order | Out of scope, §3.5. |
| Ticket transfer, resale, "send to a friend" | Out of scope, §3.5. |
| Gate scanning, ticket checker | Needs a third actor. Out of scope, UC-04. |
| Email or SMS notification settings | Out of scope, §3.5. |
| Payment method or promotion settings for the organizer | Plug-ins are added in code, not through a screen (`AGENTS.md` §8). |
| A card-number form inside our app | Card details are handled by the provider, never by us (CON-03). |
| Live chat, social sharing, reviews | Not part of the product. |

---

## 8.3 Rules for every screen

### Devices

| Side | Device | Frame size | Why |
|---|---|---|---|
| Customer | Desktop browser | **1440 × 900** | Kenji has the page open on a laptop at 09:59 (`03-prd.md` §3.3). Changed 11 Oct 2026 from a phone frame, ASM-11. |
| Organizer | Desktop browser | **1440 × 900** | Chai is watching a live table of numbers, not tapping. See OQ-01. |

Every screen must also work from **1024 px** wide with no sideways scrolling. There is no phone layout (ASM-11).

### Design system

The look is **Redesign V1, look 1a**, in two clickable desktop designs:
`design/Ticket App Web.dc.html` (customer, SCR-01 to SCR-18) and
`design/Organizer Console.dc.html` (organizer, SCR-30 to SCR-34a/b). The earlier
phone prototype `design/TicketApp.dc.html` is kept for history only. That prototype is the source of truth for
colours, type, radius and glow. This document does not repeat it.

The three rules from it that every screen here depends on:

- **Lime `#C5F25A` means "move forward".** Primary buttons, the queue bar, the
  countdown, the live dot, the selected tier.
- **Coral `#FF8B7B` means "stop".** Low stock, sold out, errors, and the
  expired screens. Nothing else is coloured.
- **Only the queue bar and the live dot glow.**

Where this document still says cyan, read lime. Where it says magenta, read
coral.

### Writing rules for screen text

- Plain words. Never show the user `token`, `reservation`, `idempotency`,
  `timeout`, `transaction`, `authorised`, `voided` or any state name in
  capitals such as `HELD`.
- No urgency tricks. No "Hurry!", no flashing, no colour change as the timer
  gets low. The scarcity is real; the screen does not need to shout.
- Money is always `฿` then digits with commas: **฿13,000**. Never `13000 THB`.
- Times are 24-hour: **10:00**, **19:00**. Countdowns are `mm:ss`: **09:47**.
- Dates are `14 Nov 2026` in short form and `Saturday 14 November` in long form.

### What the user can and cannot leave

- The customer tab bar has 3 tabs: **Events**, **Tickets**, **Account**.
- It is shown only on SCR-01 to SCR-04.
- SCR-05 to SCR-18 are a **takeover**. No tab bar. Each screen offers only
  the ways out written in its spec.
- The organizer console has a top bar on every screen (see SCR-30).

---

## 8.4 Shared parts

These appear on more than one screen. Design them once.

### C-01 · Live indicator `[D-live]`
- A small cyan dot that glows, and a line of text beside it.
- Text: `Updated just now` when the last refresh was under 5 seconds ago.
  `Updated 12 seconds ago` otherwise, counting up.
- After **15 seconds** with no refresh, the dot turns grey and the text reads
  `Reconnecting…`. See SCR-06d.
- Used on: SCR-06, SCR-40.

### C-02 · Countdown block `[D-live]`
- A cyan-bordered block. Label `[S] Time to pay`. Value `[D-live]` `mm:ss`,
  large, monospace, tabular digits so they do not jump.
- **The value is calculated from `Reservation.expiresAt`, which the server
  stamped.** It is display only (locked decision 2). If the phone's clock is
  wrong, the server still decides.
- At `00:00` the app moves to SCR-11 (no payment started) or waits for the
  server's answer (payment in progress, see SCR-15).
- Stays cyan the whole time. No colour change near the end.
- Used on: SCR-09, SCR-12, SCR-13, SCR-14, SCR-15.

### C-03 · Order summary
| Row | Content |
|---|---|
| Tier | `[D] TicketTier.name` — *VIP* |
| Tickets | `[D] Reservation.quantity` — *2* |
| Price each | `[D] Reservation.unitPrice` — *฿6,500* |
| Promotion lines | `[D-list] applied pricing rules`, 0 or more lines. Each: rule name on the left, minus amount on the right — *Early Bird −10%  −฿1,300*. With 0 rules, show no line and no gap. |
| Total | `[D] Reservation.totalAmount` — *฿13,000*, large, white, not cyan. |
- Used on: SCR-09, SCR-12.

### C-04 · Tier row
- Line 1: `[D] TicketTier.name` on the left, `[D] TicketTier.price` right-aligned on the same line.
- Line 2: `[D] remaining` in monospace: `350 left`.
- **Low stock:** when remaining is low (see OQ-04) the line reads `only 16 left` in magenta.
- **Sold out:** remaining is 0. Line 2 reads `[S] Sold out` in magenta. The row is dimmed and cannot be tapped.
- **Selected:** cyan 2 px border, faint cyan wash, price in cyan, small cyan check circle at the right of line 2. Only one row can be selected.
- Used on: SCR-08.

### C-05 · Ticket stub
- Top line: `[S] Ticket` `[D] n` `[S] of` `[D] total` on the left — *Ticket 1 of 2*. `[D] DigitalTicket.ticketId` on the right — *TKT-9F42-01*.
- A dashed tear line with a half-circle notch cut into each side edge.
- Below: a QR code on a light panel, and beside it `[D] TicketTier.name` in cyan, `[D] Event.name`, `[D] Event.venue`, `[D] Event.eventDateTime` — *14 Nov 2026, 19:00* — and `[S] SINGLE USE`.
- Used on: SCR-16.

### C-06 · Banner
- One line of bold text and one line of normal text, full content width, under the screen title.
- Magenta left edge for a problem. Cyan left edge for good news.
- No close button. It disappears when the situation it describes ends.

### C-07 · Button states
Every button has 4 states. Design all 4.

| State | Look |
|---|---|
| Ready | Normal. |
| Pressed | Pressed surface colour. |
| Working | Label replaced by a small spinner and a verb: `Holding…`, `Confirming…`. Cannot be tapped again. |
| Disabled | Half opacity. A hint line under it says why. A disabled button without a reason is not allowed. |

### C-08 · Loading placeholder
- Grey blocks the shape of the content, shown while data loads.
- Never a full-screen spinner. Never blank.
- Used on: any screen while its first data arrives.

### C-09 · Connection lost
- If the phone loses the internet on any customer screen, show a banner:
  **No connection.** `Your place and your seats are kept by the server. We will reconnect by ourselves.`
- Never show a "Retry" button on SCR-06 (it invites the refresh storm). Other
  screens may show **Try again**.

### Ranges every dynamic field must survive

Design with the long or large value, not only the story value.

| Field | Story value | Must also fit |
|---|---|---|
| `Event.name` | THE 1975 BANGKOK | 40 characters, e.g. *THE ROLLING STONES — HACKNEY DIAMONDS TOUR*. Wraps to 2 lines, then cuts with `…`. |
| `Event.venue` | Impact Arena | 30 characters, e.g. *Rajamangala National Stadium*. |
| `TicketTier.name` | VIP | 20 characters, e.g. *Standing Front Zone A*. |
| `TicketTier.price` | ฿6,500 | ฿0 (shown as `Free`) to ฿99,999. |
| Remaining | 16 | 0 to 99,999. |
| Queue position | 4,182 | 1 to 99,999. Width must not jump as digits change. |
| Number of tiers | 3 | 1 to 8. More than 4 rows scroll. |
| Tickets owned | 2 | 0 to 50 cards in SCR-02. |
| Revenue | ฿14,691,600 | up to ฿999,999,999. |

---

## 8.5 The flow between screens

```
CUSTOMER

 SCR-01 Events ──tap event──► SCR-05 Event ──Join──► SCR-06 Queue
    │                            │ (d sold out / e closed / f cancelled: no Join)
    │                            │
    │                            ▼  admitted
    │                        SCR-08 Select ──idle too long──► SCR-07 Turn passed
    │                            │ Hold
    │                            ▼
    │                        SCR-09 Held ──Release──► SCR-10 Released
    │                            │      └──timer 00:00──► SCR-11 Hold ran out
    │                            │ Continue
    │                            ▼
    │                        SCR-12 Checkout
    │                         │           │
    │                     PromptPay      Card
    │                         ▼           ▼
    │                     SCR-13      SCR-14
    │                         └─────┬─────┘
    │                               ▼
    │                        SCR-15 Confirming ──hold expired first──► SCR-18 Expired
    │                               │ (declined: back to SCR-13b / SCR-14b)
    │                               ▼
    │                        SCR-16 Confirmed ──Open ticket──► SCR-17 Ticket
    │
 SCR-02 Tickets ──tap card──► SCR-17 Ticket        SCR-03 = SCR-02 with no tickets
 SCR-04 Account

ORGANIZER

 SCR-30 My events ──Create──► SCR-31 ──► SCR-32 ──► SCR-33 ──Publish──► SCR-34
       └──tap event──────────────────────────────────────────────────► SCR-34 Event overview
                                                                         │
                         dialogs: SCR-35 · SCR-36 · SCR-37 · SCR-38 · SCR-39
                                                                         │
                                                                         └──► SCR-40 Live dashboard
```

---

## 8.6 Customer screens — shell (tab bar visible)

### SCR-01 · Events (home)

**Purpose.** Find an event to buy for. Supporting screen, no use case (OQ-03).

**When it shows.** App opens. Tab **Events**.

**Top to bottom**

1. Title `[S] Events`, large. Under it `[D] city` — *Bangkok*.
2. **Search** `[S]` *Search events or venues*, top right. It filters the hero and
   the grid by event name or venue as you type, shows a result count, and a
   no-results line when nothing matches.
3. **Feature hero** — the first event (soonest sale), one wide stage-lit card.
   (Changed 11 Oct 2026 to match the desktop design: hero + grid, no carousel.) It contains:
   - Status pill `[D] from Event.state and saleOpensAt` — *ON SALE TODAY*. Values: `OPENS 1 OCT`, `ON SALE TODAY`, `ON SALE NOW`, `SOLD OUT`, `SALE CLOSED`.
   - `[D] Event.name` — *THE 1975 BANGKOK*, heavy uppercase, up to 2 lines.
   - `[D] Event.venue · Event.eventDateTime` — *Impact Arena · 14 Nov 2026*.
   - Cyan line `[D]` — *Sale opens 10:00, from ฿1,900*. "From" is the cheapest tier price.
4. Label `[S] ALSO ON SALE`.
5. **Event cards** `[D-list]`, a 3-column grid — every other event visible to customers, ordered
   by `saleOpensAt`, soonest first. Each row: act name left, cheapest price
   right, `venue · date` beneath in mono. Story rows: *Bruno Mars ฿1,200 Thunder
   Dome · 22 Nov*, *Alan Walker ฿2,400 UOB Live · 5 Dec*, *Marshmello ฿1,500
   Moonstar Studio · 18 Dec*. Sold-out rows show `Sold out` in magenta instead
   of a price.
   - **0 rows:** hide the label too.
   - **Many rows:** the page scrolls; the tab bar stays fixed.
5. Tab bar, **Events** active.

**Only events in `SCHEDULED`, `ON_SALE` or `CLOSED` appear.** `DRAFT` never
appears. `CANCELLED` events are hidden from this list.

**Actions.** Tap the feature card or any row → SCR-05 for that event.

**Variant b · no events.** Empty state: dashed circle, `[S] Nothing on sale
right now`, `[S] New events show up here as soon as they are announced.` No
button.

---

### SCR-02 · Tickets

**Purpose.** See every ticket Kenji owns (UC-04 step 1, FR-22).

**When it shows.** Tab **Tickets**, and Kenji owns at least 1 ticket.
Otherwise SCR-03.

**Top to bottom**

1. Title `[S] Tickets`. Under it `[D]` count of upcoming events — *2 upcoming*.
   (Counts events, not tickets.)
2. Label `[S] UPCOMING` — hidden if there are none.
3. **Order cards** `[D-list]` — **one card per confirmed order**, not per
   ticket. Upcoming events first, ordered by event date, soonest first. Each card:
   - Date block on the left: day `[D]` *14* over month `[D]` *NOV*.
   - `[D] Event.name` — *The 1975 Bangkok*. Under it `[D] venue · time` in mono — *Impact Arena · 19:00*.
   - Cyan outlined pill on the right `[D] quantity · tier` — *2 · VIP*.
4. Label `[S] PAST` — hidden if there are none.
5. **Past order cards** `[D-list]` — same card, half opacity, most recent
   first. Pill reads `[S] PAST` in grey. *02 AUG · Bruno Mars · Thunder Dome · 20:00*.
6. Tab bar, **Tickets** active.

**Card for a cancelled event.** Full opacity, but the pill reads `[S] CANCELLED`
in magenta. Stays in the Upcoming group until its old date passes.

**Long lists.** 50 cards must scroll smoothly. No paging buttons.

**Actions.** Tap a card → SCR-17 showing ticket 1 of that order. Swiping
inside SCR-17 moves between the order's tickets.

---

### SCR-03 · Tickets, empty

**Purpose.** Say clearly that there are no tickets yet (UC-04 ext 1a).

1. Title `[S] Tickets`.
2. Centred: a dashed circle about 64 px, `[S] No tickets yet`,
   `[S] Tickets you buy show up here, ready to scan at the gate.`
3. Cyan button sized to its text `[S] Browse events` → SCR-01.
4. Tab bar, **Tickets** active.

All static.

---

### SCR-04 · Account

**Purpose.** Show who is signed in, and let them sign out. Supporting screen, no use case.

1. Title `[S] Account`.
2. A 58 px cyan circle with the first letter `[D]` *K*, beside `[D] name`
   *Kenji* and the masked email `[D]` *k•••••@gmail.com* in mono.
3. A card with one row: `[S] Help`.
4. A card with one row: `[S] Sign out` in magenta.
5. Tab bar, **Account** active.

> The current prototype also has *Payment methods*, *Order history* and
> *Notifications*. None of those has a screen or a requirement, and
> notifications are out of scope. They are removed here. See 8.11.

**Actions.** `Help` opens a plain static page (not designed here). `Sign out`
shows a sheet: `[S] Sign out?` with `[S] Sign out` (magenta) and `[S] Cancel`.

---

## 8.7 Customer screens — purchase takeover (no tab bar)

### SCR-05 · Event

**Purpose.** Show the event and let Kenji join the queue (UC-01 trigger).

**When it shows.** Tap an event on SCR-01.

**Top to bottom (default parts)**

1. Back arrow, top-left → SCR-01. (This is the only screen in the takeover
   with a back arrow.)
2. **Stage-lit header**, full width. `[D] Event.name` huge uppercase,
   `[D] VENUE · DATE` under it — *IMPACT ARENA · 14 NOV 2026*. Uses real event
   artwork at 780 × 560 when one exists, otherwise the typographic poster.
3. **Sale block**, centred — changes per variant (below).
4. **Price rows** `[D-list]` — every tier: name left, price right. *VIP ฿6,500 ·
   Zone A ฿3,800 · Zone B ฿1,900*. 1 to 8 rows. No remaining counts here.
5. Card `[S]` **Everyone waits in the same line.** `Joining early does not help.`
6. Footer: primary button and hint line, per variant.

| Variant | When | Sale block | Footer button | Hint line |
|---|---|---|---|---|
| **a · before sale** (ext 1a) | `SCHEDULED` | `[S] SALE OPENS`, `[D]` **10:00** very large, `[D]` *Thursday 1 October*. Under it `[D-live]` `Opens in 00:14:32`, counting down. | `[S] Join the queue`, **disabled** | `[S] The button turns on at 10:00` |
| **b · on sale** | `ON_SALE`, no token yet | `[S] ON SALE NOW`, live dot | `[S] Join the queue`, ready | `[D] Maximum 4 tickets per person` |
| **c · already in queue** (ext 2a) | `ON_SALE`, Kenji has a waiting token | `[S] YOU ARE IN THE QUEUE`, `[D]` *Position 4,182* | `[S] Back to my place` → SCR-06c | — |
| **c · seats still held** | `ON_SALE`, Kenji left his held seats inside the 10 minutes (browser back, closed tab) | Same as **c**, without a position | `[S] Back to my place` → SCR-06c, which forwards at once to the held seats | Until the hold is paid, expires or is given back; then **a** again |
| **d · sold out** (ext 1b) | every tier at 0 | `[S] SOLD OUT` in magenta. `[S] Every ticket has been sold.` | none | — |
| **e · sale closed** (ext 1c) | `CLOSED`, or closing | `[S] SALE CLOSED` in magenta. `[S] The organizer has closed this sale.` | none | — |
| **f · cancelled** | `CANCELLED` | `[S] EVENT CANCELLED` in magenta. | none | — |

In d, e and f, the price rows stay visible. The bottom shows a quiet outlined
button `[S] Back to events` instead.

**Behaviour.**
- Variant a switches to b **by itself** at 10:00:00 by the server's clock. The user does not refresh.
- Tapping **Join the queue** → the button shows `Joining…` → SCR-06a. If the
  server returns an existing token, go to SCR-06c instead (FR-03).

---

### SCR-06 · Queue

**Purpose.** Show Kenji where they stand, and stop them refreshing (UC-01, FR-02, FR-04).

**This is the screen the project brief names as the UI target.**

**Top to bottom**

1. Slim stage-lit header: `[D] Event.name` short form — *THE 1975* — and `[D] venue · date`.
2. Centre, the hero:
   - `[S] YOUR POSITION` small cyan label.
   - `[D-live] QueueToken.position` — **4,182**, about 104 px, tabular digits. **The largest thing on the screen.**
   - `[D-live]` *4,181 people are ahead of you*. (Position minus 1.)
3. **Progress bar** `[D-live]`, cyan, glowing. Fill = places moved ÷ (places
   moved + position) — *62%*. Under it, left `[D-live]` *Moved 6,823 places*,
   right in cyan `[D-live]` *about 6 min*. The word **about** is static and
   always present. Estimate = position ÷ admission rate.
4. Card `[S]` **Your place is saved.** `Keep this page open. When it is your
   turn you get 10 minutes to choose and pay.`
5. Bottom strip: C-01 live indicator.

**Must not show.** No refresh button. No prices. No countdown. No "leave
queue" button (closing the page already keeps the place).

**Refresh rate.** Position, bar and estimate update at least every 5 seconds
(UC-01 step 5). Numbers change smoothly; they do not flash.

| Variant | When | What changes |
|---|---|---|
| **a · waiting** | default | as above |
| **b · almost your turn** | position ≤ 50 | Position *12*, *11 people are ahead of you*, bar 99% full, estimate reads `[S] less than a minute`. Card becomes `[S]` **Almost your turn.** `Get ready to choose your tickets.` |
| **c · welcome back** (ext 2a, 5a) | Kenji reopened the page, or tapped Join again | Same position as before. A cyan banner on top: `[S]` **Welcome back.** `Your place was kept.` Disappears after 5 seconds. |
| **d · reconnecting** | no update for 15 s | Numbers stay, dimmed. C-01 shows grey dot and `Reconnecting…`. Card becomes `[S]` **Still holding your place.** `The server keeps it even if your connection drops.` No retry button. |
| **e · joining** | the instant after tapping Join, before the token arrives | Position area shows a placeholder block. Text `[S] Getting your place in line…`. Must last under 1 second (NFR-03). |

**Leaving this screen.** When admitted → SCR-08a, **by itself**, with a short
cyan flash of the text `[S] It is your turn`.

---

### SCR-07 · Your turn passed

**Purpose.** Tell Kenji they were let in but chose nothing in time (UC-01 ext 6a).

1. Centred grey ring icon.
2. `[S]` **Your turn has passed**
3. `[S] You were let in, but no seats were chosen in time, so the next person in line went ahead. Nothing was reserved and nothing was charged.`
4. Card `[S]` **You can try again.** `Joining again puts you at the back of the line.`
5. Cyan `[S] Join the queue again` → SCR-06e. Quiet outlined `[S] Back to the event` → SCR-05.

All static. The time limit for choosing is not decided yet (OQ-05), so the
copy gives no number.

---

### SCR-08 · Select tickets

**Purpose.** Choose a tier and a quantity, then hold them (UC-02 steps 1–4).

**Top to bottom**

1. Title `[S] You are in`, about 21 px. Under it `[S] Choose your tickets. Nothing is reserved yet.` A hairline.
2. **Tier rows** `[D-list]`, one C-04 row per tier, 1 to 8. Story values:
   - VIP ฿6,500 `only 16 left` (magenta), selected.
   - Zone A ฿3,800 `350 left`.
   - Zone B ฿1,900 `2,020 left`.
   Remaining counts are `[D]` but **not live**: they are fresh when the screen
   opens and after every refused hold (OQ-06).
3. **Quantity row**: `[S] How many` on the left, stepper on the right — minus,
   `[D]` **2**, plus. Each button about 44 px. Starts at 1.
   Under it `[S] Maximum 4 per person`.
4. Empty space, then a hairline, then the total: `[D]` *2 × VIP* small grey on
   the left, `[D]` **฿13,000** large white on the right. Price × quantity, before promotions.
5. Footer: cyan `[S] Hold these seats`. Hint under it `[S] Holding takes them out of the sale for 10 minutes`.

**Must not show.** No countdown (nothing is held yet). No seat numbers. No
payment options.

**Stepper rules.**
- Minus is disabled at 1.
- Plus is disabled at **4** or at the tier's remaining count, whichever is smaller.
- Changing tier keeps the quantity, unless it is now above the new tier's limit; then it drops to that limit.

| Variant | When | What changes |
|---|---|---|
| **a · default** | | as above |
| **b · holding** | after tapping Hold, waiting for the server | Button shows `Holding…` (C-07 working). Rows and stepper cannot be tapped. Under 2 seconds (NFR-03). |
| **c · at the limit** (ext 3a) | quantity is 4 | Plus disabled. Hint under the stepper turns magenta: `[S] 4 is the most one person can buy.` |
| **d · tier sold out** | one tier has 0 left | That row is C-04 sold out: dimmed, `Sold out`, cannot be tapped. |
| **e · more than remain** (ext 2a) | server refuses: not enough left | Magenta banner `[D]` **Only 1 VIP ticket is left.** `[S] Choose fewer tickets or another tier.` Counts refresh. Quantity drops to what is left. |
| **f · seats just went** (ext 4a) | server refuses the hold in a race | Magenta banner `[S]` **Those seats just went to someone else.** `The numbers below are up to date. Nothing was reserved.` Counts refresh. Selection kept if still possible. |
| **g · everything sold out** | all tiers reach 0 while Kenji was in line | Rows all dimmed. Title becomes `[S] Sold out`, subtitle `[S] Every ticket was sold while you were in line. Nothing was charged.` Footer: quiet outlined `[S] Back to events` only. See OQ-07. |

**Leaving.** Hold succeeds → SCR-09a.

---

### SCR-09 · Seats held

**Purpose.** Prove the seats are Kenji's now, and start the 10 minutes (UC-02 steps 5–7, FR-13).

**This screen and SCR-08 side by side are the whole architecture in one
picture: the seats leave the sale before any money is discussed.**

1. Title `[S] Seats held`. Under it `[S] Yours until the timer runs out.`
2. C-02 countdown block — **10:00** at the start.
3. C-03 order summary — *VIP · 2 · ฿6,500 · Total ฿13,000*.
4. Cyan-edged card: `[D]` **VIP just dropped from 16 to 14.** `[S] Your seats left the sale the moment you held them.` The two numbers are the remaining count before and after this hold.
5. Footer: cyan `[S] Continue to payment` → SCR-12. Under it a quiet outlined `[S] Release these seats` → SCR-09b.

**Variant b · release sheet** (ext 7a). A sheet slides up:
- `[S]` **Release these seats?**
- `[D]` *The 2 VIP seats go back on sale straight away.* `[S] To buy again you join the back of the line.`
- Buttons: `[S] Release seats` (magenta outline) → SCR-10. `[S] Keep my seats` (cyan) closes the sheet.
- The countdown keeps running behind the sheet.

**Timer reaches 00:00 here** → SCR-11.

---

### SCR-10 · Seats released

**Purpose.** Confirm the release, and show the way back (UC-02 ext 7a).

1. Centred grey check icon.
2. `[S]` **Seats released**
3. `[D]` *Your 2 VIP seats are back on sale. Nothing was charged.*
4. Cyan `[S] Join the queue again` → SCR-06e. Quiet outlined `[S] Back to the event` → SCR-05.

---

### SCR-11 · Hold ran out

**Purpose.** Tell Kenji the 10 minutes ended before they started paying (UC-02 ext 6a).

Different from SCR-18: here no payment was ever started.

1. Centred magenta ring with a slash.
2. Largest, magenta: `[S]` **Your 10 minutes ran out**
3. `[D]` *The 2 VIP seats went back on sale. You were not charged.*
4. Card `[S]` **You can try again.** `Joining again puts you at the back of the line.`
5. Cyan `[S] Join the queue again` → SCR-06e. Quiet outlined `[S] Back to the event` → SCR-05.

---

### SCR-12 · Checkout

**Purpose.** Show the final price, and choose how to pay (UC-03 steps 1–3, FR-14, FR-15).

1. Title `[D]` *Pay ฿13,000*. Under it `[D]` *2 × VIP for The 1975 Bangkok, 14 Nov 2026*.
2. C-02 countdown block, still running.
3. C-03 order summary, including promotion lines.
4. Label `[S] HOW DO YOU WANT TO PAY`.
5. **Payment method rows** `[D-list]` — one row per registered payment
   provider. 1 to 6 rows. The core does not know their names; each provider
   supplies its own label and short line. Story rows:
   - Tile `PP`, **PromptPay**, `Scan with your banking app`.
   - Tile `CARD`, **Credit or debit card**, `Visa, Mastercard`.
   Tap a row to select it (cyan border, same as C-04).
6. Footer: cyan `[D] Pay ฿13,000`, disabled until a method is chosen. Hint
   `[S] Choose how to pay`.

| Variant | When | What changes |
|---|---|---|
| **a · default** | no promotion | as above |
| **b · promotion applied** (ext 1a) | a pricing rule applies | Summary shows the line *Early Bird −10%  −฿1,300*. Total **฿11,700**. Title and button read *Pay ฿11,700*. |
| **c · one method down** (NFR-05) | one provider not answering | That row is dimmed, cannot be tapped, line reads `[S] Not available right now` in magenta. |
| **d · payment unavailable** (NFR-05) | every provider down | All rows dimmed. Magenta banner `[S]` **Payment is not available right now.** `Your seats are still held. Stay on this page, we will try again by ourselves.` Button disabled. |

**Leaving.** PromptPay → SCR-13a. Card → SCR-14a.

---

### SCR-13 · Pay by PromptPay

**Purpose.** Show the QR code Kenji scans with their bank app (UC-03 steps 4–5).

1. Title `[D]` *Pay ฿13,000*. Under it `[S] PromptPay`.
2. C-02 countdown block.
3. A large QR code on a light panel, centred. `[D]` generated per payment.
   Under it `[S] This code expires with your hold`.
4. Card `[S]` **Do not close this page.** `Your tickets are confirmed as soon as the bank answers.`
5. Footer: cyan `[S] I have paid` → SCR-15a. Quiet text link `[S] Use another way to pay` → SCR-12.

**Tapping "I have paid" twice** (ext 3a): the button goes to its working state
on the first tap and cannot be tapped again. A second request still only
charges once (FR-18), but the screen never offers the chance.

**Variant b · declined** (ext 6a, FR-19). Back on this screen after the bank
refused:
- Magenta banner `[S]` **The payment did not go through.** `[S] You were not charged. Your seats are still held for` `[D]` *07:12*.
- The QR area is replaced by the payment method rows from SCR-12, so Kenji
  can pick again. Countdown keeps running.

---

### SCR-14 · Pay by card (simulated provider)

**Purpose.** Show how a second payment plug-in plugs in (FR-15, NFR-08).
Depends on OQ-02.

**Card numbers are never typed into our app (CON-03).** This screen stands in
for the provider's own page.

1. A plain light panel at the top reading `[S] Card provider · simulated`. It
   must look like a different company's page, not ours.
2. C-02 countdown block, ours, above the panel.
3. `[D]` *Amount ฿13,000*. `[D]` *Merchant: Event Ticket & Queue*.
4. `[S] This is a simulated provider for the project demo. No real card is used.`
5. Two buttons: `[S] Approve payment` → SCR-15a. `[S] Decline payment` → SCR-14b.

**Variant b · declined.** Same as SCR-13b, with the method rows offered again.

---

### SCR-15 · Confirming payment

**Purpose.** Show that the bank is being asked, and keep Kenji on the page (UC-03 steps 6–8).

1. Centred cyan spinner, slow.
2. `[S]` **Confirming your payment**
3. `[S] Waiting for the bank. This usually takes a few seconds.`
4. C-02 countdown block, still running.
5. Card `[S]` **Do not close this page.**

No buttons. No back.

| Variant | When | What changes |
|---|---|---|
| **a · normal** | answer under 8 seconds | as above |
| **b · bank is slow** (ext 6b) | no answer after 8 seconds | Text becomes `[S] The bank is taking longer than usual. We are still checking. You will not be charged twice.` |

**Leaving.**
- Approved and seats still held → SCR-16.
- Declined → SCR-13b or SCR-14b.
- Approval came after the hold expired → SCR-18.
- Reopening the app during this wait comes back to this screen, not to SCR-12.

---

### SCR-16 · Confirmed

**Purpose.** Tell Kenji they have the tickets, and show one ticket per seat (UC-03 step 9, FR-20, INV-07).

1. Centred cyan circle with a check. A tick, not confetti.
2. `[S]` **You are going**
3. `[D]` *Paid ฿13,000 by PromptPay.* `[D]` *Two tickets, one for each seat.*
4. **Ticket stubs** `[D-list]` — one C-05 stub **per ticket**, equal to the
   quantity bought, 1 to 4. *Ticket 1 of 2 · TKT-9F42-01* and *Ticket 2 of 2 ·
   TKT-9F42-02*. Never one stub saying "×2".
   With 3 or 4 stubs the page scrolls.
5. Footer: cyan `[S] Open ticket 1` → SCR-17. Quiet text link `[S] Go to my tickets` → SCR-02 (tab bar returns).

**Must not show.** No countdown. No seat numbers.

---

### SCR-17 · Ticket

**Purpose.** The thing Kenji shows at the gate (UC-04 steps 2–4, FR-21).

1. Slim stage-lit header: `[D]` *THE 1975* and `[D]` *TICKET 1 OF 2 · VIP*.
2. Large QR code on a light panel, centred, the dominant element. It carries
   the signed, single-use payload `[D] DigitalTicket.payload + signature`.
3. Under it `[D] DigitalTicket.ticketId` in cyan mono — *TKT-9F42-01*.
4. Rows:
   - `[S] Holder` `[D]` *Kenji*
   - `[S] Event` `[D]` *The 1975 Bangkok*
   - `[S] Venue` `[D]` *Impact Arena*
   - `[S] Date` `[D]` *Saturday 14 November, 19:00*
   - `[S] Tier` `[D]` *VIP*
5. Card `[S]` **Single use.** `This code works once. Do not share it.`
6. Page dots `[D]` when the order has more than 1 ticket — swipe left and
   right to move between them.
7. Footer: quiet outlined `[S] Back to my tickets` → SCR-02.

Do not add a "brighten screen" button or an "add to wallet" button.

| Variant | When | What changes |
|---|---|---|
| **a · valid** | `VALID`, event in the future | as above |
| **b · event cancelled** (ext 3a) | `CANCELLED` | QR greyed and crossed. Magenta stamp `[S] CANCELLED` across it. Card becomes `[S]` **This event was cancelled.** `This ticket will not get you in.` |
| **c · past event** | event date has passed | Whole screen dimmed. Small grey label `[S] PAST EVENT`. QR faded to grey, with a red outlined stamp `[S] USED` across it, no fill. Card becomes `[S]` **Used.** `This event has happened. The code no longer gets anyone in.` |

---

### SCR-18 · Expired while paying

**Purpose.** The hold ran out while the bank was answering. Kenji must
understand **in under 2 seconds** that they were not charged (UC-03 ext 7a, FR-17).

**This is the most important screen in the product** (`03-prd.md` §3.9 Q1).

1. Centred magenta ring with a slash.
2. Largest thing on the screen, magenta: `[S]` **You have not been charged**
3. `[D]` *Your 10 minutes ran out before the payment finished, so the two VIP seats went back on sale. No money left your account.*
4. Card `[S]` **You can try again.** `Joining the queue again puts you at the back.`
5. Cyan `[S] Join the queue again` → SCR-06e. Quiet outlined `[S] Back to the event` → SCR-05.

**Must not show.** No technical words: not "timeout", "transaction",
"reservation expired", "authorisation voided". No blame on Kenji or the bank.

---

## 8.8 Organizer screens — desktop

### Shared frame for every organizer screen

- **Top bar**, full width: product name `[S] Ticket & Queue · Organizer` on the left,
  `[S] Events` link, and on the right `[D]` Chai's initial in a circle with `[D]` *Chai*.
- Content area max width 1200 px, centred.
- Numbers in tables are right-aligned, monospace, tabular digits.
- Every refused action shows **the reason and the numbers**, in plain words,
  next to the field that caused it.

---

### SCR-30 · My events

**Purpose.** Chai's list of events, and the way to make a new one.

1. Title `[S] My events`. On the right, cyan button `[S] Create event` → SCR-31.
2. **Event table** `[D-list]`, one row per event Chai owns, newest event date
   first. Columns:

   | Column | Example |
   |---|---|
   | Event | *The 1975 Bangkok* |
   | Venue | *Impact Arena* |
   | Event date | *14 Nov 2026, 19:00* |
   | Sale opens | *1 Oct 2026, 10:00* |
   | State | chip: `DRAFT` grey · `SCHEDULED` white outline · `ON SALE` cyan · `CLOSING` cyan outline · `CLOSED` grey · `CANCELLED` magenta |
   | Sold | *4,970 / 7,500* — hidden for `DRAFT` |

   Many rows: the table scrolls inside the page, header row stays fixed.
3. Row click → SCR-34 for that event.

**Variant b · no events.** Empty state: `[S] No events yet`, `[S] Create your first event to start selling.` Cyan `[S] Create event`.

---

### SCR-31 · Create event · 1 Details

**Purpose.** Enter the event's basic facts (UC-05 steps 1–2, FR-23).

1. Step indicator `[S] 1 Details · 2 Tiers · 3 Review`, step 1 active.
2. Title `[S] Create event`.
3. Form fields, every one required:
   | Label | Input | Story value |
   |---|---|---|
   | `[S] Event name` | text, up to 40 characters | *The 1975 Bangkok* |
   | `[S] Venue` | text, up to 30 characters | *Impact Arena* |
   | `[S] Event date and time` | date + time | *14 Nov 2026, 19:00* |
   | `[S] Sale opens` | date + time, to the second | *1 Oct 2026, 10:00:00* |
   | `[S] Admission rate` | number per second, default **200** | *200* — helper text as in SCR-38 |
4. **Card picture** (right column, above the admission-rate help; added 11 Oct 2026, no design yet). `[S] CARD PICTURE`, a 16:9 drop box `[S] Choose a picture` · *or drop it here · PNG, JPG or WebP · up to 2 MB*. Once chosen it shows the picture with the event name on it, the way the customer card shows it, with `[S] Change` and `[S] Remove`. Optional: without it the customer card shows the event name on a plain panel. A wrong file: `[S] Use a PNG, JPG or WebP picture of 2 MB or less.`
5. Footer: `[S] Cancel` (quiet) → SCR-30. Cyan `[S] Save and add tiers` → SCR-32. The event is saved as `DRAFT` at this point.

**Variant b · sale time in the past** (ext 1a). Under the field in magenta:
`[S] The sale must open in the future.` Button disabled.

Also refused in the same way: an empty required field (`[S] Required`), and a
sale time after the event time (`[S] The sale must open before the event.`).

---

### SCR-32 · Create event · 2 Tiers

**Purpose.** Add the price bands (UC-05 steps 3–4, FR-24).

1. Step indicator, step 2 active.
2. Title `[D]` *Tiers for The 1975 Bangkok*.
3. **Tier table** `[D-list]`, editable rows, 0 to 8:
   | Name | Price (฿) | Seats | |
   |---|---|---|---|
   | VIP | 6,500 | 500 | remove |
   | Zone A | 3,800 | 2,000 | remove |
   | Zone B | 1,900 | 5,000 | remove |
4. Quiet button `[S] + Add tier`. Disabled at 8 tiers.
5. Total line `[D]` *7,500 seats across 3 tiers*.
6. Footer: `[S] Back` → SCR-31. Cyan `[S] Review` → SCR-33.

**With 0 tiers:** the table shows one empty row ready to type in, and the line
`[S] Add at least one tier.`

**Variant b · invalid tier** (ext 4a). The bad cell has a magenta border and
the message names the tier and the field: `[D]` *Zone B · Seats must be more
than 0.* or *VIP · Price cannot be below ฿0.* Review is disabled.

---

### SCR-33 · Create event · 3 Review & publish

**Purpose.** Check everything once, then publish (UC-05 steps 5–6, FR-25).

1. Step indicator, step 3 active.
2. Summary card `[D]`: name, venue, event date, sale-open time, admission rate.
3. Tier table `[D-list]`, read only, with the total.
4. A line `[S] After publishing, customers see this event. Prices are frozen once the sale opens.`
5. Footer: `[S] Back` → SCR-32. Cyan `[S] Publish` → SCR-34b with a cyan banner `[S]` **Published.** `[D]` *The sale opens on 1 Oct 2026 at 10:00.*

**Variant b · no tiers** (ext 5a). Publish disabled. Hint `[S] An event needs at least one tier before it can be published.`

---

### SCR-34 · Event overview

> SCR-34a and b follow `design/Organizer Console.dc.html`. Variants c to f and SCR-35 to SCR-40 have
> no visual design yet; they are built from this text with the same parts (11 Oct 2026).

**Purpose.** See the event and make changes that its state allows (UC-06).

1. Title `[D] Event.name`, state chip beside it.
2. Sub-line `[D]` *Impact Arena · 14 Nov 2026, 19:00 · Sale opens 1 Oct 2026, 10:00*. Quiet link `[S] Edit details` → SCR-35.
3. **Action bar** — buttons depend on state (table below).
4. **Tier table** `[D-list]`:

   | Tier | Price | Total | Held | Sold | Remaining | |
   |---|---|---|---|---|---|---|
   | VIP | ฿6,500 | 500 | 12 | 476 | 12 | Edit |
   | Zone A | ฿3,800 | 2,000 | 40 | 1,610 | 350 | Edit |
   | Zone B | ฿1,900 | 5,000 | 96 | 2,884 | 2,020 | Edit |

   Held, Sold and Remaining always add up to Total. In `DRAFT` and
   `SCHEDULED` they read 0, 0 and the total. Below it, quiet `[S] + Add tier` → SCR-37.
5. **Queue card**: `[S] Admission rate` `[D]` *200 per second*, link `[S] Change` → SCR-38.

**What each state allows** (copied from UC-06; this table is the real content):

| Variant | State | Action bar | Price edit | Seats | Add tier | Rate |
|---|---|---|---|---|---|---|
| **a** | `DRAFT` | `[S] Continue setup` → SCR-32 | yes | add and reduce | yes | yes |
| **b** | `SCHEDULED` | `[S] Live dashboard` (disabled, hint `Opens when the sale starts`) | yes | add and reduce | yes | yes |
| **c** | `ON_SALE` | cyan `[S] Live dashboard` → SCR-40 · magenta outline `[S] Close the sale` → SCR-39 | **locked** | add, reduce not below held + sold | yes | yes |
| **d** | closing | cyan `[S] Live dashboard`. Banner `[D]` **Closing.** *148 seats are still inside someone's 10 minutes. The sale closes when they finish.* | locked | no | no | no |
| **e** | `CLOSED` | `[S] Final figures` → SCR-40c | no | no | no | no |
| **f** | `CANCELLED` | none. Magenta banner `[S]` **This event was cancelled.** | no | no | no | no |

When a column says no, the matching Edit link and buttons are hidden, not
just disabled. In e and f the whole page is read only.

---

### SCR-35 · Edit event details (dialog)

- Title `[S] Edit details`.
- Fields: name, venue, event date and time, prefilled `[D]`.
- Sale-open time is shown, editable only in `DRAFT` and `SCHEDULED`, with the same past-time rule as SCR-31.
- `[S] Cancel` · cyan `[S] Save`.
- **Variant b · closed:** not reachable. The `Edit details` link is hidden.

---

### SCR-36 · Edit tier (dialog)

1. Title `[D]` *Edit VIP*.
2. `[S] Name` `[D]` *VIP*.
3. `[S] Price (฿)` `[D]` *6,500*.
4. `[S] Total seats` `[D]` *500*. Under it `[D]` *476 sold · 12 held · 12 remaining*.
5. `[S] Cancel` · cyan `[S] Save`.

| Variant | When | What changes |
|---|---|---|
| **a · before sale** | `DRAFT`, `SCHEDULED` | All fields editable. |
| **b · on sale** (ext 3a, FR-26) | `ON_SALE` | Price field read only with a lock icon. Under it `[S] Prices are frozen while the sale is on, so no customer sees a price change mid-purchase.` Total seats editable. |
| **c · reduce refused** (ext 3b, FR-28) | new total below held + sold | Magenta message under the field: `[D]` *VIP: 476 sold and 12 held. Cannot reduce below 488.* Save disabled. |

---

### SCR-37 · Add tier (dialog)

- Title `[S] Add tier`.
- Fields: `Name`, `Price (฿)`, `Seats`. All empty.
- Same checks as SCR-32. Message names the field.
- `[S] Cancel` · cyan `[S] Add tier`.
- In `ON_SALE` a line warns `[S] The new tier goes on sale straight away.`

---

### SCR-38 · Admission rate (dialog)

**Purpose.** Set how many people the queue lets in each second (FR-30).

1. Title `[S] Admission rate`.
2. Number input `[D]` *200* with the unit `[S] per second`.
3. Helper, worked from the event's own numbers `[D]`:
   *7,500 seats ÷ 600 seconds of hold ≈ 12 per second. Faster than this and
   people get in only to find every seat already held.*
4. Live preview `[D]`: *At 200 per second, the 23,768 people now waiting are
   through in about 2 minutes.* (Shown only while `ON_SALE`.)
5. `[S] Cancel` · cyan `[S] Save`.
6. Values below 1 are refused: `[S] Must be at least 1.`

---

### SCR-39 · Close the sale (dialog)

**Purpose.** Stop new admissions without hurting anyone mid-purchase (FR-29, ext 4a).

1. Title `[S] Close the sale?`
2. `[S] No one new will be let in from the queue.`
3. `[D]` *148 seats are inside someone's 10 minutes. Those customers keep their time and can still pay.*
4. `[S] This cannot be undone.`
5. `[S] Keep selling` (cyan) · `[S] Close the sale` (magenta outline) → SCR-34d.

---

### SCR-40 · Live dashboard

**Purpose.** See whether the sale is working, right now (UC-07, FR-31 – FR-34).

1. Title `[D]` *The 1975 Bangkok · Live*. On the right, C-01 live indicator.
2. **Queue row**, two big figures:
   - `[S] Waiting` `[D-live]` **31,208**
   - `[S] Admitting` `[D-live]` **12 / second**
3. **Tier table** `[D-list]` with `[D-live]` cells:

   | Tier | Total | Held | Sold | Remaining |
   |---|---|---|---|---|
   | VIP | 500 | 12 | 476 | 12 |
   | Zone A | 2,000 | 40 | 1,610 | 350 |
   | Zone B | 5,000 | 96 | 2,884 | 2,020 |
   | **All** | **7,500** | **148** | **4,970** | **2,382** |

   - Each row also has a thin bar split into 3 parts: sold (white), held
     (cyan), remaining (dark). Remaining at 0 turns the row's last cell magenta `Sold out`.
   - A one-line note under the table: `[S] Held = inside a customer's 10 minutes. Not sold, not available.`
4. **Revenue** `[S] Revenue, confirmed orders only` `[D-live]` **฿14,691,600**.
5. Footer link `[S] Back to event` → SCR-34.

**Refresh.** Every figure updates at least every 5 seconds (FR-34), within 5
seconds max (NFR-03). Changed numbers ease in; no flashing.

| Variant | When | What changes |
|---|---|---|
| **a · live** | `ON_SALE` or closing | as above |
| **b · data is stale** (ext 2a) | last refresh over 15 s ago | Every figure dimmed. Magenta banner `[D]` **These figures are 41 seconds old.** `[S] We are trying to reconnect.` The age counts up. |
| **c · final** (ext 1a) | `CLOSED` | Title ends `· Final`. No live dot. Line `[D]` *Sale closed at 10:31:05.* Held column is all 0. Story final: every tier sold out, revenue **฿20,350,000**. |

---

## 8.9 Checklist before a design is accepted

**Every screen**
- [ ] Every element marked `[D]` works with the long values in 8.4.
- [ ] Every `[D-list]` has a design for 0, 1 and many items.
- [ ] Every `[D-live]` value changes without the user doing anything.
- [ ] Story numbers are exact: 4,182 · ฿6,500 · 16 left · 2 tickets · ฿13,000 · 10:00.
- [ ] Only cyan and magenta carry colour.
- [ ] No feature from "Not designed, on purpose" appears.

**Customer**
- [ ] SCR-06: position is the largest thing; no refresh button; estimate says "about".
- [ ] SCR-08 has no countdown; SCR-09 has one.
- [ ] SCR-09: remaining went 16 → 14.
- [ ] SCR-16: one stub per ticket.
- [ ] SCR-18: "You have not been charged" is first and largest.
- [ ] Tab bar only on SCR-01 to SCR-04.

**Organizer**
- [ ] Held, Sold and Remaining add up to Total on every row.
- [ ] Price is locked in SCR-36b.
- [ ] Every refusal shows its numbers.
- [ ] SCR-40b shows the age of the data.

---

## 8.10 Coverage — every use case path has a screen

| Use case path | Screen |
|---|---|
| UC-01 main | SCR-05b → SCR-06a → SCR-08a |
| UC-01 1a sale not open | SCR-05a |
| UC-01 1b sold out | SCR-05d |
| UC-01 1c sale closed | SCR-05e |
| UC-01 2a already has a token | SCR-05c, SCR-06c |
| UC-01 2b old token expired | SCR-05b (treated as new, no special screen) |
| UC-01 5a closes page, comes back | SCR-06c |
| UC-01 6a admitted, does nothing | SCR-07 |
| UC-02 main | SCR-08a → SCR-08b → SCR-09a |
| UC-02 2a more than remain | SCR-08e |
| UC-02 3a over 4 | SCR-08c |
| UC-02 4a hold fails in a race | SCR-08f |
| UC-02 6a hold expires | SCR-11 |
| UC-02 7a releases the hold | SCR-09b → SCR-10 |
| UC-03 main | SCR-12a → SCR-13a → SCR-15a → SCR-16 |
| UC-03 1a promotion applies | SCR-12b |
| UC-03 3a submits twice | SCR-13 button rule, SCR-15 |
| UC-03 6a declined | SCR-13b, SCR-14b |
| UC-03 6b provider slow | SCR-15b |
| UC-03 7a hold expired mid-payment | SCR-18 |
| NFR-05 payment provider down | SCR-12c, SCR-12d |
| UC-04 main | SCR-02 → SCR-17a |
| UC-04 1a no tickets | SCR-03 |
| UC-04 3a event cancelled | SCR-02 cancelled card, SCR-17b |
| UC-05 main | SCR-30 → SCR-31 → SCR-32 → SCR-33 |
| UC-05 1a sale time in the past | SCR-31b |
| UC-05 4a invalid tier | SCR-32b |
| UC-05 5a no tiers | SCR-33b |
| UC-06 main | SCR-34, SCR-35, SCR-36, SCR-37, SCR-38 |
| UC-06 3a price change on sale | SCR-36b |
| UC-06 3b reduce below held + sold | SCR-36c |
| UC-06 4a close the sale | SCR-39 → SCR-34d → SCR-34e |
| UC-07 main | SCR-40a |
| UC-07 1a sale not open | SCR-40c |
| UC-07 2a stale data | SCR-40b |

---

## 8.11 Mismatches found in the current prototype

The prototype in `design/prototype/app.html` and the brief in
`design/mockups/BRIEF.md` disagree with the requirements in these places.
**This document follows the requirements.** The prototype should be updated.

| # | Where | Prototype says | Should be | Why |
|---|---|---|---|---|
| 1 | Event screen | `Saturday 1 October` | **Thursday 1 October** | 1 Oct 2026 is a Thursday. 14 Nov 2026 is a Saturday. |
| 2 | Held screen | `Release and choose again` → back to Select | **Release these seats** → SCR-10, rejoin the queue | One token allows one reservation (locked decision 3, R-E03-4). After a release the token is spent. |
| 3 | Tickets, past card | pill `USED` | **PAST** | A ticket has only `VALID` and `CANCELLED`. "Used" needs gate scanning, which is out of scope. |
| 4 | Ticket screen | `Doors 18:00` | removed, or add a field to E-01 | No doors time exists in the domain model. |
| 5 | Account | Payment methods, Order history, Notifications | removed | No requirement behind them; notifications are out of scope. |
| 6 | Pay screen | goes straight to PromptPay | a choice of method first (SCR-12) | FR-15: any registered provider. |
| 7 | Event screen, before 10:00 | Join button active | disabled until 10:00 | UC-01 ext 1a: no token before the sale opens. |
| 8 | `07-glossary.md`, `02-requirements.md` NFR-01 | admission rate 200 per second | **200 per second** (was 12 until 11 Oct 2026) | The booking engine's default. The team kept it. SCR-38's helper still shows the careful number, seats ÷ 600 s. |

---

## 8.12 Open questions

Each has a recommendation, so design can continue. Change the default here
if the team decides otherwise.

| # | Question | Default used in this document |
|---|---|---|
| OQ-01 | Is the organizer console a desktop or a phone design? | Desktop 1440 × 900. Chai watches tables of numbers. |
| OQ-02 | Do we show a second payment method in the demo? | Yes, a simulated card provider (SCR-14), to prove the plug-in seam. Without it, drop SCR-14 and keep one row in SCR-12. |
| OQ-03 | Events and Account have no use case. Add UC-08 or call them supporting? | Supporting screens, noted in `03-prd.md` scope. Keeps the use case count at 7. |
| OQ-04 | When is a tier "low stock" (magenta)? | Remaining is 5% of the tier's total or less. VIP 16 of 500 is 3.2%, magenta. Zone A 350 of 2,000 is 17.5%, grey. |
| OQ-05 | How long can an admitted customer sit on SCR-08 before their turn passes? | Not decided (`03-prd.md` §3.9 Q3). SCR-07 copy gives no number until it is. |
| OQ-06 | Do remaining counts on SCR-08 update live? | No. Fresh on open and after each refusal. Live counts would multiply load by every admitted customer. |
| OQ-07 | When every seat sells, do people still waiting in line hear about it before they reach the front? | Not covered by any FR. Default: they find out on SCR-08g. Better: the queue ends early with a sold-out message. Needs a new FR if chosen. |
| OQ-08 | The domain model has a `CANCELLED` event state, but no use case cancels an event. | No cancel button is designed. Cancelled is only shown (SCR-05f, SCR-17b, SCR-34f). |

---

## 8.13 Traceability

| Screen | Requirements | Entities |
|---|---|---|
| SCR-01 | — (supporting) | E-01, E-02 |
| SCR-02, SCR-03 | FR-22 | E-04, E-06 |
| SCR-04 | — (supporting) | — |
| SCR-05 | FR-01, FR-03, FR-06, FR-29 | E-01, E-02, E-03 |
| SCR-06 | FR-02, FR-04, FR-05 | E-03 |
| SCR-07 | UC-01 ext 6a | E-03 |
| SCR-08 | FR-07, FR-08, FR-10, FR-11 | E-02, E-03 |
| SCR-09 | FR-09, FR-12, FR-13 | E-02, E-04 |
| SCR-10 | UC-02 ext 7a | E-04 |
| SCR-11 | FR-12 | E-04 |
| SCR-12 | FR-14, FR-15, NFR-05 | E-04, E-05 |
| SCR-13, SCR-14 | FR-15, FR-18, FR-19, CON-03 | E-05 |
| SCR-15 | FR-16, FR-18 | E-04, E-05 |
| SCR-16 | FR-20 | E-04, E-06 |
| SCR-17 | FR-21, FR-22 | E-06 |
| SCR-18 | FR-17 | E-04, E-05 |
| SCR-30 | FR-23 | E-01 |
| SCR-31 | FR-23 | E-01 |
| SCR-32, SCR-37 | FR-24 | E-02 |
| SCR-33 | FR-25 | E-01, E-02 |
| SCR-34 | FR-26 – FR-30 | E-01, E-02 |
| SCR-35 | UC-06 rule table | E-01 |
| SCR-36 | FR-26, FR-27, FR-28 | E-02 |
| SCR-38 | FR-30 | E-01 |
| SCR-39 | FR-29 | E-01 |
| SCR-40 | FR-31, FR-32, FR-33, FR-34 | E-01 – E-05 |
