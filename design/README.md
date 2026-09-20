# Design

Where the UI work lives: screen mockups and the clickable prototype.

```
design/
├── mockups/      screen images
│   ├── BRIEF.md              the art direction brief the images were made from
│   ├── app-01-event.png ...  BUILT: 8 rendered screens
│   ├── app-all-screens.png   BUILT: all 8 on one sheet
│   └── all-screens.png       BUILT: screenshot of the live prototype
└── prototype/
    ├── app.html        BUILT: the whole customer app, 8 screens
    └── 01-queue.html   BUILT: the queue screen on its own
```

**The brief for every screen is already written.** It is
[`../diagrams/ui-mockup-prompt.md`](../diagrams/ui-mockup-prompt.md): four
screens, every number pinned, plus an acceptance checklist. Do not invent
screen content here — take it from that file.

---

## Which screens

| # | Screen | Why it exists |
|---|---|---|
| 1 | Queue | Named in the brief as the UI mockup target |
| 2 | Selection + countdown | Two states: before the hold, after the hold |
| 3 | Confirmation | Two separate tickets, two QR codes |
| 4 | Hold expired while paying | "You have not been charged" |

Phone size: **390 × 844**, portrait.

---

## Two routes. You probably want both.

### Route A — Pictures (`design/mockups/`)

Still images of each screen. Good for the report and the slides.

### Route B — A real prototype (`design/prototype/`)

Actual HTML you can open on your phone and tap through. Better for the
demo, and it doubles as the start of the front end.

---

## The skills you just installed, and which one does what

13 were installed. Only a few matter for this project.

| Skill | Use it for | Works in Claude Code? |
|---|---|---|
| `design-taste-frontend` | Building the HTML prototype | **Yes** |
| `minimalist-ui` | Clean editorial style — calm, flat, no gradients | **Yes** |
| `high-end-visual-design` | Makes the result feel expensive rather than templated | **Yes** |
| `redesign-existing-projects` | Improving a screen that already exists | **Yes** |
| `imagegen-frontend-mobile` | Generating mobile screen **images** | No — see below |
| `image-to-code` | Generate an image, then build it | Partly — no image step |
| `brandkit`, `industrial-brutalist-ui`, `gpt-taste`, `stitch-design-taste` | Not this project | — |

### Two sets of images, and which to use

| Set | What it is | Use it for |
|---|---|---|
| `mockups/screen-*.png` | **Screenshots of the real prototype.** Pixel-exact by definition. | Anything where the layout must be right: the report, the requirements section, anything a grader reads closely. |
| `mockups/app-*.png` | Art-directed renders from an image model. Real concert photography, richer atmosphere. | Title slides, section openers, anywhere the mood matters more than the pixel. |

**Image generation is not a rendering engine.** It approximates a layout from
a description, so a render will drift: it invented radio circles on the ticket
tiers, stacked the price under the name instead of right-aligning it, and
dropped a hint line under a button. The brief now carries a section called
"Layout is not yours to reinterpret" that closes most of that, but it cannot
close all of it.

If a picture has to be correct, use `screen-*.png`.

### Generating images: summon Codex

Claude Code here has no text-to-image tool. **Codex does** (`image_gen__imagegen`),
so image work is handed to it:

```
codex exec --skip-git-repo-check -s workspace-write \
  "Read design/mockups/BRIEF.md and follow it exactly. Generate all 8 screen
   images, one per screen, fresh. Save into design/mockups/."
```

`mockups/BRIEF.md` is the art direction brief. It pins every hex value, the
two-light colour rule, the type weights and the exact copy per screen, so the
images match the built app instead of drifting into Codex's taste.

**Image generation does not control framing.** The eight came back on seven
different canvas sizes with different device scales. They are normalised
afterwards to one device height on one ground; the raw output is kept in
`mockups/raw/` and is not tracked.

---

## How to run a skill

In Claude Code, type the slash command, then your request:

```
/design-taste-frontend  Build the Queue screen from diagrams/ui-mockup-prompt.md
                        Block B1, as a 390x844 phone screen. Save to
                        design/prototype/01-queue.html
```

Or just describe what you want and ask me to use the skill by name — I will
load it before writing anything.

**Stacking two skills works and is usually better:**

```
/minimalist-ui  then  /design-taste-frontend
```

The first sets the visual language, the second builds the page.

---

## Suggested order of work

1. **Queue screen first.** It is the one the brief names, and it is the
   hardest: nothing happens on it for six minutes, so it has to feel alive
   without a refresh button.
2. **Selection screen, both states.** Before the hold and after the hold. The
   difference between those two artboards is the whole architecture in one
   picture — seats are taken before money is discussed.
3. **Confirmation.**
4. **Expired screen.** Do not skip this one. It is where a customer decides
   whether the system cheated them.

---

## Before anything goes in the report

Run the checklist at the bottom of
[`../diagrams/ui-mockup-prompt.md`](../diagrams/ui-mockup-prompt.md). The
things that get missed most:

- The queue position must be the **largest** thing on that screen
- **No refresh button** — offering one invites the behaviour that breaks the system
- The countdown appears **only after** the hold exists
- **Two** tickets for two seats, not one card saying "×2"
- On the expired screen, **"You have not been charged"** comes first and biggest

---

## A note on the skills folder

`.agents/` and `skills-lock.json` are **not** in git. They are re-installable:

```
npx skills add Leonxlnx/taste-skill
```

Everything in `design/` **is** in git — the mockups and the prototype are
project deliverables.
