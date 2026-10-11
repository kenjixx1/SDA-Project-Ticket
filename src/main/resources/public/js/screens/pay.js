// SCR-12 Checkout (a default, b promotion, c one method down, d payment unavailable),
// SCR-13 Pay by PromptPay (a waiting, b declined), SCR-14 Pay by card, simulated (a waiting, b declined),
// SCR-15 Confirming payment (a normal, b bank is slow), SCR-18 Expired while paying.
import { api, serverNow } from '../api.js';
import { html, raw, baht, dateShort, mmss, button, banner, countdown, orderSummary, qr, statusScreen, WORDS } from '../parts.js';
import { follow, atZero, againButton, backButton } from './hold.js';
import { joinAgain } from './queue.js';

const allDown = (h) => h.paymentMethods.every((m) => !m.available);

function methodRows(h, st) {
  return html`<div class="methods">${h.paymentMethods.map((m) => {
    const sel = st.method === m.key;
    return html`<button class="method ${sel ? 'sel' : ''}" data-action="choose" data-key="${m.key}" ${m.available && !st.working ? '' : raw('disabled')}>
      <span class="tile">${m.tile}</span>
      <span class="info"><span class="name">${m.label}</span><span class="left ${m.available ? '' : 'out'}">${m.available ? m.line : 'Not available right now'}</span></span>
      ${sel ? html`<span class="check" aria-label="Selected">✓</span>` : html`<span></span>`}
    </button>`;
  })}</div>`;
}

function payFooter(h, st, down, wrap = true) {
  const inner = html`${button({ label: `Pay ${baht(h.totalAmount)}`, action: 'pay', disabled: !st.method || down, working: st.working ? 'Paying…' : '' })}
    ${down ? html`<p class="hint">Payment is not available right now</p>` : st.method ? '' : html`<p class="hint">Choose how to pay</p>`}`;
  return wrap ? html`<div class="foot">${inner}</div>` : inner;
}

/** Keeps the chosen method only while it is still available. */
function keepMethod(st) {
  if (st.method && !st.h.paymentMethods.some((m) => m.key === st.method && m.available)) st.method = null;
}

// SCR-14: the simulated provider opens its own page with its own form. The values live outside
// ctx.state so that typing never redraws the screen and the caret stays where it is.
const card = { num: '', exp: '', cvc: '', name: '' };
let cardTried = false;
const CARD_TYPE = {
  num: (v) => v.replace(/\D/g, '').slice(0, 16).replace(/(.{4})(?=.)/g, '$1 '),
  exp: (v) => { const d = v.replace(/\D/g, '').slice(0, 4); return d.length > 2 ? `${d.slice(0, 2)} / ${d.slice(2)}` : d; },
  cvc: (v) => v.replace(/\D/g, '').slice(0, 4),
  name: (v) => v.slice(0, 40),
};
const TEST_CARD = { num: '4242 4242 4242 4242', exp: '12 / 28', cvc: '123', name: 'Kenji' };

function cardErrors() {
  const digits = card.num.replace(/\s/g, '');
  const exp = card.exp.replace(/\D/g, '');
  const month = Number(exp.slice(0, 2));
  return {
    num: digits.length < 15 ? 'Enter the full card number.' : '',
    exp: exp.length < 4 || month < 1 || month > 12 ? 'Enter a valid expiry date.' : '',
    cvc: card.cvc.length < 3 ? 'Enter the 3-digit code.' : '',
    name: card.name.trim() ? '' : 'Enter the name on the card.',
  };
}

document.addEventListener('input', (e) => {
  const el = e.target;
  if (!(el instanceof HTMLInputElement) || !CARD_TYPE[el.dataset.card]) return;
  el.value = card[el.dataset.card] = CARD_TYPE[el.dataset.card](el.value);
});

async function startPay(ctx, rid) {
  const st = ctx.state;
  st.working = true;
  ctx.render();
  try {
    const p = await api.startPayment(rid, st.method);
    ctx.go(`#/pay/${rid}/${p.paymentId}`);
  } catch (e) {
    st.working = false;
    if (e.code === 'PROVIDER_UNAVAILABLE') {
      st.h = await api.getHold(rid);
      keepMethod(st);
    } else if (e.code === 'HOLD_NOT_ACTIVE' || e.code === 'PAYMENT_IN_PROGRESS') {
      await follow(ctx, await api.getHold(rid));
    } else {
      throw e;
    }
  }
}

async function answer(ctx, rid, pid, approve) {
  ctx.state.working = approve ? 'yes' : 'no';
  ctx.render();
  try {
    await api.answer(pid, approve);
    ctx.go(`#/confirming/${rid}/${pid}`, { replace: true });
  } catch (e) {
    ctx.state.working = ''; // the tap can be repeated safely (FR-18)
    if (e.code !== 'PAYMENT_REPLACED') throw e;
    const moved = await follow(ctx, await api.getHold(rid));
    if (!moved) ctx.go(`#/checkout/${rid}`, { replace: true });
  }
}

/** SCR-14: the provider takes the card, then tells us whether it went through (FR-19). */
async function cardPay(ctx, rid, pid) {
  cardTried = true;
  if (Object.values(cardErrors()).some(Boolean)) {
    ctx.render();
    return;
  }
  await answer(ctx, rid, pid, !card.num.replace(/\s/g, '').endsWith('0002'));
}

async function loadHeld(ctx, rid) {
  const h = await api.getHold(rid);
  if (await follow(ctx, h)) return false;
  ctx.state.h = h;
  keepMethod(ctx.state);
  return true;
}

/** SCR-13a: the QR Kenji scans, beside the bank's own wording. */
function promptPayView(st) {
  const { h, p } = st;
  return html`<section class="screen timed">
    <div class="col">
      <div class="head"><h1 class="title">Pay ${baht(h.totalAmount)}</h1><p class="sub">PromptPay</p></div>
      <div class="payqr">
        <div class="frame"><span class="scan"></span>${qr(`PROMPTPAY|${p.paymentId}|${h.totalAmount}`, 208)}</div>
        <div class="say"><b>Scan with your banking app</b>
          <p>Open any Thai banking app, scan this code and confirm ${baht(h.totalAmount)}.</p>
          <span>This code expires with your hold</span></div>
      </div>
    </div>
    <aside class="side">
      ${countdown(h.expiresAt)}
      ${button({ label: 'I have paid', action: 'approve', working: st.working ? 'Confirming…' : '' })}
      <button class="textlink" data-action="go" data-to="#/checkout/${h.reservationId}">Use another way to pay</button>
      <div class="card note"><b>Do not close this page.</b><p>Your tickets are confirmed as soon as the bank answers.</p></div>
    </aside>
  </section>`;
}

/** SCR-14a: the provider's own page. Card numbers never reach our server (CON-03). */
function cardView(st) {
  const h = st.h;
  const err = cardTried ? cardErrors() : {};
  const off = st.working ? raw('disabled') : '';
  const field = (f, label, placeholder, numeric = true) => html`<label class="fld"><span>${label}</span>
    <input data-card="${f}" value="${card[f]}" placeholder="${placeholder}" ${numeric ? raw('inputmode="numeric"') : ''} ${off} class="${err[f] ? 'bad' : ''}">
    ${err[f] ? html`<span class="err">${err[f]}</span>` : ''}</label>`;
  return html`<section class="screen timed">
    <div class="col">
      <div class="head"><h1 class="title">Pay ${baht(h.totalAmount)}</h1><p class="sub">Credit or debit card</p></div>
      <div class="paycard">
        <div class="chead"><span class="logo"><i></i>PayCard</span><span>Card provider · simulated</span></div>
        <div class="cbody">
          ${field('num', 'Card number', '1234 1234 1234 1234')}
          <div class="three">
            ${field('exp', 'Expiry', 'MM / YY')}
            ${field('cvc', 'CVC', '123')}
            ${field('name', 'Name on card', 'Kenji Tanaka', false)}
          </div>
          <div class="act">
            <button class="textlink" data-action="fillTest">Use test card</button>
            ${button({ label: `Pay ${baht(h.totalAmount)}`, action: 'pay', working: st.working ? 'Processing…' : '' })}
          </div>
          <p class="note">This is a simulated provider for the project demo. No real card is used. Card numbers ending in 0002 are declined.</p>
        </div>
      </div>
    </div>
    <aside class="side">${countdown(h.expiresAt)}</aside>
  </section>`;
}

/** SCR-13b / SCR-14b: the bank said no. The seats are still held, so Kenji picks again. */
function declinedView(st) {
  const { h, p } = st;
  return html`<section class="screen timed">
    <div class="col">
      <div class="head"><h1 class="title">Pay ${baht(h.totalAmount)}</h1>
        <p class="sub">${p.providerKey === 'promptpay' ? 'PromptPay' : 'Credit or debit card'}</p></div>
      <div class="banner" role="status"><b>The payment did not go through.</b>
        <p>You were not charged. Your seats are still held for <span data-countdown="${h.expiresAt}"><span data-cd-text>${mmss(h.expiresAt - serverNow())}</span></span>.</p></div>
      <span class="label">How do you want to pay</span>
      ${methodRows(h, st)}
    </div>
    <aside class="side">${countdown(h.expiresAt)}${payFooter(h, st, allDown(h), false)}</aside>
  </section>`;
}

async function checkPay(ctx, rid, pid) {
  const p = await api.payment(pid);
  if (p.state === 'CAPTURED') ctx.go(`#/confirmed/${p.orderId}`, { replace: true });
  else if (p.state === 'DECLINED') ctx.go(`#/pay/${rid}/${pid}`, { replace: true });
  else if (p.state === 'VOIDED') ctx.go(`#/expired/${rid}`, { replace: true });
}

export const screens = [
  {
    id: 'checkout',
    pattern: /^\/checkout\/([^/]+)$/,
    enter: (ctx, rid) => loadHeld(ctx, rid),
    poll: { every: 5000, run: (ctx, rid) => loadHeld(ctx, rid) },
    onZero: atZero,
    view(st) {
      const h = st.h;
      const down = allDown(h);
      return html`<section class="screen timed">
        <div class="col">
          <div class="head"><h1 class="title">Pay ${baht(h.totalAmount)}</h1>
            <p class="sub">${h.quantity} × ${h.tierName} for ${h.event.name.replace(/ Bangkok$/, '')}, ${dateShort(h.event.eventDateTime)}</p></div>
          ${down ? banner('Payment is not available right now.', 'Your seats are still held. Stay on this page, we will try again by ourselves.') : ''}
          ${orderSummary(h)}
          <span class="label">How do you want to pay</span>
          ${methodRows(h, st)}
        </div>
        <aside class="side">
          ${countdown(h.expiresAt)}
          ${payFooter(h, st, down, false)}
        </aside>
      </section>`;
    },
    actions: {
      choose(ctx, data) { ctx.state.method = data.key; },
      pay: (ctx, data, rid) => startPay(ctx, rid),
    },
  },
  {
    id: 'pay',
    pattern: /^\/pay\/([^/]+)\/([^/]+)$/,
    async enter(ctx, rid, pid) {
      if (!(await loadHeld(ctx, rid))) return;
      ctx.state.p = await api.payment(pid);
      Object.assign(card, { num: '', exp: '', cvc: '', name: '' });
      cardTried = false;
    },
    onZero: atZero,
    view(st) {
      if (st.p.state === 'DECLINED') return declinedView(st);
      return st.p.providerKey === 'promptpay' ? promptPayView(st) : cardView(st);
    },
    actions: {
      approve: (ctx, data, rid, pid) => answer(ctx, rid, pid, true),
      fillTest(ctx) { Object.assign(card, TEST_CARD); cardTried = false; },
      pay(ctx, data, rid, pid) {
        const p = ctx.state.p;
        return p.providerKey === 'card' && p.state === 'PENDING' ? cardPay(ctx, rid, pid) : startPay(ctx, rid);
      },
      choose(ctx, data) { ctx.state.method = data.key; },
    },
  },
  {
    id: 'confirming',
    pattern: /^\/confirming\/([^/]+)\/([^/]+)$/,
    async enter(ctx, rid, pid) {
      ctx.state.since = Date.now();
      ctx.state.h = await api.getHold(rid);
      await checkPay(ctx, rid, pid);
    },
    poll: { every: 1000, run: (ctx, rid, pid) => checkPay(ctx, rid, pid) },
    view(st) {
      const slow = Date.now() - st.since > 8000;
      return html`<section class="screen timed">
        <div class="col">
          <div class="confirm">
            <span class="dual" role="img" aria-label="Waiting"><i></i><i></i></span>
            <h1 class="status-title">Confirming your payment</h1>
            <p class="status-text">${slow
              ? 'The bank is taking longer than usual. We are still checking. You will not be charged twice.'
              : 'Waiting for the bank. This usually takes a few seconds.'}</p>
          </div>
        </div>
        <aside class="side">
          ${countdown(st.h.expiresAt)}
          <div class="card note"><b>Do not close this page.</b></div>
        </aside>
      </section>`;
    },
  },
  {
    id: 'expired',
    pattern: /^\/expired\/([^/]+)$/,
    async enter(ctx, rid) { ctx.state.h = await api.getHold(rid); },
    view(st) {
      const h = st.h;
      const what = h.quantity === 1 ? `${h.tierName} seat` : `${WORDS[h.quantity].toLowerCase()} ${h.tierName} seats`;
      return statusScreen({
        tone: 'stop',
        icon: '⊘',
        title: 'You have not been charged',
        text: `Your 10 minutes ran out before the payment finished, so the ${what} went back on sale. No money left your account.`,
        card: ['You can try again.', 'Joining the queue again puts you at the back.'],
        primary: againButton(st),
        secondary: backButton(h),
      });
    },
    actions: { again: (ctx) => joinAgain(ctx, ctx.state.h.event.eventId) },
  },
];
