// Organizer shared parts: top bar, state chip, form field, Bangkok date helpers.
import { html, raw } from '../parts.js';

export const orgTopBar = () => html`<header class="topnav org"><a class="brand" href="#/events"><i></i>Ticket &amp; Queue · Organizer</a>
  <nav aria-label="Main"><a href="#/events" class="on">Events</a></nav><span class="who"><b>C</b>Chai</span></header>`;

const CHIP = { DRAFT: 'DRAFT', SCHEDULED: 'SCHEDULED', ON_SALE: 'ON SALE', CLOSING: 'CLOSING', CLOSED: 'CLOSED', CANCELLED: 'CANCELLED' };
/** DRAFT grey · SCHEDULED lime outline · ON_SALE lime fill · CLOSING lime outline · CLOSED grey · CANCELLED coral. */
export const chip = (state) => html`<span class="chip ${state.toLowerCase()}">${CHIP[state] || state}</span>`;

/** One labelled input. error: text shown in coral under it (and the input gets a coral border). */
export function field({ label, name, value = '', type = 'text', error = '', counter = '', suffix = '', disabled = false, extra = '' }) {
  return html`<label class="field ${error ? 'bad' : ''}"><span class="fl"><b>${label}</b>${counter ? html`<i>${counter}</i>` : ''}</span>
    <span class="fi"><input name="${name}" type="${type}" value="${value}" ${disabled ? raw('disabled') : ''} ${raw(extra)}>${suffix ? html`<em>${suffix}</em>` : ''}</span>
    ${error ? html`<span class="err" role="alert">${error}</span>` : ''}</label>`;
}

const pad = (n) => String(n).padStart(2, '0');
const BKK = 7 * 3600_000;
/** "2026-10-01" + "10:00" or "10:00:00" in Bangkok -> epoch ms. Empty input -> null. */
export const toEpoch = (d, t) => (d && t ? Date.parse(`${d}T${t.length === 5 ? t + ':00' : t}+07:00`) : null);
export const dateInput = (ms) => { if (ms == null) return ''; const x = new Date(ms + BKK); return `${x.getUTCFullYear()}-${pad(x.getUTCMonth() + 1)}-${pad(x.getUTCDate())}`; };
export const timeInput = (ms, seconds = false) => { if (ms == null) return ''; const x = new Date(ms + BKK); return `${pad(x.getUTCHours())}:${pad(x.getUTCMinutes())}${seconds ? ':' + pad(x.getUTCSeconds()) : ''}`; };
const MON = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
/** 14 Nov 2026, 19:00 */
export const dateTime = (ms) => { const x = new Date(ms + BKK); return `${x.getUTCDate()} ${MON[x.getUTCMonth()]} ${x.getUTCFullYear()}, ${pad(x.getUTCHours())}:${pad(x.getUTCMinutes())}`; };
