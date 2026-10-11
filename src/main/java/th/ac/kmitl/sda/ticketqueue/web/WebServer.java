package th.ac.kmitl.sda.ticketqueue.web;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import th.ac.kmitl.sda.ticketqueue.web.fake.DevControls;
import th.ac.kmitl.sda.ticketqueue.web.fake.FakeBookingGateway;
import th.ac.kmitl.sda.ticketqueue.web.fake.FakeClock;
import th.ac.kmitl.sda.ticketqueue.web.fake.FakeOrganizerGateway;
import th.ac.kmitl.sda.ticketqueue.web.routes.DevRoutes;
import th.ac.kmitl.sda.ticketqueue.web.routes.EventRoutes;
import th.ac.kmitl.sda.ticketqueue.web.routes.HoldRoutes;
import th.ac.kmitl.sda.ticketqueue.web.routes.MeRoutes;
import th.ac.kmitl.sda.ticketqueue.web.routes.OrgRoutes;
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
