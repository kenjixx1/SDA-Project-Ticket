package eventflow.web;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;
import eventflow.web.fake.FakeBookingGateway;
import eventflow.web.fake.FakeClock;

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
