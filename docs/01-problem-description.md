# 01 · Problem Description

**Deliverable 1 of 6 — Group Project 2**

---

## 1.1 The short version

**Selling 7,500 concert tickets to 40,000 people in one minute breaks a normal
online shop.**

Two things go wrong, and they go wrong at the same time:

- **The shop falls over.** Every one of the 40,000 people is hitting the same
  pages at the same second. The site slows down, then stops answering.
- **The same seat gets sold twice.** When many people buy at once, two of them
  can both be told "yes" for the last VIP seat. Someone arrives at Impact
  Arena with a ticket that is not real.

This project designs a system that survives both.

---

## 1.2 The situation, in one story

Every document in this set uses the same story.

> **BLACKPINK — Bangkok.** Impact Arena, 14 November 2026.
> 7,500 seats. Sale opens at exactly 10:00:00 on 1 October 2026.
> About **40,000 people** are waiting with the page already open.
>
> **Kenji** wants 2 VIP tickets at ฿6,500 each.
> **Chai** is the organizer. He set up the event and watches the sale happen.

At 10:00:00 everyone presses the button in the same second.

---

## 1.3 Why a normal online shop cannot do this

### The analogy

A normal web shop is a **supermarket**. People wander in through the whole
day, pick things off the shelf, and pay. Two customers almost never reach for
the last carton of milk in the same half-second. If it ever happens, the shop
apologises and refunds one of them.

A ticket sale is a **nightclub door on opening night**. Everybody arrives at
once. There is no "come back later". And you cannot apologise your way out of
selling seat A-14 twice — two people will be standing in front of it.

### The real numbers

| | Supermarket | BLACKPINK sale |
|---|---|---|
| People arriving | Spread over 12 hours | 40,000 in 1 second |
| Items | Restocked tomorrow | 7,500, then gone forever |
| Two people, one item | Rare, refundable | Common, and not refundable in any way that helps |

---

## 1.4 The two failures, named properly

Two words are used throughout the rest of the documents. They are defined here
and again in `07-glossary.md`.

### Failure 1 — Collapse under a spike

**Spike** means a huge jump in the number of people using the system, all at
once, with no warning ramp.

Most systems are built to handle *growth*: more users next month than last
month. A ticket sale is not growth. It is 100 users at 09:59:59 and 40,000
users at 10:00:00.

When a system collapses, everybody loses — including the 7,500 people who
would have got a ticket.

### Failure 2 — Overselling

**Overselling** means the system confirms more tickets than seats that exist.

Here is exactly how it happens, with real numbers.

Ten VIP seats are left. Kenji and Nut both want 2.

| Time | Kenji's request | Nut's request | Seats left in the database |
|---|---|---|---|
| 10:04:01.000 | reads "10 left" | | 10 |
| 10:04:01.002 | | reads "10 left" | 10 |
| 10:04:01.100 | writes 10 − 2 = **8** | | 8 |
| 10:04:01.104 | | writes 10 − 2 = **8** | 8 |

Four tickets were sold. Only two seats were taken away. The system now thinks
8 seats remain when really 6 do.

This is called a **race condition**: two pieces of work read the same value
before either one writes, so one of the writes is lost. Nobody did anything
wrong. The system simply let them both read first.

Repeat that 500 times in a minute and hundreds of people hold tickets for
seats that do not exist.

---

## 1.5 The idea behind the solution

Two ideas, one for each failure.

### Idea 1 — A waiting room, so the shop is never crowded

**Analogy.** A popular restaurant does not let 300 people crowd the dining
room. It gives everyone a numbered slip and calls them in a few at a time. The
dining room is calm even though the crowd outside is not.

**In the system.** Nobody buys directly. Everyone first gets a **queue
token** — a numbered slip — and waits. The buying part of the system only ever
serves a small number of people at a time. The 40,000 wait in front of a
screen that tells them their position.

This is where the *elasticity* requirement lives: the waiting room has to
absorb the spike so the buying engine never feels it.

### Idea 2 — Take the seat first, ask for money second

**Analogy.** At a restaurant, the waiter writes your table down the moment you
walk in. Nobody else can be seated there while you read the menu. You pay at
the end. If you walk out without ordering, the table is released.

**In the system.** When Kenji picks 2 VIP seats, those 2 seats are **held**
immediately — before they have typed a single card digit. The count of available
seats goes down at that moment. They then have **10 minutes** to pay.

- If they pay → the hold becomes a confirmed order.
- If the 10 minutes run out → the seats go back on sale automatically.

The hold is taken in a single indivisible step, so the race condition in
section 1.4 cannot happen. Either Kenji gets both seats or they get none — the
system never reads-then-writes in two separate moments.

---

## 1.6 What the system must therefore do

In plain language, before any requirement numbers:

1. Let a customer join a waiting room and see their real position.
2. Let an admitted customer choose a tier and quantity, and hold those seats
   for a fixed 10 minutes.
3. Take payment, and turn a paid hold into a real ticket.
4. Give the customer a ticket they can show at the gate and that cannot be
   forged or reused.
5. Let the organizer create an event and its price tiers.
6. Let the organizer change or close an event afterwards.
7. Let the organizer watch the queue and the sales while they happen.

Each of these becomes one use case in `04-use-cases.md`.

---

## 1.7 What is deliberately left out

Naming what is out of scope is part of the problem description. It stops the
design from being judged against work it never promised.

| Not in this project | Why |
|---|---|
| Scanning tickets at the venue gate | Needs a third actor (gate staff) and venue hardware. The system *issues* a verifiable ticket; verifying it at the door is a separate system. |
| Refunds and cancellations | Real money movement, real policy. A whole project of its own. |
| Seat-by-seat seat maps | Tiers are sold as a pool of seats, not individual chairs. Picking chair A-14 on a map is a different problem. |
| Real payment integration | Stripe and PromptPay are simulated behind a real interface. The architecture is demonstrated without live keys. |
| Resale / transfer between customers | Out of scope for this project. |

---

## 1.8 Why this problem suits this course

The course is about choosing an architecture and defending the choice.

This problem forces two architectural styles taught in Lecture 8, and each one
is forced by a different pressure:

- **Pipeline (slide 8.1)** — an order moves through fixed stages in one
  direction: admitted → checked and held → priced → saved. Each stage does one
  job. This keeps the overselling logic in exactly one place.
- **Microkernel (slide 8.2)** — payment methods and promotion rules change all
  the time and differ per event. They are plug-ins around a core booking
  engine that never learns their names.

The architecture characteristics that drive both choices are set out in
`02-requirements.md` and defended in `06-architecture.md`.

---

## 1.9 Where to read next

| Question | Document |
|---|---|
| What exactly must it do, and how is that measured? | `02-requirements.md` |
| Who are the users, what counts as success? | `03-prd.md` |
| What happens step by step, including when things fail? | `04-use-cases.md` |
| What are the things the system stores? | `05-domain-model.md` |
| How is it built, and why that way? | `06-architecture.md` |
| What does this word mean? | `07-glossary.md` |
