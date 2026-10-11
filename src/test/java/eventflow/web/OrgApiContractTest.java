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
import eventflow.web.fake.FakeOrganizerGateway;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** One check per organizer message (O-01 to O-11). The same checks must later pass against CoreOrganizerGateway. */
class OrgApiContractTest {

    private static final long EVENT = ZonedDateTime.of(2026, 11, 14, 19, 0, 0, 0, FakeBookingGateway.BANGKOK)
            .toInstant().toEpochMilli();
    private static final long SALE = ZonedDateTime.of(2026, 11, 1, 10, 0, 0, 0, FakeBookingGateway.BANGKOK)
            .toInstant().toEpochMilli();

    private final ObjectMapper mapper = new ObjectMapper();
    private FakeClock clock;
    private FakeBookingGateway fake;
    private FakeOrganizerGateway org;

    @BeforeEach
    void setUp() {
        AtomicLong real = new AtomicLong(0);
        clock = new FakeClock(FakeBookingGateway.START, real::get);
        fake = new FakeBookingGateway(clock);
        org = new FakeOrganizerGateway(fake);
    }

    private JsonNode body(Response r) throws IOException {
        return mapper.readTree(r.body().string());
    }

    private static Consumer<Request.Builder> as(String customer) {
        return b -> b.header("X-Customer", customer);
    }

    private static void assertKeys(JsonNode node, String... keys) {
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        assertEquals(Set.of(keys), actual);
    }

    private static Map<String, Object> newEvent() {
        return Map.of("name", "Test Night Bangkok", "venue", "Impact Arena", "eventDateTime", EVENT,
                "saleOpensAt", SALE, "admitPerSecond", 12);
    }

    @Test
    void listAnswersFourRows() {
        JavalinTest.test(WebServer.create(fake, org, null), (server, client) -> {
            Response r = client.get("/api/org/events", as("chai"));
            assertEquals(200, r.code());
            JsonNode data = body(r).get("data");
            assertEquals(4, data.size());
            assertEquals("marshmello", data.get(0).get("eventId").asText());
            assertKeys(data.get(0), "eventId", "name", "venue", "eventDateTime", "saleOpensAt", "state", "sold",
                    "total");
        });
    }

    @Test
    void createAnswers201Draft() {
        JavalinTest.test(WebServer.create(fake, org, null), (server, client) -> {
            Response r = client.post("/api/org/events", newEvent(), as("chai"));
            assertEquals(201, r.code());
            JsonNode data = body(r).get("data");
            assertEquals("DRAFT", data.get("state").asText());
            assertEquals(0, data.get("tiers").size());
        });
    }

    @Test
    void refusalCarriesTheField() {
        JavalinTest.test(WebServer.create(fake, org, null), (server, client) -> {
            Map<String, Object> req = Map.of("name", "Test Night Bangkok", "venue", "Impact Arena",
                    "eventDateTime", EVENT, "saleOpensAt", FakeBookingGateway.START - 1, "admitPerSecond", 12);
            Response r = client.post("/api/org/events", req, as("chai"));
            assertEquals(400, r.code());
            JsonNode err = body(r).get("error");
            assertEquals("SALE_IN_PAST", err.get("code").asText());
            assertEquals("saleOpensAt", err.get("field").asText());
            assertEquals("The sale must open in the future.", err.get("message").asText());
        });
    }

    @Test
    void fullWizardRoundTrip() {
        JavalinTest.test(WebServer.create(fake, org, null), (server, client) -> {
            Response made = client.post("/api/org/events", newEvent(), as("chai"));
            assertEquals(201, made.code());
            String id = body(made).get("data").get("eventId").asText();

            Map<String, Object> tiers = Map.of("tiers", List.of(
                    Map.of("name", "VIP", "price", 6500, "total", 500),
                    Map.of("name", "Zone A", "price", 3800, "total", 2000),
                    Map.of("name", "Zone B", "price", 1900, "total", 5000)));
            assertEquals(200, client.put("/api/org/events/" + id + "/tiers", tiers, as("chai")).code());

            assertEquals(200, client.post("/api/org/events/" + id + "/publish", null, as("chai")).code());

            JsonNode data = body(client.get("/api/org/events/" + id, as("chai"))).get("data");
            assertEquals("SCHEDULED", data.get("state").asText());
            assertEquals(3, data.get("tiers").size());
            assertKeys(data.get("tiers").get(0), "tierId", "name", "price", "total", "held", "sold", "remaining");
        });
    }

    @Test
    void belowHeldSoldCarriesTheMinimum() {
        JavalinTest.test(WebServer.create(fake, org, fake), (server, client) -> {
            assertEquals(200, client.post("/dev/advance", Map.of("seconds", 60)).code());
            String token = body(client.post("/api/events/the-1975/queue", null, as("kenji")))
                    .get("data").get("token").asText();
            clock.advance(350_000);
            assertEquals(201, client.post("/api/holds", Map.of("token", token, "tierId", "vip", "quantity", 2),
                    as("kenji")).code());

            Response r = client.patch("/api/org/events/the-1975/tiers/vip", Map.of("total", 485), as("chai"));
            assertEquals(409, r.code());
            JsonNode err = body(r).get("error");
            assertEquals("BELOW_HELD_SOLD", err.get("code").asText());
            assertEquals(486, err.get("remaining").asInt());
            assertEquals("total", err.get("field").asText());
        });
    }

    @Test
    void dashboardShape() {
        JavalinTest.test(WebServer.create(fake, org, null), (server, client) -> {
            Response r = client.get("/api/org/events/the-1975/dashboard", as("chai"));
            assertEquals(200, r.code());
            JsonNode data = body(r).get("data");
            assertKeys(data, "eventId", "name", "state", "waiting", "admitPerSecond", "tiers", "all", "revenue",
                    "closedAt");
            assertEquals("all", data.get("all").get("tierId").asText());
        });
    }

    @Test
    void otherOrganizerGets404() {
        JavalinTest.test(WebServer.create(fake, org, null), (server, client) -> {
            Response r = client.get("/api/org/events/the-1975", as("someone"));
            assertEquals(404, r.code());
            assertEquals("NOT_FOUND", body(r).get("error").get("code").asText());
        });
    }

    @Test
    void customerApiStillHidesDrafts() {
        JavalinTest.test(WebServer.create(fake, org, null), (server, client) -> {
            assertEquals(201, client.post("/api/org/events", newEvent(), as("chai")).code());
            boolean found = false;
            for (JsonNode row : body(client.get("/api/events", as("kenji"))).get("data")) {
                found = found || row.get("eventId").asText().equals("test-night-bangkok");
            }
            assertFalse(found);
        });
    }

    /** Sends a raw body exactly as written, so malformed JSON and a literal null can be tested. */
    private static Response raw(io.javalin.testtools.HttpClient client, String method, String path, String json) {
        return client.request(path, b -> b.header("X-Customer", "chai")
                .method(method, okhttp3.RequestBody.create(json, okhttp3.MediaType.get("application/json"))));
    }

    private String draftId(io.javalin.testtools.HttpClient client) throws IOException {
        return body(client.post("/api/org/events", newEvent(), as("chai"))).get("data").get("eventId").asText();
    }

    @Test
    void emptyTierBodyIsRequired() {
        JavalinTest.test(WebServer.create(fake, org, null), (server, client) -> {
            Response r = raw(client, "PUT", "/api/org/events/" + draftId(client) + "/tiers", "{}");
            assertEquals(400, r.code());
            JsonNode err = body(r).get("error");
            assertEquals("REQUIRED", err.get("code").asText());
            assertEquals("tiers", err.get("field").asText());
        });
    }

    @Test
    void nullTierRowIsRequired() {
        JavalinTest.test(WebServer.create(fake, org, null), (server, client) -> {
            Response r = raw(client, "PUT", "/api/org/events/" + draftId(client) + "/tiers", "{\"tiers\":[null]}");
            assertEquals(400, r.code());
            JsonNode err = body(r).get("error");
            assertEquals("REQUIRED", err.get("code").asText());
            assertEquals("tiers[0]", err.get("field").asText());
        });
    }

    @Test
    void nullTierBodyIsRequired() {
        JavalinTest.test(WebServer.create(fake, org, null), (server, client) -> {
            Response r = raw(client, "POST", "/api/org/events/" + draftId(client) + "/tiers", "null");
            assertEquals(400, r.code());
            JsonNode err = body(r).get("error");
            assertEquals("REQUIRED", err.get("code").asText());
            assertEquals("tier", err.get("field").asText());
        });
    }

    @Test
    void malformedJsonGetsTheEnvelope() {
        JavalinTest.test(WebServer.create(fake, org, null), (server, client) -> {
            Response r = raw(client, "POST", "/api/org/events", "{\"name\":");
            assertEquals(400, r.code());
            JsonNode b = body(r);
            assertEquals("BAD_REQUEST", b.get("error").get("code").asText());
            assertEquals(true, b.has("serverNow"));
        });
    }
}
