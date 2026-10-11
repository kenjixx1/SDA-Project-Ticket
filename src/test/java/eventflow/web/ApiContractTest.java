package eventflow.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.testtools.JavalinTest;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import eventflow.web.fake.FakeBookingGateway;
import eventflow.web.fake.FakeClock;

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
            assertEquals(1, b.get("clockRate").asInt());
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
