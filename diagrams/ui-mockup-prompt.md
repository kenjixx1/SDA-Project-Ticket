# UI Mockup Prompt

Everything needed to generate the UI mockups, written as prompts you can paste
straight into an AI tool (or hand to a designer).

**How to use this file:** paste **Block A** first — it is the shared context.
Then paste **one** screen block (B1–B4). Do not paste all four at once; one
screen per generation produces far better results.

---

## Block A · Shared context — paste this before every screen

```
You are designing high-fidelity UI mockups for a university software
architecture project. Produce a realistic product screen, not a grey
wireframe.

PRODUCT
A ticket-selling platform for high-demand concerts. Because 40,000 people
try to buy 7,500 seats in the same second, everyone goes through a virtual
waiting room. Seats are held for exactly 10 minutes while the customer pays.

THE USER
Kenji. They are standing up, holding a phone with one hand, at 10:00 in the
morning. They are anxious and will refresh the page if they are not told what is
happening. They are Thai; prices are in Thai baht (฿).

DEVICE
Mobile first. 390 x 844 (iPhone-class). Portrait. One column.
Thumb-reachable primary action at the bottom.

THE EVENT (use these exact values, never placeholders)
  Artist / event : The 1975 Bangkok
  Venue          : Impact Arena
  Event date     : 14 November 2026
  Sale opened    : 1 October 2026, 10:00:00
  Tiers          : VIP    ฿6,500 — 16 left
                   Zone A ฿3,800 — 350 left
                   Zone B ฿1,900 — 2,020 left
  Kenji is buying : 2 x VIP = ฿13,000
  Per-order limit: 4 tickets
  Hold time      : 10 minutes
  Payment method : PromptPay

TONE
Calm and honest. This is a high-stress moment and the interface's job is to
reduce panic, not to sell. Plain language. No marketing copy, no countdown
urgency tricks, no "Only 16 left, hurry!". The scarcity is real; the
interface does not need to shout about it.

VISUAL DIRECTION
- Clean, modern, high contrast. Large type.
- One accent colour used sparingly, for the single primary action and the
  most important number on the screen.
- Semantic colour is separate from the accent: amber for the countdown,
  red only for a genuine failure.
- Real content everywhere. No lorem ipsum, no "Item 1 / Item 2".
- Show both light and dark versions if the tool supports it.

DO NOT
- Do not invent features that do not exist: no seat map, no seat numbers,
  no refund button, no ticket transfer, no live chat, no social sharing.
- Do not show a card-number form. Payment is PromptPay (a Thai QR-based
  transfer); the app never stores card details.
- Do not use stock concert photography as a background behind text.
```

---

## Block B1 · The Queue screen  *(the one the brief names as the target)*

```
SCREEN: Customer Queue

This is the screen Kenji stares at for six minutes. Nothing happens on it.
That is the design problem: if it does not visibly move, they refresh, and
every refresh makes the spike worse for everyone.

MUST SHOW
1. Event header: "The 1975 Bangkok", then "Impact Arena · 14 Nov 2026".
2. Their position in the queue: 4,182. This is the LARGEST element on the
   screen. Use tabular figures so the digits do not jump as it counts down.
3. A progress bar showing how far through the queue they are (about 62%).
4. An estimated wait: "about 6 minutes", clearly marked as an estimate.
   Do not present an estimate as a promise.
5. One line of reassurance: their place is saved, they should keep the tab
   open, and they will get 10 minutes to choose and pay when it is their turn.
6. A subtle sign that the page is live — a "last updated 2 seconds ago"
   line, or a quiet pulse. Movement is a feature here, not decoration.

MUST NOT SHOW
- No "Refresh" button. Offering one invites the behaviour that breaks the
  system.
- No ticket prices yet. They cannot buy anything from this screen.
- No countdown timer. The 10 minutes have not started.

STATES TO DRAW (as separate artboards)
a. Far back in the queue — position 4,182, ~6 minutes.
b. Nearly through — position 12, "almost your turn", bar nearly full.
```

---

## Block B2 · Ticket selection and countdown

```
SCREEN: Select tickets

MUST SHOW
1. The same event header.
2. Each tier as a row: name, price, and how many are left.
      VIP     ฿6,500   16 left
      Zone A  ฿3,800   350 left
      Zone B  ฿1,900   2,020 left
   VIP is selected.
3. A quantity stepper, currently 2, with a visible cap of 4 per order.
4. The running total: ฿13,000.
5. A primary action at the bottom: "Hold these seats".

DRAW TWO STATES AS SEPARATE ARTBOARDS

STATE 1 — before the hold
   No countdown anywhere. Nothing is reserved yet. The primary action is
   "Hold these seats".

STATE 2 — after the hold
   A countdown appears and is the second most prominent thing on screen
   after the total: "09:47 left to pay". Amber, not red — this is a normal
   working state, not an error.
   One line under it: "These 2 seats are yours until the timer runs out."
   The primary action becomes "Continue to payment".
   The remaining count for VIP has dropped from 16 to 14, because the hold
   took the seats out of the pool immediately.

The difference between these two states is the single most important idea
in the whole system: seats are taken before money is discussed.
```

---

## Block B3 · Confirmation and the digital ticket

```
SCREEN: Confirmed

MUST SHOW
1. A calm success state. A tick, not confetti.
2. What they bought: 2 x VIP, The 1975 Bangkok, Impact Arena,
   14 November 2026.
3. Total paid: ฿13,000, paid by PromptPay.
4. TWO SEPARATE TICKETS, not one card saying "x2". Two people walk through
   two gates, so there are two tickets. Show them as two cards, each with
   its own QR code and its own ticket ID.
5. Under each QR code, one short line: "Single use. Do not share this
   code."

MUST NOT SHOW
- No seat numbers. Tiers are pools of seats; nobody picks chair A-14.
- No "Add to wallet" unless it is drawn as a plain secondary button.
- No countdown. It is over.
```

---

## Block B4 · The hold expired while paying  *(draw this one)*

```
SCREEN: Hold expired during payment

This is the hardest screen in the product and the reason the design exists.
Kenji's bank took too long. Their 10 minutes ran out while the payment was in
flight. The system voided the payment. They have no seats.

THE ONLY THING THAT MATTERS: they must understand, in under two seconds,
that they have NOT been charged. If they do not believe that, they call their
bank and tell their friends the site stole their money.

MUST SHOW, IN THIS ORDER
1. First, largest, unmissable: "You have not been charged."
2. Second: what happened, in one plain sentence — "Your 10 minutes ran out
   before the payment finished, so the seats went back on sale."
3. Third: what they can do now — "Join the queue again" as the primary
   action, with an honest note that they start at the back.

TONE
Apologetic but not grovelling. Factual. No blame on them, no blame on the
bank, no technical words like "timeout", "transaction", "reservation
expired" or "authorisation voided".

COLOUR
Red is allowed here — this is a genuine failure. But red is for the status
marker only, never for the sentence "You have not been charged", which
should read as calm, solid fact.
```

---

## Acceptance checklist

Before putting a mockup in the report, check every line.

**All screens**
- [ ] Every number matches this file: 4,182 · ฿6,500 · 16 left · 2 tickets · ฿13,000 · 10 minutes
- [ ] No placeholder text anywhere
- [ ] Works at 390px wide with no sideways scrolling
- [ ] No feature shown that is out of scope (seat map, refunds, transfer, chat)

**Queue screen**
- [ ] Position is the largest element
- [ ] The estimate is marked as an estimate
- [ ] No refresh button
- [ ] Some visible sign the page is live

**Selection screen**
- [ ] Two artboards: before the hold and after the hold
- [ ] The countdown appears only in the second one
- [ ] Quantity capped at 4
- [ ] VIP remaining drops 16 → 14 after the hold

**Confirmation**
- [ ] Two separate tickets with two QR codes
- [ ] "Single use" stated
- [ ] No seat numbers

**Expired screen**
- [ ] "You have not been charged" is the first and largest thing
- [ ] No technical vocabulary
- [ ] A clear way back into the queue

---

## Where these come from

| Screen | Traces to |
|---|---|
| Queue | UC-01, FR-02, FR-04 · named in the brief as the UI mockup target |
| Selection + countdown | UC-02, FR-07, FR-11, FR-13 · the hold in `docs/05-domain-model.md` E-04 |
| Confirmation | UC-04, FR-20, FR-21 · INV-07 (one ticket per seat) |
| Expired during payment | UC-03 extension 7a, FR-17 · open question 1 in `docs/03-prd.md` §3.9 |

A first sketch of the Queue screen already exists in
[`../plans/sda-project-docs/plan.html`](../plans/sda-project-docs/plan.html)
and can be used as a starting point.
