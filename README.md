# Event Ticket & Queue Management System

**KMITL · Software Design and Architecture · Group Project 2**
Due **12 October 2026**

A ticketing platform that survives a sudden traffic spike — 40,000 people
pressing "buy" in the same second for 7,500 seats — without ever selling the
same seat twice.

---

## The idea in four lines

- Nobody buys directly. Everyone gets a **queue token** and waits.
- The buying engine only ever serves a few hundred people per second, so it
  never feels the spike.
- Choosing seats **holds** them immediately, for 10 minutes, before any money
  is discussed.
- Pay inside the 10 minutes and the seats are yours. Miss it and they go back
  on sale automatically.

---

## Read the documents in this order

| # | Document | What it answers |
|---|---|---|
| 1 | [`docs/01-problem-description.md`](docs/01-problem-description.md) | What is the problem, and why does a normal online shop fail at it? |
| 2 | [`docs/02-requirements.md`](docs/02-requirements.md) | What must it do (FR), how well (NFR), and how is that measured? |
| 3 | [`docs/03-prd.md`](docs/03-prd.md) | Who is it for, what counts as success, what do we build first? |
| 4 | [`docs/04-use-cases.md`](docs/04-use-cases.md) | Seven use cases, with every way each one can go wrong. |
| 5 | [`docs/05-domain-model.md`](docs/05-domain-model.md) | The six things the system stores, and the rules that must never break. |
| 6 | [`docs/06-architecture.md`](docs/06-architecture.md) | Pipeline + microkernel, and which requirement forced each decision. |
| — | [`docs/07-glossary.md`](docs/07-glossary.md) | Every technical word, in plain English. Read whenever a word is unfamiliar. |

**In a hurry?** Read `01`, then `04`, then `06`.

---

## Course deliverables

Group Project 2 asks for six. This repository covers the first three.

| # | Deliverable | Where | Status |
|---|---|---|---|
| 1 | Problem Description | `docs/01` | ✅ Written |
| 2 | Requirements Model | `docs/02` – `docs/05` | ✅ Written |
| 3 | Design | `docs/06` | ✅ Written |
| 4 | Implementation | `src/` | ⬜ Not started |
| 5 | Operation | — | ⬜ Not started |
| 6 | Conclusion & Recommendation | — | ⬜ Not started |

Diagrams (use case, SSD, activity) and UI mockups are Member 3's work — see
[`diagrams/README.md`](diagrams/README.md), which contains their full
specification so nothing has to be invented at drawing time.

---

## The two architectural styles

Both are required by the brief, and both come from Lecture 8.

**Pipeline** (slide 8.1) — an order flows one way through four filters:

```
  PRODUCER          TESTER              TRANSFORMER    CONSUMER
  QueueIntake  ──►  AvailabilityHold ──► Pricing  ──►  OrderPersistence
                            │
                            └── hold failed ──► RejectionConsumer
```

**Microkernel** (slide 8.2) — a booking engine that never learns a payment
method's name:

```
       core: booking engine  (queue · holds · inventory · orders)
                      │  registry lookup by key
        ┌─────────────┴─────────────┐
   PaymentProvider            PricingRule
   PromptPay · Stripe    EarlyBird · Student · FanClub
```

The join: the pipeline **is** the core system's happy path.
Full reasoning in [`docs/06-architecture.md`](docs/06-architecture.md).

---

## What this repository is not

Naming this keeps the design honest and the scope finite.

- No gate scanning — the system *issues* a verifiable ticket; checking it at
  the door is another system.
- No refunds or cancellations.
- No seat maps — tiers are pools of seats, not individual chairs.
- No real Stripe or PromptPay integration — simulated behind the real
  interface.

The full list, with reasons, is in [`docs/03-prd.md`](docs/03-prd.md) §3.5.

---

## Repository layout

```
.
├── AGENTS.md           rules for every AI tool — the single source
├── CLAUDE.md           one line, pointing at AGENTS.md
├── README.md           this file
├── docs/               the seven documents
├── diagrams/           use case · SSD · activity (built) + UI mockup prompt
├── design/             screen mockups and the clickable prototype
├── src/                empty Java tree, ready for Deliverable 4
├── plans/              the plan this documentation set was approved from
└── SDA Project.pdf     the original brief
```

---

## Working in this repository with AI tools

All project rules live in **one** file: [`AGENTS.md`](AGENTS.md).

| Tool | Reads |
|---|---|
| Codex | `AGENTS.md` directly |
| Claude Code | `CLAUDE.md`, which is a one-line pointer to `AGENTS.md` |
| Command Code (DeepSeek) | `AGENTS.md` / `CLAUDE.md` |

**Change rules in `AGENTS.md` only.** Do not add rules to `CLAUDE.md` — it
exists to point, not to hold.

---

## Conventions

| Prefix | Means | Defined in |
|---|---|---|
| `UC-nn` | Use case | `docs/04` |
| `FR-nn` | Functional requirement | `docs/02` |
| `NFR-nn` | Non-functional requirement / architecture characteristic | `docs/02` |
| `CON-nn` | Constraint | `docs/02` |
| `ASM-nn` | Assumption | `docs/02` |
| `E-nn` | Domain entity | `docs/05` |
| `INV-nn` | Invariant — a rule that must always hold | `docs/05` |

**One story runs through every document**: BLACKPINK at Impact Arena, Kenji
buying 2 × VIP, Chai running the sale. The numbers are fixed in `AGENTS.md` §2
so no document contradicts another.

---

## Team

Members 1–3 are on this project. Members 4–6 are on Lab 9 patterns (Bridge,
Mediator, Proxy) — separate coursework, nothing in this repository touches it.
