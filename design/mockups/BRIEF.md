# Art direction brief: Event Ticket & Queue app, 12 screen images

Generate **12 separate images**, one per screen. Do not combine them into a
board. Do not crop one image out of another. Each image is generated fresh.

Save them into `design/mockups/` with exactly these names:

```
app-00-home.png       app-05-pay.png          app-09-tickets.png
app-01-event.png      app-06-confirmed.png    app-10-tickets-empty.png
app-02-queue.png      app-07-ticket.png       app-11-account.png
app-03-select.png     app-08-expired.png
app-04-held.png
```

---

## The product

A ticketing app for concerts where demand massively exceeds supply. Everyone
waits in a virtual queue, then gets exactly 10 minutes to choose seats and pay
before the hold expires. The app already exists as a working HTML prototype;
these images are the polished art-directed version of the same screens, so
**they must match the design system below exactly.**

## Platform mode

**iOS-native premium.** Safe-area aware, calm chrome, elegant spacing.
Content area 390 x 844. Show each screen inside a **clean, subtle iPhone
mockup with a visible thin frame**, centred, with even margins on all four
sides. Same device style and same scale in all 12 images. The frame supports
the screen; the UI content is the hero.

---

## Design bible: lock this and do not drift

**Theme paradigm:** deep dark. This is an arena, lit in the dark.

**Palette.** Exactly these. No other colours anywhere.

| Role | Hex |
|---|---|
| Page ground | `#07060D` |
| Phone body / screen base | `#0C0A16` |
| Cards, rows | `#151226` |
| Pressed surface | `#1E1A33` |
| Hairlines | `#2A2545` |
| Primary text | `#F2F0FF` |
| Secondary text | `#A49CC8` |
| Tertiary text | `#837AAD` |
| **Cyan** | `#35E3F5` |
| Text on cyan | `#021419` |
| **Magenta** | `#FF5CA8` |

**The colour rule, and it is the whole idea.** The arena has two lighting
rigs and each one means something.

- **Cyan is the light you move toward.** Every primary button, the queue
  progress bar, the countdown, the live indicator, the selected ticket tier.
- **Magenta is the light that stops you.** Low stock on a tier, and the
  expired-hold screen.
- Nothing else on any screen carries colour. If you are tempted to colour
  something a third way, leave it in `#F2F0FF` or `#A49CC8`.

**Typography.** A wide grotesk with real weight range, Archivo-like.
- Act name: 900 weight, extra wide, uppercase, very tight tracking, huge.
- Queue position number: ~104px, 800 weight, tabular figures.
- Body: 14 to 16px, comfortably readable. Never small or decorative.
- Data, IDs, timers and counts: a clean monospace.

**Shape.** Cards 18px radius, buttons 12px, phone frame ~38px. One system.

**Texture and atmosphere.** Concert stage lighting. On the event header, two
rigs: cyan from the upper left, magenta from the upper right, both falling off
into black with visible haze in the beam. Soft film grain across dark
surfaces. Restrained. Never let atmosphere reduce text contrast.

**Glow.** Exactly two things glow: the queue progress bar and the small live
dot. Buttons do not glow. Cards do not glow. Text does not glow.

**Signature components:** a large hero metric block (the queue position), a
framed card stack (ticket tiers and ticket stubs), a checkout summary card,
and a bottom action area docked above the home indicator.

**Motion implied:** sheet rise, staggered list reveal. Suggest it through
composition only.

---

## Layout is not yours to reinterpret

This app already exists. These renders must match its layout, not improve on
it. Where this brief describes an arrangement, reproduce that arrangement.
Where it does not, copy the nearest screen that does rather than inventing.

**Never add a control that is not described here.** In particular: no radio
circles, no checkboxes, no chevrons, no arrows, no "more" affordances, no
badges, no progress rings. If a row has no marker described, it has no marker.

**Row anatomy, used by every tier row.** One row, two lines:
- Line 1: the tier name on the **left**, the price **right-aligned on the same
  baseline**. Never stack the price under the name.
- Line 2: the remaining count, in mono, aligned under the name.
- The selected row, and only the selected row, carries a small filled cyan
  circle with a dark check, sitting at the right of line 2.
- Unselected rows carry nothing on the right of line 2. Empty space.

**Text colour, per element.**
- Tier name: `#F2F0FF` on every row, selected or not.
- Tier price: `#F2F0FF` on unselected rows. Cyan **only** on the selected row.
- Remaining count: `#A49CC8`, except a low count, which is magenta.
- Order total: the `2 × VIP` label is `#A49CC8` and small; the amount is
  `#F2F0FF` and large. **The total is not cyan.** Cyan is for actions.

**Order total block.** A hairline across the content, then the label on the
left and the amount on the right, sitting directly above the footer button
with empty space pushed above it, not below.

**Every footer button that this brief gives a hint line to must have it.**
The hint is one small centred line in `#837AAD` directly under the button.

**Screen titles are modest.** A plain screen title such as "You are in",
"Seats held" or "Tickets" is about 21px and sits top-left with its subtitle
under it. Only two things on this app are large: the act name on a stage-lit
poster, and the queue position number. Do not turn a screen title into a hero
headline.

**Scale sanity.** Body text 14 to 16px, small print 12 to 13px, tier names
about 17px. If an element looks more than twice the size of the same element
on another screen, it is wrong.

## The app shell

Four screens carry a **bottom tab bar**: Events, Tickets and Account.
Eight do not.

The tab bar has three items, each an icon above a short label:

| Tab | Icon | Label |
|---|---|---|
| Events | a stage microphone on a stand | Events |
| Tickets | a ticket stub | Tickets |
| Account | a person | Account |

The **active** tab is a filled icon in cyan with a cyan label. The other two
are outline icons in `#837AAD` with a matching label. Icons sit above the
label, about 25px, and the bar sits above the home indicator.

**The purchase flow has no tab bar, on purpose.** Queue, select, held, pay,
confirmed, ticket and expired are a full-screen takeover: the customer has ten
minutes and wandering off to another tab loses the seats. Do not draw a tab
bar on those screens.

---

## The 12 screens

Copy is final. Use it exactly. Do not add marketing lines.

### 00 · Home, the Events tab
Title block at the top left: **Events** large, `Bangkok` under it.
A large feature card, stage-lit with the two rigs, containing: a small cyan
pill reading `ON SALE TODAY`, then **BLACKPINK BANGKOK** in heavy uppercase
across two lines, then `Impact Arena · 14 Nov 2026`, then a cyan line
`Sale opens 10:00, from ฿1,900`.
Below it a small label `ALSO ON SALE` and three quiet rows, each with the act
on the left, the price on the right, and venue and date beneath in mono:
- `Bodyslam` ฿1,200, `Thunder Dome · 22 Nov`
- `Tame Impala` ฿2,400, `UOB Live · 5 Dec`
- `Phum Viphurit` ฿1,500, `Moonstar Studio · 18 Dec`
**Tab bar visible, Events active.**

### 01 · Event
Header: full-bleed stage-lit panel, two rigs, act name **BLACKPINK BANGKOK**
in huge uppercase, under it `IMPACT ARENA · 14 NOV 2026`.
Body, centred: small cyan label `SALE OPENS`, then **10:00** very large, then
`Saturday 1 October`. Below, three quiet price rows: `VIP ฿6,500`,
`Zone A ฿3,800`, `Zone B ฿1,900`. Then a card: **Everyone waits in the same
line.** Joining early does not help.
Bottom: full-width cyan button **Join the queue**, tiny line under it
`Maximum 4 tickets per person`.

### 02 · Queue
Slim stage-lit header with **BLACKPINK** and the venue line.
Centre of screen, the hero moment: cyan label `YOUR POSITION`, then
**4,182** enormous, then `4,181 people are ahead of you`.
Below: a thin cyan progress bar about 62% full, gently glowing, with
`Moved 6,823 places` on the left and `about 6 min` in cyan on the right.
Then a card: **Your place is saved.** Keep this page open.
Bottom strip: a small cyan dot and `Updated just now`. **No refresh button.**

### 03 · Select
Plain header, top-left: **You are in** at about 21px, and under it
`Choose your tickets. Nothing is reserved yet.` A hairline under the header.

Three tier rows, following the row anatomy above exactly:
- **VIP**, selected. Cyan 2px border, faint cyan wash inside. `VIP` white on
  the left, `฿6,500` cyan right-aligned on the same line. `only 16 left` in
  magenta mono on the second line, and a filled cyan check circle at the right
  of that line.
- **Zone A**, not selected. `Zone A` white left, `฿3,800` white right, same
  line. `350 left` in grey mono below. **Nothing on the right of line 2.**
- **Zone B**, not selected. `Zone B` white left, `฿1,900` white right, same
  line. `2,020 left` in grey mono below. **Nothing on the right of line 2.**

A bordered quantity row: `How many` on the left, and on the right a minus
button, **2**, and a plus button, each a rounded square about 44px.
Under the row, left-aligned small grey: `Maximum 4 per person`.

Then empty space, then a hairline, then the order total: `2 × VIP` small grey
on the left and **฿13,000** large white on the right.

Footer: full-width cyan **Hold these seats**, and directly under it the hint
line `Holding takes them out of the sale for 10 minutes`.

### 04 · Held
Header: **Seats held** / `Yours until the timer runs out.`
A prominent cyan-bordered countdown block: `Time to pay` and **10:00** in
large mono.
Rows: `Tier VIP`, `Tickets 2`, `Price each ฿6,500`, then `Total` **฿13,000**.
A cyan-tinted card: **VIP just dropped from 16 to 14.** Your two seats left
the sale the moment you held them.
Bottom: cyan **Continue to payment**, and a quiet outlined
**Release and choose again**.

### 05 · Pay
Header: **Pay ฿13,000** / `2 × VIP for Blackpink, 14 Nov 2026`.
Countdown block again, **10:00**.
A payment row: small cyan tile marked `PP`, then **PromptPay** and
`Scan with your banking app`.
A large QR code on a light panel, centred. Under it
`This code expires with your hold`.
A card: **Do not close this page.** We confirm your tickets as soon as the
bank answers.
Bottom: cyan **I have paid**.

### 06 · Confirmed
A cyan circle with a check, centred. Then **You are going** large.
Then `Paid ฿13,000 by PromptPay. Two tickets, one for each seat.`
Two ticket stubs stacked, each: `Ticket 1 of 2` with id `TKT-9F42-01` on the
right, a dashed perforation line with **a real half-circle notch punched into
the left and right edge**, then a QR on a light panel beside `VIP` in cyan,
`Blackpink, Bangkok`, `Impact Arena`, `14 Nov 2026, 19:00`, `SINGLE USE`.
Second stub is `Ticket 2 of 2`, `TKT-9F42-02`.
Stubs look like laminated passes catching the rigs above them.
Bottom: cyan **Open ticket 1**.

### 07 · Ticket
Slim stage-lit header: **BLACKPINK** and `TICKET 1 OF 2 · VIP`.
A large QR on a light panel, centred, dominant. Under it the id
`TKT-9F42-01` in cyan mono.
Two rows: `Holder Kenji`, `Doors 18:00`.
A card: **Single use.** This code works once.
Bottom: quiet outlined **Back to my tickets**.

### 08 · Expired
Centred. A magenta ring with a slash inside it.
Then, the largest thing on the screen, in magenta:
**You have not been charged**
Then `Your 10 minutes ran out before the payment finished, so the two VIP
seats went back on sale. No money left your account.`
A card: **You can try again.** Joining the queue again puts you at the back.
Bottom: cyan **Join the queue again**, and quiet outlined
**Back to the event**.

### 09 · Tickets
Title block: **Tickets** large, `Two upcoming` under it.
Label `UPCOMING`, then a card: a date block on the left reading **14** over
`NOV`, a divider, then **Blackpink** with `Impact Arena · 19:00` in mono
beneath, and a small cyan outlined pill on the right reading `2 · VIP`.
Label `PAST`, then the same card shape, dimmed to about half opacity:
**02 AUG**, **Bodyslam**, `Thunder Dome · 20:00`, pill reading `USED` in grey.
**Tab bar visible, Tickets active.**

### 10 · Tickets, empty
Title block: **Tickets**.
Centred in the empty space: a dashed circle outline about 64px, then
**No tickets yet**, then `Tickets you buy show up here, ready to scan at the
gate.`, then a cyan button **Browse events** sized to its text rather than
full width.
**Tab bar visible, Tickets active.**

### 11 · Account
Title block: **Account**.
A row with a 58px cyan circle containing a dark **K**, beside **Kenji** and a
masked email `k•••••@gmail.com` in mono.
Two grouped cards of rows, each row a label on the left and a quiet mono value
on the right:
- `Payment methods` → `PromptPay`, `Order history` → `2 orders`
- `Notifications` → `On`, `Help` → (no value)
A third single-row card: **Sign out** in magenta.
**Tab bar visible, Account active.**

---

## Avoid

- Purple-to-blue startup gradients. The violet is the unlit room, not a brand
  gradient.
- Glass cards, floating blobs, fake charts, stat-card spam.
- A third accent colour of any kind.
- Glow on buttons, cards or text.
- Small or decorative text. If something does not fit, remove content, do not
  shrink type.
- Generic line-icon library look.
- A website laid out inside a phone frame. These are app screens.
- Any device frame that is heavier or more interesting than the screen.

## Check before finishing

All 12 exist as separate files. The four shell screens have a tab bar with
the right one active; the eight purchase screens have none. Same device, same scale, same margins. Copy is
exactly as written. Only cyan and magenta carry colour, each meaning one
thing. Text is comfortably readable at normal size. The two stubs have real
notches. Nothing glows except the progress bar and the live dot.
