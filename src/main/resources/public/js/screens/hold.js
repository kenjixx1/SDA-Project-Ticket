// SCR-09 Seats held (a running, b release sheet), SCR-10 Seats released, SCR-11 Hold ran out.
import { api, serverNow } from '../api.js';
import { html, button, countdown, orderSummary, statusScreen, sheet, seats, mmss, num } from '../parts.js';
import { joinAgain } from './queue.js';

/** Goes to the screen that matches the hold on the server. Returns true when it moved. */
export async function follow(ctx, h) {
  const rid = h.reservationId;
  if (h.state === 'HELD') {
    if (!h.paymentId) return false;
    const p = await api.payment(h.paymentId);
    if (p.state === 'PENDING' && p.answered) {
      ctx.go(`#/confirming/${rid}/${p.paymentId}`, { replace: true });
      return true;
    }
    return false;
  }
  if (h.state === 'EXPIRED') {
    const voided = h.paymentId ? (await api.payment(h.paymentId)).state === 'VOIDED' : false;
    ctx.go(voided ? `#/expired/${rid}` : `#/ranout/${rid}`, { replace: true });
    return true;
  }
  if (h.state === 'RELEASED') {
    ctx.go(`#/released/${rid}`, { replace: true });
    return true;
  }
  const p = await api.payment(h.paymentId); // CONFIRMED
  ctx.go(`#/confirmed/${p.orderId}`, { replace: true });
  return true;
}

/** onZero for every screen with a running countdown: ask the server, then go where it says. */
export async function atZero(ctx, rid) {
  const moved = await follow(ctx, await api.getHold(rid));
  if (!moved) setTimeout(() => { ctx.run.zeroFired = false; }, 2000);
}

export const againButton = (st) => ({ label: 'Join the queue again', action: 'again', working: st.joining ? 'Joining…' : '' });
export const backButton = (h) => ({ label: 'Back to the event', action: 'go', data: { to: `#/event/${h.event.eventId}` } });

const loadHold = async (ctx, rid) => { ctx.state.h = await api.getHold(rid); };
const again = (ctx) => joinAgain(ctx, ctx.state.h.event.eventId);

export const screens = [
  {
    id: 'held',
    pattern: /^\/hold\/([^/]+)$/,
    async enter(ctx, rid) {
      const h = await api.getHold(rid);
      if (await follow(ctx, h)) return;
      ctx.state.h = h;
    },
    onZero: atZero,
    view(st) {
      const h = st.h;
      return html`<section class="screen timed">
        <div class="col">
          <div class="head"><h1 class="title">Seats held</h1><p class="sub">Yours until the timer runs out.</p></div>
          ${orderSummary(h)}
          <div class="card good"><b>${h.tierName} just dropped from ${num(h.remainingBefore)} to ${num(h.remainingAfter)}.</b>
            <p>Your seats left the sale the moment you held them.</p></div>
        </div>
        <aside class="side">
          ${countdown(h.expiresAt)}
          ${button({ label: 'Continue to payment', action: 'go', data: { to: `#/checkout/${h.reservationId}` } })}
          ${button({ label: 'Release these seats', action: 'askRelease', quiet: true })}
        </aside>
      </section>
      ${st.sheet ? sheet({
        title: 'Release these seats?',
        text: [`The ${seats(h.quantity, h.tierName)} go back on sale straight away. To buy again you join the back of the line.`],
        extra: html`<div class="sheet-time" data-countdown="${h.expiresAt}"><span>Time to pay</span><span class="mono" data-cd-text>${mmss(h.expiresAt - serverNow())}</span></div>`,
        buttons: [
          { label: 'Release seats', action: 'release', danger: true, working: st.releasing ? 'Releasing…' : '' },
          { label: 'Keep my seats', action: 'keep' },
        ],
        close: 'keep',
      }) : ''}`;
    },
    actions: {
      askRelease(ctx) { ctx.state.sheet = true; },
      keep(ctx) { ctx.state.sheet = false; },
      async release(ctx, data, rid) {
        ctx.state.releasing = true;
        ctx.render();
        try {
          await api.release(rid);
          ctx.go(`#/released/${rid}`, { replace: true });
        } catch (e) {
          ctx.state.releasing = false;
          ctx.state.sheet = false;
          if (e.code !== 'HOLD_NOT_ACTIVE') throw e;
          await follow(ctx, await api.getHold(rid)); // it ran out or was paid meanwhile
        }
      },
    },
  },
  {
    id: 'released',
    pattern: /^\/released\/([^/]+)$/,
    enter: loadHold,
    view: (st) => statusScreen({
      tone: 'calm',
      icon: '✓',
      title: 'Seats released',
      text: `Your ${seats(st.h.quantity, st.h.tierName)} ${st.h.quantity === 1 ? 'is' : 'are'} back on sale. Nothing was charged.`,
      primary: againButton(st),
      secondary: backButton(st.h),
    }),
    actions: { again },
  },
  {
    id: 'ranout',
    pattern: /^\/ranout\/([^/]+)$/,
    enter: loadHold,
    view: (st) => statusScreen({
      tone: 'stop',
      icon: '⊘',
      title: 'Your 10 minutes ran out',
      text: `The ${seats(st.h.quantity, st.h.tierName)} went back on sale. You were not charged.`,
      card: ['You can try again.', 'Joining again puts you at the back of the line.'],
      primary: againButton(st),
      secondary: backButton(st.h),
    }),
    actions: { again },
  },
];
