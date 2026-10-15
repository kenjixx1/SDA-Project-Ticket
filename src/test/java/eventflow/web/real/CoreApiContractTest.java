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
    void closingASaleBeforeItOpensIsA409NotOnSale() {
        JavalinTest.test(WebServer.create(new CoreBookingGateway(engine), new CoreOrganizerGateway(engine), engine),
                (server, client) -> {
                    Response r = client.post("/api/org/events/the-1975/close", java.util.Map.of(), b -> b.header("X-Customer", "chai"));
                    assertEquals(409, r.code());
                    assertEquals("NOT_ON_SALE", body(r).get("error").get("code").asText());
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
