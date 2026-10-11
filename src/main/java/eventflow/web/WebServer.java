package eventflow.web;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.staticfiles.Location;
import eventflow.web.fake.DevControls;
import eventflow.web.fake.FakeBookingGateway;
import eventflow.web.fake.FakeClock;
import eventflow.web.fake.FakeOrganizerGateway;
import eventflow.web.routes.DevRoutes;
import eventflow.web.routes.EventRoutes;
import eventflow.web.routes.HoldRoutes;
import eventflow.web.routes.MeRoutes;
import eventflow.web.routes.OrgRoutes;
import eventflow.web.routes.PaymentRoutes;
import eventflow.web.routes.QueueRoutes;
import eventflow.web.routes.Reply;

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
        // THE SWITCH, organizer side: organizer = new CoreOrganizerGateway(...)
        OrganizerGateway organizer = new FakeOrganizerGateway(fake);

        create(gateway, organizer, dev).start(port);
    }

    public static Javalin create(BookingGateway gateway, DevControls dev) {
        return create(gateway, null, dev);
    }

    public static Javalin create(BookingGateway gateway, OrganizerGateway organizer, DevControls dev) {
        Javalin app = Javalin.create(config -> config.staticFiles.add("/public", Location.CLASSPATH));
        app.get("/api/health", ctx -> ctx.json(Map.of("ok", true)));
        app.exception(GatewayException.class, (e, ctx) -> Reply.error(ctx, gateway, e));
        // A body that is not valid JSON, or a field of the wrong type, gets the same envelope as any refusal.
        app.exception(BadRequestResponse.class, (e, ctx) -> Reply.error(ctx, gateway,
                new GatewayException(400, "BAD_REQUEST", "The request could not be read", null, null)));
        EventRoutes.register(app, gateway);
        QueueRoutes.register(app, gateway);
        HoldRoutes.register(app, gateway);
        PaymentRoutes.register(app, gateway);
        MeRoutes.register(app, gateway);
        if (organizer != null) {
            OrgRoutes.register(app, gateway, organizer);
        }
        if (dev != null) {
            DevRoutes.register(app, gateway, dev);
        }
        return app;
    }
}
