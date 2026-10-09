// SCR-12 Checkout (a default, b promotion, c one method down, d payment unavailable),
// SCR-13 Pay by PromptPay (a waiting, b declined), SCR-14 Pay by card, simulated (a waiting, b declined),
// SCR-15 Confirming payment (a normal, b bank is slow), SCR-18 Expired while paying.
import { api, serverNow } from '../api.js';
import { html, raw, baht, dateShort, mmss, button, banner, countdown, orderSummary, qr, statusScreen, WORDS } from '../parts.js';
import { follow, atZero, againButton, backButton } from './hold.js';
import { joinAgain } from './queue.js';

const allDown = (h) => h.paymentMethods.every((m) => !m.available);

function methodRows(h, st) {
  return h.paymentMethods.map((m) => {
    const sel = st.method === m.key;
    return html`<button class="pick method ${sel ? 'sel' : ''}" data-action="choose" data-key="${m.key}" ${m.available && !st.working ? '' : raw('disabled')}>
      <span class="tile">${m.tile}</span><span class="name">${m.label}</span><span></span>
      <span class="left ${m.available ? '' : 'out'}">${m.available ? m.line : 'Not available right now'}</span>
      ${sel ? html`<span class="check" aria-label="Selected">✓</span>` : html`<span></span>`}
    </button>`;
  });
}

function payFooter(h, st, down) {
  return html`<div class="foot">
    ${button({ label: `Pay ${baht(h.totalAmount)}`, action: 'pay', disabled: !st.method || down, working: st.working ? 'Paying…' : '' })}
    ${down ? html`<p class="hint">Payment is not available right now</p>` : st.method ? '' : html`<p class="hint">Choose how to pay</p>`}
  </div>`;
}

/** Keeps the chosen method only while it is still available. */
function keepMethod(st) {
  if (st.method && !st.h.paymentMethods.some((m) => m.key === st.method && m.available)) st.method = null;
}

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
  await api.answer(pid, approve);
  ctx.go(`#/confirming/${rid}/${pid}`, { replace: true });
}

async function loadHeld(ctx, rid) {
  const h = await api.getHold(rid);
  if (await follow(ctx, h)) return false;
  ctx.state.h = h;
  keepMethod(ctx.state);
  return true;
}

function promptPayView(st) {
  const { h, p } = st;
  return html`<section class="screen">
    <div><h1 class="title sm">Pay ${baht(h.totalAmount)}</h1><p class="sub">PromptPay</p></div>
    ${countdown(h.expiresAt)}
    <div class="qr">${qr(`PROMPTPAY|${p.paymentId}|${h.totalAmount}`, 220)}</div>
    <p class="hint">This code expires with your hold</p>
    <div class="card"><b>Do not close this page.</b><p>Your tickets are confirmed as soon as the bank answers.</p></div>
  </section>
  <div class="foot">
    ${button({ label: 'I have paid', action: 'approve', working: st.working ? 'Confirming…' : '' })}
    <button class="textlink" data-action="go" data-to="#/checkout/${h.reservationId}">Use another way to pay</button>
  </div>`;
}

function cardView(st) {
  const h = st.h;
  const off = st.working ? raw('disabled') : '';
  return html`<section class="screen">
    ${countdown(h.expiresAt)}
    <div class="provider">
      <span class="brand">Card provider · simulated</span>
      <span class="amt">Amount ${baht(h.totalAmount)}</span>
      <p>Merchant: Event Ticket &amp; Queue</p>
      <p>This is a simulated provider for the project demo. No real card is used.</p>
      <button class="btn ok" data-action="approve" ${off}>${st.working === 'yes' ? 'Approving…' : 'Approve payment'}</button>
      <button class="btn no" data-action="decline" ${off}>${st.working === 'no' ? 'Declining…' : 'Decline payment'}</button>
    </div>
  </section>`;
}

function declinedView(st) {
  const { h, p } = st;
  return html`<section class="screen">
    <div><h1 class="title sm">Pay ${baht(h.totalAmount)}</h1><p class="sub">${p.providerKey === 'promptpay' ? 'PromptPay' : 'Credit or debit card'}</p></div>
    <div class="banner" role="status"><b>The payment did not go through.</b>
      <p>You were not charged. Your seats are still held for <span data-countdown="${h.expiresAt}"><span data-cd-text>${mmss(h.expiresAt - serverNow())}</span></span>.</p></div>
    ${countdown(h.expiresAt)}
    <span class="label">How do you want to pay</span>
    ${methodRows(h, st)}
  </section>
  ${payFooter(h, st, allDown(h))}`;
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
      return html`<section class="screen">
        <div><h1 class="title sm">Pay ${baht(h.totalAmount)}</h1>
          <p class="sub">${h.quantity} × ${h.tierName} for ${h.event.name}, ${dateShort(h.event.eventDateTime)}</p></div>
        ${down ? banner('Payment is not available right now.', 'Your seats are still held. Stay on this page, we will try again by ourselves.') : ''}
        ${countdown(h.expiresAt)}
        ${orderSummary(h)}
        <span class="label">How do you want to pay</span>
        ${methodRows(h, st)}
      </section>
      ${payFooter(h, st, down)}`;
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
    },
    onZero: atZero,
    view(st) {
      if (st.p.state === 'DECLINED') return declinedView(st);
      return st.p.providerKey === 'promptpay' ? promptPayView(st) : cardView(st);
    },
    actions: {
      approve: (ctx, data, rid, pid) => answer(ctx, rid, pid, true),
      decline: (ctx, data, rid, pid) => answer(ctx, rid, pid, false),
      choose(ctx, data) { ctx.state.method = data.key; },
      pay: (ctx, data, rid) => startPay(ctx, rid),
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
      return html`<section class="screen center">
        <div class="spinner" role="img" aria-label="Waiting"></div>
        <h1 class="status-title">Confirming your payment</h1>
        <p class="status-text">${slow
          ? 'The bank is taking longer than usual. We are still checking. You will not be charged twice.'
          : 'Waiting for the bank. This usually takes a few seconds.'}</p>
        ${countdown(st.h.expiresAt)}
        <div class="card" style="width:100%;text-align:left"><b>Do not close this page.</b></div>
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
