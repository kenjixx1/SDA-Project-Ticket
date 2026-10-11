#!/usr/bin/env node
// Screenshot walk. Drives the fake through the web API, then photographs one screen per shot
// into target/shots-web/<name>.png at 1440 x 900. Compare each with design/Ticket App Web.dc.html.
//
//   node src/test/e2e/walk.mjs              every shot
//   node src/test/e2e/walk.mjs 06a 06b      only these shots
//   node src/test/e2e/walk.mjs prep 08e     set up a hand check and print the address to open
//   node src/test/e2e/walk.mjs then 08e     second half of a hand check
//
// The server must be running (mvn -q compile exec:java). This resets its data.
import { execFileSync } from 'node:child_process';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';

const BASE = process.env.BASE || 'http://localhost:7070';
const T = 'the-1975';
mkdirSync('target/shots-web', { recursive: true });

async function call(as, method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'X-Customer': as, 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json();
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${JSON.stringify(json.error)}`);
  return json.data;
}

const dev = {
  reset: () => call('kenji', 'POST', '/dev/reset'),
  advance: (seconds) => call('kenji', 'POST', '/dev/advance', { seconds }),
  scenario: (scenario) => call('kenji', 'POST', '/dev/scenario', { scenario }),
};

/** Pauses the fake clock first, so nothing moves between the setup and the picture. */
async function shoot(name, as, hash, wait = 1500) {
  await call('kenji', 'POST', '/dev/speed', { times: 0 });
  execFileSync('npx', ['-y', 'playwright@1.64.0', 'screenshot', '--viewport-size=1440,900', `--wait-for-timeout=${wait}`,
    `${BASE}/?as=${as}#${hash}`, `target/shots-web/${name}.png`], { stdio: 'inherit' });
}

const fresh = () => dev.reset();
async function open() { await dev.reset(); await dev.advance(60); }
async function join(as) { return (await call(as, 'POST', `/api/events/${T}/queue`)).token; }
async function admitted(as) { const t = await join(as); await dev.advance(350); return t; }
async function held(as, tier = 'vip', q = 2) {
  const token = await admitted(as);
  return call(as, 'POST', '/api/holds', { token, tierId: tier, quantity: q });
}
const startPay = (as, rid, providerKey) => call(as, 'POST', `/api/holds/${rid}/payments`, { providerKey });
const answer = (as, pid, approve) => call(as, 'POST', `/api/payments/${pid}/answer`, { approve });
async function paid(as) {
  const h = await held(as);
  const p = await startPay(as, h.reservationId, 'promptpay');
  await answer(as, p.paymentId, true);
  await dev.advance(3);
  const s = await call(as, 'GET', `/api/payments/${p.paymentId}`);
  return { h, p, orderId: s.orderId };
}

const shots = {
  '01a': async () => { await fresh(); await shoot('01a', 'kenji', '/events'); },
  '02': async () => { await open(); await paid('kenji'); await shoot('02', 'kenji', '/tickets'); },
  '03': async () => { await fresh(); await shoot('03', 'mint', '/tickets'); },
  '04': async () => { await fresh(); await shoot('04', 'kenji', '/account'); },
  '05a': async () => { await fresh(); await shoot('05a', 'kenji', `/event/${T}`); },
  '05b': async () => { await open(); await shoot('05b', 'kenji', `/event/${T}`); },
  '05c': async () => { await open(); await join('kenji'); await shoot('05c', 'kenji', `/event/${T}`); },
  '05d': async () => { await open(); await dev.scenario('SOLD_OUT_ALL'); await shoot('05d', 'kenji', `/event/${T}`); },
  '05e': async () => { await open(); await dev.scenario('SALE_CLOSED'); await shoot('05e', 'kenji', `/event/${T}`); },
  '05f': async () => { await open(); await dev.scenario('EVENT_CANCELLED'); await shoot('05f', 'kenji', `/event/${T}`); },
  '06a': async () => { await open(); const t = await join('kenji'); await shoot('06a', 'kenji', `/queue/${T}/${t}`); },
  '06b': async () => { await open(); const t = await join('kenji'); await dev.advance(345); await shoot('06b', 'kenji', `/queue/${T}/${t}`); },
  '06c': async () => { await open(); const t = await join('kenji'); await shoot('06c', 'kenji', `/queue/${T}/${t}/back`); },
  '07': async () => { await open(); await shoot('07', 'kenji', `/passed/${T}`); },
  '08a': async () => { await open(); const t = await admitted('kenji'); await shoot('08a', 'kenji', `/select/${T}/${t}`); },
  '08d': async () => {
    await open();
    const who = ['kenji', 'a', 'b', 'c', 'd'];
    const tokens = [];
    for (const c of who) tokens.push(await join(c));
    await dev.advance(350);
    for (let i = 1; i < who.length; i++) await call(who[i], 'POST', '/api/holds', { token: tokens[i], tierId: 'vip', quantity: 4 });
    await shoot('08d', 'kenji', `/select/${T}/${tokens[0]}`);
  },
  '08g': async () => { await open(); const t = await admitted('kenji'); await dev.scenario('SOLD_OUT_ALL'); await shoot('08g', 'kenji', `/select/${T}/${t}`); },
  '09a': async () => { await open(); const h = await held('kenji'); await shoot('09a', 'kenji', `/hold/${h.reservationId}`); },
  '10': async () => {
    await open(); const h = await held('kenji');
    await call('kenji', 'DELETE', `/api/holds/${h.reservationId}`);
    await shoot('10', 'kenji', `/released/${h.reservationId}`);
  },
  '11': async () => { await open(); const h = await held('kenji'); await dev.advance(601); await shoot('11', 'kenji', `/ranout/${h.reservationId}`); },
  '12a': async () => { await open(); await dev.advance(1800); const h = await held('kenji'); await shoot('12a', 'kenji', `/checkout/${h.reservationId}`); },
  '12b': async () => { await open(); const h = await held('kenji'); await shoot('12b', 'kenji', `/checkout/${h.reservationId}`); },
  '12c': async () => { await open(); const h = await held('kenji'); await dev.scenario('PROVIDER_DOWN'); await shoot('12c', 'kenji', `/checkout/${h.reservationId}`); },
  '12d': async () => { await open(); const h = await held('kenji'); await dev.scenario('ALL_PROVIDERS_DOWN'); await shoot('12d', 'kenji', `/checkout/${h.reservationId}`); },
  '13a': async () => {
    await open(); const h = await held('kenji'); const p = await startPay('kenji', h.reservationId, 'promptpay');
    await shoot('13a', 'kenji', `/pay/${h.reservationId}/${p.paymentId}`);
  },
  '13b': async () => {
    await open(); const h = await held('kenji'); const p = await startPay('kenji', h.reservationId, 'promptpay');
    await answer('kenji', p.paymentId, false); await dev.advance(3);
    await shoot('13b', 'kenji', `/pay/${h.reservationId}/${p.paymentId}`);
  },
  '14a': async () => {
    await open(); const h = await held('kenji'); const p = await startPay('kenji', h.reservationId, 'card');
    await shoot('14a', 'kenji', `/pay/${h.reservationId}/${p.paymentId}`);
  },
  '14b': async () => {
    await open(); const h = await held('kenji'); const p = await startPay('kenji', h.reservationId, 'card');
    await answer('kenji', p.paymentId, false); await dev.advance(3);
    await shoot('14b', 'kenji', `/pay/${h.reservationId}/${p.paymentId}`);
  },
  '15a': async () => {
    await open(); const h = await held('kenji'); await dev.scenario('BANK_SLOW');
    const p = await startPay('kenji', h.reservationId, 'promptpay'); await answer('kenji', p.paymentId, true);
    await shoot('15a', 'kenji', `/confirming/${h.reservationId}/${p.paymentId}`);
  },
  '15b': async () => {
    await open(); const h = await held('kenji'); await dev.scenario('BANK_SLOW');
    const p = await startPay('kenji', h.reservationId, 'promptpay'); await answer('kenji', p.paymentId, true);
    await shoot('15b', 'kenji', `/confirming/${h.reservationId}/${p.paymentId}`, 9500);
  },
  '16': async () => { await open(); const { orderId } = await paid('kenji'); await shoot('16', 'kenji', `/confirmed/${orderId}`); },
  '17a': async () => { await open(); const { orderId } = await paid('kenji'); await shoot('17a', 'kenji', `/ticket/${orderId}/1`); },
  '17b': async () => {
    await open(); const { orderId } = await paid('kenji'); await dev.scenario('EVENT_CANCELLED');
    await shoot('17b', 'kenji', `/ticket/${orderId}/1`);
  },
  '17c': async () => { await fresh(); await shoot('17c', 'kenji', '/ticket/O-0001/1'); },
  '18': async () => {
    await open(); const h = await held('kenji'); const p = await startPay('kenji', h.reservationId, 'promptpay');
    await dev.advance(599); await answer('kenji', p.paymentId, true); await dev.advance(3);
    await shoot('18', 'kenji', `/expired/${h.reservationId}`);
  },
};

// Hand checks: variants that need a tap at the right moment.
const prep = {
  '08': async () => { await open(); const t = await admitted('kenji'); return `/select/${T}/${t}`; },
  '08e': async () => {
    await open();
    const who = ['kenji', 'a', 'b', 'c', 'd'];
    const tokens = [];
    for (const c of who) tokens.push(await join(c));
    await dev.advance(350);
    writeFileSync('target/shots-web/prep.json', JSON.stringify({ who, tokens }));
    return `/select/${T}/${tokens[0]}`;
  },
  '09': async () => { await open(); const h = await held('kenji'); return `/hold/${h.reservationId}`; },
  '12': async () => { await open(); const h = await held('kenji'); return `/checkout/${h.reservationId}`; },
};
const then = {
  '08e': async () => {
    const { who, tokens } = JSON.parse(readFileSync('target/shots-web/prep.json', 'utf8'));
    for (let i = 1; i <= 3; i++) await call(who[i], 'POST', '/api/holds', { token: tokens[i], tierId: 'vip', quantity: 4 });
    await call(who[4], 'POST', '/api/holds', { token: tokens[4], tierId: 'vip', quantity: 2 });
    console.log('VIP now has 2 left. Tap "Hold these seats" with 3 VIP chosen.');
  },
};

const args = process.argv.slice(2);
if (args[0] === 'prep') {
  const path = await prep[args[1]]();
  console.log(`Open: ${BASE}/?dev#${path}`);
} else if (args[0] === 'then') {
  await then[args[1]]();
} else {
  const only = new Set(args);
  for (const [name, fn] of Object.entries(shots)) {
    if (only.size === 0 || only.has(name)) {
      await fn();
      console.log('shot', name);
    }
  }
}
