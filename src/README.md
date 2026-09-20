# src — Implementation (Deliverable 4)

**Empty on purpose.** Nothing is implemented yet.

The package tree below already matches `docs/06-architecture.md` §6.5. Read
that section before writing the first class — it says what belongs in each
package and, more importantly, what must never import what.

```
th.ac.kmitl.sda.ticketqueue
├── queue/          the waiting room
├── core/           THE CORE SYSTEM — knows no plug-in by name
│   ├── pipeline/   Producer → Tester → Transformer → Consumer
│   ├── inventory/  the ONLY writer of heldQuantity / soldQuantity
│   ├── order/      reservation lifecycle + hold expiry
│   └── registry/   key → plug-in lookup
├── contract/       THE SEAMS: PaymentProvider, PricingRule
├── plugin/         plug-ins. core/ must never import this.
│   ├── payment/    PromptPay, Stripe  (simulated — ASM-04)
│   └── pricing/    EarlyBird, StudentDiscount, FanClubPresale
├── ticket/         signing and issuing digital tickets
└── admin/          organizer: event management + dashboard
```

## The one rule the build must enforce

```
core/  ──MUST NEVER import──►  plugin/
```

This is the NFR-08 fitness function. It is not a guideline — the build should
fail when it is broken. See `docs/06-architecture.md` §6.5.

## Where to start

`docs/03-prd.md` §3.6 orders the work into three slices. Slice 1 is
**prove the seats are safe**: the atomic hold, the 10-minute expiry that
really releases seats, and the concurrency test where 200 threads race for 10
seats and exactly 10 sell, every run.

Build that test first. Everything else in this system is easier to change than
that one guarantee.

## Conventions

- Java (CON-04), package root `th.ac.kmitl.sda.ticketqueue`
- Plug-ins are **compile-based** — a registry map, not runtime loading
- Payment providers are **simulated**. No real API keys, no network calls.
