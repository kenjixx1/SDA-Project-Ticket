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

### Admission rate
How many customers the waiting room lets into the buying part each second.
Chai sets it. In our story it is **200 per second**.

*Like a doorman letting people into a club at a steady pace, however many are
outside.*

---

### Architecture characteristic
A quality the system must have — fast, reliable, secure — as opposed to
something it must *do*.

Lecture 3.1 puts it exactly: *requirements* say what the application should
do; *architecture characteristics* say how well, and why certain design
choices were made. They are also called the "-ilities" (scalability,
availability, maintainability).

In this project they are written as **NFR-01 … NFR-08**.

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

### Consumer *(pipeline filter type)*
The **end** of a pipeline. Slide 8.1: *"the termination point for the pipeline
flow"* — it may write the result to a database or show it on a screen.

This system has two: `OrderPersistenceFilter` (success) and
`RejectionConsumer` (failure).

---

### Constraint
Something fixed that the design did not get to choose — a rule handed down.
"Must be written in Java" is a constraint. Written as **CON-nn**.

---

### Core system *(microkernel)*
The part that never changes. Slide 8.2 gives two definitions: *the minimal
functionality required to run the system*, and *the happy path — a general
processing flow with little or no custom processing*.

Here it is the **booking engine**: queue admission, holds, inventory, order
lifecycle. It contains no payment method's name and no promotion's name.

---

### Data integrity
The stored data is never wrong — not "rarely wrong", never. In this project it
means one arithmetic statement (INV-01):

```
sold + held  ≤  total
```

This is "no overselling", written as a rule a computer can check.

---

### Digital ticket
What Kenji shows at the gate. Carries a QR code with a **signed**,
**single-use** payload. Entity **E-06**.

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

### Extension *(use case)*
A branch in a use case: what happens when the ordinary story does not happen.
Numbered against the step it branches from — `6a` branches from step 6.

*A recipe tells you how to make the sauce. The extensions tell you what to do
when it splits.*

The two most important extensions in this system are **UC-02 ext. 6a** (the
hold expires) and **UC-03 ext. 7a** (the hold expires while the payment is in
flight).

---

### Filter *(pipeline)*
One processing stage. Slide 8.1: filters are **self-contained**, independent
of each other, generally **stateless**, and each **performs one task only**.

There are four types: **producer**, **transformer**, **tester**, **consumer**.

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

---

### Invariant
A statement that is **always** true — before an operation and after it, no
matter what happens in between. Written as **INV-nn**.

If an invariant is ever false, the system is not merely wrong, it is broken.
INV-01 is this project's most important line.

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

### Pipe
The channel between two filters. Slide 8.1: **unidirectional** and
**point-to-point** — one source in, one destination out. Architects favour
**small** payloads on pipes, for performance.

In this system the only thing on a pipe is one small `OrderContext` record.

---

### Pipeline
An architectural style where data flows one way through a series of filters.
Also called **pipes and filters**. Lecture 8.1.

*A car wash: wash, rinse, dry, polish. Each machine does one job, and no car
goes backwards.*

---

### Plug-in component
A standalone, independent piece that adds specialised behaviour to a core
system. Slide 8.2 stresses that plug-ins should have **no dependencies on each
other**, and that they are where **highly volatile** code belongs.

This system has exactly two kinds: `PaymentProvider` and `PricingRule`.

---

### Postcondition
What is true after a use case finishes. Written for both outcomes — success
*and* failure.

---

### Precondition
What must already be true before a use case can start.

---

### Pricing rule
A plug-in that changes an order's total — early bird, student discount,
fan-club presale. Applied by the `PricingFilter`. The core booking engine
never knows any of their names.

---

### Producer *(pipeline filter type)*
The **start** of a pipeline. Slide 8.1: *"the starting point of a process,
outbound only, sometimes called the source"*.

Here: `QueueIntakeFilter`. When a token is admitted, it creates the
`OrderContext` and sends it down the pipe.

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

### Scalability
Staying responsive as load grows **gradually over time**. Not this project's
driving concern — see **elasticity**.

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

### Structural measure
A check on the **shape** of the code rather than its behaviour — Lecture 5.1's
term. Cyclomatic complexity is the slides' example.

This project's structural measure: the build fails if anything under `core/`
imports anything under `plugin/`. That is how NFR-08 is governed rather than
merely hoped for.

---

### Tester *(pipeline filter type)*
Slide 8.1: *"accepts input, tests one or more criteria, then **optionally**
produces output, based on the test"*.

The word *optionally* is the whole point — a tester is allowed to stop an
order. Here: `AvailabilityHoldFilter`, which passes the order on only if it
actually got the hold.

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

### Transformer *(pipeline filter type)*
Slide 8.1: *"accepts input, optionally performs a transformation on some or
all of the data, then forwards it to the outbound pipe"*.

Here: `PricingFilter`. It changes the money and nothing else.

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

### Virtual waiting room
The queue that sits in front of the buying system. Everyone gets a numbered
place and waits; the buying system only ever serves a few at a time.

*A popular restaurant does not let 300 people crowd the dining room. It gives
out numbered slips and calls people in. The dining room stays calm even when
the crowd outside is not.*

This is the design's answer to **elasticity** (NFR-01).
