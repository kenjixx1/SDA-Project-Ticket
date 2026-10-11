// SCR-01 Events (a list, b no events), SCR-02 Tickets, SCR-03 Tickets empty, SCR-04 Account.
import { api, serverNow } from '../api.js';
import { html, baht, time24, dateShort, dayMonth, dayNum, monUpper, sameDay, sheet } from '../parts.js';

function pill(e) {
  if (e.state === 'CLOSED') return html`<span class="pill stop">SALE CLOSED</span>`;
  if (e.soldOut) return html`<span class="pill stop">SOLD OUT</span>`;
  if (e.state === 'ON_SALE') return html`<span class="pill">ON SALE NOW</span>`;
  if (sameDay(e.saleOpensAt, serverNow())) return html`<span class="pill">ON SALE TODAY</span>`;
  return html`<span class="pill grey">OPENS ${dayMonth(e.saleOpensAt).toUpperCase()}</span>`;
}

function heroLine(e) {
  if (e.state === 'ON_SALE' && !e.soldOut) return `On sale now, from ${baht(e.fromPrice)}`;
  return `Sale opens ${time24(e.saleOpensAt)}, from ${baht(e.fromPrice)}`;
}

// The search shows and hides the cards that are already in the page, so the keyboard keeps
// its place in the box. The input, the labels and the cards are found by data attributes.
function applySearch(input) {
  const q = input.value.trim().toLowerCase();
  const searching = q.length > 0;
  const hero = document.querySelector('[data-hero]');
  const box = input.closest('[data-searchbox]');
  const also = document.querySelector('[data-also]');
  const results = document.querySelector('[data-results]');
  const none = document.querySelector('[data-none]');
  if (!hero || !box || !also || !results || !none) return;
  let hits = 0;
  document.querySelectorAll('[data-searchcard]').forEach((card) => {
    const hit = card.dataset.text.includes(q);
    if (searching && hit) hits += 1;
    card.hidden = searching ? !hit : card.dataset.first === '1';
  });
  hero.hidden = searching;
  also.hidden = searching;
  results.hidden = !searching;
  results.textContent = hits === 1 ? '1 RESULT' : `${hits} RESULTS`;
  none.hidden = !searching || hits > 0;
  none.querySelector('[data-none-title]').textContent = `No events match “${input.value.trim()}”`;
  box.classList.toggle('on', searching);
  box.querySelector('[data-clear]').hidden = !searching;
}
document.addEventListener('input', (e) => {
  const input = e.target.closest('[data-search]');
  if (input) applySearch(input);
});
document.addEventListener('click', (e) => {
  const clear = e.target.closest('[data-clear]');
  if (!clear) return;
  const input = clear.closest('[data-searchbox]').querySelector('[data-search]');
  input.value = '';
  applySearch(input);
  input.focus();
});

function orderRow(o) {
  const tag = o.past
    ? html`<span class="pill grey">PAST</span>`
    : o.eventState === 'CANCELLED'
      ? html`<span class="pill stop">CANCELLED</span>`
      : html`<span class="pill">${o.quantity} · ${o.tierName}</span>`;
  return html`<button class="orow ${o.past ? 'past' : ''}" data-action="go" data-to="#/ticket/${o.orderId}/1">
    <span class="date"><b>${dayNum(o.event.eventDateTime)}</b><span>${monUpper(o.event.eventDateTime)}</span></span>
    <span class="what"><b>${o.event.name}</b><span>${o.event.venue} · ${time24(o.event.eventDateTime)}</span></span>
    ${tag}${o.past ? '' : html`<span class="show">Show ticket<span class="arr">›</span></span>`}
  </button>`;
}

export const screens = [
  {
    id: 'events',
    pattern: /^\/events$/,
    async enter(ctx) { ctx.state.events = await api.events(); },
    view(st) {
      const events = st.events;
      if (!events.length) {
        return html`<section class="screen wide">
          <div class="head"><div class="titles"><h1 class="h1">Events</h1><p class="under">Bangkok</p></div></div>
          <div class="blank tall"><span class="dash"></span><b>Nothing on sale right now</b>
            <p class="sub">New events show up here as soon as they are announced.</p></div>
        </section>`;
      }
      const hero = events[0];
      return html`<section class="screen wide">
        <div class="head">
          <div class="titles"><h1 class="h1">Events</h1><p class="under">Bangkok</p></div>
          <div class="sbox" data-searchbox><span class="glass"></span>
            <input data-search placeholder="Search events or venues" aria-label="Search events or venues">
            <button class="clear" data-clear hidden aria-label="Clear search">&times;</button></div>
        </div>
        <button class="feat" data-hero data-action="go" data-to="#/event/${hero.eventId}">
          <span class="feat-img">${hero.artwork ? html`<img src="${hero.artwork}" alt="">` : html`<span class="noimg">${hero.name}</span>`}</span>
          <span class="feat-shade"></span><span class="feat-sheen"></span>
          <span class="feat-cta">View event<span class="arr">→</span></span>
          <span class="feat-txt">${pill(hero)}
            <span class="feat-name">${hero.shortName}<br>BANGKOK</span>
            <span class="feat-meta">${hero.venue} · ${dateShort(hero.eventDateTime)}</span>
            <span class="feat-line">${heroLine(hero)}</span></span>
        </button>
        <span class="sect also" data-also>ALSO ON SALE</span>
        <span class="sect" data-results hidden></span>
        <div class="none" data-none hidden><span class="dash"></span><b data-none-title></b>
          <p class="sub">Try an artist or a venue name.</p></div>
        <div class="egrid">${events.map((e, i) => {
          const text = `${e.name} ${e.venue}`.toLowerCase();
          return html`<button class="ecard" data-searchcard data-text="${text}" ${i === 0 ? html`data-first="1" hidden` : ''} data-action="go" data-to="#/event/${e.eventId}">
            <span class="ecard-img">${e.artwork ? html`<img src="${e.artwork}" alt="">` : html`<span class="noimg">${e.name}</span>`}</span>
            <span class="ecard-body"><span class="ecard-name">${e.name}</span>
              <span class="ecard-meta">${e.venue} · ${dayMonth(e.eventDateTime)}</span>
              <span class="ecard-price">From ${baht(e.fromPrice)}</span></span>
          </button>`;
        })}</div>
      </section>`;
    },
  },
  {
    id: 'tickets',
    pattern: /^\/tickets$/,
    async enter(ctx) { ctx.state.orders = await api.orders(); },
    view(st) {
      if (!st.orders.length) {
        return html`<section class="screen mid">
          <h1 class="h1">Tickets</h1>
          <div class="blank mid"><span class="dash"></span><b>No tickets yet</b>
            <p class="sub">Tickets you buy show up here, ready to scan at the gate.</p>
            <button class="btn" data-action="go" data-to="#/events">Browse events</button></div>
        </section>`;
      }
      const up = st.orders.filter((o) => !o.past);
      const past = st.orders.filter((o) => o.past);
      const upcomingEvents = new Set(up.map((o) => o.event.eventId)).size;
      return html`<section class="screen mid">
        <h1 class="h1">Tickets</h1>
        <p class="under">${up.length ? `${upcomingEvents} upcoming` : 'No upcoming events'}</p>
        ${up.length ? html`<span class="sect gap">UPCOMING</span>${up.map(orderRow)}` : ''}
        ${past.length ? html`<span class="sect gap">PAST</span>${past.map(orderRow)}` : ''}
      </section>`;
    },
  },
  {
    id: 'account',
    pattern: /^\/account$/,
    async enter(ctx) { ctx.state.me = await api.me(); },
    view(st) {
      const me = st.me;
      return html`<section class="screen narrow">
        <h1 class="h1">Account</h1>
        <div class="me"><span class="avatar">${me.name[0]}</span>
          <div class="meinfo"><b>${me.name}</b><span>${me.maskedEmail}</span></div></div>
        <button class="arow">Help<span class="chev">›</span></button>
        <button class="arow stop" data-action="askSignOut">Sign out</button>
      </section>
      ${st.sheet ? sheet({ title: 'Sign out?', close: 'cancel', buttons: [
        { label: 'Cancel', action: 'cancel', quiet: true },
        { label: 'Sign out', action: 'signOut', danger: true },
      ] }) : ''}`;
    },
    actions: {
      askSignOut(ctx) { ctx.state.sheet = true; },
      cancel(ctx) { ctx.state.sheet = false; },
      signOut(ctx) { ctx.state.sheet = false; ctx.go('#/events'); }, // no sign-in exists (ASM-08)
    },
  },
];
