package eventflow.web.routes;

import io.javalin.Javalin;
import eventflow.web.BookingGateway;

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
