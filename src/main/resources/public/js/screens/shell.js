// SCR-01 Events (a list, b no events), SCR-02 Tickets, SCR-03 Tickets empty, SCR-04 Account. Tab bar visible.
import { api, serverNow } from '../api.js';
import { html, baht, time24, dateShort, dayMonth, dayNum, monUpper, sameDay, tabBar, sheet } from '../parts.js';

function pill(e) {
  if (e.state === 'CLOSED') return html`<span class="pill stop">SALE CLOSED</span>`;
  if (e.soldOut) return html`<span class="pill stop">SOLD OUT</span>`;
  if (e.state === 'ON_SALE') return html`<span class="pill">ON SALE NOW</span>`;
  if (sameDay(e.saleOpensAt, serverNow())) return html`<span class="pill">ON SALE TODAY</span>`;
  return html`<span class="pill grey">OPENS ${dayMonth(e.saleOpensAt).toUpperCase()}</span>`;
}

function orderCard(o) {
  const tag = o.past
    ? html`<span class="pill grey">PAST</span>`
    : o.eventState === 'CANCELLED'
      ? html`<span class="pill stop">CANCELLED</span>`
      : html`<span class="pill">${o.quantity} · ${o.tierName}</span>`;
  return html`<button class="ocard ${o.past ? 'past' : ''}" data-action="go" data-to="#/ticket/${o.orderId}/1">
    <div class="date"><b>${dayNum(o.event.eventDateTime)}</b><span>${monUpper(o.event.eventDateTime)}</span></div>
    <div class="what"><b>${o.event.name}</b><span>${o.event.venue} · ${time24(o.event.eventDateTime)}</span></div>
    ${tag}
  </button>`;
}

export const screens = [
  {
    id: 'events',
    pattern: /^\/events$/,
    async enter(ctx) { ctx.state.events = await api.events(); },
    view(st) {
      const [f, ...rest] = st.events;
      if (!f) {
        return html`<section class="screen tabbed"><div><h1 class="title">Events</h1><p class="sub">Bangkok</p></div>
          <div class="empty"><div class="ring"></div><b>Nothing on sale right now</b>
            <p class="sub">New events show up here as soon as they are announced.</p></div>
        </section>${tabBar('events')}`;
      }
      return html`<section class="screen tabbed">
        <div><h1 class="title">Events</h1><p class="sub">Bangkok</p></div>
        <button class="feature" data-action="go" data-to="#/event/${f.eventId}">
          <img src="${f.artwork}" alt=""><div class="shade"></div>
          <div class="txt">${pill(f)}<h2>${f.name}</h2>
            <span class="meta">${f.venue} · ${dateShort(f.eventDateTime)}</span>
            <span class="from">Sale opens ${time24(f.saleOpensAt)}, from ${baht(f.fromPrice)}</span></div>
        </button>
        ${rest.length ? html`<span class="label" style="margin-top:12px">Also on sale</span>
          <div>${rest.map((e) => html`<button class="erow" data-action="go" data-to="#/event/${e.eventId}">
            <div class="who"><img class="thumb" src="${e.artwork}" alt=""><div><b>${e.name}</b><span>${e.venue} · ${dayMonth(e.eventDateTime)}</span></div></div>
            <span class="p ${e.soldOut ? 'stop' : ''}">${e.soldOut ? 'Sold out' : baht(e.fromPrice)}</span></button>`)}</div>` : ''}
      </section>${tabBar('events')}`;
    },
  },
  {
    id: 'tickets',
    pattern: /^\/tickets$/,
    async enter(ctx) { ctx.state.orders = await api.orders(); },
    view(st) {
      if (!st.orders.length) {
        return html`<section class="screen tabbed"><h1 class="title">Tickets</h1>
          <div class="empty"><div class="ring"></div><b>No tickets yet</b>
            <p class="sub">Tickets you buy show up here, ready to scan at the gate.</p>
            <button class="btn" style="width:auto;padding:0 22px" data-action="go" data-to="#/events">Browse events</button></div>
        </section>${tabBar('tickets')}`;
      }
      const up = st.orders.filter((o) => !o.past);
      const past = st.orders.filter((o) => o.past);
      const upcomingEvents = new Set(up.map((o) => o.event.eventId)).size;
      return html`<section class="screen tabbed">
        <div><h1 class="title">Tickets</h1><p class="sub">${upcomingEvents} upcoming</p></div>
        ${up.length ? html`<span class="label">Upcoming</span>${up.map(orderCard)}` : ''}
        ${past.length ? html`<span class="label">Past</span>${past.map(orderCard)}` : ''}
      </section>${tabBar('tickets')}`;
    },
  },
  {
    id: 'account',
    pattern: /^\/account$/,
    async enter(ctx) { ctx.state.me = await api.me(); },
    view(st) {
      const me = st.me;
      return html`<section class="screen tabbed">
        <h1 class="title">Account</h1>
        <div class="who"><div class="avatar">${me.name[0]}</div>
          <div><b style="font-size:18px">${me.name}</b><p class="sub mono" style="font-size:13px">${me.maskedEmail}</p></div></div>
        <div class="menu"><button>Help</button></div>
        <div class="menu"><button class="stop" data-action="askSignOut">Sign out</button></div>
      </section>${tabBar('account')}
      ${st.sheet ? sheet({ title: 'Sign out?', buttons: [
        { label: 'Sign out', action: 'signOut', danger: true },
        { label: 'Cancel', action: 'cancel', quiet: true },
      ] }) : ''}`;
    },
    actions: {
      askSignOut(ctx) { ctx.state.sheet = true; },
      cancel(ctx) { ctx.state.sheet = false; },
      signOut(ctx) { ctx.state.sheet = false; ctx.go('#/events'); }, // no sign-in exists (ASM-08)
    },
  },
];
