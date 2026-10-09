package th.ac.kmitl.sda.ticketqueue.web.routes;

import io.javalin.Javalin;
import th.ac.kmitl.sda.ticketqueue.web.BookingGateway;
import th.ac.kmitl.sda.ticketqueue.web.dto.Requests.HoldRequest;
import th.ac.kmitl.sda.ticketqueue.web.dto.Requests.PaymentRequest;

public final class HoldRoutes {

    private HoldRoutes() {
    }

    public static void register(Javalin app, BookingGateway g) {
        app.post("/api/holds", ctx -> {                                                   // M-06
            HoldRequest req = ctx.bodyAsClass(HoldRequest.class);
            Reply.ok(ctx, g, 201, g.hold(Reply.customer(ctx), req.token(), req.tierId(), req.quantity()));
        });
        app.get("/api/holds/{reservationId}", ctx ->                                      // M-07
                Reply.ok(ctx, g, g.getHold(Reply.customer(ctx), ctx.pathParam("reservationId"))));
        app.delete("/api/holds/{reservationId}", ctx ->                                   // M-08
                Reply.ok(ctx, g, g.release(Reply.customer(ctx), ctx.pathParam("reservationId"))));
        app.post("/api/holds/{reservationId}/payments", ctx -> {                          // M-09
            PaymentRequest req = ctx.bodyAsClass(PaymentRequest.class);
            Reply.ok(ctx, g, 201, g.startPayment(Reply.customer(ctx), ctx.pathParam("reservationId"), req.providerKey()));
        });
    }
}
