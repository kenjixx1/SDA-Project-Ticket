// Shared parts C-01 to C-09, formatters and the html helper. No screen logic here.
import { serverNow, contactAge, customer } from './api.js';

// ----- html: a tagged template that escapes every value unless it is raw -----
class Raw {
  constructor(s) { this.s = s; }
  toString() { return this.s; }
}
export const raw = (s) => new Raw(String(s));
const ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
export const esc = (v) => String(v).replace(/[&<>"']/g, (c) => ESC[c]);
function piece(v) {
  if (v instanceof Raw) return v.s;
  if (Array.isArray(v)) return v.map(piece).join('');
  if (v === null || v === undefined || v === false) return '';
  return esc(v);
}
export function html(strings, ...values) {
  let out = strings[0];
  values.forEach((v, i) => { out += piece(v) + strings[i + 1]; });
  return new Raw(out);
}

// ----- formatting: Bangkok time, baht, digits -----
const TZ = 'Asia/Bangkok';
const pad = (n) => String(n).padStart(2, '0');
const fmt = (opts) => new Intl.DateTimeFormat('en-GB', { timeZone: TZ, ...opts });
const fTime = fmt({ hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
const fShort = fmt({ day: 'numeric', month: 'short', year: 'numeric' });
const fDayMonth = fmt({ day: 'numeric', month: 'short' });
const fLong = fmt({ weekday: 'long', day: 'numeric', month: 'long' });
const fDay = fmt({ day: '2-digit' });
const fMon = fmt({ month: 'short' });
const fYmd = fmt({ year: 'numeric', month: '2-digit', day: '2-digit' });

export const baht = (n) => (n === 0 ? 'Free' : '฿' + Math.round(n).toLocaleString('en-US'));
export const num = (n) => Math.round(n).toLocaleString('en-US');
export const mmss = (ms) => { const t = Math.max(0, Math.ceil(ms / 1000)); return pad(Math.floor(t / 60)) + ':' + pad(t % 60); };
export const hms = (ms) => { const t = Math.max(0, Math.ceil(ms / 1000)); return pad(Math.floor(t / 3600)) + ':' + pad(Math.floor(t / 60) % 60) + ':' + pad(t % 60); };
export const time24 = (ms) => fTime.format(ms);
export const dateShort = (ms) => fShort.format(ms);
export const dayMonth = (ms) => fDayMonth.format(ms);
export const dateLong = (ms) => fLong.format(ms).replace(',', '');
export const dayNum = (ms) => fDay.format(ms);
export const monUpper = (ms) => fMon.format(ms).toUpperCase();
export const sameDay = (a, b) => fYmd.format(a) === fYmd.format(b);
export const WORDS = ['', 'One', 'Two', 'Three', 'Four'];
export const seats = (q, tier) => (q === 1 ? `1 ${tier} seat` : `${q} ${tier} seats`);

// ----- C-01 live indicator -----
export function liveText() {
  const age = contactAge();
  if (age > 15000) return { off: true, text: 'Reconnecting…' };
  if (age < 5000) return { off: false, text: 'Updated just now' };
  return { off: false, text: `Updated ${Math.floor(age / 1000)} seconds ago` };
}
export function liveIndicator() {
  const l = liveText();
  return html`<div class="live" data-live><span class="dot ${l.off ? 'off' : ''}"></span><span data-live-text>${l.text}</span></div>`;
}

// ----- C-02 countdown -----
export const barPct = (expiresAt) => Math.max(0, Math.min(100, ((expiresAt - serverNow()) / 600000) * 100));
export function countdown(expiresAt) {
  return html`<div class="countdown" data-countdown="${expiresAt}">
    <div class="top"><span class="lbl"><i></i>Time to pay</span><span class="v" data-cd-text>${mmss(expiresAt - serverNow())}</span></div>
    <div class="bar"><i data-cd-bar style="width:${barPct(expiresAt)}%"></i></div>
  </div>`;
}

// ----- C-03 order summary -----
export function orderSummary(h) {
  return html`<div class="rows">
    <div class="row"><span class="k">Tier</span><span class="v txt">${h.tierName}</span></div>
    <div class="row"><span class="k">Tickets</span><span class="v">${h.quantity}</span></div>
    <div class="row"><span class="k">Price each</span><span class="v">${baht(h.unitPrice)}</span></div>
    ${h.promotions.map((p) => html`<div class="row"><span class="k">${p.name}</span><span class="v">−${baht(p.amount)}</span></div>`)}
    <div class="row sum"><span class="k">Total</span><span class="v">${baht(h.totalAmount)}</span></div>
  </div>`;
}

// ----- C-06 banner -----
export const banner = (title, text, good = false) =>
  html`<div class="banner ${good ? 'good' : ''}" role="status"><b>${title}</b><p>${text}</p></div>`;

// ----- C-07 button: ready, working, disabled -----
export function button({ label, action, quiet = false, danger = false, disabled = false, working = '', data = {} }) {
  const cls = ['btn', quiet && 'quiet', danger && 'danger'].filter(Boolean).join(' ');
  const attrs = Object.entries(data).map(([k, v]) => html` data-${k}="${v}"`);
  const off = disabled || working ? raw('disabled') : '';
  return html`<button class="${cls}" data-action="${action}"${attrs} ${off}>${working ? html`<span class="spin"></span>${working}` : label}</button>`;
}

// ----- C-08 loading placeholder, C-09 connection lost -----
export const skeleton = () => html`<section class="screen">
  <div class="skel" style="height:34px;width:60%"></div><div class="skel" style="height:180px"></div>
  <div class="skel" style="height:64px"></div><div class="skel" style="height:64px"></div></section>`;
export const offlineBanner = (withRetry) => html`<div style="padding:12px 20px 0">
  ${banner('No connection.', 'Your place and your seats are kept by the server. We will reconnect by ourselves.')}
  ${withRetry ? html`<button class="textlink" data-action="retry">Try again</button>` : ''}</div>`;
export const notAvailable = (msg) => html`<section class="screen center"><div class="ring"></div><h1 class="status-title">${msg}</h1></section>
  <div class="foot">${button({ label: 'Back to events', action: 'go', quiet: true, data: { to: '#/events' } })}</div>`;

// ----- QR: a stand-in pattern from a seed. It looks like a QR code; it is not scannable. -----
function seeded(str) {
  let h = 1779033703 ^ str.length;
  for (let i = 0; i < str.length; i++) { h = Math.imul(h ^ str.charCodeAt(i), 3432918353); h = (h << 13) | (h >>> 19); }
  return () => { h = Math.imul(h ^ (h >>> 16), 2246822507); h = Math.imul(h ^ (h >>> 13), 3266489909); h ^= h >>> 16; return (h >>> 0) / 4294967296; };
}
export function qr(seed, size = 200) {
  const N = 25, cell = size / N, rnd = seeded(seed);
  const finder = (r, c) => {
    for (const [or, oc] of [[0, 0], [0, N - 7], [N - 7, 0]]) {
      const dr = r - or, dc = c - oc;
      if (dr >= -1 && dr <= 7 && dc >= -1 && dc <= 7) {
        if (dr < 0 || dr > 6 || dc < 0 || dc > 6) return 0;
        return dr === 0 || dr === 6 || dc === 0 || dc === 6 || (dr >= 2 && dr <= 4 && dc >= 2 && dc <= 4) ? 1 : 0;
      }
    }
    return -1;
  };
  let rects = '';
  for (let r = 0; r < N; r++) {
    for (let c = 0; c < N; c++) {
      const f = finder(r, c);
      if (f === 1 || (f === -1 && rnd() > 0.52)) {
        rects += `<rect x="${(c * cell).toFixed(2)}" y="${(r * cell).toFixed(2)}" width="${cell.toFixed(2)}" height="${cell.toFixed(2)}"/>`;
      }
    }
  }
  return raw(`<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" fill="#0B0B0C" role="img" aria-label="QR code">${rects}</svg>`);
}

// ----- C-05 ticket stub -----
export function stub(o, t) {
  return html`<div class="stub">
    <div class="top"><span>Ticket <b>${t.n}</b> of ${t.of}</span><b class="mono">${t.ticketId}</b></div>
    <div class="tear"></div>
    <div class="body"><div class="qr">${qr(t.payload, 92)}</div>
      <div class="info"><span class="tier">${o.tierName}</span><b>${o.event.name}</b><span>${o.event.venue}</span>
        <span>${dateShort(o.event.eventDateTime)}, ${time24(o.event.eventDateTime)}</span><span class="mono">SINGLE USE</span></div></div>
  </div>`;
}

// ----- headers, tab bar, status screens, sheets -----
export const slimHeader = (title, meta, artwork) => html`<div class="slim">
  ${artwork ? html`<img src="${artwork}" alt="">` : html`<div class="ph"></div>`}<div><b>${title}</b><span>${meta}</span></div></div>`;

const NAME = customer.charAt(0).toUpperCase() + customer.slice(1);
const tabOf = (path) => (/^\/(tickets|ticket\/|confirmed\/)/.test(path) ? 'tickets' : /^\/account/.test(path) ? 'account' : 'events');
/** The customer top bar drawn above every screen. */
export const topNav = (path = '') => {
  const on = tabOf(path);
  return html`<header class="topnav"><a class="brand" href="#/events"><i></i>Ticket &amp; Queue</a>
    <nav aria-label="Main"><a href="#/events" class="${on === 'events' ? 'on' : ''}">Events</a><a href="#/tickets" class="${on === 'tickets' ? 'on' : ''}">Tickets</a></nav>
    <a class="who ${on === 'account' ? 'on' : ''}" href="#/account"><b>${NAME.charAt(0)}</b>${NAME}</a></header>`;
};
/** No tab bar on desktop. Kept so screens that still call it keep working. */
export const tabBar = () => html``;

/** tone: 'stop' (coral), 'calm' (grey), 'go' (lime). card: [title, text]. primary/secondary: button() options. */
export function statusScreen({ tone, icon, title, text, card, primary, secondary }) {
  return html`<section class="screen center">
    <div class="ring ${tone}">${icon}</div>
    <h1 class="status-title ${tone === 'stop' ? 'stop' : ''}">${title}</h1>
    <p class="status-text">${text}</p>
    ${card ? html`<div class="card" style="text-align:left;width:100%"><b>${card[0]}</b><p>${card[1]}</p></div>` : ''}
  </section>
  <div class="foot">${button(primary)}${secondary ? button({ ...secondary, quiet: true }) : ''}</div>`;
}

/** A tap on the dark backdrop runs the close action (default: the last button). extra is drawn between the text and the buttons. */
export function sheet({ title, text = [], buttons, close = buttons[buttons.length - 1].action, extra = '' }) {
  return html`<div class="sheet-back" data-action="${close}"></div>
  <div class="sheet" role="dialog" aria-modal="true" aria-label="${title}"><h2>${title}</h2>${text.map((t) => html`<p>${t}</p>`)}
    ${extra}<div class="acts">${buttons.map(button)}</div></div>`;
}
