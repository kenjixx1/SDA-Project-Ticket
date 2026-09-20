# Prototype

Real HTML screens. Open a file in a browser, or on your phone.

| File | What it is |
|---|---|
| **`themes.html`** | **Six visual directions, side by side. Pick one.** Start here. |
| `app.html` | The whole customer app, 8 screens, UC-01 to UC-04, in the current direction. |
| `01-queue.html` | Just the queue screen, standalone, in the first direction. |

Visual language: `minimalist-ui`. Build: `design-taste-frontend`.
Screen content comes from `../../diagrams/ui-mockup-prompt.md`.

## app.html - twelve screens

Three tabs with the buying flow running as a full-screen takeover on top.

**Shell (tab bar visible)**

| # | Screen | Traces to |
|---|---|---|
| 1 | Events, browse what is on sale | no use case, see below |
| 9 | Tickets, the list of what you own | **UC-04 step 1 · FR-22** |
| 10 | Tickets, empty state | **UC-04 extension 1a** |
| 11 | Account | no use case, see below |

**Purchase takeover (tab bar hidden on purpose)**

| # | Screen | Traces to |
|---|---|---|
| 2 | Event, before the sale opens | UC-01 |
| 3 | Queue, position and progress | UC-01 · FR-02, FR-04 |
| 4 | Select tier and quantity | UC-02 · FR-07, FR-11 |
| 5 | Seats held, countdown running | UC-02 · FR-09, FR-13 |
| 6 | Pay by PromptPay | UC-03 · FR-14, FR-15 |
| 7 | Confirmed, two tickets | UC-03 · FR-20 · INV-07 |
| 8 | One ticket, full screen | UC-04 · FR-21 |
| 12 | Hold expired while paying | UC-03 ext 7a · FR-17 |

### The tab bar

Icons are real **Phosphor** glyphs (stage microphone, ticket, user), inlined from
`@phosphor-icons/core`. Nothing is hand-drawn. Each tab carries both the
outline and the filled weight; the active tab swaps to filled and turns cyan,
so state is carried twice rather than by colour alone.

The icons are product-specific on purpose. A house glyph for "home" would
have been the generic choice and a calendar the safe one; a stage microphone
says live event. Alternatives considered: `calendar-dots`, `compass`,
`confetti`, `music-notes`.

### The tab bar disappears during the hold

Queue, select, held and pay have no tab bar. Those ten minutes are a timed
takeover, and wandering off to another tab is how you lose the seats, so the
app does not offer the option. The tabs come back once the purchase resolves
one way or the other.

### Two screens have no use case

**Events** and **Account** are supporting screens. A real ticketing app needs
a way to find an event and a place to keep your payment method, but neither
appears in the brief's six core use cases and neither is in
`docs/04-use-cases.md`.

Two honest options, neither taken yet:

1. Add **UC-08 Browse Events** to the requirements, which takes the set to
   eight and past the brief's stated range of five to seven.
2. Leave them as supporting screens and say so in `docs/03-prd.md` under
   scope, the same way gate scanning and refunds are handled.

Option 2 is the smaller change and keeps the use case count inside the brief.

**Two view modes.**
*Flow* gives you one phone to tap through, like a real app.
*All screens* lays every screen out on one board, which is what goes on a
slide or in the report. Exported already as
[`../mockups/all-screens.png`](../mockups/all-screens.png).

**Two buttons that demonstrate the architecture.**
*Restart* puts you back at the event page.
*Let the hold expire* jumps straight to screen 8, which is the behaviour the
brief never specified and the reason the design exists.

The 10-minute countdown on screens 4 and 5 is real. Leave it running and it
takes you to screen 8 by itself.

## How to open

Double-click the file. No build step, no npm, no server. It is one
self-contained HTML file with native CSS.

- **On a laptop** you get a phone frame so it reads as a mobile screen.
- **Under 430px wide** the frame disappears and the screen goes full bleed,
  which is what a real phone shows.

## What you can do on the queue screen

- **In the queue** / **Almost your turn** switch between the two states the
  brief asks for.
- **Watch it move** makes the position actually count down, the way it would
  during a real sale. This is the point of the screen: if it does not visibly
  move, Kenji refreshes, and every refresh makes the spike worse.

## Publishing a link for your phone

The published copy is generated from this file, so this file stays the single
source. To regenerate it:

```
python3 - <<'PY'
import re, pathlib
src = pathlib.Path('01-queue.html').read_text()
head = re.sub(r'<meta[^>]*>\s*', '', re.search(r'<head>(.*)</head>', src, re.S).group(1))
body = re.search(r'<body>(.*)</body>', src, re.S).group(1)
pathlib.Path('/tmp/queue-artifact.html').write_text(head.strip() + "\n\n" + body.strip())
PY
```

Then publish `/tmp/queue-artifact.html`. The platform supplies its own
doctype, charset and viewport, which is why they are stripped.

## Design decisions worth defending

- **The position is the largest thing on screen** and uses tabular figures, so
  the digits do not jump as it counts down.
- **There is no refresh button.** Offering one invites the exact behaviour
  that breaks the system under load.
- **No prices and no countdown timer.** Nothing is reserved yet. The countdown
  belongs on the next screen, after the hold exists.
- **The estimate says "about 6 min"** and is placed in quiet mono type. An
  estimate presented as a promise is worse than no estimate.
- **One live dot in the footer**, plus "Updated N seconds ago". That is the
  quiet proof the page is still connected. It is real state, not decoration.
- **No photography.** A queue screen with a stock concert photo behind the
  text would fight the one number that matters.

## Not built yet

The organizer side: UC-05 create event, UC-06 manage, UC-07 the live sales
dashboard. UC-07 is the interesting one for a presentation, because it is
where the held / sold / remaining split becomes visible.

## The visual direction

**Printed matter.** A ticket is a document, so the app is set like one.

The rule is structural, and it is the point:

- **Almost nothing is a card.** Content is grouped by hairlines, indents and
  space. Tier rows, settings, ticket lists and notes are all divided lists
  with no fill and no border around them.
- **Three filled objects exist**: the action button, the event block, and the
  ticket stub. The stub keeps its edges because it is the one thing here that
  is genuinely a physical object.
- **Type carries the hierarchy**, not containers. Archivo condensed at 800 to
  900 weight for the numbers that matter, mono for anything countable, four
  clear scale steps.
- **Corners are 4px.** Nothing is pill-shaped.
- **One accent, vermilion.** It does real work: the action, the countdown, the
  progress rule, the selected tier, the low-stock warning.
- **Nothing glows.** No gradient panels, no tinted washes.

Both colour schemes are built and structurally identical; the light one reads
as ink on paper, the dark one as a poster pasted on a wall.

### Why the two previous attempts were wrong

The first was too timid: small type, a 6px accent, everything centred and
evenly spaced, so it read as a banking app. The second over-corrected into
neon cyan on near-black with a glow, which is the single most generated
"cool app" look there is.

Both shared the real fault, which was not colour. **Every piece of content sat
in the same rounded card on the same raised fill.** That repetition is what
reads as machine-made, and removing it did more than any palette would have.

### Still needs a real asset

The event poster is a typographic stand-in. It wants **real event artwork at
780 x 560**. Everything else is final-quality.

QR codes are deterministic placeholder patterns, not scannable codes. They
carry the finder squares people recognise so the screen reads correctly.


---

## Choosing a direction

`themes.html` shows the same three screens (queue, select, confirmed) in six
different visual worlds. Content, copy and numbers are identical in all six,
so the only variable is the design.

| # | Direction | In one line |
|---|---|---|
| 1 | Boarding Pass | A printed document. Cool paper, black masthead, stamped red, square corners. |
| 2 | Neon Arena | Concert lighting. Violet-black with electric cyan, heavy wide type. |
| 3 | Swiss Poster | Loud and flat. White, 2px black rules, condensed 900 weight, vermilion. |
| 4 | Mint Consumer | Calm and friendly. Soft green, very round, light weights. |
| 5 | Dark Terminal | Monospace everywhere. Phosphor green on near-black, 3px corners. |
| 6 | Sunset Pop | Warm and bold. Deep plum with coral, Bricolage Grotesque. |

Each direction is one block of CSS custom properties under `[data-dir="..."]`.
The components are shared, so applying a direction to the full app is a token
swap, not a rebuild.

Exported for the report: `../mockups/directions-1-2.png`,
`directions-3-4.png`, `directions-5-6.png`.
