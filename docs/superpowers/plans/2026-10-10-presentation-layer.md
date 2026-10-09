# Presentation Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Invoke the `cc-bridge` skill before dispatching any implementer.

**Goal:** Build the EventFlow customer app (SCR-01 to SCR-18) as plain HTML, CSS and JavaScript pages served by a Javalin `web/` layer that talks to one `BookingGateway` interface, filled today by an in-memory fake.

**Architecture:** The browser page (`src/main/resources/public/`) calls JSON endpoints. Route classes in `web/routes/` turn each request into one `BookingGateway` call. `FakeBookingGateway` (in `web/fake/`) answers now with the story data and a controllable clock. The teammate's `CoreBookingGateway` replaces it later by changing one line in `WebServer.gateway()`.

**Tech Stack:** Java 17 (built with the installed JDK 21), Maven 3.9+, Javalin 6.3.0, Jackson 2.17.2, JUnit 5.10.3, plain ES-module JavaScript (no build step), Playwright CLI 1.64.0 for screenshots.

**Spec:** `docs/superpowers/specs/2026-10-10-presentation-layer-design.md`

## Global Constraints

- Package root `th.ac.kmitl.sda.ticketqueue`. All Java under `src/main/java/th/ac/kmitl/sda/ticketqueue/web/`. Tests under `src/test/java/th/ac/kmitl/sda/ticketqueue/web/`.
- `core/` must never import `web/` or `plugin/`. `web/` may import `core/`.
- `BookingGateway` is not a plug-in seam. It lives in `web/`, has 2 implementations (fake now, core later) and holds no business rule of the real system.
- Every JSON reply is an envelope: success `{"serverNow": <epoch ms>, "data": <payload>}`, failure `{"serverNow": <epoch ms>, "error": {"code": "...", "message": "...", "remaining": <int or null>}}`.
- The customer comes from the `X-Customer` request header, default `kenji`. The page reads `?as=<name>` from its address and sends it.
- Money is whole baht as a `long`. Shown as `฿` + digits with commas (`฿13,000`), `Free` for 0.
- Times are epoch milliseconds. Shown in `Asia/Bangkok`, 24-hour (`10:00`), countdowns `mm:ss`, dates `14 Nov 2026` short and `Saturday 14 November` long.
- Hold time **10 minutes** (600,000 ms). Per-order limit **4**. Admitted buyer may stay on SCR-08 for **5 minutes** (Q-3).
- Story data (Q-2): headline event **The 1975 Bangkok**, Impact Arena, 14 Nov 2026 19:00, sale opens 1 Oct 2026 10:00. Tiers VIP ฿6,500 × 500, Zone A ฿3,800 × 2,000, Zone B ฿1,900 × 5,000.
- Screen copy comes word for word from `docs/08-screen-requirements.md`. Never show `token`, `reservation`, `timeout`, `transaction`, `voided` or any capital state name to the customer.
- Colours: lime `--ac #C5F25A` means "move forward". Coral `--st #FF8B7B` means "stop" (low stock, sold out, errors, expired). These replace the old cyan and magenta.
- No pronoun guesses in any copy or doc. Kenji is "Kenji" or "they".
- Never `git add -A`. Stage named paths only. End every commit message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Run the server with `mvn -q exec:java` (port 7070). Open `http://localhost:7070/`.

## File map

```
pom.xml                                                     Task 1
src/main/java/th/ac/kmitl/sda/ticketqueue/web/
  WebServer.java                                            Task 1, changed in Task 4
  BookingGateway.java                                       Task 2
  GatewayException.java                                     Task 2
  dto/Views.java                                            Task 2   (all reply records)
  dto/Requests.java                                         Task 4   (all request records)
  routes/Reply.java                                         Task 4
  routes/EventRoutes.java  QueueRoutes.java  HoldRoutes.java
  routes/PaymentRoutes.java  MeRoutes.java  DevRoutes.java  Task 4
  fake/FakeClock.java  fake/Scenario.java  fake/DevControls.java   Task 2
  fake/FakeBookingGateway.java                              Task 2, extended in Task 3
src/test/java/th/ac/kmitl/sda/ticketqueue/web/
  WebServerTest.java  ArchitectureRuleTest.java             Task 1
  fake/FakeClockTest.java  fake/FakeQueueTest.java          Task 2
  fake/FakeBookingTest.java                                 Task 3
  ApiContractTest.java                                      Task 4
src/main/resources/public/
  index.html  css/app.css  assets/*.png                     Task 1 (placeholder), Task 5
  js/api.js  js/parts.js  js/app.js  js/dev.js              Task 5
  js/screens/event.js                                       Task 6
  js/screens/queue.js                                       Task 7
  js/screens/select.js                                      Task 8
  js/screens/hold.js                                        Task 9
  js/screens/pay.js                                         Task 10
  js/screens/shell.js  js/screens/tickets.js                Task 11
src/test/e2e/walk.mjs                                       Task 5
docs/02, 06, 07, 08, 09, src/README.md, spec               Task D (lane B)
```

## Lanes

| Lane | Tasks | Touches |
|---|---|---|
| A · Java | 1 → 2 → 3 → 4 | `pom.xml`, `src/main/java/.../web/**`, `src/test/java/**`, placeholder `public/index.html` |
| B · Docs | D | `docs/02-requirements.md`, `docs/06-architecture.md`, `docs/07-glossary.md`, `docs/08-screen-requirements.md`, `docs/09-web-api.md`, `src/README.md`, the spec |
| Join | 5 → 6 → 7 → 8 → 9 → 10 → 11 → 12 | `src/main/resources/public/**`, `src/test/e2e/walk.mjs` |

Lanes A and B share no file and can run at the same time. The join starts when both are done.

## How every screen task is checked

After each screen task, run the server and the screenshot walk for that task's shots, then compare each picture with the design reference of the same name:

```bash
mvn -q exec:java &          # leave running
node src/test/e2e/walk.mjs 06a 06b 06c 07
open target/shots/06a.png diagrams/app-flow/screens/06a.jpg
```

Pass means: same parts, same order, same words, same colours of meaning (lime forward, coral stop). Pixel-exact is not required. Write one line per shot in the task report: `06a ok` or `06a differs: <what>`.

---
### Task 1: Maven build, `WebServer` serving a page, dependency rule test

**Lane:** A

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/th/ac/kmitl/sda/ticketqueue/web/WebServer.java`
- Create: `src/main/resources/public/index.html` (placeholder, replaced in Task 5)
- Create: `src/test/java/th/ac/kmitl/sda/ticketqueue/web/WebServerTest.java`
- Create: `src/test/java/th/ac/kmitl/sda/ticketqueue/web/ArchitectureRuleTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `WebServer.create()` returning a configured, not-started `io.javalin.Javalin` that serves `src/main/resources/public/` at `/` and answers `GET /api/health` with `{"ok":true}`. Task 4 changes its signature to `create(BookingGateway gateway, DevControls dev)`.

- [ ] **Step 1: Install tools (once per machine)**

```bash
command -v mvn || brew install maven
mvn -v                     # expect Apache Maven 3.9.x and Java 21
npx -y playwright@1.64.0 install chromium
```

- [ ] **Step 2: Write `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>th.ac.kmitl.sda</groupId>
  <artifactId>ticketqueue</artifactId>
  <version>0.1.0</version>
  <name>EventFlow</name>

  <properties>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>io.javalin</groupId>
      <artifactId>javalin</artifactId>
      <version>6.3.0</version>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>2.17.2</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-simple</artifactId>
      <version>2.0.13</version>
    </dependency>
    <dependency>
      <groupId>io.javalin</groupId>
      <artifactId>javalin-testtools</artifactId>
      <version>6.3.0</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.10.3</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.2.5</version>
      </plugin>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.4.1</version>
        <configuration>
          <mainClass>th.ac.kmitl.sda.ticketqueue.web.WebServer</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 3: Write the failing tests**

`src/test/java/th/ac/kmitl/sda/ticketqueue/web/WebServerTest.java`

```java
package th.ac.kmitl.sda.ticketqueue.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebServerTest {

    @Test
    void servesThePageAndHealth() {
        JavalinTest.test(WebServer.create(), (server, client) -> {
            var page = client.get("/");
            assertEquals(200, page.code());
            assertTrue(page.body().string().contains("EventFlow"));

            var health = client.get("/api/health");
            assertEquals(200, health.code());
            assertEquals("{\"ok\":true}", health.body().string());
        });
    }
}
```

`src/test/java/th/ac/kmitl/sda/ticketqueue/web/ArchitectureRuleTest.java`

```java
package th.ac.kmitl.sda.ticketqueue.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchitectureRuleTest {

    private static final Path CORE = Path.of("src/main/java/th/ac/kmitl/sda/ticketqueue/core");

    @Test
    void coreNeverImportsWebOrPlugin() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(CORE)) {
            offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String src = read(p);
                        return src.contains("import th.ac.kmitl.sda.ticketqueue.web")
                                || src.contains("import th.ac.kmitl.sda.ticketqueue.plugin");
                    })
                    .map(Path::toString)
                    .toList();
        }
        assertEquals(List.of(), offenders);
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `mvn -q test`
Expected: compilation FAIL, `cannot find symbol: class WebServer`.

- [ ] **Step 5: Write `WebServer.java` and the placeholder page**

`src/main/java/th/ac/kmitl/sda/ticketqueue/web/WebServer.java`

```java
package th.ac.kmitl.sda.ticketqueue.web;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import java.util.Map;

/** Starts the Java doorway: serves the page files and answers the page's messages. */
public final class WebServer {

    private WebServer() {
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));
        create().start(port);
    }

    public static Javalin create() {
        Javalin app = Javalin.create(config -> config.staticFiles.add("/public", Location.CLASSPATH));
        app.get("/api/health", ctx -> ctx.json(Map.of("ok", true)));
        return app;
    }
}
```

`src/main/resources/public/index.html`

```html
<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>EventFlow</title></head>
<body><p>EventFlow is starting.</p></body>
</html>
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn -q test`
Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 7: Run the server once by hand**

Run: `mvn -q exec:java`, then in another terminal `curl -s localhost:7070/api/health`
Expected: `{"ok":true}`. Stop the server with Ctrl+C.

- [ ] **Step 8: Commit**

```bash
git add pom.xml src/main/java/th/ac/kmitl/sda/ticketqueue/web/WebServer.java src/main/resources/public/index.html src/test/java/th/ac/kmitl/sda/ticketqueue/web/WebServerTest.java src/test/java/th/ac/kmitl/sda/ticketqueue/web/ArchitectureRuleTest.java
git commit -m "Build setup: Maven, Javalin WebServer, core-imports rule test

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: `BookingGateway`, reply records, fake clock, fake catalog and queue (M-01 to M-04)

**Lane:** A

**Files:**
- Create: `src/main/java/th/ac/kmitl/sda/ticketqueue/web/BookingGateway.java`
- Create: `src/main/java/th/ac/kmitl/sda/ticketqueue/web/GatewayException.java`
- Create: `src/main/java/th/ac/kmitl/sda/ticketqueue/web/dto/Views.java`
- Create: `src/main/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeClock.java`
- Create: `src/main/java/th/ac/kmitl/sda/ticketqueue/web/fake/Scenario.java`
- Create: `src/main/java/th/ac/kmitl/sda/ticketqueue/web/fake/DevControls.java`
- Create: `src/main/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeBookingGateway.java`
- Test: `src/test/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeClockTest.java`
- Test: `src/test/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeQueueTest.java`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces (exact names later tasks use):
  - `BookingGateway` with the 14 methods below.
  - `GatewayException(int status, String code, String message, Integer remaining)` with public final fields and factories `notFound(String what)`, `badRequest(String code, String message)`, `refused(String code, String message, int remaining)`.
  - Records in `Views`: `EventSummary`, `TierPrice`, `EventDetail`, `JoinResult`, `QueueStatus`, `TierAvailability`, `EventRef`, `Promotion`, `PaymentMethod`, `HoldView`, `PaymentStart`, `PaymentStatus`, `TicketView`, `OrderView`, `Me`.
  - `FakeClock(long startFakeMillis, LongSupplier realMillis)`, `now()`, `setSpeed(int)`, `speed()`, `set(long)`, `advance(long)`.
  - `Scenario` enum: `RACE_LOST, SOLD_OUT_ALL, PROVIDER_DOWN, ALL_PROVIDERS_DOWN, BANK_SLOW, TURN_PASSED, EVENT_CANCELLED, SALE_CLOSED`.
  - `DevControls`: `scenario(Scenario)`, `speed(int)`, `advance(long seconds)`, `reset()`.
  - `FakeBookingGateway(FakeClock clock)` implements both. Public constants `START`, `SALE_OPENS`, `HOLD_MS`, `ORDER_LIMIT`, `START_AHEAD`, `START_MOVED`, `ADMIT_PER_SECOND`, `TURN_LIMIT_MS`. Event ids `the-1975`, `bruno-mars`, `alan-walker`, `marshmello`. Tier ids `vip`, `zone-a`, `zone-b` (The 1975) and `standard` (others).
  - In this task the hold, payment and order methods throw `UnsupportedOperationException("Task 3")`. Task 3 replaces them.

- [ ] **Step 1: Write the failing clock test**

`src/test/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeClockTest.java`

```java
package th.ac.kmitl.sda.ticketqueue.web.fake;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FakeClockTest {

    @Test
    void runsAtTheChosenSpeedAndCanJump() {
        AtomicLong real = new AtomicLong(1_000);
        FakeClock clock = new FakeClock(50_000, real::get);

        real.addAndGet(2_000);
        assertEquals(52_000, clock.now());

        clock.setSpeed(100);
        real.addAndGet(1_000);
        assertEquals(152_000, clock.now());

        clock.advance(8_000);
        assertEquals(160_000, clock.now());

        clock.set(10);
        assertEquals(10, clock.now());
        assertEquals(100, clock.speed());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q test -Dtest=FakeClockTest`
Expected: compilation FAIL, `cannot find symbol: class FakeClock`.

- [ ] **Step 3: Write `FakeClock`, `Scenario`, `DevControls`**

`src/main/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeClock.java`

```java
package th.ac.kmitl.sda.ticketqueue.web.fake;

import java.util.function.LongSupplier;

/** A clock for the demo. It can run faster than real time and can jump forward. */
public final class FakeClock {

    private final LongSupplier realMillis;
    private long baseFake;
    private long baseReal;
    private int speed = 1;

    public FakeClock(long startFakeMillis, LongSupplier realMillis) {
        this.realMillis = realMillis;
        this.baseFake = startFakeMillis;
        this.baseReal = realMillis.getAsLong();
    }

    public synchronized long now() {
        return baseFake + (realMillis.getAsLong() - baseReal) * speed;
    }

    public synchronized int speed() {
        return speed;
    }

    public synchronized void setSpeed(int times) {
        rebase();
        speed = times;
    }

    public synchronized void set(long fakeMillis) {
        baseFake = fakeMillis;
        baseReal = realMillis.getAsLong();
    }

    public synchronized void advance(long millis) {
        rebase();
        baseFake += millis;
    }

    private void rebase() {
        baseFake = now();
        baseReal = realMillis.getAsLong();
    }
}
```

`src/main/java/th/ac/kmitl/sda/ticketqueue/web/fake/Scenario.java`

```java
package th.ac.kmitl.sda.ticketqueue.web.fake;

/** A hard case the fake can be told to act out, so every screen variant can be shown. */
public enum Scenario {
    RACE_LOST,          // next hold is refused: someone else took the seats (SCR-08f)
    SOLD_OUT_ALL,       // every tier of The 1975 goes to 0 (SCR-05d, SCR-08g)
    PROVIDER_DOWN,      // the card provider stops answering (SCR-12c)
    ALL_PROVIDERS_DOWN, // every provider stops answering (SCR-12d)
    BANK_SLOW,          // payments take 12 seconds to settle instead of 2 (SCR-15b)
    TURN_PASSED,        // the buyer's place in line ends now (SCR-07)
    EVENT_CANCELLED,    // The 1975 is cancelled (SCR-05f, SCR-17b)
    SALE_CLOSED         // The 1975 sale is closed (SCR-05e)
}
```

`src/main/java/th/ac/kmitl/sda/ticketqueue/web/fake/DevControls.java`

```java
package th.ac.kmitl.sda.ticketqueue.web.fake;

/** Demo-only controls. Exposed under /dev only while the fake is switched on. */
public interface DevControls {

    void scenario(Scenario scenario);

    /** Allowed values: 1, 10, 30, 100. */
    void speed(int times);

    void advance(long seconds);

    void reset();
}
```

- [ ] **Step 4: Run the clock test to verify it passes**

Run: `mvn -q test -Dtest=FakeClockTest`
Expected: `Tests run: 1, Failures: 0`.

- [ ] **Step 5: Write `BookingGateway`, `GatewayException`, `Views`**

`src/main/java/th/ac/kmitl/sda/ticketqueue/web/BookingGateway.java`

```java
package th.ac.kmitl.sda.ticketqueue.web;

import th.ac.kmitl.sda.ticketqueue.web.dto.Views.EventDetail;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.EventSummary;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.HoldView;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.JoinResult;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.Me;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.OrderView;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.PaymentStart;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.PaymentStatus;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.QueueStatus;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.TierAvailability;

import java.util.List;

/**
 * Everything the customer pages can ask for. The presentation layer's own adapter, not a plug-in seam.
 * Filled by FakeBookingGateway today and by CoreBookingGateway when core/ is ready.
 * Every method may throw GatewayException.
 */
public interface BookingGateway {

    /** The server's clock, epoch milliseconds. */
    long now();

    List<EventSummary> listEvents(String customerId);                                      // M-01

    EventDetail getEvent(String customerId, String eventId);                               // M-02

    JoinResult joinQueue(String customerId, String eventId);                               // M-03

    QueueStatus queueStatus(String customerId, String token);                              // M-04

    List<TierAvailability> availability(String eventId);                                  // M-05

    HoldView hold(String customerId, String token, String tierId, int quantity);           // M-06

    HoldView getHold(String customerId, String reservationId);                             // M-07

    HoldView release(String customerId, String reservationId);                             // M-08

    PaymentStart startPayment(String customerId, String reservationId, String providerKey); // M-09

    PaymentStatus answerPayment(String customerId, String paymentId, boolean approve);     // M-10

    PaymentStatus paymentStatus(String customerId, String paymentId);                      // M-11

    List<OrderView> orders(String customerId);                                             // M-12

    Me me(String customerId);                                                              // M-13
}
```

`src/main/java/th/ac/kmitl/sda/ticketqueue/web/GatewayException.java`

```java
package th.ac.kmitl.sda.ticketqueue.web;

/** A refusal or error the page must show. status is the HTTP status the routes send. */
public class GatewayException extends RuntimeException {

    public final int status;
    public final String code;
    public final Integer remaining;

    public GatewayException(int status, String code, String message, Integer remaining) {
        super(message);
        this.status = status;
        this.code = code;
        this.remaining = remaining;
    }

    public static GatewayException notFound(String what) {
        return new GatewayException(404, "NOT_FOUND", what + " not found", null);
    }

    public static GatewayException badRequest(String code, String message) {
        return new GatewayException(400, code, message, null);
    }

    public static GatewayException refused(String code, String message, int remaining) {
        return new GatewayException(409, code, message, remaining);
    }
}
```

`src/main/java/th/ac/kmitl/sda/ticketqueue/web/dto/Views.java`

```java
package th.ac.kmitl.sda.ticketqueue.web.dto;

import java.util.List;

/** Every reply shape the page receives. Field names are the JSON names. */
public final class Views {

    private Views() {
    }

    /** M-01. state: SCHEDULED, ON_SALE, CLOSED. */
    public record EventSummary(String eventId, String name, String shortName, String venue,
                               long eventDateTime, long saleOpensAt, String state,
                               long fromPrice, String artwork, boolean soldOut) {
    }

    public record TierPrice(String tierId, String name, long price) {
    }

    /** M-02. state: SCHEDULED, ON_SALE, CLOSED, CANCELLED. myToken is null when the customer is not in line. */
    public record EventDetail(String eventId, String name, String shortName, String venue,
                              long eventDateTime, long saleOpensAt, String state, String artwork,
                              boolean soldOut, List<TierPrice> tiers, String myToken, Integer myPosition) {
    }

    /** M-03. isExisting true means the customer already had a place (SCR-06c). */
    public record JoinResult(String token, String eventId, int position, boolean isExisting) {
    }

    /** M-04. state: WAITING, ADMITTED, SPENT, EXPIRED. */
    public record QueueStatus(String token, String eventId, String state, int position,
                              int moved, int admitPerSecond) {
    }

    /** M-05. */
    public record TierAvailability(String tierId, String name, long price, int remaining, int total) {
    }

    public record EventRef(String eventId, String name, String shortName, String venue, long eventDateTime) {
    }

    /** amount is the discount in baht, a positive number. */
    public record Promotion(String name, long amount) {
    }

    public record PaymentMethod(String key, String tile, String label, String line, boolean available) {
    }

    /** M-06, M-07, M-08. state: HELD, CONFIRMED, EXPIRED, RELEASED. paymentId is the latest payment, or null. */
    public record HoldView(String reservationId, String token, String state, EventRef event,
                           String tierId, String tierName, int quantity, long unitPrice,
                           List<Promotion> promotions, long totalAmount, long expiresAt,
                           int remainingBefore, int remainingAfter,
                           List<PaymentMethod> paymentMethods, String paymentId) {
    }

    /** M-09. display: what the provider wants shown (the QR text, or the simulated card page line). */
    public record PaymentStart(String paymentId, String reservationId, String providerKey,
                               String providerLabel, long amount, String display) {
    }

    /** M-10, M-11. state: PENDING, CAPTURED, DECLINED, VOIDED. answered: the customer pressed pay or a card button. */
    public record PaymentStatus(String paymentId, String reservationId, String providerKey,
                                String state, boolean answered, String orderId) {
    }

    /** state: VALID, CANCELLED. */
    public record TicketView(String ticketId, int n, int of, String payload, String state) {
    }

    /** M-12. eventState: the event's state now. past: the event date has passed. */
    public record OrderView(String orderId, EventRef event, String eventState, String tierId,
                            String tierName, int quantity, long paidAmount, String providerLabel,
                            boolean past, List<TicketView> tickets) {
    }

    /** M-13. */
    public record Me(String customerId, String name, String maskedEmail) {
    }
}
```

- [ ] **Step 6: Write the failing queue test**

`src/test/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeQueueTest.java`

```java
package th.ac.kmitl.sda.ticketqueue.web.fake;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import th.ac.kmitl.sda.ticketqueue.web.GatewayException;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeQueueTest {

    private FakeClock clock;
    private FakeBookingGateway gw;

    @BeforeEach
    void setUp() {
        AtomicLong real = new AtomicLong(0);
        clock = new FakeClock(FakeBookingGateway.START, real::get);
        gw = new FakeBookingGateway(clock);
    }

    @Test
    void listsFourEventsSoonestSaleFirst() {
        var events = gw.listEvents("kenji");
        assertEquals(4, events.size());
        assertEquals("the-1975", events.get(0).eventId());
        assertEquals("SCHEDULED", events.get(0).state());
        assertEquals(1900, events.get(0).fromPrice());
        assertEquals("Impact Arena", events.get(0).venue());
    }

    @Test
    void cannotJoinBeforeTheSaleOpens() {
        var e = assertThrows(GatewayException.class, () -> gw.joinQueue("kenji", "the-1975"));
        assertEquals("NOT_ON_SALE", e.code);
        assertEquals(400, e.status);
    }

    @Test
    void joinGivesAPlaceAndJoiningAgainGivesTheSamePlace() {
        clock.advance(60_000);
        var first = gw.joinQueue("kenji", "the-1975");
        assertEquals(FakeBookingGateway.START_AHEAD, first.position());
        assertFalse(first.isExisting());

        var again = gw.joinQueue("kenji", "the-1975");
        assertEquals(first.token(), again.token());
        assertTrue(again.isExisting());

        var detail = gw.getEvent("kenji", "the-1975");
        assertEquals(first.token(), detail.myToken());
        assertEquals("ON_SALE", detail.state());
        assertNull(gw.getEvent("mint", "the-1975").myToken());
    }

    @Test
    void positionMovesAtTheAdmissionRateUntilAdmitted() {
        clock.advance(60_000);
        var join = gw.joinQueue("kenji", "the-1975");

        clock.advance(10_000);
        var q = gw.queueStatus("kenji", join.token());
        assertEquals("WAITING", q.state());
        assertEquals(4182 - 120, q.position());
        assertEquals(6823 + 120, q.moved());

        clock.advance(340_000);
        assertEquals("ADMITTED", gw.queueStatus("kenji", join.token()).state());
    }

    @Test
    void admittedTurnPassesAfterFiveMinutes() {
        clock.advance(60_000);
        var join = gw.joinQueue("kenji", "the-1975");
        clock.advance(350_000 + FakeBookingGateway.TURN_LIMIT_MS + 1_000);
        assertEquals("EXPIRED", gw.queueStatus("kenji", join.token()).state());
    }

    @Test
    void turnPassedScenarioEndsThePlaceNow() {
        clock.advance(60_000);
        var join = gw.joinQueue("kenji", "the-1975");
        gw.scenario(Scenario.TURN_PASSED);
        assertEquals("EXPIRED", gw.queueStatus("kenji", join.token()).state());
    }

    @Test
    void anotherCustomersTokenIsNotFound() {
        clock.advance(60_000);
        var join = gw.joinQueue("kenji", "the-1975");
        var e = assertThrows(GatewayException.class, () -> gw.queueStatus("mint", join.token()));
        assertEquals(404, e.status);
    }

    @Test
    void cancelledEventLeavesTheList() {
        gw.scenario(Scenario.EVENT_CANCELLED);
        assertEquals(3, gw.listEvents("kenji").size());
        assertEquals("CANCELLED", gw.getEvent("kenji", "the-1975").state());
    }

    @Test
    void resetPutsTheClockBack() {
        gw.speed(100);
        gw.advance(500);
        gw.reset();
        assertEquals(FakeBookingGateway.START, clock.now());
        assertEquals(1, clock.speed());
    }

    @Test
    void speedOnlyAcceptsTheFourValues() {
        assertThrows(GatewayException.class, () -> gw.speed(7));
    }
}
```

- [ ] **Step 7: Run it to verify it fails**

Run: `mvn -q test -Dtest=FakeQueueTest`
Expected: compilation FAIL, `cannot find symbol: class FakeBookingGateway`.

- [ ] **Step 8: Write `FakeBookingGateway` (catalog, queue, me, dev controls)**

`src/main/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeBookingGateway.java`

```java
package th.ac.kmitl.sda.ticketqueue.web.fake;

import th.ac.kmitl.sda.ticketqueue.web.BookingGateway;
import th.ac.kmitl.sda.ticketqueue.web.GatewayException;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.EventDetail;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.EventRef;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.EventSummary;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.HoldView;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.JoinResult;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.Me;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.OrderView;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.PaymentStart;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.PaymentStatus;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.QueueStatus;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.TierAvailability;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.TierPrice;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An in-memory stand-in for core/. Just enough rules to drive every customer screen.
 * Throw-away: replaced by CoreBookingGateway when core/ is ready.
 */
public class FakeBookingGateway implements BookingGateway, DevControls {

    public static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    public static final long SALE_OPENS = at(2026, 10, 1, 10, 0);
    public static final long START = SALE_OPENS - 30_000;           // 09:59:30, so SCR-05a shows first
    public static final long HOLD_MS = 10 * 60_000L;
    public static final int ORDER_LIMIT = 4;
    public static final int START_AHEAD = 4182;
    public static final int START_MOVED = 6823;
    public static final int ADMIT_PER_SECOND = 12;
    public static final long TURN_LIMIT_MS = 5 * 60_000L;
    static final long ADMIT_AFTER_MS = (long) Math.ceil(START_AHEAD * 1000.0 / ADMIT_PER_SECOND);

    final FakeClock clock;
    final Set<Scenario> scenarios = EnumSet.noneOf(Scenario.class);
    final Map<String, Event> events = new LinkedHashMap<>();
    final Map<String, Token> tokens = new LinkedHashMap<>();
    int tokenSeq;

    public FakeBookingGateway(FakeClock clock) {
        this.clock = clock;
        seed();
    }

    static long at(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, BANGKOK).toInstant().toEpochMilli();
    }

    // ---------- data ----------

    static final class Event {
        final String id, name, shortName, venue, artwork;
        final long dateTime, saleOpensAt;
        final List<Tier> tiers = new ArrayList<>();
        boolean cancelled, closed;

        Event(String id, String name, String shortName, String venue, String artwork, long dateTime, long saleOpensAt) {
            this.id = id;
            this.name = name;
            this.shortName = shortName;
            this.venue = venue;
            this.artwork = artwork;
            this.dateTime = dateTime;
            this.saleOpensAt = saleOpensAt;
        }

        Tier tier(String tierId) {
            return tiers.stream().filter(t -> t.id.equals(tierId)).findFirst()
                    .orElseThrow(() -> GatewayException.notFound("Tier"));
        }

        boolean soldOut() {
            return tiers.stream().allMatch(t -> t.remaining() == 0);
        }

        EventRef ref() {
            return new EventRef(id, name, shortName, venue, dateTime);
        }
    }

    static final class Tier {
        final String id, name;
        final long price;
        final int total;
        int held, sold;

        Tier(String id, String name, long price, int total, int sold) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.total = total;
            this.sold = sold;
        }

        int remaining() {
            return total - held - sold;
        }
    }

    static final class Token {
        final String id, eventId, customerId;
        final long issuedAt;
        String state = "WAITING";
        long admittedAt;

        Token(String id, String eventId, String customerId, long issuedAt) {
            this.id = id;
            this.eventId = eventId;
            this.customerId = customerId;
            this.issuedAt = issuedAt;
        }
    }

    void seed() {
        events.clear();
        tokens.clear();
        scenarios.clear();
        tokenSeq = 1000;

        Event e1975 = new Event("the-1975", "The 1975 Bangkok", "THE 1975", "Impact Arena",
                "assets/the-1975-card.png", at(2026, 11, 14, 19, 0), SALE_OPENS);
        e1975.tiers.add(new Tier("vip", "VIP", 6500, 500, 484));
        e1975.tiers.add(new Tier("zone-a", "Zone A", 3800, 2000, 1650));
        e1975.tiers.add(new Tier("zone-b", "Zone B", 1900, 5000, 2980));
        add(e1975);

        add(single("bruno-mars", "Bruno Mars", "BRUNO MARS", "Thunder Dome", "assets/bruno-mars.png",
                at(2026, 11, 22, 20, 0), at(2026, 10, 8, 10, 0), 1200, 3000));
        add(single("alan-walker", "Alan Walker", "ALAN WALKER", "UOB Live", "assets/alan-walker.png",
                at(2026, 12, 5, 20, 0), at(2026, 10, 15, 10, 0), 2400, 4000));
        add(single("marshmello", "Marshmello", "MARSHMELLO", "Moonstar Studio", "assets/marshmello.png",
                at(2026, 12, 18, 20, 0), at(2026, 10, 22, 10, 0), 1500, 2500));
        seedOrders();
    }

    /** Task 3 fills this in with reservations, payments and Kenji's past order. */
    void seedOrders() {
    }

    private static Event single(String id, String name, String shortName, String venue, String art,
                                long date, long sale, long price, int total) {
        Event e = new Event(id, name, shortName, venue, art, date, sale);
        e.tiers.add(new Tier("standard", "Standard", price, total, 0));
        return e;
    }

    private void add(Event e) {
        events.put(e.id, e);
    }

    Event event(String eventId) {
        Event e = events.get(eventId);
        if (e == null) {
            throw GatewayException.notFound("Event");
        }
        return e;
    }

    String state(Event e) {
        if (e.cancelled) {
            return "CANCELLED";
        }
        if (e.closed) {
            return "CLOSED";
        }
        return clock.now() < e.saleOpensAt ? "SCHEDULED" : "ON_SALE";
    }

    Token ownedToken(String customerId, String tokenId) {
        Token t = tokens.get(tokenId);
        if (t == null || !t.customerId.equals(customerId)) {
            throw GatewayException.notFound("Place in line");
        }
        refresh(t);
        return t;
    }

    void refresh(Token t) {
        long now = clock.now();
        if (t.state.equals("WAITING") && now >= t.issuedAt + ADMIT_AFTER_MS) {
            t.state = "ADMITTED";
            t.admittedAt = t.issuedAt + ADMIT_AFTER_MS;
        }
        if (t.state.equals("ADMITTED") && now - t.admittedAt > TURN_LIMIT_MS) {
            t.state = "EXPIRED";
        }
    }

    int admittedSoFar(Token t) {
        long elapsed = Math.max(0, clock.now() - t.issuedAt);
        return (int) Math.min(START_AHEAD, elapsed * ADMIT_PER_SECOND / 1000);
    }

    int position(Token t) {
        return t.state.equals("WAITING") ? Math.max(1, START_AHEAD - admittedSoFar(t)) : 0;
    }

    /** Task 3 overrides the body: expires holds and settles payments that are due. */
    void sweep() {
    }

    // ---------- M-01 to M-04, M-13 ----------

    @Override
    public synchronized long now() {
        return clock.now();
    }

    @Override
    public synchronized List<EventSummary> listEvents(String customerId) {
        sweep();
        return events.values().stream()
                .filter(e -> !e.cancelled)
                .sorted(Comparator.comparingLong(e -> e.saleOpensAt))
                .map(e -> new EventSummary(e.id, e.name, e.shortName, e.venue, e.dateTime, e.saleOpensAt,
                        state(e), e.tiers.stream().mapToLong(t -> t.price).min().orElse(0), e.artwork, e.soldOut()))
                .toList();
    }

    @Override
    public synchronized EventDetail getEvent(String customerId, String eventId) {
        sweep();
        Event e = event(eventId);
        Token mine = activeToken(customerId, eventId);
        return new EventDetail(e.id, e.name, e.shortName, e.venue, e.dateTime, e.saleOpensAt, state(e),
                e.artwork, e.soldOut(),
                e.tiers.stream().map(t -> new TierPrice(t.id, t.name, t.price)).toList(),
                mine == null ? null : mine.id,
                mine == null || !mine.state.equals("WAITING") ? null : position(mine));
    }

    private Token activeToken(String customerId, String eventId) {
        Token found = null;
        for (Token t : tokens.values()) {
            if (t.customerId.equals(customerId) && t.eventId.equals(eventId)) {
                refresh(t);
                if (t.state.equals("WAITING") || t.state.equals("ADMITTED")) {
                    found = t;
                }
            }
        }
        return found;
    }

    @Override
    public synchronized JoinResult joinQueue(String customerId, String eventId) {
        Event e = event(eventId);
        if (!state(e).equals("ON_SALE")) {
            throw GatewayException.badRequest("NOT_ON_SALE", "This sale is not open");
        }
        Token existing = activeToken(customerId, eventId);
        if (existing != null) {
            return new JoinResult(existing.id, eventId, position(existing), true);
        }
        Token t = new Token("Q-" + (++tokenSeq), eventId, customerId, clock.now());
        tokens.put(t.id, t);
        return new JoinResult(t.id, eventId, position(t), false);
    }

    @Override
    public synchronized QueueStatus queueStatus(String customerId, String tokenId) {
        Token t = ownedToken(customerId, tokenId);
        if (scenarios.remove(Scenario.TURN_PASSED)
                && (t.state.equals("WAITING") || t.state.equals("ADMITTED"))) {
            t.state = "EXPIRED";
        }
        return new QueueStatus(t.id, t.eventId, t.state, position(t), START_MOVED + admittedSoFar(t),
                ADMIT_PER_SECOND);
    }

    @Override
    public synchronized Me me(String customerId) {
        if (customerId.equals("kenji")) {
            return new Me(customerId, "Kenji", "k•••••@gmail.com");
        }
        String name = Character.toUpperCase(customerId.charAt(0)) + customerId.substring(1);
        return new Me(customerId, name, customerId.charAt(0) + "•••••@example.com");
    }

    // ---------- M-05 to M-12: filled in by Task 3 ----------

    @Override
    public synchronized List<TierAvailability> availability(String eventId) {
        throw new UnsupportedOperationException("Task 3");
    }

    @Override
    public synchronized HoldView hold(String customerId, String token, String tierId, int quantity) {
        throw new UnsupportedOperationException("Task 3");
    }

    @Override
    public synchronized HoldView getHold(String customerId, String reservationId) {
        throw new UnsupportedOperationException("Task 3");
    }

    @Override
    public synchronized HoldView release(String customerId, String reservationId) {
        throw new UnsupportedOperationException("Task 3");
    }

    @Override
    public synchronized PaymentStart startPayment(String customerId, String reservationId, String providerKey) {
        throw new UnsupportedOperationException("Task 3");
    }

    @Override
    public synchronized PaymentStatus answerPayment(String customerId, String paymentId, boolean approve) {
        throw new UnsupportedOperationException("Task 3");
    }

    @Override
    public synchronized PaymentStatus paymentStatus(String customerId, String paymentId) {
        throw new UnsupportedOperationException("Task 3");
    }

    @Override
    public synchronized List<OrderView> orders(String customerId) {
        throw new UnsupportedOperationException("Task 3");
    }

    // ---------- dev controls ----------

    @Override
    public synchronized void scenario(Scenario scenario) {
        Event e = events.get("the-1975");
        switch (scenario) {
            case EVENT_CANCELLED -> e.cancelled = true;
            case SALE_CLOSED -> e.closed = true;
            case SOLD_OUT_ALL -> e.tiers.forEach(t -> t.sold = t.total - t.held);
            default -> scenarios.add(scenario);
        }
    }

    @Override
    public synchronized void speed(int times) {
        if (times != 1 && times != 10 && times != 30 && times != 100) {
            throw GatewayException.badRequest("BAD_SPEED", "Speed must be 1, 10, 30 or 100");
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
        clock.set(START);
        seed();
    }
}
```

- [ ] **Step 9: Run the queue test to verify it passes**

Run: `mvn -q test -Dtest=FakeQueueTest`
Expected: `Tests run: 10, Failures: 0`.

- [ ] **Step 10: Run all tests**

Run: `mvn -q test`
Expected: `Tests run: 13, Failures: 0`.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/th/ac/kmitl/sda/ticketqueue/web/BookingGateway.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/GatewayException.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/dto/Views.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeClock.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/fake/Scenario.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/fake/DevControls.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeBookingGateway.java src/test/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeClockTest.java src/test/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeQueueTest.java
git commit -m "BookingGateway, reply records, fake clock, fake catalog and queue

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---
### Task 3: Fake holds, payments and orders (M-05 to M-12)

**Lane:** A

**Files:**
- Modify: `src/main/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeBookingGateway.java`
- Test: `src/test/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeBookingTest.java`

**Interfaces:**
- Consumes: everything Task 2 produced.
- Produces: working M-05 to M-12 in the fake. First reservation id `R-1042`, first payment `P-5001`, first new order `O-9001` with tickets `TKT-9F42-01`, `TKT-9F42-02`. Kenji's past order `O-0001` (Bruno Mars, Thunder Dome, 2 Aug 2026 20:00, Zone A, 1 ticket `TKT-3B17-01`, ฿3,800, PromptPay). Payment method keys `promptpay` and `card`. Promotion name `Early Bird −10%` while the hold was made before 10:30 on sale day. Payments settle 2 s after the answer, 12 s with `BANK_SLOW`.

- [ ] **Step 1: Write the failing test**

`src/test/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeBookingTest.java`

```java
package th.ac.kmitl.sda.ticketqueue.web.fake;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import th.ac.kmitl.sda.ticketqueue.web.GatewayException;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.HoldView;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.OrderView;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeBookingTest {

    private FakeClock clock;
    private FakeBookingGateway gw;

    @BeforeEach
    void setUp() {
        AtomicLong real = new AtomicLong(0);
        clock = new FakeClock(FakeBookingGateway.START, real::get);
        gw = new FakeBookingGateway(clock);
        clock.advance(60_000); // sale is open
    }

    /** Joins every customer, then waits once until all are admitted. Returns tokens in order. */
    private List<String> admit(String... customers) {
        List<String> tokens = new java.util.ArrayList<>();
        for (String c : customers) {
            tokens.add(gw.joinQueue(c, "the-1975").token());
        }
        clock.advance(350_000);
        return tokens;
    }

    private int vipLeft() {
        return gw.availability("the-1975").stream().filter(t -> t.tierId().equals("vip")).findFirst().orElseThrow().remaining();
    }

    @Test
    void holdTakesSeatsOutAndStampsTenMinutes() {
        String token = admit("kenji").get(0);
        long before = clock.now();
        HoldView h = gw.hold("kenji", token, "vip", 2);

        assertEquals("R-1042", h.reservationId());
        assertEquals("HELD", h.state());
        assertEquals(16, h.remainingBefore());
        assertEquals(14, h.remainingAfter());
        assertEquals(before + 600_000, h.expiresAt());
        assertEquals(6500, h.unitPrice());
        assertEquals(1, h.promotions().size());
        assertEquals("Early Bird −10%", h.promotions().get(0).name());
        assertEquals(1300, h.promotions().get(0).amount());
        assertEquals(11700, h.totalAmount());
        assertEquals("The 1975 Bangkok", h.event().name());
        assertEquals(14, vipLeft());
        assertEquals("SPENT", gw.queueStatus("kenji", token).state());
    }

    @Test
    void onePlaceInLineHoldsOnce() {
        String token = admit("kenji").get(0);
        gw.hold("kenji", token, "vip", 1);
        var e = assertThrows(GatewayException.class, () -> gw.hold("kenji", token, "vip", 1));
        assertEquals("NOT_ADMITTED", e.code);
    }

    @Test
    void moreThanFourIsRefused() {
        String token = admit("kenji").get(0);
        var e = assertThrows(GatewayException.class, () -> gw.hold("kenji", token, "vip", 5));
        assertEquals("OVER_LIMIT", e.code);
        assertEquals(409, e.status);
    }

    @Test
    void moreThanRemainIsRefusedWithTheCountLeft() {
        List<String> t = admit("kenji", "a", "b", "c", "d");
        gw.hold("a", t.get(1), "vip", 4);
        gw.hold("b", t.get(2), "vip", 4);
        gw.hold("c", t.get(3), "vip", 4);
        gw.hold("d", t.get(4), "vip", 4);
        var e = assertThrows(GatewayException.class, () -> gw.hold("kenji", t.get(0), "vip", 1));
        assertEquals("NOT_ENOUGH", e.code);
        assertEquals(0, e.remaining);
    }

    @Test
    void holdExpiresAfterTenMinutesAndSeatsReturn() {
        HoldView h = gw.hold("kenji", admit("kenji").get(0), "vip", 2);
        clock.advance(600_000);
        assertEquals("EXPIRED", gw.getHold("kenji", h.reservationId()).state());
        assertEquals(16, vipLeft());
    }

    @Test
    void releaseReturnsSeats() {
        HoldView h = gw.hold("kenji", admit("kenji").get(0), "vip", 2);
        assertEquals("RELEASED", gw.release("kenji", h.reservationId()).state());
        assertEquals(16, vipLeft());
        assertThrows(GatewayException.class, () -> gw.release("kenji", h.reservationId()));
    }

    @Test
    void approvedPaymentConfirmsOnceEvenIfAnsweredTwice() {
        HoldView h = gw.hold("kenji", admit("kenji").get(0), "vip", 2);
        var start = gw.startPayment("kenji", h.reservationId(), "promptpay");
        assertEquals("P-5001", start.paymentId());
        assertEquals(11700, start.amount());

        gw.answerPayment("kenji", start.paymentId(), true);
        gw.answerPayment("kenji", start.paymentId(), true);
        assertEquals("PENDING", gw.paymentStatus("kenji", start.paymentId()).state());

        clock.advance(2_000);
        var status = gw.paymentStatus("kenji", start.paymentId());
        assertEquals("CAPTURED", status.state());
        assertEquals("O-9001", status.orderId());
        assertEquals("CONFIRMED", gw.getHold("kenji", h.reservationId()).state());
        assertEquals(14, vipLeft());

        List<OrderView> orders = gw.orders("kenji");
        assertEquals(2, orders.size());
        OrderView bought = orders.get(0);
        assertEquals("O-9001", bought.orderId());
        assertFalse(bought.past());
        assertEquals(2, bought.tickets().size());
        assertEquals("TKT-9F42-01", bought.tickets().get(0).ticketId());
        assertEquals("TKT-9F42-02", bought.tickets().get(1).ticketId());
        assertTrue(bought.tickets().get(0).payload().startsWith("EVENTFLOW|TKT-9F42-01|the-1975|vip|kenji|"));
        assertEquals("PromptPay", bought.providerLabel());
        assertEquals(11700, bought.paidAmount());
    }

    @Test
    void declinedPaymentKeepsTheHold() {
        HoldView h = gw.hold("kenji", admit("kenji").get(0), "vip", 2);
        var start = gw.startPayment("kenji", h.reservationId(), "card");
        gw.answerPayment("kenji", start.paymentId(), false);
        clock.advance(2_000);
        assertEquals("DECLINED", gw.paymentStatus("kenji", start.paymentId()).state());
        assertEquals("HELD", gw.getHold("kenji", h.reservationId()).state());
    }

    @Test
    void approvalAfterTheHoldRanOutIsVoided() {
        HoldView h = gw.hold("kenji", admit("kenji").get(0), "vip", 2);
        var start = gw.startPayment("kenji", h.reservationId(), "promptpay");
        clock.advance(599_000);
        gw.answerPayment("kenji", start.paymentId(), true);
        clock.advance(3_000);
        assertEquals("VOIDED", gw.paymentStatus("kenji", start.paymentId()).state());
        assertEquals("EXPIRED", gw.getHold("kenji", h.reservationId()).state());
        assertEquals(1, gw.orders("kenji").size());
        assertEquals(16, vipLeft());
    }

    @Test
    void raceLostRefusesOnceAndTakesSeats() {
        String token = admit("kenji").get(0);
        gw.scenario(Scenario.RACE_LOST);
        var e = assertThrows(GatewayException.class, () -> gw.hold("kenji", token, "vip", 2));
        assertEquals("RACE_LOST", e.code);
        assertEquals(14, e.remaining);
        assertNotNull(gw.hold("kenji", token, "vip", 2));
    }

    @Test
    void providerDownDimsCardOnly() {
        HoldView h = gw.hold("kenji", admit("kenji").get(0), "vip", 2);
        gw.scenario(Scenario.PROVIDER_DOWN);
        var methods = gw.getHold("kenji", h.reservationId()).paymentMethods();
        assertTrue(methods.get(0).available());
        assertFalse(methods.get(1).available());
        var e = assertThrows(GatewayException.class, () -> gw.startPayment("kenji", h.reservationId(), "card"));
        assertEquals("PROVIDER_UNAVAILABLE", e.code);
    }

    @Test
    void slowBankTakesTwelveSeconds() {
        HoldView h = gw.hold("kenji", admit("kenji").get(0), "vip", 2);
        gw.scenario(Scenario.BANK_SLOW);
        var start = gw.startPayment("kenji", h.reservationId(), "promptpay");
        gw.answerPayment("kenji", start.paymentId(), true);
        clock.advance(2_000);
        assertEquals("PENDING", gw.paymentStatus("kenji", start.paymentId()).state());
        clock.advance(10_000);
        assertEquals("CAPTURED", gw.paymentStatus("kenji", start.paymentId()).state());
    }

    @Test
    void kenjiStartsWithOnePastOrder() {
        List<OrderView> orders = gw.orders("kenji");
        assertEquals(1, orders.size());
        assertEquals("O-0001", orders.get(0).orderId());
        assertTrue(orders.get(0).past());
        assertEquals("TKT-3B17-01", orders.get(0).tickets().get(0).ticketId());
        assertEquals(0, gw.orders("mint").size());
    }

    @Test
    void noPromotionAfterHalfPastTen() {
        clock.advance(30 * 60_000L);
        HoldView h = gw.hold("kenji", admit("kenji").get(0), "vip", 2);
        assertEquals(0, h.promotions().size());
        assertEquals(13000, h.totalAmount());
    }

    @Test
    void cancelledEventMarksTicketsCancelled() {
        HoldView h = gw.hold("kenji", admit("kenji").get(0), "vip", 1);
        var start = gw.startPayment("kenji", h.reservationId(), "promptpay");
        gw.answerPayment("kenji", start.paymentId(), true);
        clock.advance(2_000);
        gw.scenario(Scenario.EVENT_CANCELLED);
        OrderView o = gw.orders("kenji").get(0);
        assertEquals("CANCELLED", o.eventState());
        assertEquals("CANCELLED", o.tickets().get(0).state());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q test -Dtest=FakeBookingTest`
Expected: FAIL with `UnsupportedOperationException: Task 3`.

- [ ] **Step 3: Add the imports**

In `FakeBookingGateway.java`, add these imports next to the existing ones:

```java
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.PaymentMethod;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.Promotion;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.TicketView;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
```

- [ ] **Step 4: Add the state fields**

Directly under the line `int tokenSeq;`, add:

```java
    final Map<String, Reservation> reservations = new LinkedHashMap<>();
    final Map<String, Payment> payments = new LinkedHashMap<>();
    final Map<String, Order> orders = new LinkedHashMap<>();
    int reservationSeq;
    int paymentSeq;
    int orderSeq;

    record Provider(String key, String tile, String label, String line) {
    }

    static final List<Provider> PROVIDERS = List.of(
            new Provider("promptpay", "PP", "PromptPay", "Scan with your banking app"),
            new Provider("card", "CARD", "Credit or debit card", "Visa, Mastercard"));
```

- [ ] **Step 5: Add the 3 record classes**

Directly under the `Token` class, add:

```java
    static final class Reservation {
        final String id, tokenId, customerId, eventId, tierId;
        final int quantity, before, after;
        final long unitPrice, heldAt, expiresAt;
        String state = "HELD";
        String paymentId;

        Reservation(String id, String tokenId, String customerId, String eventId, String tierId, int quantity,
                    long unitPrice, long heldAt, long expiresAt, int before, int after) {
            this.id = id;
            this.tokenId = tokenId;
            this.customerId = customerId;
            this.eventId = eventId;
            this.tierId = tierId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.heldAt = heldAt;
            this.expiresAt = expiresAt;
            this.before = before;
            this.after = after;
        }
    }

    static final class Payment {
        final String id, reservationId, customerId, providerKey;
        final long amount, requestedAt;
        String state = "PENDING";
        boolean answered, approve;
        long settleAt;
        String orderId;

        Payment(String id, String reservationId, String customerId, String providerKey, long amount, long requestedAt) {
            this.id = id;
            this.reservationId = reservationId;
            this.customerId = customerId;
            this.providerKey = providerKey;
            this.amount = amount;
            this.requestedAt = requestedAt;
        }
    }

    static final class Order {
        final String id, customerId, tierId, tierName, providerLabel;
        final EventRef event;
        final int quantity;
        final long paid;
        final List<String> ticketIds = new ArrayList<>();

        Order(String id, String customerId, EventRef event, String tierId, String tierName, int quantity,
              long paid, String providerLabel) {
            this.id = id;
            this.customerId = customerId;
            this.event = event;
            this.tierId = tierId;
            this.tierName = tierName;
            this.quantity = quantity;
            this.paid = paid;
            this.providerLabel = providerLabel;
        }
    }
```

- [ ] **Step 6: Replace `seedOrders()` and `sweep()`**

Replace the empty `seedOrders()` method (and its comment) with:

```java
    void seedOrders() {
        reservations.clear();
        payments.clear();
        orders.clear();
        reservationSeq = 1041;
        paymentSeq = 5000;
        orderSeq = 9000;
        EventRef past = new EventRef("bruno-mars-aug", "Bruno Mars", "BRUNO MARS", "Thunder Dome",
                at(2026, 8, 2, 20, 0));
        Order o = new Order("O-0001", "kenji", past, "zone-a", "Zone A", 1, 3800, "PromptPay");
        o.ticketIds.add("TKT-3B17-01");
        orders.put(o.id, o);
    }
```

Replace the empty `sweep()` method (and its comment) with:

```java
    /** Settles payments that are due and expires holds whose 10 minutes are up. */
    void sweep() {
        for (Reservation r : reservations.values()) {
            refresh(r);
        }
    }

    void refresh(Reservation r) {
        if (r.paymentId != null) {
            settle(payments.get(r.paymentId));
        }
        if (r.state.equals("HELD") && clock.now() >= r.expiresAt) {
            r.state = "EXPIRED";
            tierOf(r).held -= r.quantity;
        }
    }

    void settle(Payment p) {
        if (!p.state.equals("PENDING") || !p.answered || clock.now() < p.settleAt) {
            return;
        }
        Reservation r = reservations.get(p.reservationId);
        if (!p.approve) {
            p.state = "DECLINED";
            return;
        }
        if (p.settleAt >= r.expiresAt || !r.state.equals("HELD")) {
            p.state = "VOIDED";
            if (r.state.equals("HELD")) {
                r.state = "EXPIRED";
                tierOf(r).held -= r.quantity;
            }
            return;
        }
        Tier t = tierOf(r);
        p.state = "CAPTURED";
        r.state = "CONFIRMED";
        t.held -= r.quantity;
        t.sold += r.quantity;
        Order o = new Order("O-" + (++orderSeq), r.customerId, event(r.eventId).ref(), t.id, t.name,
                r.quantity, p.amount, providerLabel(p.providerKey));
        for (int n = 1; n <= r.quantity; n++) {
            o.ticketIds.add(String.format("TKT-%04X-%02d", 0x9F42 + orderSeq - 9001, n));
        }
        orders.put(o.id, o);
        p.orderId = o.id;
    }

    Tier tierOf(Reservation r) {
        return event(r.eventId).tier(r.tierId);
    }

    Reservation ownedHold(String customerId, String reservationId) {
        Reservation r = reservations.get(reservationId);
        if (r == null || !r.customerId.equals(customerId)) {
            throw GatewayException.notFound("Hold");
        }
        return r;
    }

    Payment ownedPayment(String customerId, String paymentId) {
        Payment p = payments.get(paymentId);
        if (p == null || !p.customerId.equals(customerId)) {
            throw GatewayException.notFound("Payment");
        }
        return p;
    }

    boolean available(String providerKey) {
        if (scenarios.contains(Scenario.ALL_PROVIDERS_DOWN)) {
            return false;
        }
        return !(providerKey.equals("card") && scenarios.contains(Scenario.PROVIDER_DOWN));
    }

    String providerLabel(String key) {
        return PROVIDERS.stream().filter(p -> p.key().equals(key)).findFirst().orElseThrow().label();
    }

    List<Promotion> promotions(Reservation r) {
        if (r.heldAt < event(r.eventId).saleOpensAt + 30 * 60_000L) {
            return List.of(new Promotion("Early Bird −10%", Math.round(r.unitPrice * r.quantity * 0.10)));
        }
        return List.of();
    }

    long total(Reservation r) {
        return r.unitPrice * r.quantity - promotions(r).stream().mapToLong(Promotion::amount).sum();
    }

    HoldView view(Reservation r) {
        Tier t = tierOf(r);
        List<PaymentMethod> methods = PROVIDERS.stream()
                .map(p -> new PaymentMethod(p.key(), p.tile(), p.label(), p.line(), available(p.key())))
                .toList();
        return new HoldView(r.id, r.tokenId, r.state, event(r.eventId).ref(), t.id, t.name, r.quantity,
                r.unitPrice, promotions(r), total(r), r.expiresAt, r.before, r.after, methods, r.paymentId);
    }

    PaymentStart start(Payment p) {
        String display = p.providerKey.equals("promptpay")
                ? "PROMPTPAY|" + p.id + "|" + p.amount
                : "Merchant: Event Ticket & Queue";
        return new PaymentStart(p.id, p.reservationId, p.providerKey, providerLabel(p.providerKey), p.amount, display);
    }

    PaymentStatus status(Payment p) {
        return new PaymentStatus(p.id, p.reservationId, p.providerKey, p.state, p.answered, p.orderId);
    }

    OrderView orderView(Order o, long now) {
        Event live = events.get(o.event.eventId());
        String eventState = live == null ? "CLOSED" : state(live);
        String ticketState = eventState.equals("CANCELLED") ? "CANCELLED" : "VALID";
        List<TicketView> tickets = new ArrayList<>();
        for (int i = 0; i < o.ticketIds.size(); i++) {
            String id = o.ticketIds.get(i);
            tickets.add(new TicketView(id, i + 1, o.ticketIds.size(), payload(id, o), ticketState));
        }
        return new OrderView(o.id, o.event, eventState, o.tierId, o.tierName, o.quantity, o.paid,
                o.providerLabel, now > o.event.eventDateTime(), tickets);
    }

    static String payload(String ticketId, Order o) {
        String body = "EVENTFLOW|" + ticketId + "|" + o.event.eventId() + "|" + o.tierId + "|" + o.customerId;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("eventflow-demo-key".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return body + "|" + HexFormat.of().formatHex(digest, 0, 8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
```

- [ ] **Step 7: Replace the 8 Task 3 stub methods**

Replace everything from the comment `// ---------- M-05 to M-12: filled in by Task 3 ----------` down to (not including) `// ---------- dev controls ----------` with:

```java
    // ---------- M-05 to M-12 ----------

    @Override
    public synchronized List<TierAvailability> availability(String eventId) {
        sweep();
        return event(eventId).tiers.stream()
                .map(t -> new TierAvailability(t.id, t.name, t.price, t.remaining(), t.total))
                .toList();
    }

    @Override
    public synchronized HoldView hold(String customerId, String tokenId, String tierId, int quantity) {
        sweep();
        Token t = ownedToken(customerId, tokenId);
        if (!t.state.equals("ADMITTED")) {
            throw GatewayException.badRequest("NOT_ADMITTED", "This place in line cannot hold seats now");
        }
        Event e = event(t.eventId);
        Tier tier = e.tier(tierId);
        if (quantity < 1 || quantity > ORDER_LIMIT) {
            throw GatewayException.refused("OVER_LIMIT", "At most 4 tickets per person", tier.remaining());
        }
        if (e.soldOut()) {
            throw GatewayException.refused("SOLD_OUT", "Every ticket has been sold", 0);
        }
        if (scenarios.remove(Scenario.RACE_LOST)) {
            tier.sold += Math.min(tier.remaining(), 2);
            throw GatewayException.refused("RACE_LOST", "Those seats just went to someone else", tier.remaining());
        }
        if (tier.remaining() < quantity) {
            throw GatewayException.refused("NOT_ENOUGH", "Not enough seats left in this tier", tier.remaining());
        }
        int before = tier.remaining();
        tier.held += quantity;
        long now = clock.now();
        Reservation r = new Reservation("R-" + (++reservationSeq), t.id, customerId, e.id, tier.id, quantity,
                tier.price, now, now + HOLD_MS, before, tier.remaining());
        reservations.put(r.id, r);
        t.state = "SPENT";
        return view(r);
    }

    @Override
    public synchronized HoldView getHold(String customerId, String reservationId) {
        sweep();
        return view(ownedHold(customerId, reservationId));
    }

    @Override
    public synchronized HoldView release(String customerId, String reservationId) {
        sweep();
        Reservation r = ownedHold(customerId, reservationId);
        if (!r.state.equals("HELD")) {
            throw GatewayException.badRequest("HOLD_NOT_ACTIVE", "These seats are no longer held");
        }
        r.state = "RELEASED";
        tierOf(r).held -= r.quantity;
        return view(r);
    }

    @Override
    public synchronized PaymentStart startPayment(String customerId, String reservationId, String providerKey) {
        sweep();
        Reservation r = ownedHold(customerId, reservationId);
        if (!r.state.equals("HELD")) {
            throw GatewayException.badRequest("HOLD_NOT_ACTIVE", "These seats are no longer held");
        }
        Provider provider = PROVIDERS.stream().filter(p -> p.key().equals(providerKey)).findFirst()
                .orElseThrow(() -> GatewayException.notFound("Payment method"));
        if (!available(provider.key())) {
            throw GatewayException.badRequest("PROVIDER_UNAVAILABLE", "This way to pay is not available right now");
        }
        if (r.paymentId != null) {
            Payment last = payments.get(r.paymentId);
            if (last.state.equals("PENDING") && !last.answered && last.providerKey.equals(provider.key())) {
                return start(last);
            }
        }
        Payment p = new Payment("P-" + (++paymentSeq), r.id, customerId, provider.key(), total(r), clock.now());
        payments.put(p.id, p);
        r.paymentId = p.id;
        return start(p);
    }

    @Override
    public synchronized PaymentStatus answerPayment(String customerId, String paymentId, boolean approve) {
        sweep();
        Payment p = ownedPayment(customerId, paymentId);
        if (p.state.equals("PENDING") && !p.answered) {
            p.answered = true;
            p.approve = approve;
            p.settleAt = clock.now() + (scenarios.contains(Scenario.BANK_SLOW) ? 12_000 : 2_000);
        }
        return status(p);
    }

    @Override
    public synchronized PaymentStatus paymentStatus(String customerId, String paymentId) {
        sweep();
        return status(ownedPayment(customerId, paymentId));
    }

    @Override
    public synchronized List<OrderView> orders(String customerId) {
        sweep();
        long now = clock.now();
        return orders.values().stream()
                .filter(o -> o.customerId.equals(customerId))
                .map(o -> orderView(o, now))
                .sorted(Comparator.comparing(OrderView::past)
                        .thenComparingLong(v -> v.past() ? -v.event().eventDateTime() : v.event().eventDateTime()))
                .toList();
    }

```

- [ ] **Step 8: Run the test to verify it passes**

Run: `mvn -q test -Dtest=FakeBookingTest`
Expected: `Tests run: 15, Failures: 0`.

- [ ] **Step 9: Run all tests**

Run: `mvn -q test`
Expected: `Tests run: 28, Failures: 0`.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeBookingGateway.java src/test/java/th/ac/kmitl/sda/ticketqueue/web/fake/FakeBookingTest.java
git commit -m "Fake holds, payments and orders with expiry and void rules

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---
### Task 4: Routes, the envelope, dev routes, the 1-line switch (M-01 to M-13 over HTTP)

**Lane:** A

**Files:**
- Create: `src/main/java/th/ac/kmitl/sda/ticketqueue/web/dto/Requests.java`
- Create: `src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/Reply.java`
- Create: `src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/EventRoutes.java`
- Create: `src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/QueueRoutes.java`
- Create: `src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/HoldRoutes.java`
- Create: `src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/PaymentRoutes.java`
- Create: `src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/MeRoutes.java`
- Create: `src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/DevRoutes.java`
- Modify: `src/main/java/th/ac/kmitl/sda/ticketqueue/web/WebServer.java` (whole file replaced)
- Modify: `src/test/java/th/ac/kmitl/sda/ticketqueue/web/WebServerTest.java` (whole file replaced)
- Test: `src/test/java/th/ac/kmitl/sda/ticketqueue/web/ApiContractTest.java`

**Interfaces:**
- Consumes: `BookingGateway`, `GatewayException`, `Views`, `FakeBookingGateway`, `FakeClock`, `DevControls`, `Scenario` from Tasks 2 and 3.
- Produces: `WebServer.create(BookingGateway gateway, DevControls dev)` (pass `null` for `dev` to hide `/dev`). HTTP endpoints:

| # | Method and path | Body | Success status |
|---|---|---|---|
| M-01 | `GET /api/events` | | 200 |
| M-02 | `GET /api/events/{eventId}` | | 200 |
| M-03 | `POST /api/events/{eventId}/queue` | | 200 |
| M-04 | `GET /api/queue/{token}` | | 200 |
| M-05 | `GET /api/events/{eventId}/availability` | | 200 |
| M-06 | `POST /api/holds` | `{"token","tierId","quantity"}` | 201 |
| M-07 | `GET /api/holds/{reservationId}` | | 200 |
| M-08 | `DELETE /api/holds/{reservationId}` | | 200 |
| M-09 | `POST /api/holds/{reservationId}/payments` | `{"providerKey"}` | 201 |
| M-10 | `POST /api/payments/{paymentId}/answer` | `{"approve"}` | 200 |
| M-11 | `GET /api/payments/{paymentId}` | | 200 |
| M-12 | `GET /api/me/orders` | | 200 |
| M-13 | `GET /api/me` | | 200 |
| dev | `POST /dev/scenario` `/dev/speed` `/dev/advance` `/dev/reset` | `{"scenario"}` `{"times"}` `{"seconds"}` none | 200 |

- [ ] **Step 1: Write the failing contract test**

`src/test/java/th/ac/kmitl/sda/ticketqueue/web/ApiContractTest.java`

```java
package th.ac.kmitl.sda.ticketqueue.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.testtools.JavalinTest;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import th.ac.kmitl.sda.ticketqueue.web.fake.FakeBookingGateway;
import th.ac.kmitl.sda.ticketqueue.web.fake.FakeClock;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One check per message on the menu. The same checks must later pass against CoreBookingGateway. */
class ApiContractTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private FakeClock clock;
    private FakeBookingGateway fake;

    @BeforeEach
    void setUp() {
        AtomicLong real = new AtomicLong(0);
        clock = new FakeClock(FakeBookingGateway.START, real::get);
        fake = new FakeBookingGateway(clock);
    }

    private JsonNode body(Response r) throws IOException {
        return mapper.readTree(r.body().string());
    }

    private static Consumer<Request.Builder> as(String customer) {
        return b -> b.header("X-Customer", customer);
    }

    private String admittedToken() {
        clock.advance(60_000);
        String token = fake.joinQueue("kenji", "the-1975").token();
        clock.advance(350_000);
        return token;
    }

    @Test
    void m01ListsEventsInTheEnvelope() {
        JavalinTest.test(WebServer.create(fake, null), (server, client) -> {
            Response r = client.get("/api/events", as("kenji"));
            assertEquals(200, r.code());
            JsonNode b = body(r);
            assertEquals(FakeBookingGateway.START, b.get("serverNow").asLong());
            assertEquals(4, b.get("data").size());
            assertEquals("the-1975", b.get("data").get(0).get("eventId").asText());
            assertEquals(1900, b.get("data").get(0).get("fromPrice").asLong());
        });
    }

    @Test
    void m02EventDetailAndNotFound() {
        JavalinTest.test(WebServer.create(fake, null), (server, client) -> {
            JsonNode b = body(client.get("/api/events/the-1975", as("kenji")));
            assertEquals(3, b.get("data").get("tiers").size());
            assertTrue(b.get("data").get("myToken").isNull());

            Response missing = client.get("/api/events/nope", as("kenji"));
            assertEquals(404, missing.code());
            JsonNode err = body(missing);
            assertEquals("NOT_FOUND", err.get("error").get("code").asText());
            assertTrue(err.has("serverNow"));
        });
    }

    @Test
    void m03JoinRefusedBeforeSaleThenGivesTheSamePlace() {
        JavalinTest.test(WebServer.create(fake, null), (server, client) -> {
            Response early = client.post("/api/events/the-1975/queue", null, as("kenji"));
            assertEquals(400, early.code());
            assertEquals("NOT_ON_SALE", body(early).get("error").get("code").asText());

            clock.advance(60_000);
            JsonNode first = body(client.post("/api/events/the-1975/queue", null, as("kenji"))).get("data");
            assertEquals("Q-1001", first.get("token").asText());
            assertFalse(first.get("isExisting").asBoolean());
            JsonNode again = body(client.post("/api/events/the-1975/queue", null, as("kenji"))).get("data");
            assertTrue(again.get("isExisting").asBoolean());
        });
    }

    @Test
    void m04QueueStatus() {
        clock.advance(60_000);
        String token = fake.joinQueue("kenji", "the-1975").token();
        JavalinTest.test(WebServer.create(fake, null), (server, client) -> {
            JsonNode q = body(client.get("/api/queue/" + token, as("kenji"))).get("data");
            assertEquals("WAITING", q.get("state").asText());
            assertEquals(4182, q.get("position").asInt());
            assertEquals(12, q.get("admitPerSecond").asInt());
            assertEquals(404, client.get("/api/queue/" + token, as("mint")).code());
        });
    }

    @Test
    void m05Availability() {
        JavalinTest.test(WebServer.create(fake, null), (server, client) -> {
            JsonNode tiers = body(client.get("/api/events/the-1975/availability", as("kenji"))).get("data");
            assertEquals("vip", tiers.get(0).get("tierId").asText());
            assertEquals(16, tiers.get(0).get("remaining").asInt());
            assertEquals(500, tiers.get(0).get("total").asInt());
        });
    }

    @Test
    void m06ToM08HoldReadRelease() {
        String token = admittedToken();
        JavalinTest.test(WebServer.create(fake, null), (server, client) -> {
            Response over = client.post("/api/holds", Map.of("token", token, "tierId", "vip", "quantity", 5), as("kenji"));
            assertEquals(409, over.code());
            JsonNode err = body(over).get("error");
            assertEquals("OVER_LIMIT", err.get("code").asText());
            assertEquals(16, err.get("remaining").asInt());

            Response made = client.post("/api/holds", Map.of("token", token, "tierId", "vip", "quantity", 2), as("kenji"));
            assertEquals(201, made.code());
            JsonNode hold = body(made).get("data");
            assertEquals("R-1042", hold.get("reservationId").asText());
            assertEquals(14, hold.get("remainingAfter").asInt());

            JsonNode read = body(client.get("/api/holds/R-1042", as("kenji"))).get("data");
            assertEquals(11700, read.get("totalAmount").asLong());
            assertEquals(2, read.get("paymentMethods").size());
            assertEquals(clock.now() + 600_000, read.get("expiresAt").asLong());
            assertEquals(404, client.get("/api/holds/R-1042", as("mint")).code());

            Response released = client.request("/api/holds/R-1042", b -> b.delete().header("X-Customer", "kenji"));
            assertEquals("RELEASED", body(released).get("data").get("state").asText());
        });
    }

    @Test
    void m09ToM12PayAndSeeTheOrder() {
        fake.hold("kenji", admittedToken(), "vip", 2);
        JavalinTest.test(WebServer.create(fake, null), (server, client) -> {
            Response started = client.post("/api/holds/R-1042/payments", Map.of("providerKey", "promptpay"), as("kenji"));
            assertEquals(201, started.code());
            assertEquals("P-5001", body(started).get("data").get("paymentId").asText());

            JsonNode answered = body(client.post("/api/payments/P-5001/answer", Map.of("approve", true), as("kenji"))).get("data");
            assertEquals("PENDING", answered.get("state").asText());
            assertTrue(answered.get("answered").asBoolean());

            clock.advance(2_000);
            JsonNode done = body(client.get("/api/payments/P-5001", as("kenji"))).get("data");
            assertEquals("CAPTURED", done.get("state").asText());
            assertEquals("O-9001", done.get("orderId").asText());

            JsonNode orders = body(client.get("/api/me/orders", as("kenji"))).get("data");
            assertEquals(2, orders.size());
            assertEquals(2, orders.get(0).get("tickets").size());
        });
    }

    @Test
    void m13WhoIsBuying() {
        JavalinTest.test(WebServer.create(fake, null), (server, client) -> {
            assertEquals("Kenji", body(client.get("/api/me")).get("data").get("name").asText());
            assertEquals("Mint", body(client.get("/api/me", as("Mint"))).get("data").get("name").asText());
        });
    }

    @Test
    void devRoutesControlTheFake() {
        JavalinTest.test(WebServer.create(fake, fake), (server, client) -> {
            assertEquals(400, client.post("/dev/speed", Map.of("times", 7)).code());
            assertEquals("BAD_SCENARIO", body(client.post("/dev/scenario", Map.of("scenario", "NOPE"))).get("error").get("code").asText());

            JsonNode advanced = body(client.post("/dev/advance", Map.of("seconds", 60)));
            assertEquals(FakeBookingGateway.START + 60_000, advanced.get("serverNow").asLong());

            client.post("/dev/scenario", Map.of("scenario", "EVENT_CANCELLED"));
            assertEquals(3, body(client.get("/api/events")).get("data").size());

            JsonNode reset = body(client.post("/dev/reset"));
            assertEquals(FakeBookingGateway.START, reset.get("serverNow").asLong());
            assertEquals(4, body(client.get("/api/events")).get("data").size());
        });
    }

    @Test
    void devRoutesAreAbsentWithoutTheFake() {
        JavalinTest.test(WebServer.create(fake, null), (server, client) ->
                assertEquals(404, client.post("/dev/reset").code()));
    }
}
```

- [ ] **Step 2: Replace `WebServerTest.java`**

```java
package th.ac.kmitl.sda.ticketqueue.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;
import th.ac.kmitl.sda.ticketqueue.web.fake.FakeBookingGateway;
import th.ac.kmitl.sda.ticketqueue.web.fake.FakeClock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebServerTest {

    @Test
    void servesThePageAndHealth() {
        var fake = new FakeBookingGateway(new FakeClock(FakeBookingGateway.START, System::currentTimeMillis));
        JavalinTest.test(WebServer.create(fake, null), (server, client) -> {
            var page = client.get("/");
            assertEquals(200, page.code());
            assertTrue(page.body().string().contains("EventFlow"));

            var health = client.get("/api/health");
            assertEquals(200, health.code());
            assertEquals("{\"ok\":true}", health.body().string());
        });
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -q test`
Expected: compilation FAIL, `method create in class WebServer cannot be applied to given types`.

- [ ] **Step 4: Write `Requests` and `Reply`**

`src/main/java/th/ac/kmitl/sda/ticketqueue/web/dto/Requests.java`

```java
package th.ac.kmitl.sda.ticketqueue.web.dto;

/** Every request body the page sends. Field names are the JSON names. */
public final class Requests {

    private Requests() {
    }

    public record HoldRequest(String token, String tierId, int quantity) {
    }

    public record PaymentRequest(String providerKey) {
    }

    public record AnswerRequest(boolean approve) {
    }

    public record ScenarioRequest(String scenario) {
    }

    public record SpeedRequest(int times) {
    }

    public record AdvanceRequest(long seconds) {
    }
}
```

`src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/Reply.java`

```java
package th.ac.kmitl.sda.ticketqueue.web.routes;

import io.javalin.http.Context;
import th.ac.kmitl.sda.ticketqueue.web.BookingGateway;
import th.ac.kmitl.sda.ticketqueue.web.GatewayException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Wraps every answer as {serverNow, data} or {serverNow, error}, and reads who is buying. */
public final class Reply {

    private Reply() {
    }

    public static String customer(Context ctx) {
        String raw = ctx.header("X-Customer");
        String id = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
        return id.isEmpty() ? "kenji" : id;
    }

    public static void ok(Context ctx, BookingGateway g, Object data) {
        ok(ctx, g, 200, data);
    }

    public static void ok(Context ctx, BookingGateway g, int status, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serverNow", g.now());
        body.put("data", data);
        ctx.status(status).json(body);
    }

    public static void error(Context ctx, long now, GatewayException e) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", e.code);
        err.put("message", e.getMessage());
        err.put("remaining", e.remaining);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serverNow", now);
        body.put("error", err);
        ctx.status(e.status).json(body);
    }
}
```

- [ ] **Step 5: Write the 6 route classes**

`src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/EventRoutes.java`

```java
package th.ac.kmitl.sda.ticketqueue.web.routes;

import io.javalin.Javalin;
import th.ac.kmitl.sda.ticketqueue.web.BookingGateway;

public final class EventRoutes {

    private EventRoutes() {
    }

    public static void register(Javalin app, BookingGateway g) {
        app.get("/api/events", ctx ->                                                     // M-01
                Reply.ok(ctx, g, g.listEvents(Reply.customer(ctx))));
        app.get("/api/events/{eventId}", ctx ->                                           // M-02
                Reply.ok(ctx, g, g.getEvent(Reply.customer(ctx), ctx.pathParam("eventId"))));
        app.get("/api/events/{eventId}/availability", ctx ->                              // M-05
                Reply.ok(ctx, g, g.availability(ctx.pathParam("eventId"))));
    }
}
```

`src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/QueueRoutes.java`

```java
package th.ac.kmitl.sda.ticketqueue.web.routes;

import io.javalin.Javalin;
import th.ac.kmitl.sda.ticketqueue.web.BookingGateway;

public final class QueueRoutes {

    private QueueRoutes() {
    }

    public static void register(Javalin app, BookingGateway g) {
        app.post("/api/events/{eventId}/queue", ctx ->                                    // M-03
                Reply.ok(ctx, g, g.joinQueue(Reply.customer(ctx), ctx.pathParam("eventId"))));
        app.get("/api/queue/{token}", ctx ->                                              // M-04
                Reply.ok(ctx, g, g.queueStatus(Reply.customer(ctx), ctx.pathParam("token"))));
    }
}
```

`src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/HoldRoutes.java`

```java
package th.ac.kmitl.sda.ticketqueue.web.routes;

import io.javalin.Javalin;
import th.ac.kmitl.sda.ticketqueue.web.BookingGateway;
import th.ac.kmitl.sda.ticketqueue.web.dto.Requests.HoldRequest;
import th.ac.kmitl.sda.ticketqueue.web.dto.Requests.PaymentRequest;

public final class HoldRoutes {

    private HoldRoutes() {
    }

    public static void register(Javalin app, BookingGateway g) {
        app.post("/api/holds", ctx -> {                                                   // M-06
            HoldRequest req = ctx.bodyAsClass(HoldRequest.class);
            Reply.ok(ctx, g, 201, g.hold(Reply.customer(ctx), req.token(), req.tierId(), req.quantity()));
        });
        app.get("/api/holds/{reservationId}", ctx ->                                      // M-07
                Reply.ok(ctx, g, g.getHold(Reply.customer(ctx), ctx.pathParam("reservationId"))));
        app.delete("/api/holds/{reservationId}", ctx ->                                   // M-08
                Reply.ok(ctx, g, g.release(Reply.customer(ctx), ctx.pathParam("reservationId"))));
        app.post("/api/holds/{reservationId}/payments", ctx -> {                          // M-09
            PaymentRequest req = ctx.bodyAsClass(PaymentRequest.class);
            Reply.ok(ctx, g, 201, g.startPayment(Reply.customer(ctx), ctx.pathParam("reservationId"), req.providerKey()));
        });
    }
}
```

`src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/PaymentRoutes.java`

```java
package th.ac.kmitl.sda.ticketqueue.web.routes;

import io.javalin.Javalin;
import th.ac.kmitl.sda.ticketqueue.web.BookingGateway;
import th.ac.kmitl.sda.ticketqueue.web.dto.Requests.AnswerRequest;

public final class PaymentRoutes {

    private PaymentRoutes() {
    }

    public static void register(Javalin app, BookingGateway g) {
        app.post("/api/payments/{paymentId}/answer", ctx -> {                             // M-10
            AnswerRequest req = ctx.bodyAsClass(AnswerRequest.class);
            Reply.ok(ctx, g, g.answerPayment(Reply.customer(ctx), ctx.pathParam("paymentId"), req.approve()));
        });
        app.get("/api/payments/{paymentId}", ctx ->                                       // M-11
                Reply.ok(ctx, g, g.paymentStatus(Reply.customer(ctx), ctx.pathParam("paymentId"))));
    }
}
```

`src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/MeRoutes.java`

```java
package th.ac.kmitl.sda.ticketqueue.web.routes;

import io.javalin.Javalin;
import th.ac.kmitl.sda.ticketqueue.web.BookingGateway;

public final class MeRoutes {

    private MeRoutes() {
    }

    public static void register(Javalin app, BookingGateway g) {
        app.get("/api/me/orders", ctx -> Reply.ok(ctx, g, g.orders(Reply.customer(ctx))));   // M-12
        app.get("/api/me", ctx -> Reply.ok(ctx, g, g.me(Reply.customer(ctx))));             // M-13
    }
}
```

`src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/DevRoutes.java`

```java
package th.ac.kmitl.sda.ticketqueue.web.routes;

import io.javalin.Javalin;
import th.ac.kmitl.sda.ticketqueue.web.BookingGateway;
import th.ac.kmitl.sda.ticketqueue.web.GatewayException;
import th.ac.kmitl.sda.ticketqueue.web.dto.Requests.AdvanceRequest;
import th.ac.kmitl.sda.ticketqueue.web.dto.Requests.ScenarioRequest;
import th.ac.kmitl.sda.ticketqueue.web.dto.Requests.SpeedRequest;
import th.ac.kmitl.sda.ticketqueue.web.fake.DevControls;
import th.ac.kmitl.sda.ticketqueue.web.fake.Scenario;

import java.util.Map;

/** Demo-only. Registered only while the fake is switched on. */
public final class DevRoutes {

    private DevRoutes() {
    }

    public static void register(Javalin app, BookingGateway g, DevControls dev) {
        app.post("/dev/scenario", ctx -> {
            String name = ctx.bodyAsClass(ScenarioRequest.class).scenario();
            Scenario scenario;
            try {
                scenario = Scenario.valueOf(name);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw GatewayException.badRequest("BAD_SCENARIO", "Unknown scenario " + name);
            }
            dev.scenario(scenario);
            Reply.ok(ctx, g, Map.of("scenario", scenario.name()));
        });
        app.post("/dev/speed", ctx -> {
            int times = ctx.bodyAsClass(SpeedRequest.class).times();
            dev.speed(times);
            Reply.ok(ctx, g, Map.of("speed", times));
        });
        app.post("/dev/advance", ctx -> {
            long seconds = ctx.bodyAsClass(AdvanceRequest.class).seconds();
            dev.advance(seconds);
            Reply.ok(ctx, g, Map.of("advanced", seconds));
        });
        app.post("/dev/reset", ctx -> {
            dev.reset();
            Reply.ok(ctx, g, Map.of("reset", true));
        });
    }
}
```

- [ ] **Step 6: Replace `WebServer.java`**

```java
package th.ac.kmitl.sda.ticketqueue.web;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import th.ac.kmitl.sda.ticketqueue.web.fake.DevControls;
import th.ac.kmitl.sda.ticketqueue.web.fake.FakeBookingGateway;
import th.ac.kmitl.sda.ticketqueue.web.fake.FakeClock;
import th.ac.kmitl.sda.ticketqueue.web.routes.DevRoutes;
import th.ac.kmitl.sda.ticketqueue.web.routes.EventRoutes;
import th.ac.kmitl.sda.ticketqueue.web.routes.HoldRoutes;
import th.ac.kmitl.sda.ticketqueue.web.routes.MeRoutes;
import th.ac.kmitl.sda.ticketqueue.web.routes.PaymentRoutes;
import th.ac.kmitl.sda.ticketqueue.web.routes.QueueRoutes;
import th.ac.kmitl.sda.ticketqueue.web.routes.Reply;

import java.util.Map;

/** Starts the Java doorway: serves the page files and answers the page's messages. */
public final class WebServer {

    private WebServer() {
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));
        FakeBookingGateway fake = new FakeBookingGateway(
                new FakeClock(FakeBookingGateway.START, System::currentTimeMillis));

        // THE SWITCH. When core/ is ready: gateway = new CoreBookingGateway(...); dev = null;
        BookingGateway gateway = fake;
        DevControls dev = fake;

        create(gateway, dev).start(port);
    }

    public static Javalin create(BookingGateway gateway, DevControls dev) {
        Javalin app = Javalin.create(config -> config.staticFiles.add("/public", Location.CLASSPATH));
        app.get("/api/health", ctx -> ctx.json(Map.of("ok", true)));
        app.exception(GatewayException.class, (e, ctx) -> Reply.error(ctx, gateway.now(), e));
        EventRoutes.register(app, gateway);
        QueueRoutes.register(app, gateway);
        HoldRoutes.register(app, gateway);
        PaymentRoutes.register(app, gateway);
        MeRoutes.register(app, gateway);
        if (dev != null) {
            DevRoutes.register(app, gateway, dev);
        }
        return app;
    }
}
```

- [ ] **Step 7: Run all tests to verify they pass**

Run: `mvn -q test`
Expected: `Tests run: 38, Failures: 0`.

- [ ] **Step 8: Smoke test by hand**

```bash
mvn -q exec:java &
sleep 4
curl -s localhost:7070/api/events | head -c 200; echo
curl -s -X POST -H 'Content-Type: application/json' -d '{"seconds":60}' localhost:7070/dev/advance; echo
curl -s -X POST localhost:7070/api/events/the-1975/queue; echo
kill %1
```

Expected: the first line starts with `{"serverNow":` and contains `"the-1975"`. The last line contains `"token":"Q-1001"`.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/th/ac/kmitl/sda/ticketqueue/web/dto/Requests.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/Reply.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/EventRoutes.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/QueueRoutes.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/HoldRoutes.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/PaymentRoutes.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/MeRoutes.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/routes/DevRoutes.java src/main/java/th/ac/kmitl/sda/ticketqueue/web/WebServer.java src/test/java/th/ac/kmitl/sda/ticketqueue/web/WebServerTest.java src/test/java/th/ac/kmitl/sda/ticketqueue/web/ApiContractTest.java
git commit -m "Routes for M-01 to M-13, reply envelope, dev routes, gateway switch

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---
### Task D: Document updates (traceability)

**Lane:** B (runs in parallel with Lane A, shares no file with it)

**Files:**
- Create: `docs/09-web-api.md`
- Modify: `docs/02-requirements.md` (§2.6 assumptions table, after the ASM-06 row)
- Modify: `docs/06-architecture.md` (§6.5 tree and dependency rule)
- Modify: `src/README.md` (tree)
- Modify: `docs/07-glossary.md` (6 new entries)
- Modify: `docs/08-screen-requirements.md` (§8.3 Design system)
- Modify: `docs/superpowers/specs/2026-10-10-presentation-layer-design.md` (§4 token line)
- Modify: `README.md` (reading-order table)

**Interfaces:**
- Consumes: the message list and envelope from Task 4's Interfaces table (copied below, no code dependency).
- Produces: documents only.

Every document follows `AGENTS.md` §5: plain words, short sentences, a technical word defined before its first use, the one story.

- [ ] **Step 1: Write `docs/09-web-api.md`**

````markdown
# 09 · Web API

**Part of Deliverable 4 — Implementation**

**The web API is the list of messages the customer page can send to the Java
server, and the answer it gets back for each one.** Both the page and the
booking engine are built against this list. Change it here first.

**Analogy.** A cafe menu. The customer can only order what is on it, and the
kitchen only has to cook what is on it.

**Real example.** Kenji taps **Hold these seats** for 2 VIP. The page sends
M-06 and gets back reservation **R-1042**, which expires at **10:16:50**.

---

## 9.1 Words this document uses

| Word | Plain meaning |
|---|---|
| **Endpoint** | One address on the server that answers one kind of message, like `/api/holds`. |
| **JSON** | A plain text format for data: names and values in curly brackets. |
| **Envelope** | The fixed outer shape every answer has, so the page always knows where to look. |
| **Gateway** | The one Java interface, `BookingGateway`, that the endpoints call. Today a fake fills it. Later `core/` fills it. |

---

## 9.2 Who is asking

The page sends a header `X-Customer: kenji` with every message. There is no
sign-in screen (ASM-01, ASM-08). Opening the page as `?as=mint` makes a second
buyer for the demo.

## 9.3 The envelope

Every answer has the server's clock, `serverNow`, in epoch milliseconds. The
page draws countdowns from it, never from the phone's clock (locked decision 2).

```json
{ "serverNow": 1790827190000, "data": { "...": "..." } }
```

A refusal or error:

```json
{ "serverNow": 1790827190000,
  "error": { "code": "NOT_ENOUGH", "message": "Not enough seats left in this tier", "remaining": 1 } }
```

| Status | Means | Page shows |
|---|---|---|
| 200, 201 | Done | The next screen |
| 400 | The request does not fit the current state | Depends on `code` |
| 404 | Not found, or not this customer's | "This page is not available" |
| 409 | A hold was refused. A normal answer with its own screen | SCR-08e, 08f, 08g |
| no answer, 5xx | The server could not be reached | C-09 *No connection*, keep polling |

## 9.4 The messages

| # | Method and path | Body | Answer `data` | Used by |
|---|---|---|---|---|
| M-01 | `GET /api/events` | | list of `{eventId, name, shortName, venue, eventDateTime, saleOpensAt, state, fromPrice, artwork, soldOut}` | SCR-01 |
| M-02 | `GET /api/events/{eventId}` | | event fields + `tiers[{tierId, name, price}]` + `myToken` + `myPosition` | SCR-05 |
| M-03 | `POST /api/events/{eventId}/queue` | | `{token, eventId, position, isExisting}` | SCR-05, "join again" |
| M-04 | `GET /api/queue/{token}` | | `{token, eventId, state, position, moved, admitPerSecond}`. `state` is `WAITING`, `ADMITTED`, `SPENT` or `EXPIRED` | SCR-06 every 2 s |
| M-05 | `GET /api/events/{eventId}/availability` | | list of `{tierId, name, price, remaining, total}` | SCR-08 |
| M-06 | `POST /api/holds` | `{token, tierId, quantity}` | **201** `HoldView`, or **409** with `code` `NOT_ENOUGH`, `RACE_LOST`, `SOLD_OUT`, `OVER_LIMIT` and `remaining` | SCR-08 |
| M-07 | `GET /api/holds/{reservationId}` | | `HoldView` | SCR-09 to SCR-14, SCR-10, 11, 18 |
| M-08 | `DELETE /api/holds/{reservationId}` | | `HoldView` with `state` `RELEASED` | SCR-09b |
| M-09 | `POST /api/holds/{reservationId}/payments` | `{providerKey}` | **201** `{paymentId, reservationId, providerKey, providerLabel, amount, display}` | SCR-12, 13b, 14b |
| M-10 | `POST /api/payments/{paymentId}/answer` | `{approve}` | `PaymentStatus`. Sending it twice changes nothing (FR-18) | SCR-13, SCR-14 |
| M-11 | `GET /api/payments/{paymentId}` | | `PaymentStatus` = `{paymentId, reservationId, providerKey, state, answered, orderId}`. `state` is `PENDING`, `CAPTURED`, `DECLINED` or `VOIDED` | SCR-15 every 1 s |
| M-12 | `GET /api/me/orders` | | list of `{orderId, event, eventState, tierId, tierName, quantity, paidAmount, providerLabel, past, tickets[{ticketId, n, of, payload, state}]}` | SCR-02, 03, 16, 17 |
| M-13 | `GET /api/me` | | `{customerId, name, maskedEmail}` | SCR-04, SCR-17 |

`HoldView` = `{reservationId, token, state, event{eventId, name, shortName,
venue, eventDateTime}, tierId, tierName, quantity, unitPrice,
promotions[{name, amount}], totalAmount, expiresAt, remainingBefore,
remainingAfter, paymentMethods[{key, tile, label, line, available}],
paymentId}`. `state` is `HELD`, `CONFIRMED`, `EXPIRED` or `RELEASED`.

**Story values.** Kenji holds 2 VIP at 10:06:50: `unitPrice` 6500,
`promotions` `[{"name":"Early Bird −10%","amount":1300}]`, `totalAmount`
11700, `remainingBefore` 16, `remainingAfter` 14, `expiresAt` 10:16:50.

## 9.5 Demo-only messages

These exist only while the fake is switched on (`WebServer.main`).

| Path | Body | Does |
|---|---|---|
| `POST /dev/scenario` | `{scenario}` | Forces a hard case: `RACE_LOST`, `SOLD_OUT_ALL`, `PROVIDER_DOWN`, `ALL_PROVIDERS_DOWN`, `BANK_SLOW`, `TURN_PASSED`, `EVENT_CANCELLED`, `SALE_CLOSED` |
| `POST /dev/speed` | `{times}` | Runs the fake clock 1, 10, 30 or 100 times faster |
| `POST /dev/advance` | `{seconds}` | Jumps the fake clock forward |
| `POST /dev/reset` | | Back to 09:59:30 on 1 October 2026, story data restored |

## 9.6 For the teammate building `core/`

Write `web/CoreBookingGateway.java` that implements `BookingGateway` by
calling the `core/` services. Then change the 2 lines marked `THE SWITCH` in
`WebServer.main`. `ApiContractTest` holds the checks the real gateway must also
pass.
````

- [ ] **Step 2: Add ASM-07 and ASM-08 to `docs/02-requirements.md`**

Insert these 2 rows directly after the `| ASM-06 | ...` row:

```markdown
| ASM-07 | All data lives **in memory** for this project. Nothing is saved to a database, so a restart starts the sale again. | A database would be added behind `core/`. The pages and the web API do not change. |
| ASM-08 | The customer is named by a request header, `X-Customer`, for the demo. There is no sign-in. | A real sign-in would set the same customer id. The web API does not change. |
```

- [ ] **Step 3: Update `docs/06-architecture.md` §6.5**

3a. In the §6.5 code-block tree, change `└── admin/` to `├── admin/`, and start the 2 lines under it with `│   ` instead of 4 spaces. Then, directly after the line ending `UC-07 · listens to every event` and before the closing ```` ``` ````, add:

```
│
└── web/                      ◄── PRESENTATION LAYER. The doorway for the customer pages.
    ├── WebServer.java                serves resources/public/, picks the gateway
    ├── BookingGateway.java           1 interface, every message the pages can send (docs/09)
    ├── routes/                       1 class per area: Event, Queue, Hold, Payment, Me, Dev
    ├── dto/                          the JSON shapes
    └── fake/                         in-memory stand-in for core/, demo only (ASM-07)

src/main/resources/public/    ◄── THE SCREENS. Plain HTML, CSS and JavaScript (SCR-01 to SCR-18).
```

3b. Replace the dependency picture under **The one dependency rule** with:

```
  plugin/  ──depends on──►  contract/  ◄──depends on──  core/  ◄──depends on──  web/

  core/  ──MUST NEVER depend on──►  plugin/
  core/  ──MUST NEVER depend on──►  web/
```

3c. Directly after the paragraph that starts `This is not a guideline.`, add:

```markdown
`web/` is the presentation layer. It may call `core/`, and `core/` never
calls it back. `BookingGateway` is **not** a third plug-in seam: it is the
presentation layer's own adapter, with exactly 2 versions (the fake and the
real one), and it holds no business rule. `ArchitectureRuleTest` fails the
build if `core/` imports `web/` or `plugin/`.
```

- [ ] **Step 4: Update `src/README.md`**

4a. In the tree, directly after the line `└── admin/          organizer: event management + dashboard`, change that line's `└──` to `├──` and add:

```
└── web/            presentation layer: routes, BookingGateway, the fake (docs/09)

src/main/resources/public/   the customer screens (plain HTML, CSS, JavaScript)
```

4b. Replace the block under **The one rule the build must enforce** with:

```
core/  ──MUST NEVER import──►  plugin/
core/  ──MUST NEVER import──►  web/
```

and change the following sentence to: `This is the NFR-08 fitness function. ArchitectureRuleTest fails the build when it is broken.`

4c. Replace the first line under the title, `**Empty on purpose.** Nothing is implemented yet.`, with:

```markdown
**Started.** The presentation layer (`web/` and `resources/public/`) runs on a
fake booking engine. `core/` is still empty and is being built in parallel.
Run it with `mvn -q exec:java` and open http://localhost:7070/.
```

- [ ] **Step 5: Add 6 entries to `docs/07-glossary.md`**

Each entry goes in alphabetical order, as its own block followed by `---`.

Before `### Architecture characteristic`:

```markdown
### API
Short for application programming interface. The list of messages one program
accepts from another, and the answer to each.

*A cafe menu. You can only order what is on it.*

**Example:** the customer page uses 13 messages, M-01 to M-13, listed in `09-web-api.md`.

---

```

Before `### Event`:

```markdown
### Endpoint
One address on the server that answers one kind of message.

**Example:** `POST /api/holds` is the endpoint Kenji's page calls to hold 2 VIP seats.

---

```

Before `### FIFO — first in, first out`:

```markdown
### Fake
A stand-in that behaves like the real part, but with simple made-up rules.
It lets one part be built and tested before the real part exists.

*A cardboard cut-out of a person at a photo stand.*

**Example:** `FakeBookingGateway` holds seats and takes payments in memory
while the real `core/` is still being written.

---

```

Before `### Held quantity`:

```markdown
### Gateway
The single doorway one layer uses to reach another. Swapping what is behind
the doorway does not change the layer in front of it.

*A wall socket. The lamp works the same whichever power station is behind it.*

**Example:** `BookingGateway` is filled by the fake today and by `core/` later.
Only 2 lines in `WebServer` change.

---

```

Before `### Live content`:

```markdown
### JSON
A plain text format for sending data: names and values inside curly brackets.

**Example:** `{"tierId": "vip", "quantity": 2}` is the body of Kenji's hold message.

---

```

Before `### Postcondition`:

```markdown
### Polling
Asking the server the same question again on a timer, to keep a screen up to date.

*Checking the queue number board at a bank every few seconds.*

**Example:** the queue screen asks for Kenji's position every 2 seconds (SCR-06).

---

```

- [ ] **Step 6: Replace the Design system part of `docs/08-screen-requirements.md` §8.3**

Replace everything from the line `### Design system` down to (not including) `### Writing rules for screen text` with:

```markdown
### Design system

The look is **Redesign V1**, the clickable prototype in
`design/TicketApp.dc.html`, look **a**. Its reference screenshots are in
`diagrams/app-flow/screens/`. That prototype is the source of truth for
colours, type, radius and glow. This document does not repeat it.

The three rules from it that every screen here depends on:

- **Lime `#C5F25A` means "move forward".** Primary buttons, the queue bar, the
  countdown, the live dot, the selected tier.
- **Coral `#FF8B7B` means "stop".** Low stock, sold out, errors, and the
  expired screens. Nothing else is coloured.
- **Only the queue bar and the live dot glow.**

Where this document still says cyan, read lime. Where it says magenta, read
coral.

```

- [ ] **Step 7: Update the spec's token line**

In `docs/superpowers/specs/2026-10-10-presentation-layer-design.md` §4, replace the paragraph that starts `**The token.**` with:

```markdown
**The token.** The queue token from M-03 travels in the screen address, for
example `#/queue/the-1975/Q-1001`, and is sent with every purchase message
(locked decision 3). Reopening the event asks M-02, which returns `myToken`,
so the server, not the browser, remembers the place. No `localStorage` is used.
```

- [ ] **Step 8: Add docs/09 to `README.md`**

In the reading-order table, directly after the row for `docs/08-screen-requirements.md`, add:

```markdown
| 9 | [`docs/09-web-api.md`](docs/09-web-api.md) | The 13 messages the customer page sends to the Java server. |
```

- [ ] **Step 9: Check**

Run: `grep -n "ASM-07\|ASM-08" docs/02-requirements.md && grep -n "^### API\|^### Endpoint\|^### Fake\|^### Gateway\|^### JSON\|^### Polling" docs/07-glossary.md && grep -n "web/" docs/06-architecture.md src/README.md | head`
Expected: 2 ASM rows, 6 glossary headings, `web/` lines in both files.

- [ ] **Step 10: Commit**

```bash
git add docs/09-web-api.md docs/02-requirements.md docs/06-architecture.md src/README.md docs/07-glossary.md docs/08-screen-requirements.md docs/superpowers/specs/2026-10-10-presentation-layer-design.md README.md
git commit -m "Docs: web API, ASM-07/08, web/ in the layout, glossary, design source

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---
### Task 5: Page skeleton, shared parts, router, dev panel, screenshot walk

**Lane:** Join (starts after Tasks 4 and D)

**Files:**
- Modify: `src/main/resources/public/index.html` (whole file replaced)
- Create: `src/main/resources/public/css/app.css`
- Create: `src/main/resources/public/assets/` (5 PNG files copied from `design/assets/`)
- Create: `src/main/resources/public/js/api.js`
- Create: `src/main/resources/public/js/parts.js`
- Create: `src/main/resources/public/js/app.js`
- Create: `src/main/resources/public/js/dev.js`
- Create: `src/main/resources/public/js/screens/shell.js`, `event.js`, `queue.js`, `select.js`, `hold.js`, `pay.js`, `tickets.js` (each a 1-line stub, replaced by Tasks 6 to 11)
- Create: `src/test/e2e/walk.mjs`

**Interfaces:**
- Consumes: the HTTP messages and envelope from Task 4.
- Produces (every screen task uses these exact names):
  - `api.js`: `customer`, `devMode`, `serverNow()`, `contactAge()`, `ApiError {status, code, message, remaining}`, and `api.events()`, `api.event(id)`, `api.join(id)`, `api.queue(token)`, `api.availability(id)`, `api.hold(token, tierId, quantity)`, `api.getHold(rid)`, `api.release(rid)`, `api.startPayment(rid, providerKey)`, `api.answer(pid, approve)`, `api.payment(pid)`, `api.orders()`, `api.me()`, `api.dev.scenario(name)`, `api.dev.speed(times)`, `api.dev.advance(seconds)`, `api.dev.reset()`.
  - `parts.js`: `html` (tagged template, escapes values), `raw(s)`, `esc(v)`, `baht`, `num`, `mmss`, `hms`, `time24`, `dateShort`, `dayMonth`, `dateLong`, `dayNum`, `monUpper`, `sameDay`, `WORDS`, `seats(q, tier)`, `barPct`, `liveText`, `liveIndicator()`, `countdown(expiresAt)`, `orderSummary(hold)`, `banner(title, text, good)`, `button({label, action, quiet, danger, disabled, working, data})`, `skeleton()`, `offlineBanner(withRetry)`, `notAvailable(msg)`, `qr(seed, size)`, `stub(order, ticket)`, `slimHeader(title, meta, artwork)`, `tabBar(active)`, `statusScreen({tone, icon, title, text, card, primary, secondary})`, `sheet({title, text, buttons})`.
  - The screen contract in `app.js`. Each `js/screens/*.js` exports `screens`, an array of objects:

```js
{
  id: 'queue',                         // unique name
  pattern: /^\/queue\/([^/]+)$/,       // matched against the address after '#'; groups become params
  enter: async (ctx, ...params) => {}, // optional. Load data into ctx.state. Throwing ApiError is fine.
  poll: { every: 2000, run: async (ctx, ...params) => {} }, // optional. Runs on a timer after enter.
  onZero: async (ctx, ...params) => {},// optional. Runs once when any countdown on screen reaches 0.
  loading: () => html`...`,            // optional. Shown before enter finishes (default: skeleton).
  ownOffline: true,                    // optional. The screen draws its own "no connection" state.
  view: (state, ctx) => html`...`,     // required. Returns the whole screen.
  actions: { name: async (ctx, dataset, ...params) => {} }, // run by clicks on [data-action="name"]
}
```

  - `ctx` has `state` (mutate it, never replace it), `params`, `go(hash, {replace})`, `render()`, `alive()`, `run`.
  - Built-in actions on any element: `data-action="go" data-to="#/events"` navigates. `data-action="retry"` reloads the screen.
  - A countdown is any element with `data-countdown="<epoch ms>"` containing `[data-cd-text]` and optionally `[data-cd-bar]`. Add `data-fmt="hms"` for `hh:mm:ss`. The app updates them every 0.5 s.
- Routes used by the later tasks: `#/events`, `#/tickets`, `#/account`, `#/event/{eventId}`, `#/queue/{eventId}/{token}` and `#/queue/{eventId}/{token}/back`, `#/passed/{eventId}`, `#/select/{eventId}/{token}`, `#/hold/{rid}`, `#/released/{rid}`, `#/ranout/{rid}`, `#/checkout/{rid}`, `#/pay/{rid}/{pid}`, `#/confirming/{rid}/{pid}`, `#/expired/{rid}`, `#/confirmed/{orderId}`, `#/ticket/{orderId}/{n}`.

- [ ] **Step 1: Copy the artwork**

```bash
mkdir -p src/main/resources/public/assets
cp design/assets/the-1975-card.png design/assets/the-1975.png design/assets/bruno-mars.png design/assets/alan-walker.png design/assets/marshmello.png src/main/resources/public/assets/
```

- [ ] **Step 2: Replace `index.html`**

```html
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="theme-color" content="#000000">
<title>EventFlow</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Instrument+Sans:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap">
<link rel="stylesheet" href="css/app.css">
</head>
<body>
<main id="app" aria-live="polite"></main>
<div id="dev"></div>
<script type="module" src="js/app.js"></script>
</body>
</html>
```

- [ ] **Step 3: Write `css/app.css`**

```css
/* Look a of design/TicketApp.dc.html (Redesign V1). Lime means forward, coral means stop. */
:root{
  --bg:#000000;--s1:#111112;--s2:#1A1A1C;--s3:#252527;
  --l1:rgba(255,255,255,.08);--l2:rgba(255,255,255,.18);
  --tx:#F2F2F2;--mu:#8F8F94;
  --ac:#C5F25A;--aci:#000000;--acw:rgba(197,242,90,.09);
  --st:#FF8B7B;--stw:rgba(255,139,123,.11);
  --qr:#F3F6FA;--r:22px;--rb:16px;
  --f:'Instrument Sans',system-ui,sans-serif;--fm:'JetBrains Mono',ui-monospace,monospace;
  color-scheme:dark;
}
*{box-sizing:border-box}
html,body{margin:0;background:var(--bg);color:var(--tx);font:400 15px/1.45 var(--f)}
button{font:inherit;color:inherit;background:none;border:0;padding:0;cursor:pointer}
button:focus-visible,a:focus-visible{outline:2px solid var(--ac);outline-offset:3px}
img{max-width:100%;display:block}
#app{max-width:430px;min-height:100dvh;margin:0 auto;position:relative}

.screen{padding:24px 20px 210px;display:flex;flex-direction:column;gap:16px}
.screen.enter{animation:tq-in .35s cubic-bezier(.2,.8,.2,1) both}
.screen.tabbed{padding-bottom:110px}
.screen.center{min-height:100dvh;justify-content:center;align-items:center;text-align:center;padding-bottom:220px}
.title{font:600 34px/1.1 var(--f);letter-spacing:-.02em;margin:0}
.title.sm{font-size:28px}
.sub{color:var(--mu);font-size:15px;margin:0}
.label{font:600 11px/1 var(--fm);letter-spacing:.1em;color:var(--mu);text-transform:uppercase}
.mono{font-family:var(--fm);font-variant-numeric:tabular-nums}
.hair{border:0;border-top:1px solid var(--l1);margin:0}

.card{background:var(--s1);border:1px solid var(--l1);border-radius:var(--r);padding:16px 18px;display:flex;flex-direction:column;gap:4px}
.card b{font-weight:600;font-size:16px}
.card p{margin:0;color:var(--mu)}
.card.good{background:linear-gradient(var(--acw),var(--acw)),var(--s1)}
.card.good b{color:var(--ac)}
.card.good p{color:var(--tx)}
.banner{background:var(--s1);border:1px solid var(--l1);border-left:3px solid var(--st);border-radius:14px;padding:12px 14px;display:flex;flex-direction:column;gap:2px;text-align:left}
.banner.good{border-left-color:var(--ac)}
.banner b{font-weight:600}
.banner p{margin:0;color:var(--mu);font-size:14px}

.foot{position:fixed;bottom:0;left:50%;transform:translateX(-50%);width:min(100%,430px);padding:14px 20px calc(18px + env(safe-area-inset-bottom,0px));background:linear-gradient(to bottom,transparent,var(--bg) 28%);border-top:1px solid var(--l1);display:flex;flex-direction:column;gap:10px;z-index:10}
.foot.bare{border-top:0;background:none}
.btn{height:54px;width:100%;border-radius:var(--rb);background:var(--ac);color:var(--aci);font:600 17px/1 var(--f);display:flex;align-items:center;justify-content:center;gap:10px;box-shadow:0 10px 30px -8px rgba(197,242,90,.45);transition:transform .15s}
.btn:active{transform:scale(.98)}
.btn.quiet{background:transparent;color:var(--tx);border:1px solid var(--l2);box-shadow:none}
.btn.danger{background:transparent;color:var(--st);border:1px solid var(--st);box-shadow:none}
.btn:disabled{opacity:.5;cursor:default;transform:none}
.btn .spin{width:18px;height:18px;border-radius:50%;border:2px solid currentColor;border-right-color:transparent;animation:tq-spin .8s linear infinite}
.textlink{color:var(--mu);font-weight:500;text-align:center;padding:6px}
.hint{color:var(--mu);font-size:13px;text-align:center;margin:0}
.hint.stop{color:var(--st)}

/* C-04 tier row, and the payment method row */
.pick{display:grid;grid-template-columns:1fr auto;gap:6px 12px;align-items:center;padding:16px 18px;background:var(--s1);border:1px solid var(--l1);border-radius:var(--r);text-align:left;width:100%}
.pick .name{font:600 17px/1.2 var(--f)}
.pick .price{font:600 18px/1 var(--fm);text-align:right}
.pick .left{font:500 12px/1 var(--fm);color:var(--mu)}
.pick .left.low,.pick .left.out{color:var(--st)}
.pick .check{width:22px;height:22px;border-radius:50%;background:var(--ac);color:var(--aci);display:grid;place-items:center;font-size:13px;justify-self:end}
.pick.sel{border:2px solid var(--ac);padding:15px 17px;background:linear-gradient(var(--acw),var(--acw)),var(--s1)}
.pick.sel .price{color:var(--ac)}
.pick:disabled{opacity:.45;cursor:default}
.pick.method{grid-template-columns:auto 1fr auto}
.pick .tile{width:44px;height:44px;border-radius:12px;background:var(--s3);display:grid;place-items:center;font:600 11px/1 var(--fm);grid-row:span 2}

.stepper{display:flex;align-items:center;gap:18px}
.stepper button{width:44px;height:44px;border-radius:50%;border:1px solid var(--l2);font-size:20px;display:grid;place-items:center}
.stepper button:disabled{opacity:.35;cursor:default}
.stepper .n{font:600 24px/1 var(--fm);min-width:20px;text-align:center}
.qty{display:flex;justify-content:space-between;align-items:center}
.total{display:flex;justify-content:space-between;align-items:baseline}
.total .what{font:400 13px var(--fm);color:var(--mu)}
.total .sum{font:600 28px/1 var(--f)}

/* C-02 countdown */
.countdown{border:1.5px solid var(--ac);border-radius:var(--r);padding:16px 18px 14px;display:flex;flex-direction:column;gap:12px;background:linear-gradient(var(--acw),var(--acw)),var(--bg);width:100%}
.countdown .top{display:flex;justify-content:space-between;align-items:center}
.countdown .lbl{display:flex;align-items:center;gap:8px;color:var(--ac);font-weight:500}
.countdown .lbl i{width:8px;height:8px;border-radius:50%;background:var(--ac)}
.countdown .v{font:600 44px/1 var(--fm);color:var(--ac);font-variant-numeric:tabular-nums}
.bar{height:6px;border-radius:3px;background:var(--s3);overflow:hidden}
.bar i{display:block;height:100%;background:var(--ac);border-radius:3px;transition:width .6s linear}

/* C-03 order summary, and the ticket detail rows */
.rows{background:var(--s1);border:1px solid var(--l1);border-radius:var(--r);padding:4px 18px}
.row{display:flex;justify-content:space-between;align-items:baseline;gap:12px;padding:12px 0;border-bottom:1px solid var(--l1)}
.row:last-child{border-bottom:0}
.row .k{color:var(--mu)}
.row .v{font:600 15px var(--fm);text-align:right}
.row .v.txt{font-family:var(--f)}
.row.sum .k{color:var(--tx);font-weight:600}
.row.sum .v{font:600 26px/1 var(--f)}

/* C-01 live indicator */
.live{display:flex;align-items:center;justify-content:center;gap:8px;font:500 12px var(--fm);color:var(--mu)}
.dot{width:9px;height:9px;border-radius:50%;background:var(--ac);box-shadow:0 0 0 4px var(--acw),0 0 12px var(--ac)}
.dot.off{background:var(--mu);box-shadow:none}

/* SCR-06 queue */
.qhero{display:flex;flex-direction:column;align-items:center;gap:6px;padding:48px 0 20px;text-align:center}
.qhero .label{color:var(--ac);letter-spacing:.2em}
.qhero .pos{font:600 104px/1 var(--f);letter-spacing:-.04em;font-variant-numeric:tabular-nums}
.qhero.dim .pos,.qhero.dim .ahead{opacity:.45}
.qbar{height:8px;border-radius:4px;background:var(--s3);overflow:hidden}
.qbar i{display:block;height:100%;background:var(--ac);border-radius:4px;box-shadow:0 0 14px var(--ac);transition:width 1s ease}
.qmeta{display:flex;justify-content:space-between;font:500 13px var(--fm);color:var(--mu)}
.qmeta .eta{color:var(--ac)}
.slim{display:flex;align-items:center;gap:14px;padding-bottom:16px;border-bottom:1px solid var(--l1)}
.slim img,.slim .ph{width:40px;height:40px;border-radius:10px;object-fit:cover;background:var(--s3)}
.slim b{font:700 17px/1.2 var(--f);display:block}
.slim span{font:400 12px var(--fm);color:var(--mu)}

/* SCR-05 event */
.poster{position:relative;margin:-24px -20px 0;aspect-ratio:780/560;max-height:320px;overflow:hidden;background:var(--s2)}
.poster img{width:100%;height:100%;object-fit:cover}
.poster .shade{position:absolute;inset:0;background:linear-gradient(to bottom,rgba(0,0,0,.1),rgba(0,0,0,.2) 40%,var(--bg))}
.poster .txt{position:absolute;left:20px;right:20px;bottom:16px;display:flex;flex-direction:column;gap:6px}
.poster h1{font:700 40px/1 var(--f);letter-spacing:-.02em;text-transform:uppercase;margin:0;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
.poster .where{font:500 12px var(--fm);letter-spacing:.08em;color:var(--mu)}
.back{position:absolute;top:16px;left:16px;width:40px;height:40px;border-radius:50%;background:rgba(0,0,0,.55);display:grid;place-items:center;font-size:20px;z-index:2}
.sale{display:flex;flex-direction:column;align-items:center;gap:6px;padding:18px 0;text-align:center}
.sale .big{font:600 64px/1 var(--fm);letter-spacing:-.03em;text-shadow:0 0 40px var(--acw)}
.sale .big.pos{font-size:44px}
.sale .stop{font:600 22px/1.1 var(--f);color:var(--st)}
.sale .on{display:flex;align-items:center;gap:10px;font:600 15px var(--fm);letter-spacing:.08em;color:var(--ac)}
.sale .opens{font:500 13px var(--fm);color:var(--ac)}
.prices div{display:flex;justify-content:space-between;padding:12px 0;border-bottom:1px solid var(--l1)}
.prices span:last-child{font:500 15px var(--fm)}

/* SCR-01 events */
.feature{position:relative;border-radius:var(--r);overflow:hidden;min-height:300px;display:flex;align-items:flex-end;text-align:left;width:100%;background:var(--s2)}
.feature img{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}
.feature .shade{position:absolute;inset:0;background:linear-gradient(to bottom,transparent 30%,rgba(0,0,0,.9))}
.feature .txt{position:relative;padding:18px;display:flex;flex-direction:column;gap:6px}
.feature h2{font:700 30px/1.05 var(--f);text-transform:uppercase;margin:0}
.feature .meta{font:400 13px var(--fm);color:var(--mu)}
.feature .from{color:var(--ac);font-weight:500}
.pill{align-self:flex-start;font:600 11px/1 var(--fm);letter-spacing:.06em;padding:6px 9px;border-radius:999px;border:1px solid var(--ac);color:var(--ac);white-space:nowrap}
.pill.stop{border-color:var(--st);color:var(--st)}
.pill.grey{border-color:var(--l2);color:var(--mu)}
.erow{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:14px 0;border-bottom:1px solid var(--l1);width:100%;text-align:left}
.erow b{font:600 16px var(--f);display:block}
.erow span{font:400 12px var(--fm);color:var(--mu)}
.erow .p{font:500 15px var(--fm);color:var(--tx)}
.erow .p.stop{color:var(--st)}

/* SCR-02 tickets */
.ocard{display:flex;align-items:center;gap:16px;padding:14px 16px;background:var(--s1);border:1px solid var(--l1);border-radius:var(--r);width:100%;text-align:left}
.ocard.past{opacity:.5}
.ocard .pill{align-self:center}
.ocard .date{width:46px;display:flex;flex-direction:column;align-items:center;gap:4px;padding-right:14px;border-right:1px solid var(--l1)}
.ocard .date b{font:600 24px/1 var(--f)}
.ocard .date span{font:500 10px/1 var(--fm);color:var(--mu);letter-spacing:.08em}
.ocard .what{flex:1;min-width:0;display:flex;flex-direction:column;gap:5px}
.ocard .what b{font:600 16px/1.2 var(--f)}
.ocard .what span{font:400 12px var(--fm);color:var(--mu)}

/* empty states and status screens (SCR-03, 07, 10, 11, 16, 18) */
.ring{width:72px;height:72px;border-radius:50%;border:2px solid var(--mu);display:grid;place-items:center;color:var(--mu);font-size:28px}
.ring.stop{border-color:var(--st);color:var(--st)}
.ring.go{border-color:var(--ac);color:var(--aci);background:var(--ac);animation:tq-pop .5s both}
.empty{display:flex;flex-direction:column;align-items:center;text-align:center;gap:10px;padding:120px 24px 0}
.empty .ring{width:64px;height:64px;border:1.5px dashed var(--l2)}
.status-title{font:600 28px/1.15 var(--f);margin:0;max-width:22ch}
.status-title.stop{color:var(--st)}
.status-text{color:var(--mu);margin:0;max-width:32ch}

/* QR, ticket stub (C-05), ticket (SCR-17) */
.qr{background:var(--qr);border-radius:16px;padding:14px;display:grid;place-items:center;align-self:center}
.qr svg{display:block}
.qrwrap{position:relative;align-self:center}
.qrwrap.void .qr{opacity:.35}
.stamp{position:absolute;inset:0;display:grid;place-items:center;font:700 22px/1 var(--fm);color:var(--st);transform:rotate(-12deg)}
.stub{background:var(--s1);border:1px solid var(--l1);border-radius:var(--r);overflow:hidden;text-align:left}
.stub .top{display:flex;justify-content:space-between;padding:14px 16px;font:500 12px var(--fm);color:var(--mu)}
.stub .top b{color:var(--tx)}
.stub .tear{border-top:1.5px dashed var(--l2);margin:0 12px;position:relative}
.stub .tear::before,.stub .tear::after{content:"";position:absolute;top:-10px;width:20px;height:20px;border-radius:50%;background:var(--bg)}
.stub .tear::before{left:-22px}
.stub .tear::after{right:-22px}
.stub .body{display:flex;gap:16px;padding:16px;align-items:center}
.stub .body .qr{padding:8px;border-radius:10px}
.stub .info{display:flex;flex-direction:column;gap:4px;font-size:13px}
.stub .info .tier{color:var(--ac);font-weight:600}
.stub .info span{color:var(--mu)}
.dots{display:flex;justify-content:center;gap:8px}
.dots button{width:8px;height:8px;border-radius:50%;background:var(--l2)}
.dots button.on{background:var(--ac)}
.screen.past{opacity:.6}

/* tab bar */
.tabbar{position:fixed;bottom:0;left:50%;transform:translateX(-50%);width:min(100%,430px);display:grid;grid-template-columns:repeat(3,1fr);padding:10px 12px calc(14px + env(safe-area-inset-bottom,0px));background:rgba(0,0,0,.92);border-top:1px solid var(--l1);z-index:10}
.tabbar a{display:flex;flex-direction:column;align-items:center;gap:4px;color:var(--mu);text-decoration:none;font:500 11px var(--f);padding:6px}
.tabbar a.on{color:var(--ac)}
.tabbar svg{width:22px;height:22px}

/* SCR-04 account */
.who{display:flex;align-items:center;gap:16px}
.avatar{width:58px;height:58px;border-radius:50%;background:var(--ac);color:var(--aci);display:grid;place-items:center;font:600 24px var(--f)}
.menu{background:var(--s1);border:1px solid var(--l1);border-radius:var(--r)}
.menu button{width:100%;text-align:left;padding:16px 18px;font-weight:500}
.menu button.stop{color:var(--st)}

/* sheets */
.sheet-back{position:fixed;inset:0;background:rgba(0,0,0,.6);z-index:20;animation:tq-fade .2s both}
.sheet{position:fixed;left:50%;bottom:0;transform:translateX(-50%);width:min(100%,430px);background:var(--s2);border-radius:24px 24px 0 0;padding:24px 20px calc(24px + env(safe-area-inset-bottom,0px));display:flex;flex-direction:column;gap:12px;z-index:21;animation:tq-up .3s cubic-bezier(.2,.8,.2,1) both}
.sheet h2{font:600 22px/1.2 var(--f);margin:0}
.sheet p{margin:0;color:var(--mu)}
.sheet .acts{display:flex;flex-direction:column;gap:10px;margin-top:8px}

/* SCR-14 the simulated card provider's own page */
.provider{background:#F7F7F9;color:#1A1A1C;border-radius:16px;padding:20px;display:flex;flex-direction:column;gap:10px}
.provider .brand{font:600 12px var(--fm);letter-spacing:.08em;color:#6B6B73;text-transform:uppercase}
.provider .amt{font:600 28px/1 var(--f)}
.provider p{margin:0;color:#4A4A52;font-size:13px}
.provider .btn{box-shadow:none}
.provider .btn.ok{background:#1A1A1C;color:#FFFFFF}
.provider .btn.no{background:transparent;color:#1A1A1C;border:1px solid #C9C9D0}

/* C-08 loading, the "your turn" flash, SCR-15 spinner */
.skel{background:var(--s2);border-radius:14px;animation:tq-pulse 1.4s ease-in-out infinite}
.flash{position:fixed;inset:0;display:grid;place-items:center;background:rgba(0,0,0,.85);z-index:30;font:600 30px var(--f);color:var(--ac);animation:tq-fade .2s both}
.spinner{width:56px;height:56px;border-radius:50%;border:3px solid var(--acw);border-top-color:var(--ac);animation:tq-spin 1.4s linear infinite}

/* dev panel, only with ?dev */
#dev{position:fixed;top:8px;right:8px;z-index:40;font:500 11px var(--fm)}
#dev .panel{background:#1D2333;color:#DFE6FF;border:1px solid #3A4562;border-radius:10px;padding:8px;display:flex;flex-direction:column;gap:6px;width:190px}
#dev button,#dev select{font:inherit;color:inherit;background:#2A3249;border:1px solid #3A4562;border-radius:6px;padding:4px 6px}
#dev .row2{display:flex;gap:4px;flex-wrap:wrap}

@keyframes tq-spin{to{transform:rotate(360deg)}}
@keyframes tq-pulse{0%,100%{opacity:1}50%{opacity:.45}}
@keyframes tq-up{from{transform:translate(-50%,100%)}to{transform:translate(-50%,0)}}
@keyframes tq-fade{from{opacity:0}to{opacity:1}}
@keyframes tq-in{from{opacity:0;transform:translateY(12px)}to{opacity:1;transform:none}}
@keyframes tq-pop{0%{transform:scale(.3);opacity:0}60%{transform:scale(1.1);opacity:1}100%{transform:scale(1)}}
@media (prefers-reduced-motion:reduce){*{animation:none!important;transition:none!important}}
```

- [ ] **Step 4: Write `js/api.js`**

```js
// The ONLY file that talks to Java. Every answer is {serverNow, data} or {serverNow, error}.
const params = new URLSearchParams(location.search);
export const customer = (params.get('as') || 'kenji').toLowerCase().replace(/[^a-z0-9-]/g, '') || 'kenji';
export const devMode = params.has('dev');

let offset = 0;
let lastContact = 0;

/** The server's clock. Countdowns use this, never the phone's clock. */
export const serverNow = () => Date.now() + offset;
/** Milliseconds since the server last answered. */
export const contactAge = () => (lastContact ? Date.now() - lastContact : 0);

export class ApiError extends Error {
  constructor(status, code, message, remaining) {
    super(message);
    this.status = status;
    this.code = code;
    this.remaining = remaining;
  }
}

async function call(method, path, body) {
  let res;
  try {
    res = await fetch(path, {
      method,
      headers: { 'X-Customer': customer, ...(body ? { 'Content-Type': 'application/json' } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, 'OFFLINE', 'No connection', null);
  }
  let json = null;
  try {
    json = await res.json();
  } catch {
    json = null;
  }
  if (json && typeof json.serverNow === 'number') {
    offset = json.serverNow - Date.now();
    lastContact = Date.now();
  }
  if (!res.ok) {
    const e = (json && json.error) || {};
    throw new ApiError(res.status, e.code || 'HTTP_' + res.status, e.message || 'Request failed', e.remaining ?? null);
  }
  return json.data;
}

const enc = encodeURIComponent;

export const api = {
  events: () => call('GET', '/api/events'),                                              // M-01
  event: (id) => call('GET', `/api/events/${enc(id)}`),                                  // M-02
  join: (id) => call('POST', `/api/events/${enc(id)}/queue`),                            // M-03
  queue: (token) => call('GET', `/api/queue/${enc(token)}`),                             // M-04
  availability: (id) => call('GET', `/api/events/${enc(id)}/availability`),              // M-05
  hold: (token, tierId, quantity) => call('POST', '/api/holds', { token, tierId, quantity }), // M-06
  getHold: (rid) => call('GET', `/api/holds/${enc(rid)}`),                               // M-07
  release: (rid) => call('DELETE', `/api/holds/${enc(rid)}`),                            // M-08
  startPayment: (rid, providerKey) => call('POST', `/api/holds/${enc(rid)}/payments`, { providerKey }), // M-09
  answer: (pid, approve) => call('POST', `/api/payments/${enc(pid)}/answer`, { approve }), // M-10
  payment: (pid) => call('GET', `/api/payments/${enc(pid)}`),                            // M-11
  orders: () => call('GET', '/api/me/orders'),                                           // M-12
  me: () => call('GET', '/api/me'),                                                      // M-13
  dev: {
    scenario: (scenario) => call('POST', '/dev/scenario', { scenario }),
    speed: (times) => call('POST', '/dev/speed', { times }),
    advance: (seconds) => call('POST', '/dev/advance', { seconds }),
    reset: () => call('POST', '/dev/reset'),
  },
};
```

- [ ] **Step 5: Write `js/parts.js`**

```js
// Shared parts C-01 to C-09, formatters and the html helper. No screen logic here.
import { serverNow, contactAge } from './api.js';

// ----- html: a tagged template that escapes every value unless it is raw -----
class Raw {
  constructor(s) { this.s = s; }
  toString() { return this.s; }
}
export const raw = (s) => new Raw(String(s));
const ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
export const esc = (v) => String(v).replace(/[&<>"']/g, (c) => ESC[c]);
function piece(v) {
  if (v instanceof Raw) return v.s;
  if (Array.isArray(v)) return v.map(piece).join('');
  if (v === null || v === undefined || v === false) return '';
  return esc(v);
}
export function html(strings, ...values) {
  let out = strings[0];
  values.forEach((v, i) => { out += piece(v) + strings[i + 1]; });
  return new Raw(out);
}

// ----- formatting: Bangkok time, baht, digits -----
const TZ = 'Asia/Bangkok';
const pad = (n) => String(n).padStart(2, '0');
const fmt = (opts) => new Intl.DateTimeFormat('en-GB', { timeZone: TZ, ...opts });
const fTime = fmt({ hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
const fShort = fmt({ day: 'numeric', month: 'short', year: 'numeric' });
const fDayMonth = fmt({ day: 'numeric', month: 'short' });
const fLong = fmt({ weekday: 'long', day: 'numeric', month: 'long' });
const fDay = fmt({ day: '2-digit' });
const fMon = fmt({ month: 'short' });
const fYmd = fmt({ year: 'numeric', month: '2-digit', day: '2-digit' });

export const baht = (n) => (n === 0 ? 'Free' : '฿' + Math.round(n).toLocaleString('en-US'));
export const num = (n) => Math.round(n).toLocaleString('en-US');
export const mmss = (ms) => { const t = Math.max(0, Math.ceil(ms / 1000)); return pad(Math.floor(t / 60)) + ':' + pad(t % 60); };
export const hms = (ms) => { const t = Math.max(0, Math.ceil(ms / 1000)); return pad(Math.floor(t / 3600)) + ':' + pad(Math.floor(t / 60) % 60) + ':' + pad(t % 60); };
export const time24 = (ms) => fTime.format(ms);
export const dateShort = (ms) => fShort.format(ms);
export const dayMonth = (ms) => fDayMonth.format(ms);
export const dateLong = (ms) => fLong.format(ms).replace(',', '');
export const dayNum = (ms) => fDay.format(ms);
export const monUpper = (ms) => fMon.format(ms).toUpperCase();
export const sameDay = (a, b) => fYmd.format(a) === fYmd.format(b);
export const WORDS = ['', 'One', 'Two', 'Three', 'Four'];
export const seats = (q, tier) => (q === 1 ? `1 ${tier} seat` : `${q} ${tier} seats`);

// ----- C-01 live indicator -----
export function liveText() {
  const age = contactAge();
  if (age > 15000) return { off: true, text: 'Reconnecting…' };
  if (age < 5000) return { off: false, text: 'Updated just now' };
  return { off: false, text: `Updated ${Math.floor(age / 1000)} seconds ago` };
}
export function liveIndicator() {
  const l = liveText();
  return html`<div class="live" data-live><span class="dot ${l.off ? 'off' : ''}"></span><span data-live-text>${l.text}</span></div>`;
}

// ----- C-02 countdown -----
export const barPct = (expiresAt) => Math.max(0, Math.min(100, ((expiresAt - serverNow()) / 600000) * 100));
export function countdown(expiresAt) {
  return html`<div class="countdown" data-countdown="${expiresAt}">
    <div class="top"><span class="lbl"><i></i>Time to pay</span><span class="v" data-cd-text>${mmss(expiresAt - serverNow())}</span></div>
    <div class="bar"><i data-cd-bar style="width:${barPct(expiresAt)}%"></i></div>
  </div>`;
}

// ----- C-03 order summary -----
export function orderSummary(h) {
  return html`<div class="rows">
    <div class="row"><span class="k">Tier</span><span class="v txt">${h.tierName}</span></div>
    <div class="row"><span class="k">Tickets</span><span class="v">${h.quantity}</span></div>
    <div class="row"><span class="k">Price each</span><span class="v">${baht(h.unitPrice)}</span></div>
    ${h.promotions.map((p) => html`<div class="row"><span class="k">${p.name}</span><span class="v">−${baht(p.amount)}</span></div>`)}
    <div class="row sum"><span class="k">Total</span><span class="v">${baht(h.totalAmount)}</span></div>
  </div>`;
}

// ----- C-06 banner -----
export const banner = (title, text, good = false) =>
  html`<div class="banner ${good ? 'good' : ''}" role="status"><b>${title}</b><p>${text}</p></div>`;

// ----- C-07 button: ready, working, disabled -----
export function button({ label, action, quiet = false, danger = false, disabled = false, working = '', data = {} }) {
  const cls = ['btn', quiet && 'quiet', danger && 'danger'].filter(Boolean).join(' ');
  const attrs = Object.entries(data).map(([k, v]) => html` data-${k}="${v}"`);
  const off = disabled || working ? raw('disabled') : '';
  return html`<button class="${cls}" data-action="${action}"${attrs} ${off}>${working ? html`<span class="spin"></span>${working}` : label}</button>`;
}

// ----- C-08 loading placeholder, C-09 connection lost -----
export const skeleton = () => html`<section class="screen">
  <div class="skel" style="height:34px;width:60%"></div><div class="skel" style="height:180px"></div>
  <div class="skel" style="height:64px"></div><div class="skel" style="height:64px"></div></section>`;
export const offlineBanner = (withRetry) => html`<div style="padding:12px 20px 0">
  ${banner('No connection.', 'Your place and your seats are kept by the server. We will reconnect by ourselves.')}
  ${withRetry ? html`<button class="textlink" data-action="retry">Try again</button>` : ''}</div>`;
export const notAvailable = (msg) => html`<section class="screen center"><div class="ring"></div><h1 class="status-title">${msg}</h1></section>
  <div class="foot">${button({ label: 'Back to events', action: 'go', quiet: true, data: { to: '#/events' } })}</div>`;

// ----- QR: a stand-in pattern from a seed. It looks like a QR code; it is not scannable. -----
function seeded(str) {
  let h = 1779033703 ^ str.length;
  for (let i = 0; i < str.length; i++) { h = Math.imul(h ^ str.charCodeAt(i), 3432918353); h = (h << 13) | (h >>> 19); }
  return () => { h = Math.imul(h ^ (h >>> 16), 2246822507); h = Math.imul(h ^ (h >>> 13), 3266489909); h ^= h >>> 16; return (h >>> 0) / 4294967296; };
}
export function qr(seed, size = 200) {
  const N = 25, cell = size / N, rnd = seeded(seed);
  const finder = (r, c) => {
    for (const [or, oc] of [[0, 0], [0, N - 7], [N - 7, 0]]) {
      const dr = r - or, dc = c - oc;
      if (dr >= -1 && dr <= 7 && dc >= -1 && dc <= 7) {
        if (dr < 0 || dr > 6 || dc < 0 || dc > 6) return 0;
        return dr === 0 || dr === 6 || dc === 0 || dc === 6 || (dr >= 2 && dr <= 4 && dc >= 2 && dc <= 4) ? 1 : 0;
      }
    }
    return -1;
  };
  let rects = '';
  for (let r = 0; r < N; r++) {
    for (let c = 0; c < N; c++) {
      const f = finder(r, c);
      if (f === 1 || (f === -1 && rnd() > 0.52)) {
        rects += `<rect x="${(c * cell).toFixed(2)}" y="${(r * cell).toFixed(2)}" width="${cell.toFixed(2)}" height="${cell.toFixed(2)}"/>`;
      }
    }
  }
  return raw(`<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" fill="#0B0B0C" role="img" aria-label="QR code">${rects}</svg>`);
}

// ----- C-05 ticket stub -----
export function stub(o, t) {
  return html`<div class="stub">
    <div class="top"><span>Ticket <b>${t.n}</b> of ${t.of}</span><b class="mono">${t.ticketId}</b></div>
    <div class="tear"></div>
    <div class="body"><div class="qr">${qr(t.payload, 92)}</div>
      <div class="info"><span class="tier">${o.tierName}</span><b>${o.event.name}</b><span>${o.event.venue}</span>
        <span>${dateShort(o.event.eventDateTime)}, ${time24(o.event.eventDateTime)}</span><span class="mono">SINGLE USE</span></div></div>
  </div>`;
}

// ----- headers, tab bar, status screens, sheets -----
export const slimHeader = (title, meta, artwork) => html`<div class="slim">
  ${artwork ? html`<img src="${artwork}" alt="">` : html`<div class="ph"></div>`}<div><b>${title}</b><span>${meta}</span></div></div>`;

const TABS = [
  ['events', 'Events', 'M4 6h16M4 12h16M4 18h10'],
  ['tickets', 'Tickets', 'M4 7h16v4a2 2 0 0 0 0 4v4H4v-4a2 2 0 0 0 0-4z'],
  ['account', 'Account', 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 20a8 8 0 0 1 16 0'],
];
export const tabBar = (active) => html`<nav class="tabbar" aria-label="Main">${TABS.map(([id, label, d]) => html`<a href="#/${id}" class="${id === active ? 'on' : ''}">
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${d}"/></svg>${label}</a>`)}</nav>`;

/** tone: 'stop' (coral), 'calm' (grey), 'go' (lime). card: [title, text]. primary/secondary: button() options. */
export function statusScreen({ tone, icon, title, text, card, primary, secondary }) {
  return html`<section class="screen center">
    <div class="ring ${tone}">${icon}</div>
    <h1 class="status-title ${tone === 'stop' ? 'stop' : ''}">${title}</h1>
    <p class="status-text">${text}</p>
    ${card ? html`<div class="card" style="text-align:left;width:100%"><b>${card[0]}</b><p>${card[1]}</p></div>` : ''}
  </section>
  <div class="foot">${button(primary)}${secondary ? button({ ...secondary, quiet: true }) : ''}</div>`;
}

/** The last button closes the sheet, and so does a tap on the dark backdrop. */
export function sheet({ title, text = [], buttons }) {
  return html`<div class="sheet-back" data-action="${buttons[buttons.length - 1].action}"></div>
  <div class="sheet" role="dialog" aria-modal="true" aria-label="${title}"><h2>${title}</h2>${text.map((t) => html`<p>${t}</p>`)}
    <div class="acts">${buttons.map(button)}</div></div>`;
}
```

- [ ] **Step 6: Write `js/app.js`**

```js
// Picks the screen from the address, loads it, polls it, draws it, and runs its buttons.
import { ApiError, serverNow, devMode } from './api.js';
import { html, skeleton, offlineBanner, notAvailable, mmss, hms, barPct, liveText } from './parts.js';
import { screens as shell } from './screens/shell.js';
import { screens as event } from './screens/event.js';
import { screens as queue } from './screens/queue.js';
import { screens as select } from './screens/select.js';
import { screens as hold } from './screens/hold.js';
import { screens as pay } from './screens/pay.js';
import { screens as tickets } from './screens/tickets.js';
import { mountDev } from './dev.js';

const SCREENS = [...shell, ...event, ...queue, ...select, ...hold, ...pay, ...tickets];
const appEl = document.getElementById('app');
let run = null;

function go(hash, { replace = false } = {}) {
  if (location.hash !== hash) history[replace ? 'replaceState' : 'pushState'](null, '', hash);
  route(true);
}

function stop() {
  if (!run) return;
  run.alive = false;
  clearInterval(run.timer);
  clearTimeout(run.retry);
  run = null;
}

function route(force = false) {
  const path = location.hash.replace(/^#/, '') || '/events';
  if (!force && run && run.path === path) return;
  stop();
  const def = SCREENS.find((d) => d.pattern.test(path));
  if (!def) {
    if (path !== '/events') return go('#/events', { replace: true });
    appEl.innerHTML = String(notAvailable('This page is not available.'));
    return;
  }
  start(def, path.match(def.pattern).slice(1), path);
}

function start(def, params, path) {
  const me = { def, params, path, state: {}, alive: true, loaded: false, animated: false, zeroFired: false };
  run = me;
  me.ctx = { state: me.state, params, run: me, go, render: () => render(me), alive: () => me.alive };
  render(me);
  load(me);
  if (def.poll) {
    me.timer = setInterval(async () => {
      if (!me.loaded || me.busy || me.state.error) return;
      me.busy = true;
      await guard(me, () => def.poll.run(me.ctx, ...params));
      me.busy = false;
      render(me);
    }, def.poll.every);
  }
}

async function load(me) {
  const ok = await guard(me, () => (me.def.enter ? me.def.enter(me.ctx, ...me.params) : null));
  if (!me.alive) return;
  if (ok) {
    me.loaded = true;
    render(me);
  } else if (me.state.offline) {
    me.retry = setTimeout(() => me.alive && load(me), 3000);
  }
}

/** Runs fn. Offline or 5xx marks the screen offline. 404 shows "not available". Returns true when fn worked. */
async function guard(me, fn) {
  try {
    await fn();
    me.state.offline = false;
    return true;
  } catch (e) {
    if (!me.alive) return false;
    if (e instanceof ApiError && (e.code === 'OFFLINE' || e.status >= 500)) {
      me.state.offline = true;
      render(me);
      return false;
    }
    console.error(e);
    me.state.error = e instanceof ApiError && e.status === 404 ? 'missing' : 'failed';
    me.loaded = true;
    render(me);
    return false;
  }
}

function render(me) {
  if (!me.alive) return;
  let body;
  if (me.state.error === 'missing') body = notAvailable('This page is not available.');
  else if (me.state.error === 'failed') body = notAvailable('Something went wrong. Please try again.');
  else if (!me.loaded) body = me.def.loading ? me.def.loading() : skeleton();
  else body = me.def.view(me.state, me.ctx);
  const top = me.state.offline && !me.def.ownOffline ? offlineBanner(!me.def.poll) : '';
  appEl.innerHTML = String(html`${top}${body}`);
  if (me.loaded && !me.animated) {
    me.animated = true;
    appEl.querySelector('.screen')?.classList.add('enter');
  }
  tick();
}

/** Updates countdowns and live dots without redrawing. Fires the screen's onZero once. */
function tick() {
  const now = serverNow();
  let hitZero = false;
  document.querySelectorAll('[data-countdown]').forEach((el) => {
    const end = Number(el.dataset.countdown);
    const left = end - now;
    const f = el.dataset.fmt === 'hms' ? hms : mmss;
    el.querySelectorAll('[data-cd-text]').forEach((t) => { t.textContent = f(left); });
    el.querySelectorAll('[data-cd-bar]').forEach((b) => { b.style.width = barPct(end) + '%'; });
    if (left <= 0) hitZero = true;
  });
  document.querySelectorAll('[data-live]').forEach((el) => {
    const l = liveText();
    el.querySelector('.dot').classList.toggle('off', l.off);
    el.querySelector('[data-live-text]').textContent = l.text;
  });
  const r = run;
  if (hitZero && r && r.loaded && !r.zeroFired && r.def.onZero) {
    r.zeroFired = true;
    guard(r, () => r.def.onZero(r.ctx, ...r.params)).then(() => render(r));
  }
}

document.addEventListener('click', (e) => {
  const el = e.target.closest('[data-action]');
  if (!el || el.disabled || !run || !el.closest('#app')) return;
  const name = el.dataset.action;
  if (name === 'retry') return route(true);
  if (name === 'go') return go(el.dataset.to);
  const fn = run.def.actions && run.def.actions[name];
  if (!fn) return;
  const r = run;
  guard(r, () => fn(r.ctx, el.dataset, ...r.params)).then(() => render(r));
});

window.addEventListener('hashchange', () => route());
setInterval(tick, 500);
if (devMode) mountDev(() => route(true));
route(true);
```

- [ ] **Step 7: Write `js/dev.js`**

```js
// Demo controls, shown only when the address has ?dev. Calls the /dev messages of the fake.
import { api } from './api.js';

const SCENARIOS = ['RACE_LOST', 'SOLD_OUT_ALL', 'PROVIDER_DOWN', 'ALL_PROVIDERS_DOWN', 'BANK_SLOW', 'TURN_PASSED', 'EVENT_CANCELLED', 'SALE_CLOSED'];

export function mountDev(refresh) {
  const el = document.getElementById('dev');
  el.innerHTML = `<div class="panel"><b>Demo controls</b>
    <div class="row2">${[1, 10, 30, 100].map((n) => `<button data-dev="speed" data-n="${n}">×${n}</button>`).join('')}</div>
    <div class="row2"><button data-dev="advance" data-n="60">+1 min</button><button data-dev="advance" data-n="600">+10 min</button></div>
    <select id="dev-scenario" aria-label="Scenario">${SCENARIOS.map((s) => `<option>${s}</option>`).join('')}</select>
    <div class="row2"><button data-dev="scenario">Set scenario</button><button data-dev="reset">Reset</button></div>
    <span id="dev-msg"></span></div>`;
  el.addEventListener('click', async (e) => {
    const b = e.target.closest('[data-dev]');
    if (!b) return;
    const msg = el.querySelector('#dev-msg');
    try {
      const kind = b.dataset.dev;
      if (kind === 'speed') await api.dev.speed(Number(b.dataset.n));
      if (kind === 'advance') await api.dev.advance(Number(b.dataset.n));
      if (kind === 'scenario') await api.dev.scenario(el.querySelector('#dev-scenario').value);
      if (kind === 'reset') {
        await api.dev.reset();
        history.replaceState(null, '', '#/events');
      }
      msg.textContent = 'Done';
      refresh();
    } catch (err) {
      msg.textContent = err.message;
    }
  });
}
```

- [ ] **Step 8: Write the 7 screen stubs**

Each of these files gets exactly this one line. Tasks 6 to 11 replace them.

`js/screens/shell.js`, `js/screens/event.js`, `js/screens/queue.js`, `js/screens/select.js`, `js/screens/hold.js`, `js/screens/pay.js`, `js/screens/tickets.js`:

```js
export const screens = [];
```

- [ ] **Step 9: Write `src/test/e2e/walk.mjs`**

```js
#!/usr/bin/env node
// Screenshot walk. Drives the fake through the web API, then photographs one screen per shot
// into target/shots/<name>.png at 390 x 844. Compare each with diagrams/app-flow/screens/<name>.jpg.
//
//   node src/test/e2e/walk.mjs              every shot
//   node src/test/e2e/walk.mjs 06a 06b      only these shots
//   node src/test/e2e/walk.mjs prep 08e     set up a hand check and print the address to open
//   node src/test/e2e/walk.mjs then 08e     second half of a hand check
//
// The server must be running (mvn -q exec:java). This resets its data.
import { execFileSync } from 'node:child_process';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';

const BASE = process.env.BASE || 'http://localhost:7070';
const T = 'the-1975';
mkdirSync('target/shots', { recursive: true });

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

function shoot(name, as, hash, wait = 1500) {
  execFileSync('npx', ['-y', 'playwright@1.64.0', 'screenshot', '--viewport-size=390,844', `--wait-for-timeout=${wait}`,
    `${BASE}/?as=${as}#${hash}`, `target/shots/${name}.png`], { stdio: 'inherit' });
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
  '01a': async () => { await fresh(); shoot('01a', 'kenji', '/events'); },
  '02': async () => { await open(); await paid('kenji'); shoot('02', 'kenji', '/tickets'); },
  '03': async () => { await fresh(); shoot('03', 'mint', '/tickets'); },
  '04': async () => { await fresh(); shoot('04', 'kenji', '/account'); },
  '05a': async () => { await fresh(); shoot('05a', 'kenji', `/event/${T}`); },
  '05b': async () => { await open(); shoot('05b', 'kenji', `/event/${T}`); },
  '05c': async () => { await open(); await join('kenji'); shoot('05c', 'kenji', `/event/${T}`); },
  '05d': async () => { await open(); await dev.scenario('SOLD_OUT_ALL'); shoot('05d', 'kenji', `/event/${T}`); },
  '05e': async () => { await open(); await dev.scenario('SALE_CLOSED'); shoot('05e', 'kenji', `/event/${T}`); },
  '05f': async () => { await open(); await dev.scenario('EVENT_CANCELLED'); shoot('05f', 'kenji', `/event/${T}`); },
  '06a': async () => { await open(); const t = await join('kenji'); shoot('06a', 'kenji', `/queue/${T}/${t}`); },
  '06b': async () => { await open(); const t = await join('kenji'); await dev.advance(345); shoot('06b', 'kenji', `/queue/${T}/${t}`); },
  '06c': async () => { await open(); const t = await join('kenji'); shoot('06c', 'kenji', `/queue/${T}/${t}/back`); },
  '07': async () => { await open(); shoot('07', 'kenji', `/passed/${T}`); },
  '08a': async () => { await open(); const t = await admitted('kenji'); shoot('08a', 'kenji', `/select/${T}/${t}`); },
  '08d': async () => {
    await open();
    const who = ['kenji', 'a', 'b', 'c', 'd'];
    const tokens = [];
    for (const c of who) tokens.push(await join(c));
    await dev.advance(350);
    for (let i = 1; i < who.length; i++) await call(who[i], 'POST', '/api/holds', { token: tokens[i], tierId: 'vip', quantity: 4 });
    shoot('08d', 'kenji', `/select/${T}/${tokens[0]}`);
  },
  '08g': async () => { await open(); const t = await admitted('kenji'); await dev.scenario('SOLD_OUT_ALL'); shoot('08g', 'kenji', `/select/${T}/${t}`); },
  '09a': async () => { await open(); const h = await held('kenji'); shoot('09a', 'kenji', `/hold/${h.reservationId}`); },
  '10': async () => {
    await open(); const h = await held('kenji');
    await call('kenji', 'DELETE', `/api/holds/${h.reservationId}`);
    shoot('10', 'kenji', `/released/${h.reservationId}`);
  },
  '11': async () => { await open(); const h = await held('kenji'); await dev.advance(601); shoot('11', 'kenji', `/ranout/${h.reservationId}`); },
  '12a': async () => { await open(); await dev.advance(1800); const h = await held('kenji'); shoot('12a', 'kenji', `/checkout/${h.reservationId}`); },
  '12b': async () => { await open(); const h = await held('kenji'); shoot('12b', 'kenji', `/checkout/${h.reservationId}`); },
  '12c': async () => { await open(); const h = await held('kenji'); await dev.scenario('PROVIDER_DOWN'); shoot('12c', 'kenji', `/checkout/${h.reservationId}`); },
  '12d': async () => { await open(); const h = await held('kenji'); await dev.scenario('ALL_PROVIDERS_DOWN'); shoot('12d', 'kenji', `/checkout/${h.reservationId}`); },
  '13a': async () => {
    await open(); const h = await held('kenji'); const p = await startPay('kenji', h.reservationId, 'promptpay');
    shoot('13a', 'kenji', `/pay/${h.reservationId}/${p.paymentId}`);
  },
  '13b': async () => {
    await open(); const h = await held('kenji'); const p = await startPay('kenji', h.reservationId, 'promptpay');
    await answer('kenji', p.paymentId, false); await dev.advance(3);
    shoot('13b', 'kenji', `/pay/${h.reservationId}/${p.paymentId}`);
  },
  '14a': async () => {
    await open(); const h = await held('kenji'); const p = await startPay('kenji', h.reservationId, 'card');
    shoot('14a', 'kenji', `/pay/${h.reservationId}/${p.paymentId}`);
  },
  '14b': async () => {
    await open(); const h = await held('kenji'); const p = await startPay('kenji', h.reservationId, 'card');
    await answer('kenji', p.paymentId, false); await dev.advance(3);
    shoot('14b', 'kenji', `/pay/${h.reservationId}/${p.paymentId}`);
  },
  '15a': async () => {
    await open(); const h = await held('kenji'); await dev.scenario('BANK_SLOW');
    const p = await startPay('kenji', h.reservationId, 'promptpay'); await answer('kenji', p.paymentId, true);
    shoot('15a', 'kenji', `/confirming/${h.reservationId}/${p.paymentId}`);
  },
  '15b': async () => {
    await open(); const h = await held('kenji'); await dev.scenario('BANK_SLOW');
    const p = await startPay('kenji', h.reservationId, 'promptpay'); await answer('kenji', p.paymentId, true);
    shoot('15b', 'kenji', `/confirming/${h.reservationId}/${p.paymentId}`, 9500);
  },
  '16': async () => { await open(); const { orderId } = await paid('kenji'); shoot('16', 'kenji', `/confirmed/${orderId}`); },
  '17a': async () => { await open(); const { orderId } = await paid('kenji'); shoot('17a', 'kenji', `/ticket/${orderId}/1`); },
  '17b': async () => {
    await open(); const { orderId } = await paid('kenji'); await dev.scenario('EVENT_CANCELLED');
    shoot('17b', 'kenji', `/ticket/${orderId}/1`);
  },
  '17c': async () => { await fresh(); shoot('17c', 'kenji', '/ticket/O-0001/1'); },
  '18': async () => {
    await open(); const h = await held('kenji'); const p = await startPay('kenji', h.reservationId, 'promptpay');
    await dev.advance(599); await answer('kenji', p.paymentId, true); await dev.advance(3);
    shoot('18', 'kenji', `/expired/${h.reservationId}`);
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
    writeFileSync('target/shots/prep.json', JSON.stringify({ who, tokens }));
    return `/select/${T}/${tokens[0]}`;
  },
  '09': async () => { await open(); const h = await held('kenji'); return `/hold/${h.reservationId}`; },
  '12': async () => { await open(); const h = await held('kenji'); return `/checkout/${h.reservationId}`; },
};
const then = {
  '08e': async () => {
    const { who, tokens } = JSON.parse(readFileSync('target/shots/prep.json', 'utf8'));
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
```

- [ ] **Step 10: Check the skeleton runs**

```bash
mvn -q exec:java &
sleep 4
curl -s localhost:7070/js/app.js | head -2
node src/test/e2e/walk.mjs 01a
```

Expected: the curl prints the first 2 lines of `app.js`. `target/shots/01a.png` exists and shows "This page is not available." with a "Back to events" button (no screens are registered yet). Open `http://localhost:7070/?dev` in a browser: the dev panel appears top right and the browser console has no errors. Stop the server with `kill %1`.

- [ ] **Step 11: Run the Java tests (the page must still contain "EventFlow")**

Run: `mvn -q test`
Expected: `Tests run: 38, Failures: 0`.

- [ ] **Step 12: Commit**

```bash
git add src/main/resources/public/index.html src/main/resources/public/css/app.css src/main/resources/public/assets src/main/resources/public/js/api.js src/main/resources/public/js/parts.js src/main/resources/public/js/app.js src/main/resources/public/js/dev.js src/main/resources/public/js/screens src/test/e2e/walk.mjs
git commit -m "Page skeleton: shared parts, router, polling, dev panel, screenshot walk

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---
### Task 6: SCR-05 Event

**Lane:** Join

**Files:**
- Modify: `src/main/resources/public/js/screens/event.js` (whole file replaced)

**Interfaces:**
- Consumes: `api.event`, `api.join`, `serverNow` from `api.js`; `html`, `button`, `baht`, `num`, `time24`, `dateShort`, `dateLong`, `hms` from `parts.js`; the screen contract from Task 5.
- Produces: route `#/event/{eventId}`. Join leads to `#/queue/{eventId}/{token}`, or `#/queue/{eventId}/{token}/back` when the server says the place already existed.

Copy for every variant comes from `docs/08-screen-requirements.md` SCR-05.

- [ ] **Step 1: Replace `event.js`**

```js
// SCR-05 Event. Variants: a before sale, b on sale, c already in line, d sold out, e sale closed, f cancelled.
import { api, serverNow } from '../api.js';
import { html, button, baht, num, time24, dateShort, dateLong, hms } from '../parts.js';

function variant(ev) {
  if (ev.state === 'CANCELLED') return 'f';
  if (ev.state === 'CLOSED') return 'e';
  if (ev.soldOut) return 'd';
  if (ev.myToken) return 'c';
  return ev.state === 'SCHEDULED' ? 'a' : 'b';
}

function saleBlock(ev, v) {
  if (v === 'a') {
    return html`<span class="label">Sale opens</span>
      <span class="big">${time24(ev.saleOpensAt)}</span>
      <p class="sub">${dateLong(ev.saleOpensAt)}</p>
      <span class="opens" data-countdown="${ev.saleOpensAt}" data-fmt="hms">Opens in <span data-cd-text>${hms(ev.saleOpensAt - serverNow())}</span></span>`;
  }
  if (v === 'b') return html`<span class="on"><span class="dot"></span>ON SALE NOW</span>`;
  if (v === 'c') {
    return html`<span class="label">You are in the queue</span>
      <span class="big pos">${ev.myPosition ? `Position ${num(ev.myPosition)}` : 'It is your turn'}</span>`;
  }
  if (v === 'd') return html`<span class="stop">SOLD OUT</span><p class="sub">Every ticket has been sold.</p>`;
  if (v === 'e') return html`<span class="stop">SALE CLOSED</span><p class="sub">The organizer has closed this sale.</p>`;
  return html`<span class="stop">EVENT CANCELLED</span>`;
}

function footer(ev, v, st) {
  if (v === 'a') {
    return html`${button({ label: 'Join the queue', action: 'join', disabled: true })}
      <p class="hint">The button turns on at ${time24(ev.saleOpensAt)}</p>`;
  }
  if (v === 'b') {
    return html`${button({ label: 'Join the queue', action: 'join', working: st.joining ? 'Joining…' : '' })}
      <p class="hint">Maximum 4 tickets per person</p>`;
  }
  if (v === 'c') {
    return button({ label: 'Back to my place', action: 'go', data: { to: `#/queue/${ev.eventId}/${ev.myToken}/back` } });
  }
  return button({ label: 'Back to events', action: 'go', quiet: true, data: { to: '#/events' } });
}

const load = async (ctx, eventId) => { ctx.state.ev = await api.event(eventId); };

export const screens = [
  {
    id: 'event',
    pattern: /^\/event\/([^/]+)$/,
    enter: load,
    poll: { every: 5000, run: load },
    async onZero(ctx, eventId) {
      await load(ctx, eventId);
      if (ctx.state.ev.state === 'SCHEDULED') setTimeout(() => { ctx.run.zeroFired = false; }, 2000);
    },
    view(st) {
      const ev = st.ev;
      const v = variant(ev);
      return html`<section class="screen">
        <div class="poster"><img src="${ev.artwork}" alt=""><div class="shade"></div>
          <div class="txt"><h1>${ev.name}</h1><span class="where">${ev.venue.toUpperCase()} · ${dateShort(ev.eventDateTime).toUpperCase()}</span></div>
        </div>
        <button class="back" data-action="go" data-to="#/events" aria-label="Back to events">←</button>
        <div class="sale">${saleBlock(ev, v)}</div>
        <div class="prices">${ev.tiers.map((t) => html`<div><span>${t.name}</span><span>${baht(t.price)}</span></div>`)}</div>
        <div class="card"><b>Everyone waits in the same line.</b><p>Joining early does not help.</p></div>
      </section>
      <div class="foot">${footer(ev, v, st)}</div>`;
    },
    actions: {
      async join(ctx, data, eventId) {
        ctx.state.joining = true;
        ctx.render();
        try {
          const r = await api.join(eventId);
          ctx.go(`#/queue/${eventId}/${r.token}${r.isExisting ? '/back' : ''}`);
        } catch (e) {
          ctx.state.joining = false;
          if (e.code !== 'NOT_ON_SALE') throw e;
          await load(ctx, eventId);
        }
      },
    },
  },
];
```

- [ ] **Step 2: Screenshot check**

```bash
mvn -q exec:java &
sleep 4
node src/test/e2e/walk.mjs 05a 05b 05c 05d 05e 05f
```

Compare each `target/shots/05x.png` with `diagrams/app-flow/screens/05x.jpg`. Check in particular:
- 05a: `10:00` very large, `Thursday 1 October`, `Opens in 00:00:2x` counting, Join disabled, hint `The button turns on at 10:00`.
- 05b: `ON SALE NOW` with a lime dot, Join ready, hint `Maximum 4 tickets per person`.
- 05c: `Position 4,182`, button `Back to my place`.
- 05d, 05e, 05f: coral heading, price rows still shown, quiet `Back to events`.

- [ ] **Step 3: Hand check the switch at 10:00**

Open `http://localhost:7070/?dev#/event/the-1975` after `curl -s -X POST localhost:7070/dev/reset`. Wait 30 seconds without touching the page.
Expected: within 5 seconds of `Opens in 00:00:00`, the page changes to 05b by itself. Tap **Join the queue**: the button shows `Joining…`, then the address becomes `#/queue/the-1975/Q-1001` (the screen there is empty until Task 7). Stop the server with `kill %1`.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/public/js/screens/event.js
git commit -m "SCR-05 Event screen with all 6 variants

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: SCR-06 Queue and SCR-07 Your turn passed

**Lane:** Join

**Files:**
- Modify: `src/main/resources/public/js/screens/queue.js` (whole file replaced)

**Interfaces:**
- Consumes: `api.event`, `api.queue`, `api.join`, `contactAge` from `api.js`; `html`, `num`, `dateShort`, `button`, `banner`, `liveIndicator`, `slimHeader`, `statusScreen` from `parts.js`.
- Produces: routes `#/queue/{eventId}/{token}[/back]` and `#/passed/{eventId}`. Exports `joinAgain(ctx, eventId)`, used by Tasks 9 and 10 for every "Join the queue again" button. When admitted, goes to `#/select/{eventId}/{token}`.

- [ ] **Step 1: Replace `queue.js`**

```js
// SCR-06 Queue (a waiting, b almost your turn, c welcome back, d reconnecting, e joining) and SCR-07 Your turn passed.
import { api, contactAge } from '../api.js';
import { html, num, dateShort, banner, liveIndicator, slimHeader, statusScreen } from '../parts.js';

/** Joins the back of the line again. Used by SCR-07, 10, 11 and 18. */
export async function joinAgain(ctx, eventId) {
  ctx.state.joining = true;
  ctx.render();
  const r = await api.join(eventId);
  ctx.go(`#/queue/${eventId}/${r.token}`);
}

const estimate = (q) =>
  q.position <= 50 ? 'less than a minute' : `about ${Math.max(1, Math.round(q.position / q.admitPerSecond / 60))} min`;

async function check(ctx, eventId, token) {
  const q = await api.queue(token);
  ctx.state.q = q;
  if (q.state === 'ADMITTED' && !ctx.state.flash) {
    ctx.state.flash = true;
    ctx.render();
    setTimeout(() => ctx.alive() && ctx.go(`#/select/${eventId}/${token}`, { replace: true }), 1200);
  } else if (q.state === 'EXPIRED') {
    ctx.go(`#/passed/${eventId}`, { replace: true });
  } else if (q.state === 'SPENT') {
    ctx.go(`#/event/${eventId}`, { replace: true });
  }
}

export const screens = [
  {
    id: 'queue',
    pattern: /^\/queue\/([^/]+)\/([^/]+?)(\/back)?$/,
    ownOffline: true,
    loading: () => html`<section class="screen">
      <div class="qhero"><div class="skel" style="width:220px;height:104px"></div><p class="sub">Getting your place in line…</p></div>
    </section>`,
    async enter(ctx, eventId, token, back) {
      ctx.state.ev = await api.event(eventId);
      if (back) {
        ctx.state.welcome = true;
        setTimeout(() => { ctx.state.welcome = false; ctx.render(); }, 5000);
      }
      await check(ctx, eventId, token);
    },
    poll: { every: 2000, run: (ctx, eventId, token) => check(ctx, eventId, token) },
    view(st) {
      const { ev, q } = st;
      const stale = st.offline || contactAge() > 15000;
      const almost = q.position <= 50;
      const pct = almost ? 99 : Math.round((q.moved / (q.moved + q.position)) * 100);
      const ahead = q.position - 1;
      const card = stale
        ? ['Still holding your place.', 'The server keeps it even if your connection drops.']
        : almost
          ? ['Almost your turn.', 'Get ready to choose your tickets.']
          : ['Your place is saved.', 'Keep this page open. When it is your turn you get 10 minutes to choose and pay.'];
      return html`<section class="screen">
        ${slimHeader(ev.shortName, `${ev.venue} · ${dateShort(ev.eventDateTime)}`, ev.artwork)}
        ${st.welcome ? banner('Welcome back.', 'Your place was kept.', true) : ''}
        <div class="qhero ${stale ? 'dim' : ''}">
          <span class="label">Your position</span>
          <span class="pos">${num(q.position)}</span>
          <span class="sub ahead">${ahead === 1 ? '1 person is ahead of you' : `${num(ahead)} people are ahead of you`}</span>
        </div>
        <div class="qbar"><i style="width:${pct}%"></i></div>
        <div class="qmeta"><span>Moved ${num(q.moved)} places</span><span class="eta">${estimate(q)}</span></div>
        <div class="card"><b>${card[0]}</b><p>${card[1]}</p></div>
      </section>
      <div class="foot bare">${liveIndicator()}</div>
      ${st.flash ? html`<div class="flash" role="status">It is your turn</div>` : ''}`;
    },
  },
  {
    id: 'passed',
    pattern: /^\/passed\/([^/]+)$/,
    enter(ctx, eventId) { ctx.state.eventId = eventId; },
    view: (st) => statusScreen({
      tone: 'calm',
      icon: '',
      title: 'Your turn has passed',
      text: 'You were let in, but no seats were chosen in time, so the next person in line went ahead. Nothing was reserved and nothing was charged.',
      card: ['You can try again.', 'Joining again puts you at the back of the line.'],
      primary: { label: 'Join the queue again', action: 'again', working: st.joining ? 'Joining…' : '' },
      secondary: { label: 'Back to the event', action: 'go', data: { to: `#/event/${st.eventId}` } },
    }),
    actions: { again: (ctx, data, eventId) => joinAgain(ctx, eventId) },
  },
];
```

- [ ] **Step 2: Screenshot check**

```bash
mvn -q exec:java &
sleep 4
node src/test/e2e/walk.mjs 06a 06b 06c 07
```

Compare with `diagrams/app-flow/screens/06a.jpg`, `06b.jpg`, `06c.jpg`, `07.jpg`. Check:
- 06a: `4,182` is the largest thing on screen, `4,181 people are ahead of you`, `Moved 6,823 places`, `about 6 min` in lime, `Updated just now` at the bottom. No refresh button, no prices, no countdown.
- 06b: position under 50, `less than a minute`, card `Almost your turn.`
- 06c: lime-edged banner `Welcome back.` on top.
- 07: grey ring, `Your turn has passed`, 2 buttons.

- [ ] **Step 3: Hand check admission and reconnecting**

1. `curl -s -X POST -H 'Content-Type: application/json' -d '{"seconds":60}' localhost:7070/dev/advance`, then open `http://localhost:7070/?dev#/event/the-1975` and tap **Join the queue**. In the dev panel tap **×100**.
   Expected: the position falls every 2 seconds. In about 4 seconds the lime `It is your turn` flash shows, then the address becomes `#/select/the-1975/Q-1001`.
2. Join again with `?as=mint` in a second tab. Stop the server (`kill %1`) and wait 16 seconds.
   Expected (06d): numbers dim, the dot turns grey, the text reads `Reconnecting…`, the card reads `Still holding your place.` There is no retry button.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/public/js/screens/queue.js
git commit -m "SCR-06 Queue with live position, SCR-07 Your turn passed

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: SCR-08 Select tickets

**Lane:** Join

**Files:**
- Modify: `src/main/resources/public/js/screens/select.js` (whole file replaced)

**Interfaces:**
- Consumes: `api.availability`, `api.hold`, `ApiError` fields `status`, `code`, `remaining`; `html`, `raw`, `baht`, `num`, `button`, `banner` from `parts.js`.
- Produces: route `#/select/{eventId}/{token}`. A successful hold goes to `#/hold/{reservationId}`. `NOT_ADMITTED` goes to `#/passed/{eventId}`.

- [ ] **Step 1: Replace `select.js`**

```js
// SCR-08 Select tickets. Variants: a default, b holding, c at the limit of 4, d tier sold out,
// e more than remain, f seats just went, g everything sold out.
import { api } from '../api.js';
import { html, raw, baht, num, button, banner } from '../parts.js';

const LIMIT = 4;
const low = (t) => t.remaining > 0 && t.remaining <= t.total * 0.05; // OQ-04: 5% or less
const maxFor = (t) => Math.min(LIMIT, t.remaining);

/** Keeps the selection valid after the counts change. sel becomes -1 when nothing is left. */
function fit(st) {
  if (!st.tiers[st.sel] || st.tiers[st.sel].remaining === 0) st.sel = st.tiers.findIndex((t) => t.remaining > 0);
  if (st.sel >= 0) st.qty = Math.max(1, Math.min(st.qty, maxFor(st.tiers[st.sel])));
}

async function refresh(ctx, eventId) {
  ctx.state.tiers = await api.availability(eventId);
  fit(ctx.state);
}

function tierRow(t, i, st) {
  const out = t.remaining === 0;
  const sel = i === st.sel;
  const left = out ? 'Sold out' : low(t) ? `only ${num(t.remaining)} left` : `${num(t.remaining)} left`;
  return html`<button class="pick ${sel ? 'sel' : ''}" data-action="pick" data-i="${i}" ${out || st.working ? raw('disabled') : ''}>
    <span class="name">${t.name}</span><span class="price">${baht(t.price)}</span>
    <span class="left ${out ? 'out' : low(t) ? 'low' : ''}">${left}</span>
    ${sel ? html`<span class="check" aria-label="Selected">✓</span>` : html`<span></span>`}
  </button>`;
}

function soldOutView(st) {
  return html`<section class="screen">
    <div><h1 class="title sm">Sold out</h1><p class="sub">Every ticket was sold while you were in line. Nothing was charged.</p></div>
    <hr class="hair">
    ${st.tiers.map((t, i) => tierRow(t, i, st))}
  </section>
  <div class="foot">${button({ label: 'Back to events', action: 'go', quiet: true, data: { to: '#/events' } })}</div>`;
}

export const screens = [
  {
    id: 'select',
    pattern: /^\/select\/([^/]+)\/([^/]+)$/,
    async enter(ctx, eventId) {
      ctx.state.sel = 0;
      ctx.state.qty = 1;
      await refresh(ctx, eventId);
    },
    view(st) {
      if (st.sel < 0) return soldOutView(st);
      const tier = st.tiers[st.sel];
      const atLimit = st.qty >= LIMIT;
      const busy = st.working ? raw('disabled') : '';
      return html`<section class="screen">
        <div><h1 class="title sm">You are in</h1><p class="sub">Choose your tickets. Nothing is reserved yet.</p></div>
        <hr class="hair">
        ${st.banner ? banner(st.banner[0], st.banner[1]) : ''}
        ${st.tiers.map((t, i) => tierRow(t, i, st))}
        <div class="card">
          <div class="qty"><b>How many</b>
            <div class="stepper">
              <button data-action="minus" aria-label="One fewer" ${st.qty <= 1 ? raw('disabled') : busy}>−</button>
              <span class="n">${st.qty}</span>
              <button data-action="plus" aria-label="One more" ${st.qty >= maxFor(tier) ? raw('disabled') : busy}>+</button>
            </div>
          </div>
          <p class="hint ${atLimit ? 'stop' : ''}" style="text-align:left">${atLimit ? '4 is the most one person can buy.' : 'Maximum 4 per person'}</p>
        </div>
      </section>
      <div class="foot">
        <div class="total"><span class="what">${st.qty} × ${tier.name}</span><span class="sum">${baht(tier.price * st.qty)}</span></div>
        ${button({ label: 'Hold these seats', action: 'hold', working: st.working ? 'Holding…' : '' })}
        <p class="hint">Holding takes them out of the sale for 10 minutes</p>
      </div>`;
    },
    actions: {
      pick(ctx, data) {
        ctx.state.sel = Number(data.i);
        ctx.state.banner = null;
        fit(ctx.state);
      },
      minus(ctx) { ctx.state.qty = Math.max(1, ctx.state.qty - 1); },
      plus(ctx) {
        const st = ctx.state;
        st.qty = Math.min(maxFor(st.tiers[st.sel]), st.qty + 1);
      },
      async hold(ctx, data, eventId, token) {
        const st = ctx.state;
        const tier = st.tiers[st.sel];
        st.working = true;
        st.banner = null;
        ctx.render();
        try {
          const h = await api.hold(token, tier.tierId, st.qty);
          ctx.go(`#/hold/${h.reservationId}`);
        } catch (e) {
          st.working = false;
          if (e.code === 'NOT_ADMITTED') return ctx.go(`#/passed/${eventId}`);
          if (e.status !== 409) throw e;
          await refresh(ctx, eventId);
          if (e.code === 'NOT_ENOUGH' && e.remaining > 0) {
            st.banner = [`Only ${num(e.remaining)} ${tier.name} ticket${e.remaining === 1 ? ' is' : 's are'} left.`, 'Choose fewer tickets or another tier.'];
          } else if (e.code === 'OVER_LIMIT') {
            st.banner = ['4 is the most one person can buy.', 'Choose 4 tickets or fewer.'];
          } else if (e.code !== 'SOLD_OUT') {
            st.banner = ['Those seats just went to someone else.', 'The numbers below are up to date. Nothing was reserved.'];
          }
        }
      },
    },
  },
];
```

- [ ] **Step 2: Screenshot check**

```bash
mvn -q exec:java &
sleep 4
node src/test/e2e/walk.mjs 08a 08d 08g
```

Compare with `diagrams/app-flow/screens/08a.jpg`, `08d.jpg`, `08g.jpg`. Check:
- 08a: VIP selected (lime border and check), `only 16 left` in coral, Zone A `350 left`, Zone B `2,020 left`, stepper at 1 with minus disabled, `1 × VIP` and `฿6,500`, button `Hold these seats`. No countdown.
- 08d: VIP row dimmed with `Sold out`, Zone A selected.
- 08g: title `Sold out`, every row dimmed, only `Back to events`.

- [ ] **Step 3: Hand check 08b, 08c, 08e, 08f**

1. `node src/test/e2e/walk.mjs prep 08` and open the printed address.
   - Tap plus 3 times (08c): plus disables at 4, hint turns coral `4 is the most one person can buy.`
   - In the dev panel choose `RACE_LOST`, tap **Set scenario**, then tap **Hold these seats** (08b then 08f): button shows `Holding…`, then a coral banner `Those seats just went to someone else.` and VIP shows `only 14 left`.
2. `node src/test/e2e/walk.mjs prep 08e`, open the address, choose VIP and 3 tickets. Run `node src/test/e2e/walk.mjs then 08e`. Tap **Hold these seats**.
   Expected (08e): coral banner `Only 2 VIP tickets are left.`, quantity drops to 2, VIP shows `only 2 left`.
3. Tap **Hold these seats** again. Expected: the address becomes `#/hold/R-10xx` (the screen there is empty until Task 9).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/public/js/screens/select.js
git commit -m "SCR-08 Select tickets with stepper rules and refusal banners

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---
### Task 9: SCR-09 Seats held, SCR-10 Seats released, SCR-11 Hold ran out

**Lane:** Join

**Files:**
- Modify: `src/main/resources/public/js/screens/hold.js` (whole file replaced)

**Interfaces:**
- Consumes: `api.getHold`, `api.release`, `api.payment`; `joinAgain` from `queue.js`; `html`, `button`, `countdown`, `orderSummary`, `statusScreen`, `sheet`, `seats` from `parts.js`.
- Produces: routes `#/hold/{rid}`, `#/released/{rid}`, `#/ranout/{rid}`. Exports used by Task 10:
  - `follow(ctx, hold)`: moves to the screen that matches the hold's state on the server and returns `true`, or returns `false` when the hold is `HELD` with no payment in progress.
  - `againButton(state)` and `backButton(hold)`: the 2 buttons every "it ended" screen shows.

- [ ] **Step 1: Replace `hold.js`**

```js
// SCR-09 Seats held (a running, b release sheet), SCR-10 Seats released, SCR-11 Hold ran out.
import { api } from '../api.js';
import { html, button, countdown, orderSummary, statusScreen, sheet, seats } from '../parts.js';
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
      return html`<section class="screen">
        <div><h1 class="title sm">Seats held</h1><p class="sub">Yours until the timer runs out.</p></div>
        ${countdown(h.expiresAt)}
        ${orderSummary(h)}
        <div class="card good"><b>${h.tierName} just dropped from ${h.remainingBefore} to ${h.remainingAfter}.</b>
          <p>Your seats left the sale the moment you held them.</p></div>
      </section>
      <div class="foot">
        ${button({ label: 'Continue to payment', action: 'go', data: { to: `#/checkout/${h.reservationId}` } })}
        ${button({ label: 'Release these seats', action: 'askRelease', quiet: true })}
      </div>
      ${st.sheet ? sheet({
        title: 'Release these seats?',
        text: [`The ${seats(h.quantity, h.tierName)} go back on sale straight away.`, 'To buy again you join the back of the line.'],
        buttons: [
          { label: 'Release seats', action: 'release', danger: true, working: st.releasing ? 'Releasing…' : '' },
          { label: 'Keep my seats', action: 'keep' },
        ],
      }) : ''}`;
    },
    actions: {
      askRelease(ctx) { ctx.state.sheet = true; },
      keep(ctx) { ctx.state.sheet = false; },
      async release(ctx, data, rid) {
        ctx.state.releasing = true;
        ctx.render();
        await api.release(rid);
        ctx.go(`#/released/${rid}`, { replace: true });
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
```

- [ ] **Step 2: Screenshot check**

```bash
mvn -q exec:java &
sleep 4
node src/test/e2e/walk.mjs 09a 10 11
```

Compare with `diagrams/app-flow/screens/09a.jpg`, `10.jpg`, `11.jpg`. Check:
- 09a: `Seats held`, lime countdown near `10:00`, summary `VIP · 2 · ฿6,500`, the `Early Bird −10%` line `−฿1,300`, total `฿11,700`, lime card `VIP just dropped from 16 to 14.`
- 10: grey check ring, `Seats released`, `Your 2 VIP seats are back on sale. Nothing was charged.`
- 11: coral ring, coral `Your 10 minutes ran out`, `The 2 VIP seats went back on sale. You were not charged.`

- [ ] **Step 3: Hand check 09b and the clock**

`node src/test/e2e/walk.mjs prep 09` and open the printed address.
1. Tap **Release these seats** (09b): the sheet slides up, the countdown keeps running behind it. Tap the dark area: the sheet closes. Open it again and tap **Release seats**: SCR-10 shows.
2. Run prep again, open the address, tap **×100** in the dev panel.
   Expected: the countdown runs to `00:00` in about 6 seconds, then SCR-11 shows by itself.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/public/js/screens/hold.js
git commit -m "SCR-09 Seats held, SCR-10 released, SCR-11 ran out

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: SCR-12 Checkout, SCR-13 PromptPay, SCR-14 Card, SCR-15 Confirming, SCR-18 Expired while paying

**Lane:** Join

**Files:**
- Modify: `src/main/resources/public/js/screens/pay.js` (whole file replaced)

**Interfaces:**
- Consumes: `api.getHold`, `api.startPayment`, `api.answer`, `api.payment`, `serverNow`; `follow`, `atZero`, `againButton`, `backButton` from `hold.js`; `joinAgain` from `queue.js`; `html`, `raw`, `baht`, `dateShort`, `mmss`, `button`, `banner`, `countdown`, `orderSummary`, `qr`, `statusScreen`, `WORDS` from `parts.js`.
- Produces: routes `#/checkout/{rid}`, `#/pay/{rid}/{pid}`, `#/confirming/{rid}/{pid}`, `#/expired/{rid}`. A captured payment goes to `#/confirmed/{orderId}`.

- [ ] **Step 1: Replace `pay.js`**

```js
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
    ${st.method ? '' : html`<p class="hint">Choose how to pay</p>`}
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
    } else if (e.code === 'HOLD_NOT_ACTIVE') {
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
```

- [ ] **Step 2: Screenshot check**

```bash
mvn -q exec:java &
sleep 4
node src/test/e2e/walk.mjs 12a 12b 12c 12d 13a 13b 14a 14b 15a 15b 18
```

Compare each with `diagrams/app-flow/screens/<name>.jpg`. Check:
- 12a: title `Pay ฿13,000`, no promotion line. 12b: `Early Bird −10%  −฿1,300`, title `Pay ฿11,700`. Both: 2 method rows `PP PromptPay` and `CARD Credit or debit card`, Pay button disabled, hint `Choose how to pay`.
- 12c: card row dimmed, `Not available right now` in coral. 12d: both rows dimmed, coral banner `Payment is not available right now.`
- 13a: QR on a light panel, `This code expires with your hold`, `I have paid`, link `Use another way to pay`.
- 13b and 14b: coral banner `The payment did not go through.` with a running `mm:ss`, method rows instead of the QR.
- 14a: a light panel that looks like another company's page, `Card provider · simulated`, `Approve payment`, `Decline payment`.
- 15a: spinner, `Waiting for the bank. This usually takes a few seconds.` 15b: `The bank is taking longer than usual. We are still checking. You will not be charged twice.`
- 18: coral ring, coral `You have not been charged`, `...so the two VIP seats went back on sale. No money left your account.` No technical words.

- [ ] **Step 3: Hand check the whole payment path**

`node src/test/e2e/walk.mjs prep 12` and open the printed address.
1. Choose **PromptPay**, tap **Pay ฿11,700**, tap **I have paid** twice quickly.
   Expected: the button shows `Confirming…` after the first tap, SCR-15 shows, then after about 2 seconds the address becomes `#/confirmed/O-9001` (empty until Task 11). Only 1 order exists: `curl -s localhost:7070/api/me/orders | grep -o '"orderId":"O-[0-9]*"'` prints `O-9001` and `O-0001` only.
2. Prep again. Choose **Credit or debit card**, pay, tap **Decline payment**.
   Expected: SCR-15, then SCR-14b with the method rows and the countdown still running.
3. Prep again. Reload the browser while on SCR-15 (after **I have paid**).
   Expected: the page comes back to SCR-15, not SCR-12.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/public/js/screens/pay.js
git commit -m "SCR-12 to SCR-15 checkout and payment, SCR-18 expired while paying

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: SCR-01 to SCR-04 shell, SCR-16 Confirmed, SCR-17 Ticket

**Lane:** Join

**Files:**
- Modify: `src/main/resources/public/js/screens/shell.js` (whole file replaced)
- Modify: `src/main/resources/public/js/screens/tickets.js` (whole file replaced)

**Interfaces:**
- Consumes: `api.events`, `api.orders`, `api.me`, `serverNow`; `html`, `button` (tickets.js only), `baht`, `time24`, `dateShort`, `dateLong`, `dayMonth`, `dayNum`, `monUpper`, `sameDay`, `tabBar`, `sheet`, `stub`, `qr`, `slimHeader`, `WORDS` from `parts.js`.
- Produces: routes `#/events`, `#/tickets`, `#/account`, `#/confirmed/{orderId}`, `#/ticket/{orderId}/{n}`.

- [ ] **Step 1: Replace `shell.js`**

```js
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
            <div><b>${e.name}</b><span>${e.venue} · ${dayMonth(e.eventDateTime)}</span></div>
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
```

- [ ] **Step 2: Replace `tickets.js`**

```js
// SCR-16 Confirmed and SCR-17 Ticket (a valid, b event cancelled, c past event).
import { api } from '../api.js';
import { html, button, baht, time24, dateLong, stub, qr, slimHeader, WORDS } from '../parts.js';

async function loadOrder(ctx, orderId) {
  const order = (await api.orders()).find((o) => o.orderId === orderId);
  if (!order) ctx.state.error = 'missing';
  ctx.state.o = order;
}

export const screens = [
  {
    id: 'confirmed',
    pattern: /^\/confirmed\/([^/]+)$/,
    enter: loadOrder,
    view(st) {
      const o = st.o;
      const q = o.quantity;
      return html`<section class="screen" style="align-items:center;text-align:center">
        <div class="ring go">✓</div>
        <h1 class="status-title">You are going</h1>
        <p class="status-text">Paid ${baht(o.paidAmount)} by ${o.providerLabel}. ${q === 1 ? 'One ticket.' : `${WORDS[q]} tickets, one for each seat.`}</p>
        <div style="display:flex;flex-direction:column;gap:12px;width:100%">${o.tickets.map((t) => stub(o, t))}</div>
      </section>
      <div class="foot">
        ${button({ label: 'Open ticket 1', action: 'go', data: { to: `#/ticket/${o.orderId}/1` } })}
        <button class="textlink" data-action="go" data-to="#/tickets">Go to my tickets</button>
      </div>`;
    },
  },
  {
    id: 'ticket',
    pattern: /^\/ticket\/([^/]+)\/(\d+)$/,
    async enter(ctx, orderId) {
      await loadOrder(ctx, orderId);
      ctx.state.me = await api.me();
    },
    view(st, ctx) {
      const o = st.o;
      const n = Math.min(Math.max(1, Number(ctx.params[1])), o.tickets.length);
      const t = o.tickets[n - 1];
      const cancelled = t.state === 'CANCELLED';
      return html`<section class="screen ${o.past ? 'past' : ''}">
        ${slimHeader(o.event.shortName, `TICKET ${n} OF ${o.tickets.length} · ${o.tierName.toUpperCase()}`)}
        ${o.past ? html`<span class="label">Past event</span>` : ''}
        <div class="qrwrap ${cancelled ? 'void' : ''}"><div class="qr">${qr(t.payload, 240)}</div>
          ${cancelled ? html`<span class="stamp">CANCELLED</span>` : ''}</div>
        <p class="mono" style="color:var(--ac);text-align:center;margin:0">${t.ticketId}</p>
        <div class="rows">
          <div class="row"><span class="k">Holder</span><span class="v txt">${st.me.name}</span></div>
          <div class="row"><span class="k">Event</span><span class="v txt">${o.event.name}</span></div>
          <div class="row"><span class="k">Venue</span><span class="v txt">${o.event.venue}</span></div>
          <div class="row"><span class="k">Date</span><span class="v txt">${dateLong(o.event.eventDateTime)}, ${time24(o.event.eventDateTime)}</span></div>
          <div class="row"><span class="k">Tier</span><span class="v txt">${o.tierName}</span></div>
        </div>
        <div class="card">${cancelled
          ? html`<b>This event was cancelled.</b><p>This ticket will not get you in.</p>`
          : html`<b>Single use.</b><p>This code works once. Do not share it.</p>`}</div>
        ${o.tickets.length > 1 ? html`<div class="dots">${o.tickets.map((x) => html`<button class="${x.n === n ? 'on' : ''}"
          data-action="go" data-to="#/ticket/${o.orderId}/${x.n}" aria-label="Ticket ${x.n}"></button>`)}</div>` : ''}
      </section>
      <div class="foot">${button({ label: 'Back to my tickets', action: 'go', quiet: true, data: { to: '#/tickets' } })}</div>`;
    },
  },
];
```

- [ ] **Step 3: Screenshot check**

```bash
mvn -q exec:java &
sleep 4
node src/test/e2e/walk.mjs 01a 02 03 04 16 17a 17b 17c
```

Compare with `diagrams/app-flow/screens/<name>.jpg`. Check:
- 01a: `Events`, `Bangkok`, feature card for THE 1975 BANGKOK with pill `ON SALE TODAY`, `Impact Arena · 14 Nov 2026`, lime `Sale opens 10:00, from ฿1,900`; label `ALSO ON SALE` and 3 rows (Bruno Mars ฿1,200, Alan Walker ฿2,400, Marshmello ฿1,500); tab bar with Events lit.
- 02: `1 upcoming`, an upcoming card `14 NOV · The 1975 Bangkok · 2 · VIP`, a faded past card `02 AUG · Bruno Mars · PAST`.
- 03 (`?as=mint`): dashed circle, `No tickets yet`, `Browse events`.
- 04: lime circle `K`, `Kenji`, `k•••••@gmail.com`, `Help`, coral `Sign out`.
- 16: lime check, `You are going`, `Paid ฿11,700 by PromptPay. Two tickets, one for each seat.`, 2 stubs `TKT-9F42-01` and `TKT-9F42-02`.
- 17a: big QR, `TKT-9F42-01` in lime, Holder `Kenji`, Date `Saturday 14 November, 19:00`, 2 dots. 17b: QR faded with coral `CANCELLED` stamp and the cancelled card. 17c: the whole screen faded with `PAST EVENT`.

- [ ] **Step 4: Hand check the tab bar and sign out**

Open `http://localhost:7070/`. Tap each tab: the lit tab changes and the back button returns to the previous tab. On Account tap **Sign out**: the sheet shows `Sign out?`; **Cancel** closes it.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/public/js/screens/shell.js src/main/resources/public/js/screens/tickets.js
git commit -m "SCR-01 to SCR-04 shell, SCR-16 Confirmed, SCR-17 Ticket

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: Full check

**Lane:** Join (last)

**Files:**
- No new files. Fix only what this task finds, in the file that owns it.

**Interfaces:**
- Consumes: everything.
- Produces: a verified branch, ready for review.

- [ ] **Step 1: All Java tests**

Run: `mvn -q test`
Expected: `Tests run: 38, Failures: 0, Errors: 0`.

- [ ] **Step 2: Every screenshot**

```bash
mvn -q exec:java &
sleep 4
node src/test/e2e/walk.mjs
```

Expected: 35 lines `shot <name>` and 35 PNG files in `target/shots/`. Compare each with the reference of the same name in `diagrams/app-flow/screens/`. Write one line per shot in the report: `<name> ok` or `<name> differs: <what>`.

- [ ] **Step 2b: Phone width check**

Run: `npx -y playwright@1.64.0 screenshot --viewport-size=360,780 "http://localhost:7070/#/events" target/shots/narrow-01.png`
Expected: nothing is cut off and there is no sideways scroll at 360 px wide.

- [ ] **Step 3: The demo path in one go**

Reset (`curl -s -X POST localhost:7070/dev/reset`), open `http://localhost:7070/?dev` and buy 2 VIP seats from the Events tab with no curl help: wait for 10:00 or tap **+1 min**, Join, tap **×100** to reach the front, select VIP × 2, Hold, Continue, PromptPay, Pay, I have paid, Open ticket 1.
Expected: every step lands on the next screen by itself, the browser console shows no errors, and the Tickets tab then shows the new order.

- [ ] **Step 4: 2 buyers race for the last seats**

Reset, then leave only 4 VIP seats:

```bash
curl -s -X POST localhost:7070/dev/reset >/dev/null
curl -s -X POST -H 'Content-Type: application/json' -d '{"seconds":60}' localhost:7070/dev/advance >/dev/null
for c in a b c kenji mint; do curl -s -X POST -H "X-Customer: $c" localhost:7070/api/events/the-1975/queue >/dev/null; done
curl -s -X POST -H 'Content-Type: application/json' -d '{"seconds":350}' localhost:7070/dev/advance >/dev/null
for c in a b c; do curl -s -X POST -H "X-Customer: $c" -H 'Content-Type: application/json' -d "{\"token\":\"$(curl -s -X POST -H "X-Customer: $c" localhost:7070/api/events/the-1975/queue | sed 's/.*"token":"\([^"]*\)".*/\1/')\",\"tierId\":\"vip\",\"quantity\":4}" localhost:7070/api/holds >/dev/null; done
```

Open 2 tabs, `http://localhost:7070/?as=kenji#/event/the-1975` and `http://localhost:7070/?as=mint#/event/the-1975`. In each tap **Back to my place**, wait for SCR-08, choose VIP and 4 tickets (VIP shows `only 4 left`). Tap **Hold these seats** in both tabs as close together as you can.
Expected: exactly 1 tab reaches SCR-09. The other shows the coral banner `Those seats just went to someone else.` and VIP `Sold out`. VIP never goes below 0.

- [ ] **Step 5: Stop the server and report**

`kill %1`. The report lists: test count, each shot line, the result of steps 3 and 4, and anything left undone with the reason.

---

## Self-review notes (for the plan author, already applied)

- Spec §2 file map: every file has a task. `dto/` became 2 files (`Views.java`, `Requests.java`) instead of 1 record per file, to keep the plan short. The spec's §4 `localStorage` line is replaced in Task D step 7, because the server already returns `myToken` (M-02).
- Spec §3 messages M-01 to M-13 and the 4 dev messages: Tasks 2 to 4, tested in `ApiContractTest`. `/dev/advance` is added beyond the spec so screenshots do not wait in real time.
- Spec §5 scenarios: all 8 in `Scenario`, each reached by a shot in `walk.mjs` or a hand check.
- Spec §6 testing: JUnit for the messages (Task 4) and the fake's rules (Tasks 2 and 3), the screenshot walk (Tasks 5 to 12), the `core/` import rule (Task 1).
- Spec §7 documents: Task D.
- Spec §8 build order: Tasks 1 to 4, then 5 to 11 with the buy path (5 to 10) before the shell (11).
