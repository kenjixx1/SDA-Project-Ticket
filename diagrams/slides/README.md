# Slide diagrams

Plain, static, white-background diagrams — made for **presenting**, not for
reading on screen.

| File | What it is |
|---|---|
| `slide-ssd.png` / `.svg` | System Sequence Diagram |
| `slide-activity.png` / `.svg` | Activity Diagram |
| `slide-bce-sequence.png` / `.svg` | Sequence diagram with boundary / control / entity |

## How to use them

- **PowerPoint / Keynote / Google Slides:** drag the `.png` straight in. They
  are 3200 × ~1800 (2× retina), so they stay sharp on a projector.
- **Want to edit or resize without blur:** use the `.svg`. It opens in
  Illustrator, Figma, Inkscape, or any browser.
- Both are **16:9**, the same shape as a slide. They already have a title, so
  you can drop one onto a blank slide with no title box.

## Rebuild

**`slide-ssd` and `slide-activity`** — the `.svg` is the source. Edit it, then
rebuild the `.png`:

```
python3 -c "import cairosvg; cairosvg.svg2png(url='slide-ssd.svg', \
  write_to='slide-ssd.png', scale=2, background_color='white')"
```

**`slide-bce-sequence`** — the SVG here is a **generated file** and says so
in its first line. The source is the Python script in `src/`. Edit that,
then:

```
python3 diagrams/slides/src/slide-bce-sequence.py
```

It writes both the `.svg` and the `.png`.

## How these differ from the interactive versions

The files one folder up (`../ssd-booking-payment.html`,
`../activity-booking-flow.html`) are the full, detailed diagrams. Those two
slide versions are **cut down on purpose**, because an audience reads a slide
for about ten seconds. The BCE sequence diagram has no fuller version — the
slide *is* the diagram.

| Cut from the slide version | Still in the full version |
|---|---|
| Activity: the "payment declined → retry" branch | yes |
| Activity: separate "Request authorisation" step (folded into *Pay with PromptPay*) | yes |
| Activity: the queue "your turn?" decision (shown as a self-loop instead) | yes |
| SSD: `listTiers` as its own message | folded into *admitted + tiers* in both |

Nothing was changed, only left out. Every number still matches
`../../AGENTS.md` §2.

## What to say when the slide is up

**Sequence diagram** — "Two lifelines only. Kenji, and the system as one box.
The last line is the one that matters: if the bank answers after the ten
minutes, they lose the seats but they are never charged."

**Activity diagram** — "Follow the green line for the normal path. The two
orange diamonds are the whole design. The first one stops the same seat being
sold twice. The second one stops Kenji paying for a seat they no longer have."

**BCE sequence diagram** — "Same diagram as the first one, but the system is
no longer one box. Three kinds of circle on top. A bar on the left means a
screen. An arrow on top means logic. A line underneath means stored data. The
rule is that a screen never touches stored data — every arrow goes through
the middle. The two red arrows are the two places the design can go wrong:
one owner of the seat count, and one last check before we take the money.""
