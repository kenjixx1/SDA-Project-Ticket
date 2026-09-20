"""Source for diagrams/slides/slide-robustness.svg and .png.

Edit THIS file and rerun it. Never hand-edit the SVG or the PNG.

    python3 diagrams/slides/src/slide-robustness.py
"""
import pathlib

W, H = 1600, 900
INK   = "#14232E"; INK2 = "#5A6E79"; INK3 = "#8A969D"
BOUND = "#2B4A5E"; CTRL = "#B4741C"; ENT = "#0B7A63"
HOT   = "#B3261E"; RULE = "#DCE3E5"
R = 25

def esc(t): return t.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")

def boundary(cx, cy, label, sub=""):
    x0 = cx - R - 20
    return f'''<g>
  <line x1="{x0}" y1="{cy-R-4}" x2="{x0}" y2="{cy+R+4}" stroke="{BOUND}" stroke-width="2.5"/>
  <line x1="{x0}" y1="{cy}" x2="{cx-R}" y2="{cy}" stroke="{BOUND}" stroke-width="2.5"/>
  <circle cx="{cx}" cy="{cy}" r="{R}" fill="#FFFFFF" stroke="{BOUND}" stroke-width="2.5"/>
  <text x="{cx}" y="{cy+R+22}" font-size="15" font-weight="600" fill="{INK}" text-anchor="middle">{esc(label)}</text>
  {f'<text x="{cx}" y="{cy+R+40}" font-size="12" fill="{INK3}" text-anchor="middle">{esc(sub)}</text>' if sub else ''}
</g>'''

def control(cx, cy, label, sub=""):
    # circle with the arrow on top
    return f'''<g>
  <circle cx="{cx}" cy="{cy}" r="{R}" fill="#FFFFFF" stroke="{CTRL}" stroke-width="2.5"/>
  <path d="M {cx-13} {cy-R-3} A 15 12 0 0 1 {cx+12} {cy-R-5}" fill="none" stroke="{CTRL}" stroke-width="2.5"/>
  <path d="M {cx+13} {cy-R-12} L {cx+14} {cy-R-2} L {cx+5} {cy-R-6} z" fill="{CTRL}"/>
  <text x="{cx}" y="{cy+R+22}" font-size="15" font-weight="600" fill="{INK}" text-anchor="middle">{esc(label)}</text>
  {f'<text x="{cx}" y="{cy+R+40}" font-size="12" fill="{INK3}" text-anchor="middle">{esc(sub)}</text>' if sub else ''}
</g>'''

def entity(cx, cy, label, attrs=""):
    return f'''<g>
  <circle cx="{cx}" cy="{cy}" r="{R}" fill="#FFFFFF" stroke="{ENT}" stroke-width="2.5"/>
  <line x1="{cx-R}" y1="{cy+R+5}" x2="{cx+R}" y2="{cy+R+5}" stroke="{ENT}" stroke-width="2.5"/>
  <text x="{cx+R+18}" y="{cy-2}" font-size="15" font-weight="600" fill="{INK}">{esc(label)}</text>
  {f'<text x="{cx+R+18}" y="{cy+16}" font-size="11.5" font-family="ui-monospace, Menlo, monospace" fill="{INK3}">{esc(attrs)}</text>' if attrs else ''}
</g>'''

def line(x1,y1,x2,y2,col=INK2,w=1.6,dash=""):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    return f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{col}" stroke-width="{w}"{d}/>'

BX, CX, EX = 400, 810, 1210
BYS = [200, 320, 440, 560, 680]
CYS = [200, 320, 440, 560, 680]
EYS = [178, 276, 374, 472, 570, 668, 766]

BOUNDS = [("Event screen",""), ("Queue screen",""), ("Selection screen",""),
          ("Payment screen",""), ("Ticket screen","")]
CTRLS  = [("Join queue",""), ("Admit customer",""), ("Hold seats",""),
          ("Take payment",""), ("Issue ticket","")]
ENTS   = [("User","userId, name, email, role"),
          ("Event","eventId, title, venue, salesOpenAt"),
          ("QueueToken","tokenId, position, status"),
          ("TicketTier","tierId, price, total, available"),
          ("Reservation","reservationId, quantity, status, holdExpiresAt"),
          ("Payment","paymentId, amount, method, status"),
          ("DigitalTicket","ticketId, verificationCode, status")]

# control index -> entity indices
LINKS = {0:[0,1,2], 1:[2], 2:[3,4], 3:[4,5], 4:[4,6]}
CRITICAL = {(2,3): "atomic hold", (3,4): "still held?"}

svg = [f'<!-- GENERATED FILE. Do not edit by hand. Source: diagrams/slides/src/slide-robustness.py -->\n<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" '
       f'font-family="Helvetica Neue, Helvetica, Arial, sans-serif">',
       f'<rect width="{W}" height="{H}" fill="#FFFFFF"/>',
       f'<text x="60" y="60" font-size="31" font-weight="700" fill="{INK}">Robustness Diagram — Booking and Payment</text>',
       f'<text x="60" y="90" font-size="17" fill="{INK2}">Which object does which part of the work. Boundary, control and entity.</text>']

# column headers
for x, t, c in ((BX,"BOUNDARY",BOUND), (CX,"CONTROL",CTRL), (EX,"ENTITY",ENT)):
    svg.append(f'<text x="{x}" y="{118}" font-size="12" font-weight="700" fill="{c}" '
               f'text-anchor="middle" letter-spacing="3">{t}</text>')
svg.append(f'<text x="120" y="118" font-size="12" font-weight="700" fill="{INK3}" text-anchor="middle" letter-spacing="3">ACTOR</text>')

# connections first so nodes sit on top
for i, by in enumerate(BYS):
    svg.append(line(150, 460, BX-R-24, by))
    svg.append(line(BX+R, by, CX-R, CYS[i]))
for ci, eis in LINKS.items():
    for ei in eis:
        hot = (ci, ei) in CRITICAL
        svg.append(line(CX+R, CYS[ci], EX-R, EYS[ei], HOT if hot else INK2, 2.6 if hot else 1.6))
        if hot:
            mx, my = (CX+R+EX-R)//2, (CYS[ci]+EYS[ei])//2
            svg.append(f'<text x="{mx}" y="{my-9}" font-size="12.5" font-weight="700" fill="{HOT}" '
                       f'text-anchor="middle">{CRITICAL[(ci,ei)]}</text>')

# actor
svg.append(f'''<g>
  <circle cx="120" cy="424" r="15" fill="#FFFFFF" stroke="{INK}" stroke-width="2.5"/>
  <line x1="120" y1="439" x2="120" y2="482" stroke="{INK}" stroke-width="2.5"/>
  <line x1="96" y1="452" x2="144" y2="452" stroke="{INK}" stroke-width="2.5"/>
  <line x1="120" y1="482" x2="100" y2="512" stroke="{INK}" stroke-width="2.5"/>
  <line x1="120" y1="482" x2="140" y2="512" stroke="{INK}" stroke-width="2.5"/>
  <text x="120" y="540" font-size="15" font-weight="600" fill="{INK}" text-anchor="middle">Customer</text>
</g>''')

for (lab, sub), y in zip(BOUNDS, BYS): svg.append(boundary(BX, y, lab, sub))
for (lab, sub), y in zip(CTRLS,  CYS): svg.append(control(CX, y, lab, sub))
for (lab, att), y in zip(ENTS,   EYS): svg.append(entity(EX, y, lab, att))

# legend
ly = 846
svg.append(f'<line x1="60" y1="{ly-38}" x2="{W-60}" y2="{ly-38}" stroke="{RULE}" stroke-width="1.5"/>')
def mini(x, kind, name, desc):
    cy = ly - 4
    if kind == "b":
        g = (f'<line x1="{x-20}" y1="{cy-13}" x2="{x-20}" y2="{cy+13}" stroke="{BOUND}" stroke-width="2"/>'
             f'<line x1="{x-20}" y1="{cy}" x2="{x-12}" y2="{cy}" stroke="{BOUND}" stroke-width="2"/>'
             f'<circle cx="{x}" cy="{cy}" r="12" fill="none" stroke="{BOUND}" stroke-width="2"/>')
        col = BOUND
    elif kind == "c":
        g = (f'<circle cx="{x}" cy="{cy}" r="12" fill="none" stroke="{CTRL}" stroke-width="2"/>'
             f'<path d="M {x-7} {cy-14} A 9 7 0 0 1 {x+6} {cy-15}" fill="none" stroke="{CTRL}" stroke-width="2"/>'
             f'<path d="M {x+7} {cy-20} L {x+8} {cy-13} L {x+2} {cy-16} z" fill="{CTRL}"/>')
        col = CTRL
    else:
        g = (f'<circle cx="{x}" cy="{cy}" r="12" fill="none" stroke="{ENT}" stroke-width="2"/>'
             f'<line x1="{x-12}" y1="{cy+15}" x2="{x+12}" y2="{cy+15}" stroke="{ENT}" stroke-width="2"/>')
        col = ENT
    return (g + f'<text x="{x+24}" y="{cy-3}" font-size="14" font-weight="700" fill="{col}">{name}</text>'
              + f'<text x="{x+24}" y="{cy+15}" font-size="13" fill="{INK2}">{desc}</text>')
svg.append(mini(90,  "b", "Boundary", "what the customer touches"))
svg.append(mini(520, "c", "Control",  "the logic that coordinates"))
svg.append(mini(950, "e", "Entity",   "the stored object classes"))
svg.append('</svg>')

OUT = pathlib.Path(__file__).resolve().parent.parent
SVG = OUT / 'slide-robustness.svg'
PNG = OUT / 'slide-robustness.png'
SVG.write_text("\n".join(svg), encoding='utf-8')
import cairosvg
cairosvg.svg2png(url=str(SVG), write_to=str(PNG),
                 scale=2, background_color='white')
print('robustness diagram written')
