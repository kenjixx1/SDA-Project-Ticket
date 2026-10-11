# Core Gateway Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the customer pages and the organizer console run on the real booking engine (`core/`) behind a switch, filling the gaps core has in the gateway, and make 200 per second the admission rate everywhere.

**Architecture:** A new package `eventflow.web.real` holds `RealEngine` (one `EventFlow` plus the gateway's own side tables: drafts, pictures, event state, payments, orders) and two gateways, `CoreBookingGateway` (M-01..M-13) and `CoreOrganizerGateway` (O-01..O-11), that read and write through it. `core/` is not edited. The 6 READY calls go straight to core, the 9 PARTLY calls are completed by side tables, and the 9 MISSING calls use a stand-in or refuse with `409 NOT_IN_CORE`. `WebServer.main` picks the engine from the `ENGINE` environment variable; the fake stays the default.

**Tech Stack:** Java 17 language level, Javalin 6.3.0, Maven, JUnit 5.10.3. Plain ES modules for the pages.

## Global Constraints

- Never edit anything under `src/main/java/eventflow/core/`, `src/main/java/eventflow/admin/`, `src/main/java/eventflow/contract/`, `src/main/java/eventflow/plugin/` or `src/main/java/eventflow/EventFlow.java`. They belong to the teammate.
- New Java code goes in `src/main/java/eventflow/web/real/` (package `eventflow.web.real`), plus `src/main/java/eventflow/web/OrgChecks.java`. Tests go in `src/test/java/eventflow/web/real/`.
- `eventflow.web.real` never imports `eventflow.plugin.*`. It reaches payment methods only through `flow.registry()`.
- The fake engine stays the default. The core engine is used only when `ENGINE=core`.
- Admission rate default is **200** per second everywhere (core's `QueueService.DEFAULT_ADMISSION_RATE`).
- Error code for a call core cannot do: HTTP **409**, code `NOT_IN_CORE`, message `<what> is not built in the booking engine yet.` Never a 5xx: `js/router.js` treats any status ≥ 500 as "offline".
- Card payment stays unavailable on core: `PaymentMethod("card", "CARD", "Credit or debit card", "Visa, Mastercard", false)`. PromptPay is `PaymentMethod("promptpay", "PP", "PromptPay", "Scan with your banking app", true)`.
- Every gateway method that reads or writes the engine runs inside `synchronized (engine) { engine.tick(); ... }`. Only `now()`, `clockRate()` and `me()` skip it.
- Before every commit run `mvn -q test` (web) and `./run.sh test` (core). Both must pass: baseline is 86 web tests and 31 core tests.
- Never `git add -A`. Stage the named paths only.
- Never kill a Java process you did not start. A worktree server uses port 7071 (lane A) or 7072 (lane B), never 7070.
- Commit messages end with:
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`

## Lanes

| Lane | Tasks | Files it owns |
|---|---|---|
| A · rate 200 | Task 1 | `web/fake/FakeBookingGateway.java`; tests `web/ApiContractTest.java`, `web/OrgApiContractTest.java`, `web/fake/FakeQueueTest.java`, `web/fake/FakeBookingTest.java`, `web/fake/FakeOrganizerTest.java`; `public/js/org/wizard.js`, `public/js/org/dialogs.js`; `docs/04-use-cases.md`, `docs/07-glossary.md`, `docs/08-screen-requirements.md`, `docs/10-operation.md` |
| B · core gateway | Tasks 2 → 3 → 4 → 5 | `web/OrgChecks.java`, `web/fake/FakeOrganizerGateway.java`, everything in `web/real/` and `test/.../web/real/` |
| Join | Task 6 | `web/WebServer.java`, `test/.../web/real/CoreApiContractTest.java`, `docs/09-web-api.md`, `docs/10-operation.md` (§10.3 and §10.7 only), `AGENTS.md` (§1 table row 4 only) |

No file is in two lanes. Lane B reads nothing lane A writes. Task 6 runs after both lanes are merged.

---

### Task 1: Admission rate 200 everywhere (lane A)

**Implementer:** deepseek

**Files:**
- Modify: `src/main/java/eventflow/web/fake/FakeBookingGateway.java`
- Modify: `src/test/java/eventflow/web/ApiContractTest.java`, `src/test/java/eventflow/web/OrgApiContractTest.java`, `src/test/java/eventflow/web/fake/FakeQueueTest.java`, `src/test/java/eventflow/web/fake/FakeBookingTest.java`, `src/test/java/eventflow/web/fake/FakeOrganizerTest.java`
- Modify: `src/main/resources/public/js/org/wizard.js`, `src/main/resources/public/js/org/dialogs.js`
- Modify: `docs/04-use-cases.md`, `docs/07-glossary.md`, `docs/08-screen-requirements.md`, `docs/10-operation.md`

**Interfaces:**
- Produces: `FakeBookingGateway.ADMIT_PER_SECOND == 200`. Admission now comes `ceil(4182 × 1000 / 200) = 20,910 ms` after joining.

The numbers below are worked out at 200 per second. Kenji at position 4,182 waits 4,182 ÷ 200 ≈ 21 seconds. The story crowd of 35,768 at 10:00 empties after 179 seconds; at 10:01:00 (60 s after opening) 35,768 − 12,000 = **23,768** are still waiting.

- [ ] **Step 1: Change the constant and its comments**

In `FakeBookingGateway.java`:

```java
    public static final int ADMIT_PER_SECOND = 200;
```

Replace the comment above `int waiting(Event e)`:

```java
    /** People waiting now. The 1975 also has the crowd from the story: 23,768 at 10:01:00 at 200 per second. */
```

- [ ] **Step 2: Run the web tests to see what the new rate breaks**

Run: `mvn -q test`
Expected: FAIL. Tests that wait 350 s for admission now find the place EXPIRED (admitted at 21 s, and the turn lasts 5 minutes).

- [ ] **Step 3: Update the tests**

Exact edits:

1. Every `clock.advance(350_000);` that waits for admission becomes `clock.advance(30_000);`. That is: `ApiContractTest.java` line 47, `OrgApiContractTest.java` line 132, `FakeBookingTest.java` line 37 (inside `admit`), `FakeOrganizerTest.java` lines 311, 372, 453, 496.
   Do **not** change `FakeQueueTest.java` line 80 (`350_000 + FakeBookingGateway.TURN_LIMIT_MS + 1_000`); it tests expiry and still passes.
2. `ApiContractTest.java` line 104: `assertEquals(200, q.get("admitPerSecond").asInt());`
3. `FakeQueueTest.positionMovesAtTheAdmissionRateUntilAdmitted` becomes:

```java
    @Test
    void positionMovesAtTheAdmissionRateUntilAdmitted() {
        clock.advance(60_000);
        var join = gw.joinQueue("kenji", "the-1975");

        clock.advance(10_000);
        var q = gw.queueStatus("kenji", join.token());
        assertEquals("WAITING", q.state());
        assertEquals(4182 - 2000, q.position());
        assertEquals(6823 + 2000, q.moved());

        clock.advance(11_000);
        assertEquals("ADMITTED", gw.queueStatus("kenji", join.token()).state());
    }
```

4. `FakeOrganizerTest.java` line 445: `assertEquals(200, updated.admitPerSecond());`
5. `FakeOrganizerTest.dashboardAddsUp`: the first line becomes `clock.advance(90_000); // 10:01:00, 60 seconds after the sale opened`, `assertEquals(31_208, d.waiting());` becomes `assertEquals(23_768, d.waiting());`, and `assertEquals(12, d.admitPerSecond());` becomes `assertEquals(200, d.admitPerSecond());`. Every other line stays.

Calls that pass `12` explicitly inside `EventDetails(...)` or a JSON body stay `12`: they test that a chosen rate is kept.

- [ ] **Step 4: Run the tests**

Run: `mvn -q test`
Expected: PASS, 86 tests. If another test fails only because of the rate, recompute its number at 200 per second the same way and list it in the report.

- [ ] **Step 5: Update the organizer pages**

`wizard.js` line 8: `rate: '12'` becomes `rate: '200'`.

`wizard.js` line 188, the `<p>` becomes:

```html
        <p>How many people the queue lets in each second. The booking engine starts at 200. Lower it if people get in only to find every seat already held.</p>
```

`dialogs.js` line 247, the comment becomes:

```js
/** "At 200 per second, the 23,768 people now waiting are through in about 2 minutes." */
```

The SCR-38 helper line in `dialogs.js` (`… seats ÷ 600 seconds of hold ≈ … per second …`) stays: it is advice worked out from the event's own numbers.

- [ ] **Step 6: Update the documents**

1. `docs/04-use-cases.md` line 99: `an estimated wait of ~6 minutes` becomes `an estimated wait of about 21 seconds`.
2. `docs/04-use-cases.md` lines 445–451, the whole note becomes:

```markdown
> **Why the admission rate starts at 200 per second.**
> 200 is the booking engine's default (`QueueService.DEFAULT_ADMISSION_RATE`),
> and the team chose to keep it on 11 October 2026. Kenji at position 4,182
> waits about 21 seconds. The risk of a high rate: people can be let in faster
> than seats free up, and find every seat already held by someone still inside
> their ten minutes. Chai can lower it in SCR-38. The helper there works out
> the careful number: 7,500 seats ÷ 600 seconds of hold ≈ 12 per second.
```

3. `docs/07-glossary.md` lines 529–530: the example becomes `**Example:** "People let in, then told the seats just went → lower the admission rate in SCR-38" (`10-operation.md` §10.5).` Line 39 already says 200 and stays.
4. `docs/08-screen-requirements.md`:
   - line 813: `default **12** | *12*` becomes `default **200** | *200*`.
   - line 884: `*12 per second*` becomes `*200 per second*`.
   - line 943: `` `[D]` *12* `` becomes `` `[D]` *200* ``.
   - lines 947–948: `*At 12 per second, the 31,208 people now waiting are through in about 43 minutes.*` becomes `*At 200 per second, the 23,768 people now waiting are through in about 2 minutes.*`
   - line 1083, the row becomes: `| 8 | `07-glossary.md`, `02-requirements.md` NFR-01 | admission rate 200 per second | **200 per second** (was 12 until 11 Oct 2026) | The booking engine's default. The team kept it. SCR-38's helper still shows the careful number, seats ÷ 600 s. |`
   - Everything else in `docs/08` stays, including the screen example *about 6 min* (it is design sample data).
5. `docs/10-operation.md`:
   - lines 13–14: `31,208 people are waiting by 10:06:20, the queue lets in 12 per second` becomes `23,768 people are still waiting at 10:01:00, the queue lets in 200 per second`.
   - line 91: `set 12 per second` becomes `keep 200 per second`.
   - line 96: `Position 4,182, about 6 minutes` becomes `Position 4,182, about 21 seconds`.
   - line 98: `| 10:06 |` becomes `| 10:00:51 |`.
   - line 115: `Raise it in SCR-38. Keep it near seats ÷ 600 s: 7,500 ÷ 600 ≈ 12 per second` becomes `Raise it in SCR-38`.
   - line 116: `Lower it in SCR-38, back towards 12 per second` becomes `Lower it in SCR-38. The careful number is seats ÷ 600 s: 7,500 ÷ 600 ≈ 12 per second`.

- [ ] **Step 7: Screenshot check**

Run the app on port 7071: `PORT=7071 mvn -q compile exec:java`. Open `http://localhost:7071/organizer.html?dev`, start a new event, and screenshot step 1 of the wizard: the rate field reads 200 and the helper is the new sentence. Stop only the server you started.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/eventflow/web/fake/FakeBookingGateway.java src/test/java/eventflow/web/ApiContractTest.java src/test/java/eventflow/web/OrgApiContractTest.java src/test/java/eventflow/web/fake/FakeQueueTest.java src/test/java/eventflow/web/fake/FakeBookingTest.java src/test/java/eventflow/web/fake/FakeOrganizerTest.java src/main/resources/public/js/org/wizard.js src/main/resources/public/js/org/dialogs.js docs/04-use-cases.md docs/07-glossary.md docs/08-screen-requirements.md docs/10-operation.md
git commit -m "Admission rate 200 per second everywhere (core's default)"
```

---

### Task 2: Shared organizer checks and the RealEngine (lane B)

**Implementer:** deepseek

**Files:**
- Create: `src/main/java/eventflow/web/OrgChecks.java`
- Modify: `src/main/java/eventflow/web/fake/FakeOrganizerGateway.java` (use `OrgChecks`, delete its own copies)
- Create: `src/main/java/eventflow/web/real/EngineClock.java`
- Create: `src/main/java/eventflow/web/real/RealEngine.java`
- Test: `src/test/java/eventflow/web/real/RealEngineTest.java`

**Interfaces:**
- Consumes (core, read-only): `new EventFlow(Clock)`, `flow.events().createEvent(name, venue, LocalDate, LocalDateTime)`, `.addTier(eventId, name, Money, int)`, `.openSale(eventId)`, `.setAdmissionRate(eventId, int)`, `flow.admission().admitBatch(eventId)`, `.expireIdle()`, `flow.expiry().sweep()`, `flow.inventory().tiersOf(eventId)`, `flow.bus().subscribe(UserAdmitted.class, ...)`.
- Produces for Tasks 3–5 (all package-private unless marked public):
  - `public final class RealEngine implements DevControls` with `public RealEngine(FakeClock clock)`.
  - Fields: `final FakeClock clock`, `EventFlow flow`, `final Map<String, EventInfo> events` (by our id), `final Map<String, String> byCoreId`, `final Map<String, String> myTokens` (key `customerId + "@" + eventId`), `final Map<String, HoldInfo> holds`, `final Map<String, PaymentInfo> payments`, `final Map<String, OrderInfo> orders`, `final Map<String, Integer> admitted` (core event id → people let in), `int paymentSeq`, `int orderSeq`.
  - Classes `EventInfo`, `DraftTier`, `HoldInfo`, `PaymentInfo`, `OrderInfo` exactly as in Step 3.
  - Methods: `void tick()`, `String state(EventInfo e)` → `"DRAFT" | "SCHEDULED" | "ON_SALE"`, `EventInfo event(String id)` (404 `Event`), `EventInfo customerEvent(String id)` (404 for drafts), `EventInfo byCore(String coreId)`, `List<TicketTier> coreTiers(EventInfo e)`, `void publish(EventInfo e)`, `EventRef ref(EventInfo e)`, `static GatewayException notInCore(String what)`.
  - `OrgChecks` public static: `MAX_TIERS = 8`, `MAX_ARTWORK_CHARS = 2_800_000`, `checkedName`, `checkedVenue`, `checkedArtwork`, `checkRow`, `slug`, `shortName`.

- [ ] **Step 1: Move the organizer checks into `OrgChecks`**

Create `src/main/java/eventflow/web/OrgChecks.java` by moving these members out of `FakeOrganizerGateway` unchanged in behaviour, made `public static`: `MAX_TIERS` (8), `MAX_ARTWORK_CHARS` (2_800_000), `checkedArtwork`, `checkedName`, `checkedVenue`, `checkRow`, `slug`, `shortName`. Class header:

```java
package eventflow.web;

import java.util.Locale;

/** The organizer's input rules (UC-05), shared by the fake and the core gateways so both refuse the same way. */
public final class OrgChecks {

    private OrgChecks() {
    }

    public static final int MAX_TIERS = 8;
    public static final int MAX_ARTWORK_CHARS = 2_800_000; // a 2 MB picture as base64
    // ... the moved methods, bodies unchanged ...
}
```

In `FakeOrganizerGateway`, delete the moved private copies, add `import eventflow.web.OrgChecks;` and `import static eventflow.web.OrgChecks.*;`, and keep these two package-level aliases so existing tests compile unchanged:

```java
    static final int MAX_ARTWORK_CHARS = OrgChecks.MAX_ARTWORK_CHARS;

    static String checkedArtwork(String artwork) {
        return OrgChecks.checkedArtwork(artwork);
    }
```

Run: `mvn -q test` → PASS, 86 tests. Commit:

```bash
git add src/main/java/eventflow/web/OrgChecks.java src/main/java/eventflow/web/fake/FakeOrganizerGateway.java
git commit -m "Move the organizer's input checks into OrgChecks so the core gateway can share them"
```

- [ ] **Step 2: Write the failing engine test**

`src/test/java/eventflow/web/real/RealEngineTest.java`:

```java
package eventflow.web.real;

import eventflow.web.GatewayException;
import eventflow.web.fake.FakeBookingGateway;
import eventflow.web.fake.FakeClock;
import eventflow.web.fake.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealEngineTest {

    private FakeClock clock;
    private RealEngine engine;

    @BeforeEach
    void setUp() {
        AtomicLong real = new AtomicLong(0);
        clock = new FakeClock(FakeBookingGateway.START, real::get);
        engine = new RealEngine(clock);
    }

    @Test
    void seedPublishesTheFourStoryEventsIntoCore() {
        assertEquals(4, engine.events.size());
        RealEngine.EventInfo e = engine.event("the-1975");
        assertEquals("The 1975 Bangkok", e.name);
        assertEquals("chai", e.owner);
        assertEquals(3, engine.coreTiers(e).size());
        assertEquals(7500, engine.coreTiers(e).stream().mapToInt(t -> t.totalQuantity()).sum());
        assertEquals("SCHEDULED", engine.state(e));
        assertEquals(200, engine.flow.queue().admissionRate(e.coreId));
    }

    @Test
    void theSaleOpensAtTenAndTheQueueLetsPeopleIn() {
        clock.advance(31_000); // 10:00:01
        engine.tick();
        RealEngine.EventInfo e = engine.event("the-1975");
        assertEquals("ON_SALE", engine.state(e));
        var join = engine.flow.queue().join("kenji", e.coreId);
        assertTrue(join.hasToken());
        clock.advance(1_000);
        engine.tick();
        assertEquals("ADMITTED", engine.flow.queue().token(join.token().tokenId()).state().name());
        assertEquals(1, engine.admitted.get(e.coreId));
    }

    @Test
    void scenariosAreRefused() {
        GatewayException e = assertThrows(GatewayException.class, () -> engine.scenario(Scenario.RACE_LOST));
        assertEquals(400, e.status);
        assertEquals("NOT_IN_CORE", e.code);
    }

    @Test
    void resetBuildsAFreshEngineAndPutsTheClockBack() {
        var old = engine.flow;
        engine.speed(100);
        engine.advance(500);
        engine.reset();
        assertEquals(FakeBookingGateway.START, clock.now());
        assertEquals(1, clock.speed());
        assertTrue(old != engine.flow);
        assertEquals(4, engine.events.size());
    }

    @Test
    void speedOnlyAcceptsTheAllowedValues() {
        assertThrows(GatewayException.class, () -> engine.speed(7));
    }

    @Test
    void notInCoreIsA409() {
        GatewayException e = RealEngine.notInCore("Closing a sale");
        assertEquals(409, e.status);
        assertEquals("NOT_IN_CORE", e.code);
        assertEquals("Closing a sale is not built in the booking engine yet.", e.getMessage());
    }
}
```

Run: `mvn -q test -Dtest=RealEngineTest` → FAIL (RealEngine does not exist).

- [ ] **Step 3: Write `EngineClock` and `RealEngine`**

`src/main/java/eventflow/web/real/EngineClock.java`:

```java
package eventflow.web.real;

import eventflow.web.fake.FakeClock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** Lets core read the demo clock, so "+10 min" on the demo panel also ends core's 10-minute holds. */
final class EngineClock extends Clock {

    private final FakeClock demo;

    EngineClock(FakeClock demo) {
        this.demo = demo;
    }

    @Override public Instant instant()           { return Instant.ofEpochMilli(demo.now()); }
    @Override public ZoneId getZone()            { return RealEngine.BANGKOK; }
    @Override public Clock withZone(ZoneId zone) { return this; }
}
```

`src/main/java/eventflow/web/real/RealEngine.java`:

```java
package eventflow.web.real;

import eventflow.EventFlow;
import eventflow.contract.Money;
import eventflow.core.event.UserAdmitted;
import eventflow.core.inventory.TicketTier;
import eventflow.core.queue.QueueService;
import eventflow.web.GatewayException;
import eventflow.web.dto.Views.EventRef;
import eventflow.web.fake.DevControls;
import eventflow.web.fake.FakeBookingGateway;
import eventflow.web.fake.FakeClock;
import eventflow.web.fake.Scenario;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The real booking engine (core/) plus what the pages need that core does not keep yet:
 * drafts, pictures, the event's state, payments and orders. Shared by both core gateways.
 */
public final class RealEngine implements DevControls {

    static final ZoneId BANGKOK = FakeBookingGateway.BANGKOK;
    static final String NOT_IN_CORE = "NOT_IN_CORE";
    /** At most an hour of admission batches is caught up in one tick. */
    static final int MAX_BATCHES = 3600;

    final FakeClock clock;
    EventFlow flow;
    final Map<String, EventInfo> events = new LinkedHashMap<>();
    final Map<String, String> byCoreId = new HashMap<>();
    final Map<String, String> myTokens = new HashMap<>();
    final Map<String, HoldInfo> holds = new LinkedHashMap<>();
    final Map<String, PaymentInfo> payments = new LinkedHashMap<>();
    final Map<String, OrderInfo> orders = new LinkedHashMap<>();
    final Map<String, Integer> admitted = new HashMap<>();
    int paymentSeq;
    int orderSeq;

    static final class EventInfo {
        final String id;
        String name, shortName, venue, artwork, owner = "chai";
        long dateTime, saleOpensAt;
        int admitPerSecond = QueueService.DEFAULT_ADMISSION_RATE;
        final List<DraftTier> draftTiers = new ArrayList<>();
        String coreId;      // null while a draft: core has no drafts, so the event is created in core on publish
        boolean opened;
        long lastTick;

        EventInfo(String id) {
            this.id = id;
        }

        boolean draft() {
            return coreId == null;
        }
    }

    static final class DraftTier {
        final String id;
        String name;
        long price;
        int total;

        DraftTier(String id, String name, long price, int total) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.total = total;
        }
    }

    static final class HoldInfo {
        final String reservationId, customerId, eventId;
        final int before, after;
        String paymentId;

        HoldInfo(String reservationId, String customerId, String eventId, int before, int after) {
            this.reservationId = reservationId;
            this.customerId = customerId;
            this.eventId = eventId;
            this.before = before;
            this.after = after;
        }
    }

    static final class PaymentInfo {
        final String id, reservationId, customerId, providerKey;
        final long amount;
        String state = "PENDING";
        boolean answered;
        String orderId;

        PaymentInfo(String id, String reservationId, String customerId, String providerKey, long amount) {
            this.id = id;
            this.reservationId = reservationId;
            this.customerId = customerId;
            this.providerKey = providerKey;
            this.amount = amount;
        }
    }

    static final class OrderInfo {
        final String id, customerId, eventId, tierId, tierName, providerLabel;
        final int quantity;
        final long paid;
        final List<String> ticketIds = new ArrayList<>();

        OrderInfo(String id, String customerId, String eventId, String tierId, String tierName, int quantity,
                  long paid, String providerLabel) {
            this.id = id;
            this.customerId = customerId;
            this.eventId = eventId;
            this.tierId = tierId;
            this.tierName = tierName;
            this.quantity = quantity;
            this.paid = paid;
            this.providerLabel = providerLabel;
        }
    }

    public RealEngine(FakeClock clock) {
        this.clock = clock;
        seed();
    }

    // ---------- story data ----------

    synchronized void seed() {
        flow = new EventFlow(new EngineClock(clock));
        events.clear();
        byCoreId.clear();
        myTokens.clear();
        holds.clear();
        payments.clear();
        orders.clear();
        admitted.clear();
        paymentSeq = 5000;
        orderSeq = 9000;
        flow.bus().subscribe(UserAdmitted.class, a -> admitted.merge(a.eventId(), 1, Integer::sum));

        EventInfo e1975 = story("the-1975", "The 1975 Bangkok", "THE 1975", "Impact Arena",
                "assets/the-1975-card.png", at(2026, 11, 14, 19, 0), FakeBookingGateway.SALE_OPENS);
        e1975.draftTiers.add(new DraftTier("vip", "VIP", 6500, 500));
        e1975.draftTiers.add(new DraftTier("zone-a", "Zone A", 3800, 2000));
        e1975.draftTiers.add(new DraftTier("zone-b", "Zone B", 1900, 5000));
        publish(e1975);

        single("bruno-mars", "Bruno Mars", "BRUNO MARS", "Thunder Dome", "assets/bruno-mars.png",
                at(2026, 11, 22, 20, 0), at(2026, 10, 8, 10, 0), 1200, 3000);
        single("alan-walker", "Alan Walker", "ALAN WALKER", "UOB Live", "assets/alan-walker.png",
                at(2026, 12, 5, 20, 0), at(2026, 10, 15, 10, 0), 2400, 4000);
        single("marshmello", "Marshmello", "MARSHMELLO", "Moonstar Studio", "assets/marshmello.png",
                at(2026, 12, 18, 20, 0), at(2026, 10, 22, 10, 0), 1500, 2500);
    }

    private EventInfo story(String id, String name, String shortName, String venue, String art,
                            long date, long sale) {
        EventInfo e = new EventInfo(id);
        e.name = name;
        e.shortName = shortName;
        e.venue = venue;
        e.artwork = art;
        e.dateTime = date;
        e.saleOpensAt = sale;
        events.put(id, e);
        return e;
    }

    private void single(String id, String name, String shortName, String venue, String art,
                        long date, long sale, long price, int total) {
        EventInfo e = story(id, name, shortName, venue, art, date, sale);
        e.draftTiers.add(new DraftTier("standard", "Standard", price, total));
        publish(e);
    }

    static long at(int y, int mo, int d, int h, int mi) {
        return LocalDateTime.of(y, mo, d, h, mi).atZone(BANGKOK).toInstant().toEpochMilli();
    }

    // ---------- the gaps core leaves, filled here ----------

    /** Creates the event in core with its tiers and rate. Core has no drafts, so this happens on publish. */
    void publish(EventInfo e) {
        LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.dateTime), BANGKOK);
        LocalDateTime sale = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.saleOpensAt), BANGKOK);
        String coreId = flow.events().createEvent(e.name, e.venue, date.toLocalDate(), sale).eventId();
        for (DraftTier t : e.draftTiers) {
            flow.events().addTier(coreId, t.name, Money.baht(t.price), t.total);
        }
        flow.events().setAdmissionRate(coreId, e.admitPerSecond);
        e.draftTiers.clear();
        e.coreId = coreId;
        byCoreId.put(coreId, e.id);
    }

    /**
     * Brings core up to the demo clock: opens sales whose time has come (core's openSale opens at once),
     * lets one admission batch in per second passed, ends idle turns and expired holds.
     */
    void tick() {
        long now = clock.now();
        for (EventInfo e : events.values()) {
            if (e.draft()) {
                continue;
            }
            if (!e.opened && now >= e.saleOpensAt) {
                flow.events().openSale(e.coreId);
                e.opened = true;
                e.lastTick = e.saleOpensAt;
            }
            if (e.opened) {
                long seconds = Math.max(0, (now - e.lastTick) / 1000);
                for (long i = 0; i < Math.min(seconds, MAX_BATCHES); i++) {
                    flow.admission().admitBatch(e.coreId);
                }
                e.lastTick += seconds * 1000;
            }
        }
        flow.admission().expireIdle();
        flow.expiry().sweep();
    }

    /** Core only knows "on sale or not". DRAFT and SCHEDULED come from the gateway's own records. */
    String state(EventInfo e) {
        if (e.draft()) {
            return "DRAFT";
        }
        return clock.now() < e.saleOpensAt ? "SCHEDULED" : "ON_SALE";
    }

    EventInfo event(String id) {
        EventInfo e = events.get(id);
        if (e == null) {
            throw GatewayException.notFound("Event");
        }
        return e;
    }

    /** An event a customer may know about: a draft does not exist yet. */
    EventInfo customerEvent(String id) {
        EventInfo e = event(id);
        if (e.draft()) {
            throw GatewayException.notFound("Event");
        }
        return e;
    }

    EventInfo byCore(String coreId) {
        return events.get(byCoreId.get(coreId));
    }

    List<TicketTier> coreTiers(EventInfo e) {
        return e.draft() ? List.of() : flow.inventory().tiersOf(e.coreId);
    }

    EventRef ref(EventInfo e) {
        return new EventRef(e.id, e.name, e.shortName, e.venue, e.dateTime, e.artwork);
    }

    static GatewayException notInCore(String what) {
        return GatewayException.conflict(NOT_IN_CORE, null, what + " is not built in the booking engine yet.", null);
    }

    // ---------- demo controls ----------

    @Override
    public synchronized void scenario(Scenario scenario) {
        throw GatewayException.badRequest(NOT_IN_CORE, "Scenarios only work on the fake engine.");
    }

    @Override
    public synchronized void speed(int times) {
        if (times != 0 && times != 1 && times != 10 && times != 30 && times != 100) {
            throw GatewayException.badRequest("BAD_SPEED", "Speed must be 0, 1, 10, 30 or 100");
        }
        clock.setSpeed(times);
    }

    @Override
    public synchronized void advance(long seconds) {
        clock.advance(seconds * 1000);
    }

    @Override
    public synchronized void reset() {
        clock.setSpeed(1);
        clock.set(FakeBookingGateway.START);
        seed();
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `mvn -q test` → PASS, 92 tests (86 + 6). Run `./run.sh test` → 31 passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/eventflow/web/real/EngineClock.java src/main/java/eventflow/web/real/RealEngine.java src/test/java/eventflow/web/real/RealEngineTest.java
git commit -m "RealEngine: core plus the gateway's own records, on the demo clock"
```

---

### Task 3: CoreBookingGateway, browse, queue and hold (M-01 to M-07, M-13) (lane B)

**Implementer:** deepseek

**Files:**
- Create: `src/main/java/eventflow/web/real/CoreBookingGateway.java`
- Test: `src/test/java/eventflow/web/real/CoreBookingTest.java`

**Interfaces:**
- Consumes: everything `RealEngine` produces in Task 2. Core: `flow.queue().join(customerId, coreEventId)` → `JoinResult(outcome, token)`, `flow.queue().status(tokenId)` → `QueueStatus(state, position, estimatedWait)`, `flow.queue().token(tokenId)` (throws `IllegalArgumentException` when unknown), `flow.queue().admissionRate(coreEventId)`, `flow.inventory().hold(tokenId, tierId, quantity)` → `HoldResult(outcome, reservation)`, `flow.inventory().tier(tierId)` (throws `IllegalArgumentException` when unknown), `flow.inventory().soldOut(coreEventId)`, `flow.store().find(reservationId)`, `flow.store().all()`, `flow.registry().paymentKeys()`.
- Produces: `public final class CoreBookingGateway implements BookingGateway` with `public CoreBookingGateway(RealEngine engine)`. Methods M-08 to M-12 in this task throw `RealEngine.notInCore("This")` as a placeholder that Task 4 replaces. Package-private helpers Task 4 uses: `HoldInfo ownedHold(String customerId, String reservationId)`, `HoldView view(HoldInfo h)`, `static final List<PaymentMethodRow> PROVIDERS`, `String providerLabel(String key)`.

Exact mapping, call by call:

| Call | Core does | Gateway adds |
|---|---|---|
| M-01 | `inventory().tiersOf`, `soldOut` | list from `engine.events` (no drafts), sorted by `saleOpensAt`; state from `engine.state` |
| M-02 | tiers | `myToken` from `engine.myTokens` if that token is WAITING or ADMITTED; `myPosition` only when WAITING |
| M-03 | `queue().join` | `NOT_ON_SALE` 400 when state is not ON_SALE or outcome `SALE_NOT_OPEN`; `SOLD_OUT` → `refused("SOLD_OUT", "Every ticket has been sold", 0)`; `isExisting` = outcome `ALREADY_IN_QUEUE` |
| M-04 | `queue().token`, `status`, `admissionRate` | 404 `Place in line` for an unknown or someone else's token; `moved` = `engine.admitted`; `reservationId` = the reservation in `flow.store().all()` whose `tokenId()` matches, or null |
| M-05 | `tiersOf` | `TierAvailability(tierId, name, price.baht(), remaining(), totalQuantity())` |
| M-06 | `inventory().hold` | see Step 3 |
| M-07 | `store().find` | ownership from `engine.holds` |
| M-13 | nothing | stand-in, same as the fake |

- [ ] **Step 1: Write the failing tests**

`src/test/java/eventflow/web/real/CoreBookingTest.java`:

```java
package eventflow.web.real;

import eventflow.web.GatewayException;
import eventflow.web.dto.Views.HoldView;
import eventflow.web.fake.FakeBookingGateway;
import eventflow.web.fake.FakeClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreBookingTest {

    private FakeClock clock;
    private CoreBookingGateway gw;

    @BeforeEach
    void setUp() {
        AtomicLong real = new AtomicLong(0);
        clock = new FakeClock(FakeBookingGateway.START, real::get);
        gw = new CoreBookingGateway(new RealEngine(clock));
    }

    private String admitted(String customer) {
        clock.advance(60_000);
        String token = gw.joinQueue(customer, "the-1975").token();
        clock.advance(2_000);
        return token;
    }

    private String vip() {
        return gw.availability("the-1975").stream().filter(t -> t.name().equals("VIP")).findFirst().orElseThrow().tierId();
    }

    @Test
    void m01ListsTheFourEventsCheapestFirstPrice() {
        var list = gw.listEvents("kenji");
        assertEquals(4, list.size());
        assertEquals("the-1975", list.get(0).eventId());
        assertEquals("SCHEDULED", list.get(0).state());
        assertEquals(1900, list.get(0).fromPrice());
        assertFalse(list.get(0).soldOut());
    }

    @Test
    void m02UnknownEventIsNotFound() {
        assertEquals(404, assertThrows(GatewayException.class, () -> gw.getEvent("kenji", "nope")).status);
        assertEquals(3, gw.getEvent("kenji", "the-1975").tiers().size());
    }

    @Test
    void m03CannotJoinBeforeTen() {
        GatewayException e = assertThrows(GatewayException.class, () -> gw.joinQueue("kenji", "the-1975"));
        assertEquals("NOT_ON_SALE", e.code);
        assertEquals(400, e.status);
    }

    @Test
    void m03JoiningTwiceGivesTheSamePlace() {
        clock.advance(60_000);
        var first = gw.joinQueue("kenji", "the-1975");
        assertFalse(first.isExisting());
        assertEquals(1, first.position());
        var again = gw.joinQueue("kenji", "the-1975");
        assertTrue(again.isExisting());
        assertEquals(first.token(), again.token());
        assertEquals(first.token(), gw.getEvent("kenji", "the-1975").myToken());
        assertNull(gw.getEvent("mint", "the-1975").myToken());
    }

    @Test
    void m04TheQueueLetsKenjiInWithinASecond() {
        String token = admitted("kenji");
        var q = gw.queueStatus("kenji", token);
        assertEquals("ADMITTED", q.state());
        assertEquals(1, q.moved());
        assertEquals(200, q.admitPerSecond());
        assertNull(q.reservationId());
        assertEquals(404, assertThrows(GatewayException.class, () -> gw.queueStatus("mint", token)).status);
    }

    @Test
    void m06HoldsTwoVipForTenMinutes() {
        String token = admitted("kenji");
        HoldView h = gw.hold("kenji", token, vip(), 2);
        assertEquals("HELD", h.state());
        assertEquals(13_000, h.totalAmount());
        assertEquals(500, h.remainingBefore());
        assertEquals(498, h.remainingAfter());
        assertEquals(gw.now() + 10 * 60_000L, h.expiresAt());
        assertEquals(0, h.promotions().size());
        assertTrue(h.paymentMethods().stream().anyMatch(m -> m.key().equals("promptpay") && m.available()));
        assertTrue(h.paymentMethods().stream().anyMatch(m -> m.key().equals("card") && !m.available()));
        assertEquals("SPENT", gw.queueStatus("kenji", token).state());
        assertEquals(h.reservationId(), gw.queueStatus("kenji", token).reservationId());
    }

    @Test
    void m06OneTokenHoldsOnce() {
        String token = admitted("kenji");
        gw.hold("kenji", token, vip(), 2);
        GatewayException e = assertThrows(GatewayException.class, () -> gw.hold("kenji", token, vip(), 1));
        assertEquals("NOT_ADMITTED", e.code);
    }

    @Test
    void m06FiveTicketsIsOverTheLimit() {
        String token = admitted("kenji");
        GatewayException e = assertThrows(GatewayException.class, () -> gw.hold("kenji", token, vip(), 5));
        assertEquals("OVER_LIMIT", e.code);
        assertEquals(409, e.status);
    }

    @Test
    void m07TheHoldEndsAfterTenMinutesAndTheSeatsComeBack() {
        String token = admitted("kenji");
        HoldView h = gw.hold("kenji", token, vip(), 2);
        clock.advance(10 * 60_000L);
        assertEquals("EXPIRED", gw.getHold("kenji", h.reservationId()).state());
        assertEquals(500, gw.availability("the-1975").stream()
                .filter(t -> t.name().equals("VIP")).findFirst().orElseThrow().remaining());
        assertEquals(404, assertThrows(GatewayException.class, () -> gw.getHold("mint", h.reservationId())).status);
    }

    @Test
    void m13MeIsKenji() {
        assertEquals("Kenji", gw.me("kenji").name());
    }
}
```

Run: `mvn -q test -Dtest=CoreBookingTest` → FAIL (class missing).

- [ ] **Step 2: Write the gateway skeleton, M-01 to M-05, M-13**

```java
package eventflow.web.real;

import eventflow.core.inventory.HoldResult;
import eventflow.core.inventory.InventoryService;
import eventflow.core.inventory.TicketTier;
import eventflow.core.order.Reservation;
import eventflow.core.queue.JoinResult.Outcome;
import eventflow.core.queue.QueueToken;
import eventflow.core.queue.QueueTokenState;
import eventflow.web.BookingGateway;
import eventflow.web.GatewayException;
import eventflow.web.dto.Views.EventDetail;
import eventflow.web.dto.Views.EventSummary;
import eventflow.web.dto.Views.HoldView;
import eventflow.web.dto.Views.JoinResult;
import eventflow.web.dto.Views.Me;
import eventflow.web.dto.Views.OrderView;
import eventflow.web.dto.Views.PaymentMethod;
import eventflow.web.dto.Views.PaymentStart;
import eventflow.web.dto.Views.PaymentStatus;
import eventflow.web.dto.Views.QueueStatus;
import eventflow.web.dto.Views.TierAvailability;
import eventflow.web.dto.Views.TierPrice;

import java.util.Comparator;
import java.util.List;

/**
 * The customer pages on the real booking engine (core/). Where core has no answer yet,
 * RealEngine's own records fill the gap, or the call is refused with NOT_IN_CORE.
 */
public final class CoreBookingGateway implements BookingGateway {

    record PaymentMethodRow(String key, String tile, String label, String line) {
    }

    static final List<PaymentMethodRow> PROVIDERS = List.of(
            new PaymentMethodRow("promptpay", "PP", "PromptPay", "Scan with your banking app"),
            new PaymentMethodRow("card", "CARD", "Credit or debit card", "Visa, Mastercard"));

    private final RealEngine engine;

    public CoreBookingGateway(RealEngine engine) {
        this.engine = engine;
    }

    @Override
    public long now() {
        return engine.clock.now();
    }

    @Override
    public int clockRate() {
        return engine.clock.speed();
    }

    @Override
    public List<EventSummary> listEvents(String customerId) {                                // M-01
        synchronized (engine) {
            engine.tick();
            return engine.events.values().stream()
                    .filter(e -> !e.draft())
                    .sorted(Comparator.comparingLong(e -> e.saleOpensAt))
                    .map(e -> new EventSummary(e.id, e.name, e.shortName, e.venue, e.dateTime, e.saleOpensAt,
                            engine.state(e), fromPrice(e), e.artwork, engine.flow.inventory().soldOut(e.coreId)))
                    .toList();
        }
    }

    @Override
    public EventDetail getEvent(String customerId, String eventId) {                         // M-02
        synchronized (engine) {
            engine.tick();
            RealEngine.EventInfo e = engine.customerEvent(eventId);
            QueueToken mine = activeToken(customerId, eventId);
            Integer position = mine != null && mine.state() == QueueTokenState.WAITING
                    ? position(mine.tokenId()) : null;
            return new EventDetail(e.id, e.name, e.shortName, e.venue, e.dateTime, e.saleOpensAt,
                    engine.state(e), e.artwork, engine.flow.inventory().soldOut(e.coreId),
                    engine.coreTiers(e).stream().map(t -> new TierPrice(t.tierId(), t.name(), t.price().baht())).toList(),
                    mine == null ? null : mine.tokenId(), position);
        }
    }

    @Override
    public JoinResult joinQueue(String customerId, String eventId) {                         // M-03
        synchronized (engine) {
            engine.tick();
            RealEngine.EventInfo e = engine.customerEvent(eventId);
            if (!engine.state(e).equals("ON_SALE")) {
                throw notOnSale();
            }
            var r = engine.flow.queue().join(customerId, e.coreId);
            if (r.outcome() == Outcome.SALE_NOT_OPEN) {
                throw notOnSale();
            }
            if (r.outcome() == Outcome.SOLD_OUT) {
                throw GatewayException.refused("SOLD_OUT", "Every ticket has been sold", 0);
            }
            String token = r.token().tokenId();
            engine.myTokens.put(customerId + "@" + eventId, token);
            return new JoinResult(token, eventId, position(token), r.outcome() == Outcome.ALREADY_IN_QUEUE);
        }
    }

    @Override
    public QueueStatus queueStatus(String customerId, String tokenId) {                      // M-04
        synchronized (engine) {
            engine.tick();
            QueueToken t = ownedToken(customerId, tokenId);
            String reservationId = engine.flow.store().all().stream()
                    .filter(r -> r.tokenId().equals(tokenId)).map(Reservation::reservationId)
                    .findFirst().orElse(null);
            return new QueueStatus(tokenId, engine.byCore(t.eventId()).id, t.state().name(), position(tokenId),
                    engine.admitted.getOrDefault(t.eventId(), 0), engine.flow.queue().admissionRate(t.eventId()),
                    reservationId);
        }
    }

    @Override
    public List<TierAvailability> availability(String eventId) {                             // M-05
        synchronized (engine) {
            engine.tick();
            return engine.coreTiers(engine.customerEvent(eventId)).stream()
                    .map(t -> new TierAvailability(t.tierId(), t.name(), t.price().baht(), t.remaining(),
                            t.totalQuantity()))
                    .toList();
        }
    }

    @Override
    public Me me(String customerId) {                                                        // M-13, stand-in
        if (customerId.equals("kenji")) {
            return new Me(customerId, "Kenji", "k•••••@gmail.com");
        }
        String name = Character.toUpperCase(customerId.charAt(0)) + customerId.substring(1);
        return new Me(customerId, name, customerId.charAt(0) + "•••••@example.com");
    }

    // ---------- helpers ----------

    private long fromPrice(RealEngine.EventInfo e) {
        return engine.coreTiers(e).stream().mapToLong(t -> t.price().baht()).min().orElse(0);
    }

    private QueueToken activeToken(String customerId, String eventId) {
        String id = engine.myTokens.get(customerId + "@" + eventId);
        if (id == null) {
            return null;
        }
        QueueToken t = engine.flow.queue().token(id);
        return t.state() == QueueTokenState.WAITING || t.state() == QueueTokenState.ADMITTED ? t : null;
    }

    private QueueToken ownedToken(String customerId, String tokenId) {
        try {
            QueueToken t = engine.flow.queue().token(tokenId);
            if (t.customerId().equals(customerId)) {
                return t;
            }
        } catch (IllegalArgumentException unknown) {
            // falls through to not found
        }
        throw GatewayException.notFound("Place in line");
    }

    private int position(String tokenId) {
        return (int) engine.flow.queue().status(tokenId).position();
    }

    private static GatewayException notOnSale() {
        return GatewayException.badRequest("NOT_ON_SALE", "This sale is not open");
    }
}
```

- [ ] **Step 3: Add M-06 and M-07, and placeholders for M-08 to M-12**

Add to the class:

```java
    @Override
    public HoldView hold(String customerId, String tokenId, String tierId, int quantity) {     // M-06
        synchronized (engine) {
            engine.tick();
            QueueToken t = ownedToken(customerId, tokenId);
            RealEngine.EventInfo e = engine.byCore(t.eventId());
            if (!engine.state(e).equals("ON_SALE")) {
                throw notOnSale();
            }
            TicketTier tier = engine.coreTiers(e).stream().filter(x -> x.tierId().equals(tierId)).findFirst()
                    .orElseThrow(() -> GatewayException.notFound("Tier"));
            if (quantity < 1 || quantity > InventoryService.ORDER_LIMIT) {
                throw GatewayException.refused("OVER_LIMIT", "At most 4 tickets per person", tier.remaining());
            }
            int before = tier.remaining();
            HoldResult r = engine.flow.inventory().hold(tokenId, tierId, quantity);
            switch (r.outcome()) {
                case HELD -> { }
                case NOT_ENOUGH_SEATS -> throw engine.flow.inventory().soldOut(e.coreId)
                        ? GatewayException.refused("SOLD_OUT", "Every ticket has been sold", 0)
                        : GatewayException.refused("NOT_ENOUGH", "Not enough seats left in this tier", tier.remaining());
                case OVER_ORDER_LIMIT ->
                        throw GatewayException.refused("OVER_LIMIT", "At most 4 tickets per person", tier.remaining());
                default -> throw GatewayException.badRequest("NOT_ADMITTED", "This place in line cannot hold seats now");
            }
            RealEngine.HoldInfo h = new RealEngine.HoldInfo(r.reservation().reservationId(), customerId, e.id,
                    before, tier.remaining());
            engine.holds.put(h.reservationId, h);
            return view(h);
        }
    }

    @Override
    public HoldView getHold(String customerId, String reservationId) {                       // M-07
        synchronized (engine) {
            engine.tick();
            return view(ownedHold(customerId, reservationId));
        }
    }

    RealEngine.HoldInfo ownedHold(String customerId, String reservationId) {
        RealEngine.HoldInfo h = engine.holds.get(reservationId);
        if (h == null || !h.customerId.equals(customerId)) {
            throw GatewayException.notFound("Hold");
        }
        return h;
    }

    HoldView view(RealEngine.HoldInfo h) {
        Reservation r = engine.flow.store().find(h.reservationId).orElseThrow();
        TicketTier tier = engine.flow.inventory().tier(r.tierId());
        List<PaymentMethod> methods = PROVIDERS.stream()
                .map(p -> new PaymentMethod(p.key(), p.tile(), p.label(), p.line(),
                        engine.flow.registry().paymentKeys().contains(p.key())))
                .toList();
        // Promotions are Slice 3 (PricingRule), so core charges the list price.
        return new HoldView(r.reservationId(), r.tokenId(), r.state().name(), engine.ref(engine.event(h.eventId)),
                tier.tierId(), tier.name(), r.quantity(), r.unitPrice().baht(), List.of(), r.totalAmount().baht(),
                r.expiresAt().toEpochMilli(), h.before, h.after, methods, h.paymentId);
    }

    String providerLabel(String key) {
        return PROVIDERS.stream().filter(p -> p.key().equals(key)).findFirst().orElseThrow().label();
    }

    // Task 4 replaces these five.
    @Override public HoldView release(String c, String r)                    { throw RealEngine.notInCore("This"); }
    @Override public PaymentStart startPayment(String c, String r, String k) { throw RealEngine.notInCore("This"); }
    @Override public PaymentStatus answerPayment(String c, String p, boolean a) { throw RealEngine.notInCore("This"); }
    @Override public PaymentStatus paymentStatus(String c, String p)         { throw RealEngine.notInCore("This"); }
    @Override public List<OrderView> orders(String c)                        { throw RealEngine.notInCore("This"); }
```

- [ ] **Step 4: Run the tests**

Run: `mvn -q test` → PASS, 102 tests. Run `./run.sh test` → 31 passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/eventflow/web/real/CoreBookingGateway.java src/test/java/eventflow/web/real/CoreBookingTest.java
git commit -m "CoreBookingGateway: browse, queue and hold on core (M-01 to M-07, M-13)"
```

---

### Task 4: CoreBookingGateway, pay and orders (M-08 to M-12) (lane B)

**Implementer:** deepseek

**Files:**
- Modify: `src/main/java/eventflow/web/real/CoreBookingGateway.java` (replace the five placeholders)
- Test: `src/test/java/eventflow/web/real/CorePaymentTest.java`

**Interfaces:**
- Consumes: Task 3's `ownedHold`, `view`, `PROVIDERS`, `providerLabel`. Core: `flow.checkout().checkout(reservationId, providerKey)` → `CheckoutResult` `CONFIRMED | DECLINED | HOLD_EXPIRED`. It pays in one step.
- Produces: `PaymentInfo` and `OrderInfo` rows in `RealEngine`, read by Task 5's dashboard: `engine.orders.values()` with `eventId` and `paid`.

How the 3-step pages map onto core's one-step checkout:
- **M-09 start** only records a PENDING payment in the gateway. Nothing reaches core yet.
- **M-10 answer** with `approve=true` calls `checkout` once. `CONFIRMED` → `CAPTURED` plus an order; `DECLINED` → `DECLINED`; `HOLD_EXPIRED` → `VOIDED`. With `approve=false` → `DECLINED`, and core is not called.
- **M-11 status** returns the recorded payment.

- [ ] **Step 1: Write the failing tests**

`src/test/java/eventflow/web/real/CorePaymentTest.java`:

```java
package eventflow.web.real;

import eventflow.web.GatewayException;
import eventflow.web.dto.Views.HoldView;
import eventflow.web.fake.FakeBookingGateway;
import eventflow.web.fake.FakeClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorePaymentTest {

    private FakeClock clock;
    private CoreBookingGateway gw;

    @BeforeEach
    void setUp() {
        AtomicLong real = new AtomicLong(0);
        clock = new FakeClock(FakeBookingGateway.START, real::get);
        gw = new CoreBookingGateway(new RealEngine(clock));
    }

    private HoldView twoVip(String customer) {
        clock.advance(60_000);
        String token = gw.joinQueue(customer, "the-1975").token();
        clock.advance(2_000);
        String vip = gw.availability("the-1975").stream().filter(t -> t.name().equals("VIP"))
                .findFirst().orElseThrow().tierId();
        return gw.hold(customer, token, vip, 2);
    }

    @Test
    void promptPayConfirmsTheOrderInCore() {
        HoldView h = twoVip("kenji");
        var start = gw.startPayment("kenji", h.reservationId(), "promptpay");
        assertEquals(13_000, start.amount());
        assertEquals("PROMPTPAY|" + start.paymentId() + "|13000", start.display());
        assertEquals("PENDING", gw.paymentStatus("kenji", start.paymentId()).state());

        var paid = gw.answerPayment("kenji", start.paymentId(), true);
        assertEquals("CAPTURED", paid.state());
        assertTrue(paid.answered());
        assertEquals("CONFIRMED", gw.getHold("kenji", h.reservationId()).state());

        var orders = gw.orders("kenji");
        assertEquals(1, orders.size());
        assertEquals(paid.orderId(), orders.get(0).orderId());
        assertEquals(13_000, orders.get(0).paidAmount());
        assertEquals("PromptPay", orders.get(0).providerLabel());
        assertEquals(2, orders.get(0).tickets().size());
        assertEquals("VALID", orders.get(0).tickets().get(0).state());
    }

    @Test
    void cardIsNotAvailableOnCore() {
        HoldView h = twoVip("kenji");
        GatewayException e = assertThrows(GatewayException.class,
                () -> gw.startPayment("kenji", h.reservationId(), "card"));
        assertEquals("PROVIDER_UNAVAILABLE", e.code);
    }

    @Test
    void decliningDoesNotReachCore() {
        HoldView h = twoVip("kenji");
        var start = gw.startPayment("kenji", h.reservationId(), "promptpay");
        assertEquals("DECLINED", gw.answerPayment("kenji", start.paymentId(), false).state());
        assertEquals("HELD", gw.getHold("kenji", h.reservationId()).state());
    }

    @Test
    void payingAfterTheTenMinutesIsVoided() {
        HoldView h = twoVip("kenji");
        var start = gw.startPayment("kenji", h.reservationId(), "promptpay");
        clock.advance(10 * 60_000L);
        var status = gw.answerPayment("kenji", start.paymentId(), true);
        assertEquals("VOIDED", status.state());
        assertNull(status.orderId());
        assertEquals(0, gw.orders("kenji").size());
    }

    @Test
    void startingAgainReturnsTheSamePendingPayment() {
        HoldView h = twoVip("kenji");
        var first = gw.startPayment("kenji", h.reservationId(), "promptpay");
        assertEquals(first.paymentId(), gw.startPayment("kenji", h.reservationId(), "promptpay").paymentId());
        assertEquals(first.paymentId(), gw.getHold("kenji", h.reservationId()).paymentId());
    }

    @Test
    void releasingSeatsEarlyIsNotInCore() {
        HoldView h = twoVip("kenji");
        GatewayException e = assertThrows(GatewayException.class, () -> gw.release("kenji", h.reservationId()));
        assertEquals("NOT_IN_CORE", e.code);
        assertEquals(409, e.status);
        assertEquals(404, assertThrows(GatewayException.class, () -> gw.release("mint", h.reservationId())).status);
    }

    @Test
    void someoneElsesPaymentIsNotFound() {
        HoldView h = twoVip("kenji");
        var start = gw.startPayment("kenji", h.reservationId(), "promptpay");
        assertEquals(404, assertThrows(GatewayException.class,
                () -> gw.paymentStatus("mint", start.paymentId())).status);
    }
}
```

Run: `mvn -q test -Dtest=CorePaymentTest` → FAIL.

- [ ] **Step 2: Replace the five placeholders**

Delete the five `// Task 4 replaces these five.` lines and add (imports: `eventflow.core.order.CheckoutResult`, `eventflow.core.order.ReservationState`, `eventflow.web.dto.Views.TicketView`, `java.util.ArrayList`):

```java
    @Override
    public HoldView release(String customerId, String reservationId) {                       // M-08
        synchronized (engine) {
            engine.tick();
            ownedHold(customerId, reservationId);
            throw RealEngine.notInCore("Giving seats back early");
        }
    }

    @Override
    public PaymentStart startPayment(String customerId, String reservationId, String providerKey) { // M-09
        synchronized (engine) {
            engine.tick();
            RealEngine.HoldInfo h = ownedHold(customerId, reservationId);
            Reservation r = engine.flow.store().find(reservationId).orElseThrow();
            if (r.state() != ReservationState.HELD) {
                throw GatewayException.badRequest("HOLD_NOT_ACTIVE", "These seats are no longer held");
            }
            PaymentMethodRow method = PROVIDERS.stream().filter(p -> p.key().equals(providerKey)).findFirst()
                    .orElseThrow(() -> GatewayException.notFound("Payment method"));
            if (!engine.flow.registry().paymentKeys().contains(method.key())) {
                throw GatewayException.badRequest("PROVIDER_UNAVAILABLE", "This way to pay is not available right now");
            }
            if (h.paymentId != null) {
                RealEngine.PaymentInfo last = engine.payments.get(h.paymentId);
                if (last.state.equals("PENDING") && !last.answered && last.providerKey.equals(method.key())) {
                    return start(last);
                }
            }
            RealEngine.PaymentInfo p = new RealEngine.PaymentInfo("P-" + (++engine.paymentSeq), reservationId,
                    customerId, method.key(), r.totalAmount().baht());
            engine.payments.put(p.id, p);
            h.paymentId = p.id;
            return start(p);
        }
    }

    @Override
    public PaymentStatus answerPayment(String customerId, String paymentId, boolean approve) {  // M-10
        synchronized (engine) {
            engine.tick();
            RealEngine.PaymentInfo p = ownedPayment(customerId, paymentId);
            RealEngine.HoldInfo h = engine.holds.get(p.reservationId);
            if (!p.id.equals(h.paymentId)) {
                throw GatewayException.badRequest("PAYMENT_REPLACED", "This way to pay was replaced by a newer one");
            }
            if (p.state.equals("PENDING") && !p.answered) {
                p.answered = true;
                if (!approve) {
                    p.state = "DECLINED";
                } else {
                    CheckoutResult result = engine.flow.checkout().checkout(p.reservationId, p.providerKey);
                    switch (result) {
                        case CONFIRMED -> {
                            p.state = "CAPTURED";
                            p.orderId = order(h, p).id;
                        }
                        case DECLINED -> p.state = "DECLINED";
                        case HOLD_EXPIRED -> p.state = "VOIDED";
                    }
                }
            }
            return status(p);
        }
    }

    @Override
    public PaymentStatus paymentStatus(String customerId, String paymentId) {                 // M-11
        synchronized (engine) {
            engine.tick();
            return status(ownedPayment(customerId, paymentId));
        }
    }

    @Override
    public List<OrderView> orders(String customerId) {                                       // M-12, stand-in
        synchronized (engine) {
            engine.tick();
            long now = engine.clock.now();
            return engine.orders.values().stream()
                    .filter(o -> o.customerId.equals(customerId))
                    .map(o -> orderView(o, now))
                    .sorted(Comparator.comparing(OrderView::past)
                            .thenComparingLong(v -> v.past() ? -v.event().eventDateTime() : v.event().eventDateTime()))
                    .toList();
        }
    }

    // ---------- payment and order helpers ----------

    private RealEngine.PaymentInfo ownedPayment(String customerId, String paymentId) {
        RealEngine.PaymentInfo p = engine.payments.get(paymentId);
        if (p == null || !p.customerId.equals(customerId)) {
            throw GatewayException.notFound("Payment");
        }
        return p;
    }

    private PaymentStart start(RealEngine.PaymentInfo p) {
        return new PaymentStart(p.id, p.reservationId, p.providerKey, providerLabel(p.providerKey), p.amount,
                "PROMPTPAY|" + p.id + "|" + p.amount);
    }

    private static PaymentStatus status(RealEngine.PaymentInfo p) {
        return new PaymentStatus(p.id, p.reservationId, p.providerKey, p.state, p.answered, p.orderId);
    }

    /** Core confirmed the order (OrderConfirmed). Tickets are Slice 3, so the gateway numbers them for now. */
    private RealEngine.OrderInfo order(RealEngine.HoldInfo h, RealEngine.PaymentInfo p) {
        Reservation r = engine.flow.store().find(h.reservationId).orElseThrow();
        TicketTier tier = engine.flow.inventory().tier(r.tierId());
        RealEngine.OrderInfo o = new RealEngine.OrderInfo("O-" + (++engine.orderSeq), h.customerId, h.eventId,
                tier.tierId(), tier.name(), r.quantity(), p.amount, providerLabel(p.providerKey));
        for (int n = 1; n <= r.quantity(); n++) {
            o.ticketIds.add(String.format("TKT-%s-%02d", o.id, n));
        }
        engine.orders.put(o.id, o);
        return o;
    }

    private OrderView orderView(RealEngine.OrderInfo o, long now) {
        RealEngine.EventInfo e = engine.event(o.eventId);
        List<TicketView> tickets = new ArrayList<>();
        for (int i = 0; i < o.ticketIds.size(); i++) {
            String id = o.ticketIds.get(i);
            // Not signed: signing tickets is Slice 3 (NFR-06).
            String payload = "EVENTFLOW|" + id + "|" + o.eventId + "|" + o.tierId + "|" + o.customerId + "|UNSIGNED";
            tickets.add(new TicketView(id, i + 1, o.ticketIds.size(), payload, "VALID"));
        }
        return new OrderView(o.id, engine.ref(e), engine.state(e), o.tierId, o.tierName, o.quantity, o.paid,
                o.providerLabel, now > e.dateTime, tickets);
    }
```

- [ ] **Step 3: Run the tests**

Run: `mvn -q test` → PASS, 109 tests. Run `./run.sh test` → 31 passed.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/eventflow/web/real/CoreBookingGateway.java src/test/java/eventflow/web/real/CorePaymentTest.java
git commit -m "CoreBookingGateway: PromptPay through core's checkout, orders as a stand-in (M-08 to M-12)"
```

---

### Task 5: CoreOrganizerGateway (O-01 to O-11) (lane B)

**Implementer:** deepseek

**Files:**
- Create: `src/main/java/eventflow/web/real/CoreOrganizerGateway.java`
- Test: `src/test/java/eventflow/web/real/CoreOrganizerTest.java`

**Interfaces:**
- Consumes: `RealEngine` (Task 2), `OrgChecks` (Task 2), `engine.orders` (Task 4). Core: `flow.events().addTier(coreId, name, Money, int)`, `flow.queue().setAdmissionRate(coreId, int)`, `flow.queue().queueLength(coreId)`.
- Produces: `public final class CoreOrganizerGateway implements OrganizerGateway` with `public CoreOrganizerGateway(RealEngine engine)`.

Rules, call by call. A draft lives only in the gateway; core first sees the event on publish.

| Call | Draft | Published |
|---|---|---|
| O-01 list | owner's events, newest event date first | same |
| O-02 create | same checks and codes as the fake (`OrgChecks`, `SALE_IN_PAST`, `SALE_AFTER_EVENT`, `RATE_TOO_LOW`); id = `slug(name)`, made unique with `-2`, `-3`; default rate 200 | — |
| O-03 get | tiers from `draftTiers`, held 0, sold 0 | tiers from core with held and sold |
| O-04 details | everything editable, same checks as the fake | only `artwork` may change. A different `name`, `venue`, `eventDateTime` or `saleOpensAt` → `notInCore("Changing a published event")`. A value equal to the current one is not a change |
| O-05 replace tiers | replaces `draftTiers` (fake's checks, `MAX_TIERS` 8) | 409 `NOT_DRAFT` |
| O-06 add tier | adds to `draftTiers` | `flow.events().addTier` in core |
| O-07 edit tier | edits the draft tier (404 `Tier` if missing) | `notInCore("Changing a tier after publishing")` |
| O-08 publish | 409 `NO_TIERS` if empty, else `engine.publish(e)` | 409 `NOT_DRAFT` |
| O-09 rate | `null` or < 1 → 400 `RATE_TOO_LOW`; draft: store it | also `flow.queue().setAdmissionRate` |
| O-10 close | `notInCore("Closing a sale")` | same |
| O-11 dashboard | stand-in: core's tier counts, `queueLength` while ON_SALE, revenue = sum of `engine.orders` for the event | same |

`waiting` in O-03 and O-11 is `flow.queue().queueLength(coreId)` when the state is ON_SALE, else 0. `closedAt` is always `null`.

- [ ] **Step 1: Write the failing tests**

`src/test/java/eventflow/web/real/CoreOrganizerTest.java`:

```java
package eventflow.web.real;

import eventflow.web.GatewayException;
import eventflow.web.dto.OrgRequests.EventDetails;
import eventflow.web.dto.OrgRequests.TierInput;
import eventflow.web.fake.FakeBookingGateway;
import eventflow.web.fake.FakeClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreOrganizerTest {

    private static final long EVENT = ZonedDateTime.of(2026, 11, 14, 19, 0, 0, 0, FakeBookingGateway.BANGKOK)
            .toInstant().toEpochMilli();
    private static final long SALE = ZonedDateTime.of(2026, 11, 1, 10, 0, 0, 0, FakeBookingGateway.BANGKOK)
            .toInstant().toEpochMilli();

    private FakeClock clock;
    private CoreBookingGateway gw;
    private CoreOrganizerGateway org;

    @BeforeEach
    void setUp() {
        AtomicLong real = new AtomicLong(0);
        clock = new FakeClock(FakeBookingGateway.START, real::get);
        RealEngine engine = new RealEngine(clock);
        gw = new CoreBookingGateway(engine);
        org = new CoreOrganizerGateway(engine);
    }

    private String draft() {
        return org.createEvent("chai", new EventDetails("Test Night Bangkok", "Impact Arena", EVENT, SALE, null)).eventId();
    }

    @Test
    void o01ListsChaisFourEvents() {
        assertEquals(4, org.listEvents("chai").size());
        assertEquals(0, org.listEvents("mint").size());
    }

    @Test
    void o02ADraftStartsAt200AndCustomersCannotSeeIt() {
        var e = org.createEvent("chai", new EventDetails("Test Night Bangkok", "Impact Arena", EVENT, SALE, null));
        assertEquals("test-night-bangkok", e.eventId());
        assertEquals("DRAFT", e.state());
        assertEquals(200, e.admitPerSecond());
        assertEquals(4, gw.listEvents("kenji").size());
        assertEquals(404, assertThrows(GatewayException.class, () -> gw.getEvent("kenji", e.eventId())).status);
    }

    @Test
    void o02SameChecksAsTheFake() {
        GatewayException e = assertThrows(GatewayException.class, () -> org.createEvent("chai",
                new EventDetails("Test Night Bangkok", "Impact Arena", EVENT, FakeBookingGateway.START - 1, 12)));
        assertEquals("SALE_IN_PAST", e.code);
        assertEquals("saleOpensAt", e.field);
    }

    @Test
    void o05ThenO08PublishesIntoCore() {
        String id = draft();
        org.replaceTiers("chai", id, List.of(new TierInput("VIP", 6500L, 500), new TierInput("Zone A", 3800L, 2000)));
        assertEquals(2, org.getEvent("chai", id).tiers().size());
        var published = org.publish("chai", id);
        assertEquals("SCHEDULED", published.state());
        assertEquals(2500, published.tiers().stream().mapToInt(t -> t.remaining()).sum());
        assertEquals(5, gw.listEvents("kenji").size());
        assertEquals("NOT_DRAFT", assertThrows(GatewayException.class,
                () -> org.replaceTiers("chai", id, List.of(new TierInput("VIP", 1L, 1)))).code);
    }

    @Test
    void o08NeedsATier() {
        String id = draft();
        assertEquals("NO_TIERS", assertThrows(GatewayException.class, () -> org.publish("chai", id)).code);
    }

    @Test
    void o06AddsATierInCoreAfterPublishing() {
        var e = org.addTier("chai", "the-1975", new TierInput("Standing", 1500L, 1000));
        assertEquals(4, e.tiers().size());
        assertEquals(4, gw.availability("the-1975").size());
    }

    @Test
    void o04OnlyThePictureChangesAfterPublishing() {
        var same = org.updateDetails("chai", "the-1975", new EventDetails("The 1975 Bangkok", null, null, null, null, ""));
        assertNull(same.artwork());
        GatewayException e = assertThrows(GatewayException.class, () -> org.updateDetails("chai", "the-1975",
                new EventDetails(null, "UOB Live", null, null, null)));
        assertEquals("NOT_IN_CORE", e.code);
    }

    @Test
    void o07AndO10AreNotInCore() {
        String vip = org.getEvent("chai", "the-1975").tiers().get(0).tierId();
        assertEquals("NOT_IN_CORE", assertThrows(GatewayException.class,
                () -> org.updateTier("chai", "the-1975", vip, new TierInput(null, null, 600))).code);
        assertEquals("NOT_IN_CORE", assertThrows(GatewayException.class,
                () -> org.closeSale("chai", "the-1975")).code);
    }

    @Test
    void o09TheRateReachesTheQueue() {
        assertEquals(24, org.setRate("chai", "the-1975", 24).admitPerSecond());
        clock.advance(60_000);
        String token = gw.joinQueue("kenji", "the-1975").token();
        assertEquals(24, gw.queueStatus("kenji", token).admitPerSecond());
        assertEquals("RATE_TOO_LOW", assertThrows(GatewayException.class,
                () -> org.setRate("chai", "the-1975", 0)).code);
    }

    @Test
    void o11TheDashboardAddsUpAndCountsPaidOrders() {
        clock.advance(60_000);
        String token = gw.joinQueue("kenji", "the-1975").token();
        clock.advance(2_000);
        String vip = gw.availability("the-1975").get(0).tierId();
        var hold = gw.hold("kenji", token, vip, 2);
        var pay = gw.startPayment("kenji", hold.reservationId(), "promptpay");
        gw.answerPayment("kenji", pay.paymentId(), true);

        var d = org.dashboard("chai", "the-1975");
        assertEquals("ON_SALE", d.state());
        assertEquals(7500, d.all().total());
        assertEquals(2, d.all().sold());
        assertEquals(7498, d.all().remaining());
        assertEquals(13_000, d.revenue());
        for (var t : d.tiers()) {
            assertEquals(t.total(), t.held() + t.sold() + t.remaining());
        }
        assertTrue(d.closedAt() == null);
    }

    @Test
    void anotherOrganizersEventIsNotFound() {
        assertEquals(404, assertThrows(GatewayException.class, () -> org.getEvent("mint", "the-1975")).status);
    }
}
```

Run: `mvn -q test -Dtest=CoreOrganizerTest` → FAIL.

- [ ] **Step 2: Write `CoreOrganizerGateway`**

```java
package eventflow.web.real;

import eventflow.contract.Money;
import eventflow.core.inventory.TicketTier;
import eventflow.core.queue.QueueService;
import eventflow.web.GatewayException;
import eventflow.web.OrganizerGateway;
import eventflow.web.dto.OrgRequests.EventDetails;
import eventflow.web.dto.OrgRequests.TierInput;
import eventflow.web.dto.OrgViews.Dashboard;
import eventflow.web.dto.OrgViews.OrgEvent;
import eventflow.web.dto.OrgViews.OrgEventRow;
import eventflow.web.dto.OrgViews.OrgTier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static eventflow.web.OrgChecks.MAX_TIERS;
import static eventflow.web.OrgChecks.checkRow;
import static eventflow.web.OrgChecks.checkedArtwork;
import static eventflow.web.OrgChecks.checkedName;
import static eventflow.web.OrgChecks.checkedVenue;
import static eventflow.web.OrgChecks.shortName;
import static eventflow.web.OrgChecks.slug;

/**
 * The organizer console on the real booking engine. A draft lives only here; core first sees the
 * event on publish. Edits core cannot make yet are refused with NOT_IN_CORE.
 */
public final class CoreOrganizerGateway implements OrganizerGateway {

    private final RealEngine engine;

    public CoreOrganizerGateway(RealEngine engine) {
        this.engine = engine;
    }

    @Override
    public List<OrgEventRow> listEvents(String organizerId) {                                 // O-01
        synchronized (engine) {
            engine.tick();
            return engine.events.values().stream()
                    .filter(e -> e.owner.equals(organizerId))
                    .sorted(Comparator.comparingLong((RealEngine.EventInfo e) -> e.dateTime).reversed())
                    .map(e -> {
                        List<OrgTier> t = tiers(e);
                        return new OrgEventRow(e.id, e.name, e.venue, e.dateTime, e.saleOpensAt, engine.state(e),
                                t.stream().mapToInt(OrgTier::sold).sum(), t.stream().mapToInt(OrgTier::total).sum());
                    })
                    .toList();
        }
    }

    @Override
    public OrgEvent createEvent(String organizerId, EventDetails details) {                  // O-02
        synchronized (engine) {
            engine.tick();
            String name = checkedName(details.name());
            String venue = checkedVenue(details.venue());
            if (details.eventDateTime() == null) {
                throw GatewayException.invalid("REQUIRED", "eventDateTime", "Required");
            }
            if (details.saleOpensAt() == null) {
                throw GatewayException.invalid("REQUIRED", "saleOpensAt", "Required");
            }
            long date = details.eventDateTime();
            long sale = details.saleOpensAt();
            checkSale(sale, date);
            int rate = details.admitPerSecond() == null ? QueueService.DEFAULT_ADMISSION_RATE
                    : details.admitPerSecond();
            if (rate < 1) {
                throw GatewayException.invalid("RATE_TOO_LOW", "admitPerSecond", "Must be at least 1.");
            }
            RealEngine.EventInfo e = new RealEngine.EventInfo(uniqueId(slug(name)));
            e.name = name;
            e.shortName = shortName(name);
            e.venue = venue;
            e.artwork = checkedArtwork(details.artwork());
            e.dateTime = date;
            e.saleOpensAt = sale;
            e.owner = organizerId;
            e.admitPerSecond = rate;
            engine.events.put(e.id, e);
            return view(e);
        }
    }

    @Override
    public OrgEvent getEvent(String organizerId, String eventId) {                           // O-03
        synchronized (engine) {
            engine.tick();
            return view(owned(organizerId, eventId));
        }
    }

    @Override
    public OrgEvent updateDetails(String organizerId, String eventId, EventDetails details) {  // O-04
        synchronized (engine) {
            engine.tick();
            RealEngine.EventInfo e = owned(organizerId, eventId);
            if (!e.draft()) {
                boolean changes = changed(details.name(), e.name) || changed(details.venue(), e.venue)
                        || changed(details.eventDateTime(), e.dateTime) || changed(details.saleOpensAt(), e.saleOpensAt);
                if (changes) {
                    throw RealEngine.notInCore("Changing a published event");
                }
                if (details.artwork() != null) {
                    e.artwork = checkedArtwork(details.artwork());
                }
                return view(e);
            }
            String name = details.name() == null ? e.name : checkedName(details.name());
            String venue = details.venue() == null ? e.venue : checkedVenue(details.venue());
            long date = details.eventDateTime() == null ? e.dateTime : details.eventDateTime();
            long sale = details.saleOpensAt() == null ? e.saleOpensAt : details.saleOpensAt();
            if (details.saleOpensAt() != null && details.saleOpensAt() != e.saleOpensAt) {
                checkSale(sale, date);
            } else if (sale >= date) {
                throw GatewayException.invalid("SALE_AFTER_EVENT", "saleOpensAt", "The sale must open before the event.");
            }
            String artwork = details.artwork() == null ? e.artwork : checkedArtwork(details.artwork());
            e.name = name;
            e.shortName = shortName(name);
            e.venue = venue;
            e.dateTime = date;
            e.saleOpensAt = sale;
            e.artwork = artwork;
            return view(e);
        }
    }

    @Override
    public OrgEvent replaceTiers(String organizerId, String eventId, List<TierInput> tiers) {  // O-05
        synchronized (engine) {
            engine.tick();
            RealEngine.EventInfo e = owned(organizerId, eventId);
            if (!e.draft()) {
                throw notDraft();
            }
            if (tiers == null) {
                throw GatewayException.invalid("REQUIRED", "tiers", "Required");
            }
            if (tiers.size() > MAX_TIERS) {
                throw GatewayException.invalid("TOO_MANY_TIERS", "tiers", "Up to 8 tiers.");
            }
            for (int i = 0; i < tiers.size(); i++) {
                TierInput in = tiers.get(i);
                if (in == null) {
                    throw GatewayException.invalid("REQUIRED", "tiers[" + i + "]", "Required");
                }
                checkRow(i, "tiers[" + i + "].", in.name(), in.price(), in.total());
            }
            List<RealEngine.DraftTier> built = new ArrayList<>();
            for (TierInput in : tiers) {
                String name = in.name().trim();
                built.add(new RealEngine.DraftTier(uniqueTierId(built, slug(name)), name, in.price(), in.total()));
            }
            e.draftTiers.clear();
            e.draftTiers.addAll(built);
            return view(e);
        }
    }

    @Override
    public OrgEvent addTier(String organizerId, String eventId, TierInput tier) {            // O-06
        synchronized (engine) {
            engine.tick();
            RealEngine.EventInfo e = owned(organizerId, eventId);
            if (tier == null) {
                throw GatewayException.invalid("REQUIRED", "tier", "Required");
            }
            if (tiers(e).size() >= MAX_TIERS) {
                throw GatewayException.invalid("TOO_MANY_TIERS", "tiers", "Up to 8 tiers.");
            }
            checkRow(0, "", tier.name(), tier.price(), tier.total());
            String name = tier.name().trim();
            if (e.draft()) {
                e.draftTiers.add(new RealEngine.DraftTier(uniqueTierId(e.draftTiers, slug(name)), name,
                        tier.price(), tier.total()));
            } else {
                engine.flow.events().addTier(e.coreId, name, Money.baht(tier.price()), tier.total());
            }
            return view(e);
        }
    }

    @Override
    public OrgEvent updateTier(String organizerId, String eventId, String tierId, TierInput tier) {  // O-07
        synchronized (engine) {
            engine.tick();
            RealEngine.EventInfo e = owned(organizerId, eventId);
            if (tier == null) {
                throw GatewayException.invalid("REQUIRED", "tier", "Required");
            }
            if (!e.draft()) {
                throw RealEngine.notInCore("Changing a tier after publishing");
            }
            RealEngine.DraftTier t = e.draftTiers.stream().filter(x -> x.id.equals(tierId)).findFirst()
                    .orElseThrow(() -> GatewayException.notFound("Tier"));
            String name = tier.name() == null ? t.name : tier.name().trim();
            Long price = tier.price() == null ? t.price : tier.price();
            Integer total = tier.total() == null ? t.total : tier.total();
            checkRow(0, "", name, price, total);
            t.name = name;
            t.price = price;
            t.total = total;
            return view(e);
        }
    }

    @Override
    public OrgEvent publish(String organizerId, String eventId) {                            // O-08
        synchronized (engine) {
            engine.tick();
            RealEngine.EventInfo e = owned(organizerId, eventId);
            if (!e.draft()) {
                throw notDraft();
            }
            if (e.draftTiers.isEmpty()) {
                throw GatewayException.conflict("NO_TIERS", null,
                        "An event needs at least one tier before it can be published.", null);
            }
            engine.publish(e);
            return view(e);
        }
    }

    @Override
    public OrgEvent setRate(String organizerId, String eventId, Integer admitPerSecond) {     // O-09
        synchronized (engine) {
            engine.tick();
            RealEngine.EventInfo e = owned(organizerId, eventId);
            if (admitPerSecond == null || admitPerSecond < 1) {
                throw GatewayException.invalid("RATE_TOO_LOW", "admitPerSecond", "Must be at least 1.");
            }
            e.admitPerSecond = admitPerSecond;
            if (!e.draft()) {
                engine.flow.queue().setAdmissionRate(e.coreId, admitPerSecond);
            }
            return view(e);
        }
    }

    @Override
    public OrgEvent closeSale(String organizerId, String eventId) {                          // O-10
        synchronized (engine) {
            engine.tick();
            owned(organizerId, eventId);
            throw RealEngine.notInCore("Closing a sale");
        }
    }

    @Override
    public Dashboard dashboard(String organizerId, String eventId) {                         // O-11, stand-in
        synchronized (engine) {
            engine.tick();
            RealEngine.EventInfo e = owned(organizerId, eventId);
            List<OrgTier> rows = tiers(e);
            int total = rows.stream().mapToInt(OrgTier::total).sum();
            int held = rows.stream().mapToInt(OrgTier::held).sum();
            int sold = rows.stream().mapToInt(OrgTier::sold).sum();
            OrgTier all = new OrgTier("all", "All", 0, total, held, sold, total - held - sold);
            long revenue = engine.orders.values().stream().filter(o -> o.eventId.equals(e.id))
                    .mapToLong(o -> o.paid).sum();
            return new Dashboard(e.id, e.name, engine.state(e), waiting(e), e.admitPerSecond, rows, all, revenue, null);
        }
    }

    // ---------- helpers ----------

    private RealEngine.EventInfo owned(String organizerId, String eventId) {
        RealEngine.EventInfo e = engine.events.get(eventId);
        if (e == null || !e.owner.equals(organizerId)) {
            throw GatewayException.notFound("Event");
        }
        return e;
    }

    private OrgEvent view(RealEngine.EventInfo e) {
        List<OrgTier> rows = tiers(e);
        return new OrgEvent(e.id, e.name, e.venue, e.dateTime, e.saleOpensAt, engine.state(e), e.admitPerSecond,
                rows, rows.stream().mapToInt(OrgTier::held).sum(), waiting(e), null, e.artwork);
    }

    private List<OrgTier> tiers(RealEngine.EventInfo e) {
        if (e.draft()) {
            return e.draftTiers.stream()
                    .map(t -> new OrgTier(t.id, t.name, t.price, t.total, 0, 0, t.total)).toList();
        }
        return engine.coreTiers(e).stream()
                .map((TicketTier t) -> new OrgTier(t.tierId(), t.name(), t.price().baht(), t.totalQuantity(),
                        t.heldQuantity(), t.soldQuantity(), t.remaining()))
                .toList();
    }

    private int waiting(RealEngine.EventInfo e) {
        return engine.state(e).equals("ON_SALE") ? engine.flow.queue().queueLength(e.coreId) : 0;
    }

    private void checkSale(long sale, long date) {
        if (sale <= engine.clock.now()) {
            throw GatewayException.invalid("SALE_IN_PAST", "saleOpensAt", "The sale must open in the future.");
        }
        if (sale >= date) {
            throw GatewayException.invalid("SALE_AFTER_EVENT", "saleOpensAt", "The sale must open before the event.");
        }
    }

    private static boolean changed(Object given, Object current) {
        if (given instanceof String s) {
            return !s.trim().equals(current);
        }
        return given != null && !Objects.equals(given, current);
    }

    private String uniqueId(String base) {
        String id = base;
        int n = 2;
        while (engine.events.containsKey(id)) {
            id = base + "-" + n++;
        }
        return id;
    }

    private static String uniqueTierId(List<RealEngine.DraftTier> existing, String base) {
        String id = base;
        int n = 2;
        while (taken(existing, id)) {
            id = base + "-" + n++;
        }
        return id;
    }

    private static boolean taken(List<RealEngine.DraftTier> tiers, String id) {
        return tiers.stream().anyMatch(t -> t.id.equals(id));
    }

    private static GatewayException notDraft() {
        return GatewayException.conflict("NOT_DRAFT", null, "Only a draft can do this.", null);
    }
}
```

`changed(Long given, long current)`: `details.eventDateTime()` is a `Long` and `e.dateTime` boxes to `Long`, so `Objects.equals` compares values.

- [ ] **Step 3: Run the tests**

Run: `mvn -q test` → PASS, 120 tests. Run `./run.sh test` → 31 passed.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/eventflow/web/real/CoreOrganizerGateway.java src/test/java/eventflow/web/real/CoreOrganizerTest.java
git commit -m "CoreOrganizerGateway: drafts in the gateway, publish into core (O-01 to O-11)"
```

---

### Task 6: Join: the switch, a contract test, docs, screenshots

**Implementer:** claude

**Files:**
- Modify: `src/main/java/eventflow/web/WebServer.java`
- Test: `src/test/java/eventflow/web/real/CoreApiContractTest.java`
- Modify: `docs/09-web-api.md`, `docs/10-operation.md` (§10.3 and §10.7), `AGENTS.md` (§1 table, row 4)

**Interfaces:**
- Consumes: `RealEngine`, `CoreBookingGateway`, `CoreOrganizerGateway` (Tasks 2–5).
- Produces: `ENGINE=core mvn -q compile exec:java` runs the pages on core.

- [ ] **Step 1: Merge the lanes**

Merge lane A and lane B into the join branch. Run `mvn -q test` → 120 tests pass. Run `./run.sh test` → 31 passed.

- [ ] **Step 2: Write the failing HTTP contract test**

`src/test/java/eventflow/web/real/CoreApiContractTest.java`:

```java
package eventflow.web.real;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eventflow.web.WebServer;
import eventflow.web.fake.FakeBookingGateway;
import eventflow.web.fake.FakeClock;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The same envelope and codes as the fake, through HTTP, with core behind the gateways. */
class CoreApiContractTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private RealEngine engine;

    @BeforeEach
    void setUp() {
        AtomicLong real = new AtomicLong(0);
        engine = new RealEngine(new FakeClock(FakeBookingGateway.START, real::get));
    }

    private JsonNode body(Response r) throws IOException {
        return mapper.readTree(r.body().string());
    }

    @Test
    void listsEventsInTheEnvelope() {
        JavalinTest.test(WebServer.create(new CoreBookingGateway(engine), new CoreOrganizerGateway(engine), engine),
                (server, client) -> {
                    Response r = client.get("/api/events", b -> b.header("X-Customer", "kenji"));
                    assertEquals(200, r.code());
                    JsonNode b = body(r);
                    assertEquals(FakeBookingGateway.START, b.get("serverNow").asLong());
                    assertEquals(4, b.get("data").size());
                });
    }

    @Test
    void closingASaleIsA409NotInCore() {
        JavalinTest.test(WebServer.create(new CoreBookingGateway(engine), new CoreOrganizerGateway(engine), engine),
                (server, client) -> {
                    Response r = client.post("/api/org/events/the-1975/close", java.util.Map.of(), b -> b.header("X-Customer", "chai"));
                    assertEquals(409, r.code());
                    assertEquals("NOT_IN_CORE", body(r).get("error").get("code").asText());
                });
    }

    @Test
    void scenariosAreRefusedOnTheDemoPanel() {
        JavalinTest.test(WebServer.create(new CoreBookingGateway(engine), new CoreOrganizerGateway(engine), engine),
                (server, client) -> {
                    Response r = client.post("/dev/scenario", java.util.Map.of("scenario", "RACE_LOST"));
                    assertEquals(400, r.code());
                    assertEquals("NOT_IN_CORE", body(r).get("error").get("code").asText());
                });
    }
}
```

Run: `mvn -q test -Dtest=CoreApiContractTest` → PASS (the three-argument `create` already exists). It guards the envelope on core; keep it.

- [ ] **Step 3: The switch**

`WebServer.main` becomes:

```java
    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));
        FakeClock clock = new FakeClock(FakeBookingGateway.START, System::currentTimeMillis);

        // THE SWITCH. ENGINE=core runs the pages on the real booking engine (core/). The fake stays the default.
        if ("core".equals(System.getenv("ENGINE"))) {
            RealEngine engine = new RealEngine(clock);
            create(new CoreBookingGateway(engine), new CoreOrganizerGateway(engine), engine).start(port);
            return;
        }
        FakeBookingGateway fake = new FakeBookingGateway(clock);
        create(fake, new FakeOrganizerGateway(fake), fake).start(port);
    }
```

Add imports `eventflow.web.real.CoreBookingGateway`, `eventflow.web.real.CoreOrganizerGateway`, `eventflow.web.real.RealEngine`.

- [ ] **Step 4: Docs**

1. `docs/09-web-api.md`: add a row to the error table: `| 409 | `NOT_IN_CORE` | — | `Closing a sale is not built in the booking engine yet.` | Only with `ENGINE=core`: M-08, O-04 on a published event, O-07 after publishing, O-10 |`. In §9.5, add the line: `With ENGINE=core the speed, advance and reset controls work; a scenario is refused with 400 NOT_IN_CORE.`
2. `docs/10-operation.md` §10.3: after step 4 add `To run the pages on the real booking engine: ENGINE=core mvn -q compile exec:java.`
3. `docs/10-operation.md` §10.7: replace the paragraph and table with the call-by-call result: 6 calls straight to core (M-03, M-04, M-05, M-06, O-06, O-09); 9 completed in the gateway (M-02, M-07, M-09, M-10, M-11, O-02, O-03, O-05, O-08); stand-ins M-01, M-12, M-13, O-01, O-11; refused with NOT_IN_CORE M-08, O-04 (published), O-07 (published), O-10. Keep the bold first sentence and the bullets about `THE SWITCH`, updated to say `ENGINE=core`.
4. `AGENTS.md` §1 row 4: `Slices 1–2 built — booking engine + waiting room, with tests; all 29 screens on a fake engine` becomes `Slices 1–2 built — booking engine + waiting room, with tests; all 29 screens on a fake engine, or on core with ENGINE=core`.

- [ ] **Step 5: Screenshot check on core**

Start `ENGINE=core PORT=7072 mvn -q compile exec:java`. With `?dev`, set ×10, wait for 10:00, and screenshot: home list (SCR-01), event on sale (SCR-05b), queue → select (SCR-06, SCR-08), hold (SCR-09), PromptPay result (SCR-17), My tickets (SCR-18); organizer list (SCR-30), event page (SCR-34) and dashboard (SCR-40). Each must render with no "offline" screen. Then press the hold page's "give seats back" and the organizer's "Close the sale" once each, and confirm the page shows the NOT_IN_CORE message rather than breaking. Write any page that breaks into the report. Stop only the server you started.

- [ ] **Step 6: Run everything and commit**

Run: `mvn -q test` → PASS, 123 tests. `./run.sh test` → 31 passed.

```bash
git add src/main/java/eventflow/web/WebServer.java src/test/java/eventflow/web/real/CoreApiContractTest.java docs/09-web-api.md docs/10-operation.md AGENTS.md
git commit -m "THE SWITCH: ENGINE=core runs the pages on the real booking engine"
```
