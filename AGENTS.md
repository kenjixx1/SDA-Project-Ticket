# Rules for this repository

This file is the **only** place project rules live. Claude Code, Codex and
Command Code (DeepSeek) all read it. If you change a rule, change it here.

---

## 1. What this repository is

A university coursework repository for **KMITL — Software Design and
Architecture (SDA), Group Project 2**, due **12 October 2026**.

The system being designed is **EventFlow**, the **Event Ticket & Queue
Management System**: a
ticket-selling platform that survives a sudden traffic spike (40,000 people
pressing "buy" in the same second) without ever selling the same seat twice.

The course asks for six deliverables. This repository currently covers the
first three.

| # | Deliverable | Where it lives | Status |
|---|---|---|---|
| 1 | Problem Description | `docs/01-problem-description.md` | Written |
| 2 | Requirements Model | `docs/02` – `docs/05` | Written |
| 3 | Design | `docs/06-architecture.md`, `docs/08-screen-requirements.md` | Written |
| 4 | Implementation | `src/` | Slice 1 built — core booking engine + tests |
| 5 | Operation | not yet | Not started |
| 6 | Conclusion & Recommendation | not yet | Not started |

**Code lives in `src/` only.** Slice 1 (the core booking engine) is built and
tested. Slices 2 and 3 are not. Do not put code anywhere else.

---

## 2. The one story — use it everywhere

Every example in every document uses the **same** event and the **same**
people. Do not invent a new example. Switching examples mid-topic makes the
documents harder to follow, and nothing builds up.

| Thing | Value |
|---|---|
| Event | BLACKPINK — Bangkok |
| Venue / date | Impact Arena, 14 November 2026 |
| Sale opens | 10:00:00, 1 October 2026 |
| People at the door | ~40,000 |
| Seats for sale | 7,500 total |
| VIP tier | ฿6,500 · 500 seats |
| Zone A tier | ฿3,800 · 2,000 seats |
| Zone B tier | ฿1,900 · 5,000 seats |
| The customer | **Kenji** — buying 2 × VIP |
| The organizer | **Chai** — runs the event |
| Hold time | **10 minutes** |
| Per-order limit | **4 tickets** |

If a document needs a number, take it from this table.

---

## 3. Locked decisions — do not quietly change these

These five were decided before anything was written. They are expensive to
undo because the domain model and every flow depend on them. If one of them
genuinely has to change, **say so and stop** — do not work around it.

1. **The hold is the unit of truth, not the payment.**
   Inventory goes down when a seat is *held*, not when money arrives. This is
   what makes overselling structurally impossible.

2. **The 10-minute clock belongs to the server.**
   The time-to-live is stamped when the hold is created and judged
   server-side. The countdown the customer sees is display only.

3. **The queue token carries identity through the whole purchase.**
   One token = one checkout attempt. It ties queue, hold and order together.

4. **`PaymentProvider` and `PricingRule` are the only plug-in seams.**
   Every payment method and every promotion goes through one of those two
   interfaces. Anything else means changing the core.

5. **`AGENTS.md` is the single source of rules.**
   `CLAUDE.md` is a one-line pointer to it. Do not add rules to `CLAUDE.md`.

---

## 4. ID conventions

Every numbered thing uses a prefix. Never renumber an existing ID — retire it
instead, so older documents and diagrams keep working.

| Prefix | Means | Example |
|---|---|---|
| `UC-nn` | Use case | `UC-02 Select & Hold Tickets` |
| `FR-nn` | Functional requirement | `FR-07` |
| `NFR-nn` | Non-functional requirement | `NFR-02` |
| `CON-nn` | Constraint | `CON-01` |
| `ASM-nn` | Assumption | `ASM-03` |
| `INV-nn` | Domain invariant (a rule that must always hold) | `INV-01` |
| `E-nn` | Domain entity | `E-04 Reservation` |
| `SCR-nn` | Screen (a letter after it is a variant) | `SCR-08e Select tickets, more than remain` |

---

## 5. Rules for writing the documents

The documents are read by classmates and by a grader. They are also read by
someone whose first language is not English.

- **Define a technical word in plain English before its first use.** Every
  term also gets an entry in `docs/07-glossary.md`.
- **Short sentences. One idea per sentence.**
- **Bullets and tables over paragraphs.** Lead a section with one bold
  sentence, then bullets.
- **Give an everyday analogy AND a real example.** The analogy gives the feel,
  the example gives the truth. Both use the story in section 2.
- **Never use abstract placeholder data.** No "user A buys item X". Use Kenji,
  VIP seats, ฿6,500.
- **Picture first, meaning second, symbols last.**
- Do not write about the lecturer as a person. Say "the brief", "the course",
  "the slides".

---

## 6. Traceability — the rule that keeps the set honest

Nothing may appear from nowhere.

- Every **functional requirement** names the use case it comes from.
- Every **use case** names the requirements it satisfies and the entities it
  touches.
- Every **architecture decision** names the non-functional requirement that
  forced it.
- Anything that is **not** in the brief and not derivable from it is listed in
  `docs/02-requirements.md` under *Assumptions*, with `ASM-nn`.

If you add a requirement, add its traceability row in the same edit.

---

## 7. Source material

Grounding lives outside this repository, in the course folder:

```
~/Desktop/Life/University/Software Design and Architecture/
  Lecture 3/3.1 SDA Arch Characteristics defined.pdf   architecture characteristics
  Lecture 5/5.1 SDA measure arch Characteristics.pdf   how to measure them
  Lecture 8/8.1 SDA pipe filter.pdf                    pipeline style (not used)
  Lecture 8/8.2 SDA microkernel.pdf                    microkernel style
  Lecture 8/8.5 GroupProject2.pdf                      the six deliverables
```

The project brief itself is `SDA Project.pdf` at the repository root.

**The architecture is event-driven + microkernel.** The pipeline style was
dropped on 4 October 2026; do not bring its words (filter, producer, consumer)
back into the documents.

**Use consistent vocabulary.** For microkernel, use the slides' words: *core
system* and *plug-in components*. For event-driven, use *event*, *event
channel*, *event processor*, *publish*, *listen* and *broker topology*, as
defined in `docs/07-glossary.md`. If the course has an event-driven slide,
add it to the list above and switch to its exact words.

---

## 8. Code

Slice 1 of `docs/03-prd.md` §3.6 is built. Run `./run.sh test` before every
commit that touches `src/`; all tests must pass.

- **Language:** Java. The course uses Java in almost every lab.
- **Package root:** `th.ac.kmitl.sda.ticketqueue`
- **Layout:** `src/main/java/...` and `src/test/java/...`
- The package structure follows `docs/06-architecture.md`. Read it first.
- Plug-ins are **compile-based** — a registry map, not runtime loading.
- Payment providers are **simulated**. No real Stripe or PromptPay keys, no
  network calls. They sit behind the real `PaymentProvider` interface so the
  architecture is still demonstrated.
- **No build tool, no libraries.** Plain `javac` through `./run.sh`, Java 17
  language level. Tests use the small runner in `src/test/java`, not JUnit.
- Only the composition root `EventFlow.java` (outside `core/`) may name a
  plug-in class. `ArchitectureTest` fails the build otherwise (NFR-08).

---

## 9. Working habits

- **Never `git add -A`.** Stage named paths only.
- **Before bulk edits, make a timestamped backup folder.**
- Generated files carry a banner saying they are generated. Edit the source
  and rebuild — never patch the output by hand.
- If part of a task is blocked, finish every other part and say plainly what
  was left out and why.
- Members 4, 5 and 6 are working on **Lab 9** patterns (Bridge, Mediator,
  Proxy). That is separate coursework. Nothing in this repository touches it.
