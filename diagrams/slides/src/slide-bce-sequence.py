"""Source for diagrams/slides/slide-bce-sequence.svg and .png.

Edit THIS file and rerun it. Never hand-edit the SVG or the PNG.

    python3 diagrams/slides/src/slide-bce-sequence.py
"""
import pathlib

W, H = 2000, 1580
INK   = "#14232E"; INK2 = "#5A6E79"; INK3 = "#8A969D"
BOUND = "#2B4A5E"; CTRL = "#B4741C"; ENT = "#0B7A63"
HOT   = "#B3261E"; RULE = "#DCE3E5"; PANEL = "#F4F7F8"
R = 26

def esc(t):
    return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

# ---------------------------------------------------------------- lifelines
# id -> (x, kind, label, sublabel)
P = {
    "actor": (170,  "actor", "Customer",     "actor"),
    "ui":    (455,  "b",     ":TicketUI",    "boundary"),
    "ctrl":  (740,  "c",     ":Booking",     "control"),
    "tok":   (1025, "e",     ":QueueToken",  "entity"),
    "tier":  (1310, "e",     ":TicketTier",  "entity"),
    "res":   (1595, "e",     ":Reservation", "entity"),
    "pay":   (1880, "b",     ":Payment",     "boundary · plug-in"),
}
HEAD_CY = 200
TOP     = 292      # lifelines start
BOT     = 1430     # lifelines end

# ------------------------------------------------------------ message table
# (y, from, to, label, style)
STEP = 46
MSGS = [
    (372,  "actor", "ui",   "join the queue",                 "ask"),
    (418,  "ui",    "ctrl", "joinQueue(customerId, eventId)",  "call"),
    (464,  "ctrl",  "tok",  "create(customerId, eventId)",     "call"),
    (510,  "ctrl",  "ui",   "tokenId, position",               "return"),
    (586,  "ui",    "ctrl", "getQueuePosition(tokenId)",       "call"),
    (700,  "actor", "ui",   "choose tier and quantity",        "ask"),
    (746,  "ui",    "ctrl", "hold(tokenId, tierId, qty)",      "call"),
    (822,  "ctrl",  "tier", "reserveAtomic(qty)",              "hot"),
    (868,  "ctrl",  "res",  "create(HELD, expiresAt)",         "call"),
    (938,  "ctrl",  "ui",   "none left — choose again",        "refused"),
    (1052, "actor", "ui",   "pay",                             "ask"),
    (1098, "ui",    "ctrl", "checkout(reservationId, method)", "call"),
    (1144, "ctrl",  "pay",  "authorise(amount)",               "call"),
    (1190, "pay",   "ctrl", "authorised",                      "return"),
    (1266, "ctrl",  "res",  "stillHeld()",                     "hot"),
    (1312, "ctrl",  "tier", "commit(qty) · held becomes sold", "call"),
    (1382, "ctrl",  "pay",  "void() · nothing charged",        "expired"),
]

SECTIONS = [                         # y, label
    (322,  "UC-01 · JOIN THE QUEUE"),
    (650,  "UC-02 · HOLD THE TICKETS"),
    (1002, "UC-03 · PAY"),
]

FRAMES = [                           # top, bottom, tab, condition, divider y, else-label
    (536,  612,  "loop", "every 5 s until admitted", None, None),
    (772,  964,  "alt",  "[ hold acquired ]", 898, "[ refused ]"),
    (1216, 1408, "alt",  "[ hold still alive ]", 1342, "[ hold expired ]"),
]

ACTIVE = {                           # participant -> (from y, to y)
    "ui":   (372, 1382), "ctrl": (418, 1382), "tok": (464, 510),
    "tier": (822, 1312), "res":  (868, 1312), "pay": (1144, 1382),
}

STYLE = {                            # style -> (colour, width, dash, bold)
    "ask":     (INK,   2.0, "",     False),
    "call":    (ENT,   2.0, "",     False),
    "return":  (INK2,  1.8, "7 5",  False),
    "hot":     (HOT,   2.6, "",     True),
    "refused": (CTRL,  2.0, "7 5",  False),
    "expired": (HOT,   2.2, "7 5",  True),
}
FRAME_L, FRAME_R = 120, 1948

svg = ['<!-- GENERATED FILE. Do not edit by hand. '
       'Source: diagrams/slides/src/slide-bce-sequence.py -->',
       f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
       f'viewBox="0 0 {W} {H}" '
       f'font-family="Helvetica Neue, Helvetica, Arial, sans-serif">']

# arrowheads, one per colour
svg.append('<defs>')
for name, col in (("ink", INK), ("ent", ENT), ("ink2", INK2),
                  ("hot", HOT), ("ctrl", CTRL)):
    svg.append(
        f'<marker id="a-{name}" viewBox="0 0 12 12" refX="10" refY="6" '
        f'markerWidth="8" markerHeight="8" orient="auto-start-reverse">'
        f'<path d="M 0 1 L 11 6 L 0 11 z" fill="{col}"/></marker>')
svg.append('</defs>')
svg.append(f'<rect width="{W}" height="{H}" fill="#FFFFFF"/>')

# ------------------------------------------------------------------- titles
svg.append(f'<text x="60" y="62" font-size="31" font-weight="700" fill="{INK}">'
           'Sequence Diagram — Boundary, Control, Entity</text>')
svg.append(f'<text x="60" y="94" font-size="17" fill="{INK2}">'
           'The same booking flow, opened up. Which object does which part of '
           'the work.</text>')

# ------------------------------------------------------------ lifeline heads
def head(pid):
    x, kind, label, sub = P[pid]
    cy = HEAD_CY
    if kind == "actor":
        g = (f'<circle cx="{x}" cy="{cy-26}" r="13" fill="#FFFFFF" '
             f'stroke="{INK}" stroke-width="2.5"/>'
             f'<line x1="{x}" y1="{cy-13}" x2="{x}" y2="{cy+14}" '
             f'stroke="{INK}" stroke-width="2.5"/>'
             f'<line x1="{x-19}" y1="{cy-2}" x2="{x+19}" y2="{cy-2}" '
             f'stroke="{INK}" stroke-width="2.5"/>'
             f'<line x1="{x}" y1="{cy+14}" x2="{x-15}" y2="{cy+36}" '
             f'stroke="{INK}" stroke-width="2.5"/>'
             f'<line x1="{x}" y1="{cy+14}" x2="{x+15}" y2="{cy+36}" '
             f'stroke="{INK}" stroke-width="2.5"/>')
        col = INK
    elif kind == "b":
        x0 = x - R - 18
        g = (f'<line x1="{x0}" y1="{cy-R-4}" x2="{x0}" y2="{cy+R+4}" '
             f'stroke="{BOUND}" stroke-width="2.5"/>'
             f'<line x1="{x0}" y1="{cy}" x2="{x-R}" y2="{cy}" '
             f'stroke="{BOUND}" stroke-width="2.5"/>'
             f'<circle cx="{x}" cy="{cy}" r="{R}" fill="#FFFFFF" '
             f'stroke="{BOUND}" stroke-width="2.5"/>')
        col = BOUND
    elif kind == "c":
        g = (f'<circle cx="{x}" cy="{cy}" r="{R}" fill="#FFFFFF" '
             f'stroke="{CTRL}" stroke-width="2.5"/>'
             f'<path d="M {x-13} {cy-R-3} A 15 12 0 0 1 {x+12} {cy-R-5}" '
             f'fill="none" stroke="{CTRL}" stroke-width="2.5"/>'
             f'<path d="M {x+13} {cy-R-12} L {x+14} {cy-R-2} L {x+5} {cy-R-6} z" '
             f'fill="{CTRL}"/>')
        col = CTRL
    else:
        g = (f'<circle cx="{x}" cy="{cy}" r="{R}" fill="#FFFFFF" '
             f'stroke="{ENT}" stroke-width="2.5"/>'
             f'<line x1="{x-R}" y1="{cy+R+5}" x2="{x+R}" y2="{cy+R+5}" '
             f'stroke="{ENT}" stroke-width="2.5"/>')
        col = ENT
    return (g +
            f'<text x="{x}" y="{cy+R+34}" font-size="16" font-weight="700" '
            f'fill="{INK}" text-anchor="middle">{esc(label)}</text>'
            f'<text x="{x}" y="{cy+R+53}" font-size="12.5" fill="{col}" '
            f'text-anchor="middle" letter-spacing="0.6">{esc(sub)}</text>')

for pid in P:
    svg.append(head(pid))
    x = P[pid][0]
    svg.append(f'<line x1="{x}" y1="{TOP}" x2="{x}" y2="{BOT}" '
               f'stroke="{INK3}" stroke-width="1.4" stroke-dasharray="6 6"/>')

# ---------------------------------------------------------------- activations
for pid, (y1, y2) in ACTIVE.items():
    x = P[pid][0]
    svg.append(f'<rect x="{x-5.5}" y="{y1-12}" width="11" height="{y2-y1+24}" '
               f'fill="#FFFFFF" stroke="{INK3}" stroke-width="1.3"/>')

# ------------------------------------------------------------------- frames
for top, bot, tab, cond, divy, els in FRAMES:
    svg.append(f'<rect x="{FRAME_L}" y="{top}" width="{FRAME_R-FRAME_L}" '
               f'height="{bot-top}" fill="none" stroke="{RULE}" stroke-width="1.6"/>')
    svg.append(f'<path d="M {FRAME_L} {top} h 84 l 16 16 v 14 h -100 z" '
               f'fill="{PANEL}" stroke="{RULE}" stroke-width="1.6"/>')
    svg.append(f'<text x="{FRAME_L+14}" y="{top+22}" font-size="14.5" '
               f'font-weight="700" fill="{INK}">{esc(tab)}</text>')
    svg.append(f'<text x="{FRAME_L+118}" y="{top+22}" font-size="14.5" '
               f'fill="{INK2}">{esc(cond)}</text>')
    if divy:
        svg.append(f'<line x1="{FRAME_L}" y1="{divy}" x2="{FRAME_R}" y2="{divy}" '
                   f'stroke="{RULE}" stroke-width="1.6" stroke-dasharray="8 6"/>')
        svg.append(f'<text x="{FRAME_L+14}" y="{divy+24}" font-size="14.5" '
                   f'fill="{INK2}">{esc(els)}</text>')

# ------------------------------------------------------------------ sections
for y, label in SECTIONS:
    svg.append(f'<line x1="60" y1="{y}" x2="{W-52}" y2="{y}" '
               f'stroke="{RULE}" stroke-width="1.4"/>')
    tw = len(label) * 7.6 + 20
    svg.append(f'<rect x="52" y="{y-13}" width="{tw}" height="26" fill="#FFFFFF"/>')
    svg.append(f'<text x="62" y="{y+5}" font-size="13" font-weight="700" '
               f'fill="{INK3}" letter-spacing="1.4">{esc(label)}</text>')

# ------------------------------------------------------------------ messages
MK = {INK: "ink", ENT: "ent", INK2: "ink2", HOT: "hot", CTRL: "ctrl"}
for y, a, b, label, style in MSGS:
    col, wdt, dash, bold = STYLE[style]
    x1, x2 = P[a][0], P[b][0]
    off = 5.5 if a in ACTIVE else 0
    off2 = 5.5 if b in ACTIVE else 0
    sx = x1 + (off if x2 > x1 else -off)
    ex = x2 - (off2 if x2 > x1 else -off2)
    d = f' stroke-dasharray="{dash}"' if dash else ''
    svg.append(f'<line x1="{sx}" y1="{y}" x2="{ex}" y2="{y}" stroke="{col}" '
               f'stroke-width="{wdt}"{d} marker-end="url(#a-{MK[col]})"/>')
    mid = (x1 + x2) / 2
    fw = ' font-weight="700"' if bold else ''
    tc = col if style in ("hot", "expired", "refused") else INK
    svg.append(f'<rect x="{mid - (len(label)*4.3+8)}" y="{y-26}" '
               f'width="{len(label)*8.6+16}" height="22" fill="#FFFFFF"/>')
    svg.append(f'<text x="{mid}" y="{y-10}" font-size="15"{fw} fill="{tc}" '
               f'text-anchor="middle">{esc(label)}</text>')

# -------------------------------------------------------- the two red callouts
svg.append(f'<text x="{P["tier"][0]+40}" y="{822+22}" font-size="13" '
           f'font-weight="700" fill="{HOT}">one owner of the seat count</text>')
svg.append(f'<text x="{P["res"][0]+40}" y="{1266+22}" font-size="13" '
           f'font-weight="700" fill="{HOT}">asked before the money is taken</text>')

# -------------------------------------------------------------------- legend
LY = 1478
svg.append(f'<line x1="60" y1="{LY}" x2="{W-52}" y2="{LY}" '
           f'stroke="{RULE}" stroke-width="1.6"/>')

def mini(x, kind, name, desc):
    cy = LY + 52
    if kind == "b":
        g = (f'<line x1="{x-20}" y1="{cy-12}" x2="{x-20}" y2="{cy+12}" '
             f'stroke="{BOUND}" stroke-width="2"/>'
             f'<line x1="{x-20}" y1="{cy}" x2="{x-12}" y2="{cy}" '
             f'stroke="{BOUND}" stroke-width="2"/>'
             f'<circle cx="{x}" cy="{cy}" r="12" fill="none" stroke="{BOUND}" '
             f'stroke-width="2"/>')
        col = BOUND
    elif kind == "c":
        g = (f'<circle cx="{x}" cy="{cy}" r="12" fill="none" stroke="{CTRL}" '
             f'stroke-width="2"/>'
             f'<path d="M {x-7} {cy-14} A 9 7 0 0 1 {x+6} {cy-15}" fill="none" '
             f'stroke="{CTRL}" stroke-width="2"/>'
             f'<path d="M {x+7} {cy-20} L {x+8} {cy-13} L {x+2} {cy-16} z" '
             f'fill="{CTRL}"/>')
        col = CTRL
    else:
        g = (f'<circle cx="{x}" cy="{cy}" r="12" fill="none" stroke="{ENT}" '
             f'stroke-width="2"/>'
             f'<line x1="{x-12}" y1="{cy+15}" x2="{x+12}" y2="{cy+15}" '
             f'stroke="{ENT}" stroke-width="2"/>')
        col = ENT
    return (g + f'<text x="{x+24}" y="{cy-3}" font-size="14.5" font-weight="700" '
                f'fill="{col}">{name}</text>'
              + f'<text x="{x+24}" y="{cy+16}" font-size="13.5" fill="{INK2}">'
                f'{desc}</text>')

svg.append(mini(80,   "b", "Boundary", "what the customer touches"))
svg.append(mini(600,  "c", "Control",  "the logic that coordinates"))
svg.append(mini(1120, "e", "Entity",   "the stored object classes"))
svg.append(f'<text x="1560" y="{LY+42}" font-size="13.5" fill="{INK2}">'
           'A boundary never talks to an entity.</text>')
svg.append(f'<text x="1560" y="{LY+64}" font-size="13.5" fill="{INK2}">'
           'Every line goes through the control.</text>')
svg.append('</svg>')

OUT = pathlib.Path(__file__).resolve().parent.parent
SVG = OUT / 'slide-bce-sequence.svg'
PNG = OUT / 'slide-bce-sequence.png'
SVG.write_text("\n".join(svg), encoding='utf-8')
import cairosvg
cairosvg.svg2png(url=str(SVG), write_to=str(PNG), scale=1.6,
                 background_color='white')
print('bce sequence diagram written')
