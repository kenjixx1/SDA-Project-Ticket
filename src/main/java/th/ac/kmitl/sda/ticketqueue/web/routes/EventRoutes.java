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
