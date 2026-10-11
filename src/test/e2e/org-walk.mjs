#!/usr/bin/env node
// Screenshot walk for the organizer console. Drives the fake through the web API as Chai, then photographs
// one screen per shot into target/shots-org/<name>.png at 1440 x 900. Compare each with design/Organizer Console.dc.html.
//
//   node src/test/e2e/org-walk.mjs              every shot
//   node src/test/e2e/org-walk.mjs 30a 34b      only these shots
//
// The server must be running (mvn -q compile exec:java). This resets its data.
import { execFileSync } from 'node:child_process';
import { mkdirSync } from 'node:fs';

const BASE = process.env.BASE || 'http://localhost:7070';
const T = 'the-1975';
mkdirSync('target/shots-org', { recursive: true });

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
async function shoot(name, hash, wait = 1500) {
  await call('kenji', 'POST', '/dev/speed', { times: 0 });
  execFileSync('npx', ['-y', 'playwright@1.64.0', 'screenshot', '--viewport-size=1440,900', `--wait-for-timeout=${wait}`,
    `${BASE}/organizer.html?as=chai#${hash}`, `target/shots-org/${name}.png`], { stdio: 'inherit' });
}

const chai = (method, path, body) => call('chai', method, path, body);
const fresh = () => dev.reset();
async function open() { await dev.reset(); await dev.advance(60); }

/** A draft event with details only (the state SCR-32a shows). */
async function draft() {
  await fresh();
  return chai('POST', '/api/org/events', {
    name: 'Test Night Bangkok', venue: 'Impact Arena',
    eventDateTime: Date.parse('2026-11-14T19:00:00+07:00'),
    saleOpensAt: Date.parse('2026-11-01T10:00:00+07:00'),
    admitPerSecond: 12,
  });
}
/** The same draft with the three story tiers. */
async function withTiers() {
  const e = await draft();
  await chai('PUT', `/api/org/events/${e.eventId}/tiers`, {
    tiers: [
      { name: 'VIP', price: 6500, capacity: 500 },
      { name: 'Zone A', price: 3800, capacity: 2000 },
      { name: 'Zone B', price: 1900, capacity: 5000 },
    ],
  });
  return e;
}

const shots = {
  '30a': async () => { await fresh(); await shoot('30a', '/events'); },
  '31a': async () => { await fresh(); await shoot('31a', '/new'); },
  '32a': async () => { const e = await draft(); await shoot('32a', `/event/${e.eventId}/tiers`); },
  '33a': async () => { const e = await withTiers(); await shoot('33a', `/event/${e.eventId}/review`); },
  '33b': async () => { const e = await draft(); await shoot('33b', `/event/${e.eventId}/review`); },
  '34a': async () => { const e = await draft(); await shoot('34a', `/event/${e.eventId}`); },
  '34b': async () => {
    const e = await withTiers();
    await chai('POST', `/api/org/events/${e.eventId}/publish`);
    await shoot('34b', `/event/${e.eventId}/published`);
  },
  '34c': async () => { await open(); await shoot('34c', `/event/${T}`); },
  '34d': async () => {
    await open();
    const token = (await call('kenji', 'POST', `/api/events/${T}/queue`)).token;
    await dev.advance(350);
    await call('kenji', 'POST', '/api/holds', { token, tierId: 'vip', quantity: 2 });
    await chai('POST', `/api/org/events/${T}/close`);
    await shoot('34d', `/event/${T}`);
  },
  '34e': async () => { await open(); await dev.scenario('SALE_CLOSED'); await shoot('34e', `/event/${T}`); },
  '34f': async () => { await fresh(); await dev.scenario('EVENT_CANCELLED'); await shoot('34f', `/event/${T}`); },
  '40a': async () => { await fresh(); await dev.advance(380); await shoot('40a', `/event/${T}/live`); },
  '40c': async () => { await open(); await dev.scenario('SALE_CLOSED'); await shoot('40c', `/event/${T}/live`); },
};

const only = new Set(process.argv.slice(2));
for (const [name, fn] of Object.entries(shots)) {
  if (only.size === 0 || only.has(name)) {
    await fn();
    console.log('shot', name);
  }
}
