package eventflow.web;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.staticfiles.Location;
import eventflow.web.fake.DevControls;
import eventflow.web.fake.FakeBookingGateway;
import eventflow.web.fake.FakeClock;
import eventflow.web.fake.FakeOrganizerGateway;
import eventflow.web.real.CoreBookingGateway;
import eventflow.web.real.CoreOrganizerGateway;
import eventflow.web.real.RealEngine;
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

    public static Javalin create(BookingGateway gateway, DevControls dev) {
        return create(gateway, null, dev);
    }

    public static Javalin create(BookingGateway gateway, OrganizerGateway organizer, DevControls dev) {
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public", Location.CLASSPATH);
            config.http.maxRequestSize = 4_000_000L; // room for a 2 MB event picture sent as base64
        });
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
