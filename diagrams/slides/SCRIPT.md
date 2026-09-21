# Speaking script — the three diagrams

For presenting `slides/slide-ssd.png`, `slides/slide-activity.png` and
`slides/slide-bce-sequence.png`, in that order.

**About 4 minutes 15 seconds.** Written to be said out loud: short sentences,
no word used before it is explained.

**Text in normal type is what you say.** Text in `[ square brackets ]` is a
stage direction — do that, do not read it.

---

## Open — 20 seconds

*(no slide, or the title slide)*

Our system sells concert tickets.

The hard part is not selling a ticket. The hard part is forty thousand people
pressing "buy" in the same second.

So our design makes two promises. We never sell the same seat twice. And we
never charge someone for a seat they did not get.

I will show you three diagrams. Each one answers a different question.

---

## Slide 1 — System Sequence Diagram — 75 seconds

**The question this slide answers: *what must the system do?***

`[ point at the two boxes at the top ]`

This is a System Sequence Diagram. Two lines only.

On the left is the customer. On the right is our whole system, drawn as one
box. We do not open the box on this slide. We only ask two things. What does
the customer ask for, and what comes back.

Time runs down the page. Top is first, bottom is last.

`[ move your hand down the arrows as you talk ]`

He joins the queue. We give him a position in line. He waits, and his page
asks us again every five seconds. When it is his turn we let him in and show
him the prices. He asks to hold two VIP seats. We hold them, and a ten minute
clock starts. He pays.

`[ point at the last red line ]`

Now this last line. This is the one that matters.

If the bank answers *after* the ten minutes, the hold is already gone. He
loses the seats. But he is never charged.

Most designs only draw the happy path. This is the half that is easy to
forget.

---

## Slide 2 — Activity Diagram — 80 seconds

**The question this slide answers: *in what order does it happen?***

`[ point at the three row labels on the left ]`

Same story, drawn as a workflow.

The top row is what the customer does. The middle row is what the system
does. The bottom row is what happens when it fails.

`[ trace the green line left to right ]`

Follow the green line for the normal path. Open the page. Get a queue token.
Wait your turn. Get let in. Choose seats. Seats are held, and the countdown
starts. Pay. Confirmed.

`[ point at the first orange diamond ]`

Now the two orange diamonds. These two questions decide everything.

The first one asks: were the seats secured? This is where we stop the same
seat being sold twice. Two people click at the same moment, and only one of
them can win.

`[ point at the second orange diamond ]`

The second one asks: is the hold still alive? The bank has already said yes.
But before we take the money, we check the clock. If the ten minutes are
gone, we cancel the payment. Nothing is charged.

`[ point at the dashed arrow going down ]`

And this dashed arrow is the ten minutes running out. Notice it is not a step
in the line. It can happen at any moment — even while he is typing his card
number. That is why it comes in from the side.

---

## Slide 3 — Sequence Diagram, boundary / control / entity — 80 seconds

**The question this slide answers: *which object does which job?***

Same flow one more time. But now we open the box.

`[ point at each circle on top as you name it ]`

Look at the top. Three kinds of circle.

A circle with a bar on the left is a **screen**. It is what the customer
touches.

A circle with an arrow on top is the **logic**. It decides the order of
things.

A circle with a line underneath is **stored data**. These are our object
classes — the queue token, the ticket tier, the reservation.

`[ trace one arrow from the screen to the middle ]`

And there is one rule. A screen never touches stored data. Every arrow goes
through the middle.

You can check it yourself on this slide. Every line that leaves the screen
lands on Booking. Nothing jumps across.

Why does that matter? Because all the rules live in one place. If we change
how a hold works, we change one object, not five screens.

`[ point at the two red arrows ]`

The two red arrows are the same two moments from the last slide.

Here, only this one object is allowed to change the seat count. One owner, so
two people cannot both win.

And here, we ask the reservation if the hold is still alive — before we take
the money.

---

## Close — 20 seconds

So, three diagrams, three questions.

The first says **what** the system must do. The second says in **what order**.
The third says **which object** does which job.

All three protect the same two promises. Never sell a seat twice. Never
charge for nothing.

---

## If they ask

**"Why ten minutes?"**
Long enough to finish paying, short enough that seats do not sit locked. The
clock is on the server, not the phone, so nobody can cheat it by changing
their own time.

**"What if someone refreshes the page?"**
They keep their place. The queue token holds their identity for the whole
purchase, so a refresh does not send them to the back.

**"Why not just check stock when they pay?"**
Because then two people can both pass the check and both pay. We take the
seat away at the *hold*, not at the payment. That is what makes overselling
impossible rather than unlikely.

**"Where does the payment company fit?"**
It is a plug-in. It sits behind one interface, so adding a new payment method
does not change the core.

**"Why is the first slide specific and the other two generic?"**
A system sequence diagram traces one scenario, so slide 1 uses the worked
example. Slides 2 and 3 describe the rule for any customer and any event.

---

## Two things not to say

- Do not read the method names out loud. Nobody can follow `reserveAtomic(qty)`
  spoken. Say "we take the seats away in one step".
- Do not say a word before you explain it. The first time you say "hold", add
  "— that means the seats are set aside for him".
