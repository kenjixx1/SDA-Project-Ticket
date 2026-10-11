# 07 · Glossary

Every technical word used anywhere in this documentation set, defined in plain
English, in alphabetical order.

**Rule for this project:** a word does not get used before it appears here.

---

### Actor
Someone **outside** the system who uses it to reach a goal. This system has
exactly two: the **Customer** (Kenji) and the **Organizer** (Chai).

A database, a payment provider and a clock are *not* actors — they are parts
of the solution, not people with goals.

---

### Adapter
A small piece of code that lets one part talk to another part whose shape does not fit it directly.

*A travel plug adapter: the hair dryer stays the same, only the plug shape changes.*

**Example:** `BookingGateway` is the presentation layer's adapter. The pages talk to it; behind it sits the fake today and `core/` later.

---

### Admission window
How long an admitted customer has to place a hold before their queue token
expires. **5 minutes** (ASM-07).

*Like a restaurant table held for you when your number is called. Wander off
too long and the table goes to the next person.*

---

### Admission rate
How many customers the waiting room lets into the buying part each second.
Chai sets it. In our story it is **200 per second**.

*Like a doorman letting people into a club at a steady pace, however many are
outside.*

Customers are let in as **batches** (FR-05): each second, the next 200
tokens in line.

---

### API
Short for application programming interface. The list of messages one program
accepts from another, and the answer to each.

*A cafe menu. You can only order what is on it.*

**Example:** the customer page uses 13 messages, M-01 to M-13, listed in `09-web-api.md`.

---

### Architecture characteristic
A quality the system must have — fast, reliable, secure — as opposed to
something it must *do*.

Lecture 3.1 puts it exactly: *requirements* say what the application should
do; *architecture characteristics* say how well, and why certain design
choices were made. They are also called the "-ilities" (scalability,
availability, maintainability).

In this project they are written as **NFR-01 … NFR-09**.

---

### Atomic
A step that **cannot be split in half**. Either it fully happened or it fully
did not. There is no halfway state that another request can catch.

*Like a light switch. There is no moment where the light is half on.*

**Why it matters here:** the hold on Kenji's 2 VIP seats is atomic. That is the
single reason two people cannot both be told "yes" for the same last seat.

---

### Availability
The system is up and answering when people need it. Measured as a percentage
of a time window. Here: **99.9% during the announced sale window** (NFR-04).

---

### Banner
A short message strip across the top of a screen's content. It stays until the situation it describes ends.

*Like a "Wet floor" sign. It stays up until the floor is dry.*

**Example:** SCR-13b shows "The payment did not go through" while Kenji picks another way to pay.

---

### Broker topology *(event-driven)*
A way of wiring an event-driven system with **no central controller**. Each
event processor listens, does its one job, and publishes a new event. The
other way is the **mediator** topology, where one controller directs every
step. This design uses broker.

*A kitchen with an order rail and no head chef shouting: each cook reads the
rail and does their part.*

---

### Composition root
The one place in the code that creates every part and plugs them together.
Here: `EventFlow.java`. It is the only class allowed to name a plug-in, and it
sits outside `core/`, so the core still never knows a plug-in's name (NFR-08).

*The electrician's fuse box: every wire is connected there, and nowhere else.*

---

### Constraint
Something fixed that the design did not get to choose — a rule handed down.
"Must be written in Java" is a constraint. Written as **CON-nn**.

---

### Core system *(microkernel)*
The part that never changes. Slide 8.2 gives two definitions: *the minimal
functionality required to run the system*, and *the happy path — a general
processing flow with little or no custom processing*.

Here it is the **booking engine**: queue management, ticket inventory,
reservations and booking. Its parts talk to each other by **events**. It contains no payment method's name and no promotion's name.

---

### Data integrity
The stored data is never wrong — not "rarely wrong", never. In this project it
means one arithmetic statement (INV-01):

```
sold + held  ≤  total
```

This is "no overselling", written as a rule a computer can check.

---

### Degraded
Part of the system has stopped, but the rest still works.

*Like a café whose card machine is down: you can still order and pay cash.*

**Example:** the card provider stops answering. Kenji can still join the queue,
hold seats and pay by PromptPay (NFR-05, `10-operation.md` §10.5).

---

### Dialog
A small window in the middle of a desktop screen that asks one question and blocks the page behind it until answered.

**Example:** SCR-39 asks Chai "Close the sale?" before anything happens.

---

### Digital ticket
What Kenji shows at the gate. Carries a QR code with a **signed**,
**single-use** payload. Entity **E-06**.

---

### Dynamic content
Parts of a screen that are filled in from stored data, so they change per event, per customer or over time. Marked `[D]` in `08-screen-requirements.md`. A repeating dynamic part is a **dynamic list**, `[D-list]`, and can hold 0, 1 or many items.

*Like the name on a boarding pass. The pass layout is the same for everyone, the name is not.*

**Example:** the Tickets list (SCR-02) shows one card per concert Kenji bought for. Two concerts, two cards. None, and the empty state shows instead.

---

### Elasticity
Staying responsive during a **sudden, huge spike** in users.

Lecture 3.1 separates this from **scalability**, and the difference is this
whole project:

| | Meaning |
|---|---|
| **Scalability** | Staying responsive as load grows *gradually over time* |
| **Elasticity** | Staying responsive during a *significantly high instantaneous spike* |

A ticket sale is 100 users at 09:59:59 and 40,000 at 10:00:00. There is no
gradual growth to plan for. **NFR-01.**

---

### Empty state
What a list screen shows when the list has nothing in it. It says why it is empty and what to do next.

**Example:** SCR-03 says "No tickets yet" with a Browse events button.

---

### Endpoint
One address on the server that answers one kind of message.

*A counter window at the post office: each window does one job.*

**Example:** `POST /api/holds` is the endpoint Kenji's page calls to hold 2 VIP seats.

---

### Envelope
The fixed outer shape every answer from the server has, so the page always
knows where to look.

*A printed form: the boxes are always in the same place, only what is written in them changes.*

**Example:** every answer starts with `serverNow`, the server's clock, then
either `data` or `error` (`09-web-api.md` §9.3).

---

### Event
A short message saying that something has **already happened**. It is news,
not a request.

**Real example:** `TicketHeld` — "2 VIP seats were held for Kenji at
10:03:12, reservation R-1042." It is published only after the hold is saved.

The events in this system: `UserAdmitted`, `TicketHeld`, `PaymentCompleted`,
`OrderConfirmed`, `TicketIssued`, `HoldExpired`, `PaymentVoided`.

---

### Event channel
The path that carries events from the processor that publishes them to every
processor that listens. The publisher does not know who is listening. In code
this is `EventBus`.

*The order rail in a restaurant kitchen.*

---

### Event processor
One part of an event-driven system. It **publishes** events, **listens** for
them, or both, and does one job. Here: `AdmissionScheduler`,
`InventoryService`, `ReservationService`, `TicketService` and others.

---

### Event-driven architecture
An architectural style where the parts of a system work by announcing and
reacting to **events**, instead of calling each other in a fixed line.

*A restaurant kitchen. The waiter pins an order on the rail and walks away;
each cook takes what is theirs.*

Used here for the booking flow, because admission, expiry and payment all
happen at different times. See `06-architecture.md` §6.2.

---

### Extension *(use case)*
A branch in a use case: what happens when the ordinary story does not happen.
Numbered against the step it branches from — `6a` branches from step 6.

*A recipe tells you how to make the sauce. The extensions tell you what to do
when it splits.*

The two most important extensions in this system are **UC-02 ext. 6a** (the
hold expires) and **UC-03 ext. 7a** (the hold expires while the payment is in
flight).

---

### Fairness
The characteristic that admission follows **FIFO** with no way to skip the
line. **NFR-09.** Defended by one token per account (FR-03), same place on
reconnect (FR-04) and admission by issue time (FR-05).

---

### Fake
A stand-in that behaves like the real part, but with simple made-up rules.
It lets one part be built and tested before the real part exists.

*A cardboard cut-out of a person at a photo stand.*

**Example:** `FakeBookingGateway` holds seats and takes payments in memory
while the real `core/` is still being written.

---

### FIFO — first in, first out
The rule of a normal queue: whoever arrived first is served first. Here it
decides admission order. **NFR-09.**

*Like the number machine at a bank counter: 40 is always called before 41.*

**Real example:** Kenji's token was issued at 10:00:00.412. A token issued at
10:00:00.413 is never admitted before Kenji's.

---

### Fitness function
A mechanism that gives an **objective** assessment of whether a design is
actually achieving an architecture characteristic. Lecture 5.1's term.

*A bathroom scale for a design: it gives a number, and the number does not
care how hard you tried.*

**Atomic** fitness functions check one characteristic alone; **holistic** ones
check a combination.

**Real example from this project:** 200 threads race for the last 10 seats. If
exactly 10 sell, every run, data integrity is real. If 11 ever sell, it is
not — regardless of what any document claims.

---

### Functional requirement
Something the system **does**. Written as **FR-nn**.

*"Issue a queue token to a customer who asks to enter a sale that is open."*

---

### Gateway
The single doorway one layer uses to reach another. Swapping what is behind
the doorway does not change the layer in front of it.

*A wall socket. The lamp works the same whichever power station is behind it.*

**Example:** `BookingGateway` is filled by the fake today and by `core/` later.
Only 2 lines in `WebServer` change.

---

### Held quantity
Seats inside somebody's ten minutes. Not sold, and not available.

The third number on the dashboard, and the reason the dashboard can be
trusted: `12 held + 476 sold + 12 remaining = 500 total`.

---

### Hold
Seats taken out of the pool for one customer, for a fixed time, before any
money is discussed.

*Like a waiter writing your table down the moment you walk in. Nobody else can
be seated there while you read the menu. Walk out without ordering and the
table is released.*

In this system a hold lasts **10 minutes** and is stored as a `Reservation`
(E-04) in state `HELD`.

---

### Idempotency key
A label attached to a request, so that sending the same request twice has the
same effect as sending it once.

*Like a cheque number. Presenting the same cheque twice does not move money
twice.*

**Why it matters here:** Kenji double-taps "Pay" on their phone during a
countdown. That is not an edge case — it is what people do. The key means they
are charged once (FR-18).

**Idempotent** is the same idea for event listeners: handling the same event
twice has the same effect as handling it once.

---

### Invariant
A statement that is **always** true — before an operation and after it, no
matter what happens in between. Written as **INV-nn**.

If an invariant is ever false, the system is not merely wrong, it is broken.
INV-01 is this project's most important line.

---

### JSON
A plain text format for sending data: names and values inside curly brackets.

*A labelled form: every value sits next to its name.*

**Example:** `{"tierId": "vip", "quantity": 2}` is the body of Kenji's hold message.

---

### Live content
Dynamic content that updates by itself while the screen is open, without the user doing anything. Marked `[D-live]`.

**Example:** Kenji's queue position, 4,182, counting down on SCR-06 with no refresh button.

---

### Microkernel
An architectural style with two parts: a **core system** that does not change,
and **plug-in components** that add the parts that do. Also called the
**plug-in architecture**. Lecture 8.2.

*A power drill. The motor never changes; you swap the bits.*

Real examples from the slides: the Eclipse IDE, Chrome, Jira, Jenkins.

---

### Non-functional requirement
**How well** the system must do something, as opposed to what it does. Written
as **NFR-nn**. Each one here carries a precise metric and a fitness function.

---

### Overselling
Confirming more tickets than seats that exist. The failure this project exists
to prevent.

It is not a rare accident — it is the *normal* result of many people buying at
once, unless the design specifically stops it. See **race condition**.

---

### Payload
The information carried inside something. In a digital ticket, the payload is
the event, tier, holder and ticket ID encoded in the QR code — everything
except the **signature** that proves it is genuine.

---

### Plug-in component
A standalone, independent piece that adds specialised behaviour to a core
system. Slide 8.2 stresses that plug-ins should have **no dependencies on each
other**, and that they are where **highly volatile** code belongs.

This system has exactly two kinds: `PaymentProvider` and `PricingRule`.

---

### Polling
Asking the server the same question again on a timer, to keep a screen up to date.

*Checking the queue number board at a bank every few seconds.*

**Example:** the queue screen asks for Kenji's position every 2 seconds (SCR-06).

---

### Postcondition
What is true after a use case finishes. Written for both outcomes — success
*and* failure.

---

### Precondition
What must already be true before a use case can start.

---

### Presentation layer
The part of the system the customer sees and touches, plus the code that carries their taps to the server. Here: the pages in `resources/public/` and the `web/` routes.

*The shop counter and the person serving at it. The kitchen behind is a different layer.*

**Example:** when Kenji taps **Hold these seats**, the presentation layer sends M-06 and shows SCR-09 with the answer.

---

### Pricing rule
A plug-in that changes an order's total — early bird, student discount,
fan-club presale. Applied by the `PricingService`. The core booking engine
never knows any of their names.

---

### Queue token
A customer's numbered place in the waiting room. Entity **E-03**.

*The paper slip from the machine at a bank. It carries a number, and it only
means something in that bank, on that day, for that person.*

One token per account per event (FR-03) — which is what stops ten browser tabs
from buying ten places in line.

---

### Race condition
Two pieces of work read the same value before either one writes, so one of the
writes is lost.

**Real example.** Ten VIP seats left. Kenji and Nut both want 2.

| Time | Kenji | Nut | Seats in database |
|---|---|---|---|
| .000 | reads 10 | | 10 |
| .002 | | reads 10 | 10 |
| .100 | writes 8 | | 8 |
| .104 | | writes 8 | 8 |

Four tickets sold. Two seats deducted. Nobody made a mistake — the system
simply let them both read first.

**Atomic** operations are the cure.

---

### Registry *(plug-in registry)*
A map from a **key** to a plug-in, held by the core system. The core looks up
`"promptpay"` and gets an object back.

It is what replaces the long `if / else if` chain that slide 8.2 shows as the
thing to avoid.

---

### Reservation
Seats held for one customer with a deadline. Entity **E-04**, and the most
important entity in the system — it is where overselling is prevented.

---

### Runbook
A short list of "when you see this, do that" steps, written before anything
goes wrong, so nobody has to think under pressure.

*Like the fire-drill card on a hotel door.*

**Example:** "Waiting number not going down → raise the admission rate towards
12 per second" (`10-operation.md` §10.5).

---

### Scalability
Staying responsive as load grows **gradually over time**. Not this project's
driving concern — see **elasticity**.

---

### Screen requirement
The list of everything one screen must show and do. It says *what* is on the screen, not how it looks. Each screen has an ID, `SCR-nn`. All of them are in `08-screen-requirements.md`.

*Like a menu's list of dishes and ingredients. The plating style is a separate thing.*

---

### Sheet
A panel that slides up from the bottom of a phone screen, over the current screen, and is closed by a button.

**Example:** SCR-09b, "Release these seats?", slides up while the countdown keeps running behind it.

---

### Signature *(signed)*
A code added to a digital ticket, calculated from the ticket's own contents,
that only this system could have produced. Change any field and the code stops
matching.

*The watermark on a banknote. Anyone can copy the picture; nobody can copy the
watermark.*

---

### Single-use
Good for one admission only. Whoever arrives second with the same code is
turned away.

*Why forwarding a screenshot to a friend does not create a second ticket.*

---

### Spike
A huge jump in usage, all at once, with no warning ramp. 100 users to 40,000
in one second. The condition **elasticity** is about.

---

### Static content
Parts of a screen that are fixed, the same for every customer, event and moment. Marked `[S]`.

**Example:** the button label "Join the queue".

---

### Stepper
A minus button, a number and a plus button, for picking a small quantity.

**Example:** on SCR-08 Kenji steps the quantity to 2. The plus button stops at 4.

---

### Structural measure
A check on the **shape** of the code rather than its behaviour — Lecture 5.1's
term. Cyclomatic complexity is the slides' example.

This project's structural measure: the build fails if anything under `core/`
imports anything under `plugin/`. That is how NFR-08 is governed rather than
merely hoped for.

---

### Takeover
A full-screen flow with no tab bar, so the user cannot wander off to another part of the app.

**Example:** from the queue to the confirmed screen, the purchase is a takeover. Wandering off during the 10 minutes is how seats are lost.

---

### Tier *(ticket tier)*
One price band within an event, holding a fixed number of seats: VIP ฿6,500,
Zone A ฿3,800, Zone B ฿1,900. Entity **E-02**.

*A cinema sells "front, middle, back", not "chair 14, row F".*

---

### Traceability
Every requirement can be followed to the use case it came from, and every
design decision to the requirement that forced it. Nothing appears from
nowhere.

The traceability tables are at the end of `02-requirements.md`.

---

### TTL — time to live
How long something stays valid before it expires by itself. Kenji's hold has a
TTL of **10 minutes**.

The clock belongs to the **server**, not to their phone. The countdown they see
is display only.

---

### Use case
One complete story of one actor reaching one goal — including every way it can
go wrong. Written as **UC-nn**.

A use case is not a feature name. "Checkout & Pay" is a name; the
specification in `04-use-cases.md` with its six extensions is a use case.

---

### Variant (screen)
The same screen in a different situation. Written as the screen ID plus a letter. Parts not mentioned stay the same.

**Example:** SCR-08e is the Select screen after Kenji asked for more VIP seats than are left.

---

### Virtual waiting room
The queue that sits in front of the buying system. Everyone gets a numbered
place and waits; the buying system only ever serves a few at a time.

*A popular restaurant does not let 300 people crowd the dining room. It gives
out numbered slips and calls people in. The dining room stays calm even when
the crowd outside is not.*

This is the design's answer to **elasticity** (NFR-01).
