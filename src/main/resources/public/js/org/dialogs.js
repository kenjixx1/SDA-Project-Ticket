// SCR-35 to SCR-39: the dialogs the event overview opens. Each one draws with sheet() from parts.js.
// Which dialog is open and what was typed lives in ctx.state.dialog, so typing never redraws the page.
import { org } from './api.js';
import { ApiError, serverNow } from '../api.js';
import { html, raw, num, sheet } from '../parts.js';
import { field, toEpoch, dateInput, timeInput } from './parts.js';
import { pickerKit } from './pickers.js';

// SCR-35 uses the design's pickers, the same as SCR-31. The dialog holds them: d.form is d.f, plus d.pk and d.view.
const PICKS = { evDate: { group: 'ev', up: false }, evTime: { group: 'ev', up: false }, saleDate: { group: 'sale', up: true }, saleTime: { group: 'sale', up: true } };
const kit = pickerKit({
  picks: PICKS,
  box: (ctx) => ctx.state.dialog,
  touch: (ctx, group) => { const d = ctx.state.dialog; d.touched[group] = 1; if (d.err && d.err.key === group) d.err = null; },
});

/** The screen that owns the open dialog. Only it may repaint the dialog's live checks. */
let live = null;
export const bindDialogs = (ctx) => { live = ctx; };

const LOCK = raw('<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"><rect x="4.5" y="10.5" width="15" height="9.5" rx="2.5"/><path d="M8.5 10.5v-3a3.5 3.5 0 0 1 7 0v3"/></svg>');

const tierOf = (e, tierId) => e.tiers.find((t) => t.tierId === tierId);

/** The sale time belongs to the sale, so it stops being editable once the sale is on (SCR-35b). */
const saleFrozen = (e) => !(e.state === 'DRAFT' || e.state === 'SCHEDULED');
/** A price belongs to the sale too, so it locks the moment the sale is on (SCR-36b). */
const priceFrozen = (e) => e.state === 'ON_SALE';

const number = (v) => {
  const s = String(v).trim();
  if (s === '') return null;
  const n = Number(s);
  return Number.isFinite(n) ? n : null;
};

/** Baht, seats and the rate are whole numbers, so a typed fraction is refused, never rounded into place. */
const isWhole = (n) => Number.isInteger(n);

// ----- the live checks, in the server's own words (docs/09 §9.4) -----

/** SCR-35: the same required fields as SCR-31. The sale time is only judged while its inputs are editable. */
function detailsChecks(e, d) {
  const f = d.f;
  const err = {};
  if (!f.name.trim()) err.name = 'Required';
  if (!f.venue.trim()) err.venue = 'Required';
  if (saleFrozen(e)) return err;
  const sale = toEpoch(f.saleDate, f.saleTime);
  const event = toEpoch(f.evDate, f.evTime);
  // Only a changed time can be too late: an untouched one is not sent, so a sale that opens at that
  // moment still saves. The server judges it the same way.
  if (sale === null) err.sale = 'Required';
  else if (sale !== e.saleOpensAt && sale <= serverNow()) err.sale = 'The sale must open in the future.';
  else if (event !== null && sale >= event) err.sale = 'The sale must open before the event.';
  return err;
}

/** SCR-36: the same row rules as SCR-32, plus FR-28 — a tier cannot shrink below what is sold and held. */
function tierChecks(e, d) {
  const t = tierOf(e, d.tierId);
  const err = rowChecks(d.f);
  const total = number(d.f.total);
  if (!err.total && total !== null && total < t.sold + t.held) {
    err.total = `${t.name}: ${num(t.sold)} sold and ${num(t.held)} held. Cannot reduce below ${num(t.sold + t.held)}.`;
  }
  return err;
}

/** SCR-37: the same checks as a SCR-32 row. A row without a name is named by its number. */
const addChecks = (e, d) => rowChecks(d.f);

function rowChecks(f) {
  const err = {};
  const name = f.name.trim();
  const label = name || 'Tier 1';
  if (!name) err.name = `${label} · Name is required.`;
  const price = number(f.price);
  if (price === null || price < 0) err.price = `${label} · Price cannot be below ฿0.`;
  else if (!isWhole(price)) err.price = `${label} · Price must be a whole number of baht.`;
  const total = number(f.total);
  if (total === null || total <= 0) err.total = `${label} · Seats must be more than 0.`;
  else if (!isWhole(total)) err.total = `${label} · Seats must be a whole number.`;
  return err;
}

/** SCR-38: a rate below 1 would stop the queue moving at all. */
function rateChecks(e, d) {
  const rate = number(d.f.rate);
  if (rate === null || rate < 1) return { rate: 'Must be at least 1.' };
  return isWhole(rate) ? {} : { rate: 'Must be a whole number.' };
}

const CHECKS = { details: detailsChecks, tier: tierChecks, add: addChecks, rate: rateChecks, close: () => ({}) };
const checks = (e, d) => CHECKS[d.name](e, d);

/** A refusal from the server names a field; this is the error line that field belongs under. */
const KEY = {
  details: { name: 'name', venue: 'venue', eventDateTime: 'ev', saleOpensAt: 'sale' },
  tier: { name: 'name', price: 'price', total: 'total' },
  add: { name: 'name', price: 'price', total: 'total' },
  rate: { admitPerSecond: 'rate' },
  close: {},
};

/** The inputs behind each error line, so we can tell whether the person can still clear it. */
const INPUTS = { name: ['name'], venue: ['venue'], ev: ['evDate', 'evTime'], sale: ['saleDate', 'saleTime'], price: ['price'], total: ['total'], rate: ['rate'] };

/** True while every input of that error line is on screen and can still be typed in. */
function clearable(key) {
  const root = document.getElementById('app');
  return INPUTS[key].every((n) => { const el = root.querySelector(`[name="f_${n}"], [data-pk="${n}"]`); return el && !el.disabled; });
}

/**
 * Where a refusal shows. Under its own field normally. In the dialog's general line when that
 * field is locked or gone: the person could never clear it there, and Save would never come back.
 */
function refusal(d, e) {
  const key = KEY[d.name][e.field] || null;
  return { key: key && clearable(key) ? key : null, message: e.message };
}

/** What the error line under one field says right now: the server's refusal, or a live check of a field already typed in. */
const lineOf = (e, d, key) => (d.err && d.err.key === key ? d.err.message : d.touched[key] || d.asked ? checks(e, d)[key] || '' : '');
/** A refusal that is not about one field. */
const apart = (d) => (d.err && !d.err.key ? d.err.message : '');
/** True while any error line is showing, which is when Save has nothing to send. */
const blocked = (e, d) => GROUPS.some((key) => lineOf(e, d, key));
const GROUPS = ['name', 'venue', 'ev', 'sale', 'price', 'total', 'rate'];

// ----- the sheets -----

export function dialogSheet(st) {
  const d = st.dialog;
  if (!d) return '';
  if (d.name === 'details') return detailsSheet(st.e, d);
  if (d.name === 'tier') return tierSheet(st.e, d);
  if (d.name === 'add') return addSheet(st.e, d);
  if (d.name === 'rate') return rateSheet(st.e, d);
  return closeSheet(st.e, d);
}

/** SCR-35 Edit details. The sale time belongs to the sale, so it stops being editable once the sale is on. */
function detailsSheet(e, d) {
  const f = d.f;
  const frozen = saleFrozen(e);
  const bad = (k) => (lineOf(e, d, k) ? 'bad' : '');
  return sheet({
    title: 'Edit details',
    extra: html`<div class="dlg">
      ${field({ label: 'Event name', name: 'f_name', value: f.name, error: lineOf(e, d, 'name'), extra: 'maxlength="40" placeholder="The 1975 Bangkok"' })}
      ${field({ label: 'Venue', name: 'f_venue', value: f.venue, error: lineOf(e, d, 'venue'), extra: 'maxlength="30" placeholder="Impact Arena"' })}
      <div class="field ${bad('ev')}">
        <span class="fl"><b>Event date and time</b></span>
        <span class="dlg-two">${kit.picker(d, 'evDate', 'Pick a date')}${kit.picker(d, 'evTime', '--:--')}</span>
        <span class="err" role="alert">${lineOf(e, d, 'ev')}</span>
      </div>
      <div class="field ${bad('sale')}">
        <span class="fl"><b>Sale opens</b><i>to the second</i></span>
        <span class="dlg-two">${frozen
          ? html`${kit.locked(d, 'saleDate', 'Pick a date')}${kit.locked(d, 'saleTime', '--:--:--')}`
          : html`${kit.picker(d, 'saleDate', 'Pick a date')}${kit.picker(d, 'saleTime', '--:--:--')}`}</span>
        <span class="err" role="alert">${lineOf(e, d, 'sale')}</span>
      </div>
      ${apart(d) ? html`<span class="err" role="alert">${apart(d)}</span>` : ''}
    </div>`,
    buttons: [
      { label: 'Cancel', action: 'dlgClose', quiet: true },
      { label: 'Save', action: 'dlgSaveDetails', disabled: blocked(e, d), working: d.working ? 'Saving' : '' },
    ],
    close: 'dlgClose',
  });
}

/** SCR-36 Edit tier (b: the price is frozen while the sale is on, and shows the price the server holds). */
function tierSheet(e, d) {
  const t = tierOf(e, d.tierId);
  const frozen = priceFrozen(e);
  const counts = `${num(t.sold)} sold · ${num(t.held)} held · ${num(t.remaining)} remaining`;
  const price = frozen
    ? html`<label class="field">
        <span class="fl"><b>Price (฿)</b><i class="lock">${LOCK}</i></span>
        <span class="fi"><input name="f_price" value="${t.price}" disabled></span>
        <span class="note">Prices are frozen while the sale is on, so no customer sees a price change mid-purchase.</span>
      </label>`
    : field({ label: 'Price (฿)', name: 'f_price', value: d.f.price, error: lineOf(e, d, 'price'), extra: 'inputmode="numeric" placeholder="0"' });
  return sheet({
    title: `Edit ${t.name}`,
    extra: html`<div class="dlg">
      ${field({ label: 'Name', name: 'f_name', value: d.f.name, error: lineOf(e, d, 'name'), extra: 'maxlength="20" placeholder="Tier name"' })}
      ${price}
      <label class="field ${lineOf(e, d, 'total') ? 'bad' : ''}">
        <span class="fl"><b>Total seats</b></span>
        <span class="fi"><input name="f_total" inputmode="numeric" value="${d.f.total}" placeholder="0"></span>
        <span class="note">${counts}</span>
        <span class="err" role="alert">${lineOf(e, d, 'total')}</span>
      </label>
      ${apart(d) ? html`<span class="err" role="alert">${apart(d)}</span>` : ''}
    </div>`,
    buttons: [
      { label: 'Cancel', action: 'dlgClose', quiet: true },
      { label: 'Save', action: 'dlgSaveTier', disabled: blocked(e, d), working: d.working ? 'Saving' : '' },
    ],
    close: 'dlgClose',
  });
}

/** SCR-37 Add tier. */
function addSheet(e, d) {
  return sheet({
    title: 'Add tier',
    extra: html`<div class="dlg">
      ${field({ label: 'Name', name: 'f_name', value: d.f.name, error: lineOf(e, d, 'name'), extra: 'maxlength="20" placeholder="Tier name"' })}
      ${field({ label: 'Price (฿)', name: 'f_price', value: d.f.price, error: lineOf(e, d, 'price'), extra: 'inputmode="numeric" placeholder="0"' })}
      ${field({ label: 'Seats', name: 'f_total', value: d.f.total, error: lineOf(e, d, 'total'), extra: 'inputmode="numeric" placeholder="0"' })}
      ${e.state === 'ON_SALE' ? html`<span class="note">The new tier goes on sale straight away.</span>` : ''}
      ${apart(d) ? html`<span class="err" role="alert">${apart(d)}</span>` : ''}
    </div>`,
    buttons: [
      { label: 'Cancel', action: 'dlgClose', quiet: true },
      { label: 'Add tier', action: 'dlgSaveAdd', disabled: blocked(e, d), working: d.working ? 'Adding' : '' },
    ],
    close: 'dlgClose',
  });
}

/** SCR-38 Admission rate, worked out from this event's own seats and hold. */
function rateSheet(e, d) {
  const total = e.tiers.reduce((n, t) => n + t.total, 0);
  return sheet({
    title: 'Admission rate',
    extra: html`<div class="dlg">
      ${field({ label: 'Admission rate', name: 'f_rate', value: d.f.rate, type: 'number', error: lineOf(e, d, 'rate'), suffix: 'per second', extra: 'min="1"' })}
      <span class="note">${num(total)} seats ÷ 600 seconds of hold ≈ ${Math.floor(total / 600)} per second. Faster than this and people get in only to find every seat already held.</span>
      ${e.state === 'ON_SALE' ? html`<span class="note" data-rate-preview>${preview(e, number(d.f.rate))}</span>` : ''}
      ${apart(d) ? html`<span class="err" role="alert">${apart(d)}</span>` : ''}
    </div>`,
    buttons: [
      { label: 'Cancel', action: 'dlgClose', quiet: true },
      { label: 'Save', action: 'dlgSaveRate', disabled: blocked(e, d), working: d.working ? 'Saving' : '' },
    ],
    close: 'dlgClose',
  });
}

/** "At 12 per second, the 31,208 people now waiting are through in about 43 minutes." */
const preview = (e, rate) => {
  if (rate === null || rate < 1) return '';
  const m = Math.ceil(e.waiting / rate / 60);
  return `At ${num(rate)} per second, the ${num(e.waiting)} people now waiting are through in about ${m} ${m === 1 ? 'minute' : 'minutes'}.`;
};

/** SCR-39 Close the sale? The seats inside a hold keep their time. */
function closeSheet(e, d) {
  return sheet({
    title: 'Close the sale?',
    text: [
      'No one new will be let in from the queue.',
      `${num(e.heldNow)} ${e.heldNow === 1 ? 'seat is' : 'seats are'} inside someone's 10 minutes. Those customers keep their time and can still pay.`,
      'This cannot be undone.',
    ],
    buttons: [
      { label: 'Keep selling', action: 'dlgClose' },
      { label: 'Close the sale', action: 'dlgCloseSale', danger: true, working: d.working ? 'Closing' : '' },
    ],
    close: 'dlgClose',
  });
}

// ----- opening, closing, saving -----

/** Reads the event again, so the page a dialog has just changed is fresh. */
const refresh = async (ctx) => { ctx.state.e = await org.event(ctx.params[0]); };

function open(ctx, dialog) {
  ctx.state.dialog = { touched: {}, asked: false, err: null, working: false, ...dialog };
}

/** Closing a dialog always reloads the event once: nothing polls the page while a dialog is open. */
export function closeDialog(ctx) {
  ctx.state.dialog = null;
  return refresh(ctx);
}

/** The PATCH. The sale time only goes in when its inputs are editable and the value actually changed. */
const detailsOf = (e, d) => {
  const f = d.f;
  const body = { name: f.name.trim(), venue: f.venue.trim(), eventDateTime: toEpoch(f.evDate, f.evTime) };
  const sale = toEpoch(f.saleDate, f.saleTime);
  if (!saleFrozen(e) && sale !== null && sale !== e.saleOpensAt) body.saleOpensAt = sale;
  return body;
};

const tierOfForm = (f) => ({ name: f.name.trim(), price: number(f.price), total: number(f.total) });

/** SCR-36's PATCH. A frozen price stays out of the body: the server holds the real one and would refuse a change. */
const tierOfChange = (e, d) => {
  const f = d.f;
  const body = { name: f.name.trim(), total: number(f.total) };
  if (!priceFrozen(e)) body.price = number(f.price);
  return body;
};

async function save(ctx, run) {
  const st = ctx.state;
  const d = st.dialog;
  if (d.working) return;
  d.asked = true;
  if (Object.keys(checks(st.e, d)).length) return;
  d.err = null;
  d.working = true;
  ctx.render();
  try {
    st.e = await run();
    await closeDialog(ctx);
  } catch (e) {
    d.working = false;
    if (!(e instanceof ApiError) || (e.status !== 400 && e.status !== 409)) throw e;
    await refresh(ctx); // the refusal may say the event moved on: draw the dialog as the server now has it
    ctx.render();
    d.err = refusal(d, e);
  }
}

async function closeSale(ctx, id) {
  const st = ctx.state;
  const d = st.dialog;
  d.working = true;
  ctx.render();
  try {
    st.e = await org.close(id);
    await closeDialog(ctx);
  } catch (e) {
    d.working = false;
    if (!(e instanceof ApiError) || (e.status !== 400 && e.status !== 409)) throw e;
    d.err = { key: null, message: e.message };
  }
}

export const dialogActions = {
  ...kit.actions,
  editDetails(ctx) {
    const e = ctx.state.e;
    const f = { name: e.name, venue: e.venue, evDate: dateInput(e.eventDateTime), evTime: timeInput(e.eventDateTime), saleDate: dateInput(e.saleOpensAt), saleTime: timeInput(e.saleOpensAt, true) };
    open(ctx, { name: 'details', f, form: f, pk: null, view: null });
  },
  editTier(ctx, data) {
    const t = tierOf(ctx.state.e, data.tier);
    open(ctx, { name: 'tier', tierId: t.tierId, f: { name: t.name, price: String(t.price), total: String(t.total) } });
  },
  addTier(ctx) {
    open(ctx, { name: 'add', f: { name: '', price: '', total: '' } });
  },
  changeRate(ctx) {
    open(ctx, { name: 'rate', f: { rate: String(ctx.state.e.admitPerSecond) } });
  },
  closeSale(ctx) {
    open(ctx, { name: 'close' });
  },
  dlgClose: (ctx) => closeDialog(ctx),
  dlgSaveDetails: (ctx, data, id) => save(ctx, () => org.update(id, detailsOf(ctx.state.e, ctx.state.dialog))),
  dlgSaveTier: (ctx, data, id) => save(ctx, () => org.updateTier(id, ctx.state.dialog.tierId, tierOfChange(ctx.state.e, ctx.state.dialog))),
  dlgSaveAdd: (ctx, data, id) => save(ctx, () => org.addTier(id, tierOfForm(ctx.state.dialog.f))),
  dlgSaveRate: (ctx, data, id) => save(ctx, () => org.setRate(id, number(ctx.state.dialog.f.rate))),
  dlgCloseSale: (ctx, data, id) => closeSale(ctx, id),
};

// ----- live checks: the error lines, the preview and Save change in place, so the caret stays -----

// Every input of every dialog carries f_, so no other screen's listener picks it up.
const GROUP = { name: 'name', venue: 'venue', evDate: 'ev', evTime: 'ev', saleDate: 'sale', saleTime: 'sale', price: 'price', total: 'total', rate: 'rate' };
const SAVE = { details: 'dlgSaveDetails', tier: 'dlgSaveTier', add: 'dlgSaveAdd', rate: 'dlgSaveRate', close: 'dlgCloseSale' };

document.addEventListener('input', (ev) => {
  const ctx = live && live.alive() ? live : null;
  const d = ctx && ctx.state.dialog;
  const el = ev.target;
  const name = el.name && el.name.startsWith('f_') ? el.name.slice(2) : null;
  if (!d || !name || !GROUP[name] || !el.closest('#app')) return;
  d.f[name] = el.value;
  d.touched[GROUP[name]] = 1;
  if (d.err && d.err.key === GROUP[name]) d.err = null;
  paint(ctx);
});

function paint(ctx) {
  const st = ctx.state;
  const d = st.dialog;
  const root = document.getElementById('app');
  if (!d) return;
  for (const [name, key] of Object.entries(GROUP)) {
    const input = root.querySelector(`[name="f_${name}"]`);
    const box = input && input.closest('.field');
    if (!box) continue;
    const msg = lineOf(st.e, d, key);
    const line = box.querySelector('.err');
    if (line) line.textContent = msg;
    else if (msg) {
      const span = document.createElement('span');
      span.className = 'err';
      span.setAttribute('role', 'alert');
      span.textContent = msg;
      box.append(span);
    }
    box.classList.toggle('bad', !!msg);
  }
  const save = root.querySelector(`[data-action="${SAVE[d.name]}"]`);
  if (save) save.disabled = d.working || blocked(st.e, d);
  const line = root.querySelector('[data-rate-preview]');
  if (line) {
    const text = preview(st.e, number(d.f.rate));
    line.textContent = text;
    line.hidden = !text;
  }
}
