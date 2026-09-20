# Prototype

Real HTML screens. Open a file in a browser, or on your phone.

| File | What it is |
|---|---|
| **`themes.html`** | **Six visual directions, side by side. Pick one.** Start here. |
| `app.html` | The whole customer app, 8 screens, UC-01 to UC-04, in the current direction. |
| `01-queue.html` | Just the queue screen, standalone, in the first direction. |

Visual language: `minimalist-ui`. Build: `design-taste-frontend`.
Screen content comes from `../../diagrams/ui-mockup-prompt.md`.

## app.html - the eight screens

| # | Screen | Traces to |
|---|---|---|
| 1 | Event, before the sale opens | UC-01 |
| 2 | Queue, position and progress | UC-01 · FR-02, FR-04 |
| 3 | Select tier and quantity | UC-02 · FR-07, FR-11 |
| 4 | Seats held, countdown running | UC-02 · FR-09, FR-13 |
| 5 | Pay by PromptPay | UC-03 · FR-14, FR-15 |
| 6 | Confirmed, two tickets | UC-03 · FR-20 · INV-07 |
| 7 | One ticket, full screen | UC-04 · FR-21 |
| 8 | Hold expired while paying | UC-03 ext 7a · FR-17 |

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

**A dark, stage-lit ticketing app.** Concerts happen in the dark, so the app
does too.

- **Theme is locked dark**, not auto. This is one committed visual world. A
  ticketing app that flips to warm paper in daylight would read as a document,
  not a product.
- **One accent: amber `#F0A93B`.** Used at full scale on every primary button,
  the progress bar and the countdown, not in 6px doses. Red appears only as
  semantic state on the expired screen, never as a second brand colour.
- **Archivo variable**, pushed to 900 weight and 118 width for the event
  poster. **JetBrains Mono** for anything countable.
- **Tickets are perforated stubs**, with real half-circle notches punched into
  both edges. It is the one detail only this product has.
- **No texture overlay.** The atmosphere comes from the poster light alone.
  A tiled gradient pretending to be film grain is a lattice, not noise, and
  it reads as a generated-UI tell.

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
| 6 | Sunset Pop | Warm and bold. Deep plum with coral, Space Grotesk. |

Each direction is one block of CSS custom properties under `[data-dir="..."]`.
The components are shared, so applying a direction to the full app is a token
swap, not a rebuild.

Exported for the report: `../mockups/directions-1-2.png`,
`directions-3-4.png`, `directions-5-6.png`.
