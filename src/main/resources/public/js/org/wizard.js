// SCR-31 Create event · 1 Details — the same form edits the details when the organizer comes back from Tiers.
import { org } from './api.js';
import { ApiError, serverNow } from '../api.js';
import { html, button } from '../parts.js';
import { field, toEpoch, dateInput, timeInput } from './parts.js';

const BLANK = { name: '', venue: '', evDate: '', evTime: '', saleDate: '', saleTime: '', rate: '12' };
const MAX = { name: 40, venue: 30 };
// One error line per group: the event and the sale each have a date input and a time input.
const GROUP = { name: 'name', venue: 'venue', evDate: 'ev', evTime: 'ev', saleDate: 'sale', saleTime: 'sale', rate: 'rate' };
// A server refusal names an EventDetails field; this is the error line that field belongs under.
const FIELD = { name: 'name', venue: 'venue', eventDateTime: 'ev', saleOpensAt: 'sale', admitPerSecond: 'rate' };

const STEPS = ['Details', 'Tiers', 'Review'];
/** 1 Details — 2 Tiers — 3 Review. active is the step on screen now: 0 Details, 1 Tiers, 2 Review. */
const steps = (active) => html`<div class="wiz-steps">${STEPS.map((label, i) => {
  const done = i < active;
  return html`<div class="wiz-step">
    <span class="wiz-mark"><span class="wiz-dot ${done ? 'done' : i === active ? 'on' : ''}">${done ? '✓' : i + 1}</span>
      <span class="wiz-lbl ${i === active ? 'on' : ''}">${label}</span></span>
    ${i < STEPS.length - 1 ? html`<span class="wiz-bar"></span>` : ''}
  </div>`;
})}</div>`;

const at = (date, time) => { const ms = toEpoch(date, time); return ms !== null && Number.isFinite(ms) ? ms : null; };

/** The same rules, in the same words, as the server (docs/09 §9.4). */
function errors(f) {
  const e = {};
  if (!f.name.trim()) e.name = 'Required';
  if (!f.venue.trim()) e.venue = 'Required';
  const sale = at(f.saleDate, f.saleTime);
  const ev = at(f.evDate, f.evTime);
  if (sale === null) e.sale = 'Required';
  else if (sale <= serverNow()) e.sale = 'The sale must open in the future.';
  else if (ev !== null && sale >= ev) e.sale = 'The sale must open before the event.';
  if (ev === null) e.ev = 'Required';
  if (!(Number(f.rate) >= 1)) e.rate = 'Must be at least 1.';
  return e;
}

const fromEvent = (e) => ({
  name: e.name, venue: e.venue,
  evDate: dateInput(e.eventDateTime), evTime: timeInput(e.eventDateTime),
  saleDate: dateInput(e.saleOpensAt), saleTime: timeInput(e.saleOpensAt, true),
  rate: String(e.admitPerSecond),
});

const toDetails = (f) => ({
  name: f.name.trim(), venue: f.venue.trim(),
  eventDateTime: at(f.evDate, f.evTime), saleOpensAt: at(f.saleDate, f.saleTime),
  admitPerSecond: Number(f.rate),
});

function detailsView(st) {
  const f = st.form;
  const err = errors(f);
  const off = Object.keys(err).length > 0;
  const shown = (k) => (st.failed && st.failed.key === k ? st.failed.message : st.touched[k] || st.asked ? err[k] || '' : '');
  const bad = (k) => (shown(k) ? 'bad' : '');
  const note = st.failed && !st.failed.key ? st.failed.message : off ? 'Fill in every field to continue' : 'Saves a draft. Nobody can see it yet.';
  return html`<section class="org-page ev-page">
    ${steps(0)}
    <h1 class="title">${st.edit ? 'Edit event' : 'Create event'}</h1>
    <div class="wiz-grid">
      <div class="wiz-card">
        ${field({ label: 'Event name', name: 'name', value: f.name, error: shown('name'), counter: `${f.name.length}/${MAX.name}`, extra: 'maxlength="40" placeholder="The 1975 Bangkok"' })}
        ${field({ label: 'Venue', name: 'venue', value: f.venue, error: shown('venue'), counter: `${f.venue.length}/${MAX.venue}`, extra: 'maxlength="30" placeholder="Impact Arena"' })}
        <div class="field ${bad('ev')}">
          <span class="fl"><b>Event date and time</b></span>
          <span class="wiz-two"><input type="date" name="evDate" value="${f.evDate}"><input type="time" name="evTime" value="${f.evTime}"></span>
          <span class="err" role="alert">${shown('ev')}</span>
        </div>
        <div class="field ${bad('sale')}">
          <span class="fl"><b>Sale opens</b><i>to the second</i></span>
          <span class="wiz-two"><input type="date" name="saleDate" value="${f.saleDate}"><input type="time" name="saleTime" step="1" value="${f.saleTime}"></span>
          <span class="err" role="alert">${shown('sale')}</span>
        </div>
        <div class="field ${bad('rate')}">
          <span class="fl"><b>Admission rate</b></span>
          <span class="wiz-rate"><input type="number" min="1" name="rate" value="${f.rate}"><span>per second</span></span>
          <span class="err" role="alert">${shown('rate')}</span>
        </div>
      </div>
      <aside class="wiz-help">
        <span class="label">ADMISSION RATE</span>
        <p>How many people the queue lets in each second. Work it out from your seats and the 10-minute hold: 7,500 seats ÷ 600 seconds of hold ≈ 12 per second. Faster than this and people get in only to find every seat already held.</p>
        <p>You can change it later, even while the sale is on.</p>
      </aside>
    </div>
    <div class="wiz-foot">
      ${button({ label: 'Cancel', action: 'go', quiet: true, data: { to: '#/events' } })}
      <div class="wiz-save">
        ${button({ label: 'Save and add tiers', action: 'save', disabled: off, working: st.working ? 'Saving' : '' })}
        <span class="wiz-note ${st.failed && !st.failed.key ? 'stop' : ''}" data-hint>${note}</span>
      </div>
    </div>
  </section>`;
}

async function save(ctx) {
  const st = ctx.state;
  st.asked = true;
  st.failed = null;
  if (Object.keys(errors(st.form)).length) return ctx.render();
  st.working = true;
  ctx.render();
  try {
    const e = st.edit ? await org.update(st.edit, toDetails(st.form)) : await org.create(toDetails(st.form));
    ctx.go(`#/event/${e.eventId}/tiers`);
  } catch (e) {
    st.working = false;
    if (!(e instanceof ApiError) || (e.status !== 400 && e.status !== 409)) throw e;
    st.failed = { key: FIELD[e.field] || null, message: e.message };
  }
}

// ---- live checks: the typed value, the counter and the error lines change in place, so the caret stays ----

let live = null;

function begin(ctx, edit, form) {
  ctx.state.edit = edit;
  ctx.state.form = form;
  ctx.state.touched = {};
  ctx.state.asked = false;
  ctx.state.failed = null;
  ctx.state.working = false;
  live = ctx;
}

/** What the error line of each group says right now. */
const lineOf = (st, err, k) =>
  st.failed && st.failed.key === k ? st.failed.message : st.touched[k] || st.asked ? err[k] || '' : '';

function paint(ctx) {
  const root = document.getElementById('app');
  const st = ctx.state;
  const err = errors(st.form);
  const off = Object.keys(err).length > 0;
  for (const [name, group] of Object.entries(GROUP)) {
    const input = root.querySelector(`[name="${name}"]`);
    const box = input && input.closest('.field');
    if (!box) continue;
    const msg = lineOf(st, err, group);
    let line = box.querySelector('.err');
    if (!line) {
      line = document.createElement('span');
      line.className = 'err';
      line.setAttribute('role', 'alert');
      box.append(line);
    }
    line.textContent = msg;
    box.classList.toggle('bad', !!msg);
    const count = MAX[name] ? box.querySelector('.fl i') : null;
    if (count) count.textContent = `${st.form[name].length}/${MAX[name]}`;
  }
  const save = root.querySelector('.wiz-save .btn');
  if (save) save.disabled = off || st.working;
  const hint = root.querySelector('[data-hint]');
  if (hint && !hint.classList.contains('stop')) {
    hint.textContent = off ? 'Fill in every field to continue' : 'Saves a draft. Nobody can see it yet.';
  }
}

document.addEventListener('input', (e) => {
  const ctx = live && live.alive() ? live : null;
  const el = e.target;
  const group = ctx && el.name ? GROUP[el.name] : null;
  if (!group || !el.closest('#app')) return;
  ctx.state.form[el.name] = el.value;
  ctx.state.touched[group] = 1;
  if (ctx.state.failed && ctx.state.failed.key === group) ctx.state.failed = null;
  paint(ctx);
});

export const screens = [
  {
    id: 'new',
    pattern: /^\/new$/,
    // The sale-time check reads the server's clock, so ask the server for one before the first keystroke (O-01).
    async enter(ctx) {
      await org.events();
      begin(ctx, null, { ...BLANK });
    },
    view: detailsView,
    actions: { save },
  },
  {
    id: 'details',
    pattern: /^\/event\/([^/]+)\/details$/,
    async enter(ctx, eventId) {
      begin(ctx, eventId, fromEvent(await org.event(eventId)));
    },
    view: detailsView,
    actions: { save },
  },
];
