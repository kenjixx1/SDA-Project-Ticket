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
