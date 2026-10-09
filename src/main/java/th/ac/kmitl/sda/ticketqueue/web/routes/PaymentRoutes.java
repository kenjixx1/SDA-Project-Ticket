package th.ac.kmitl.sda.ticketqueue.web.routes;

import io.javalin.Javalin;
import th.ac.kmitl.sda.ticketqueue.web.BookingGateway;
import th.ac.kmitl.sda.ticketqueue.web.dto.Requests.AnswerRequest;

public final class PaymentRoutes {

    private PaymentRoutes() {
    }

    public static void register(Javalin app, BookingGateway g) {
        app.post("/api/payments/{paymentId}/answer", ctx -> {                             // M-10
            AnswerRequest req = ctx.bodyAsClass(AnswerRequest.class);
            Reply.ok(ctx, g, g.answerPayment(Reply.customer(ctx), ctx.pathParam("paymentId"), req.approve()));
        });
        app.get("/api/payments/{paymentId}", ctx ->                                       // M-11
                Reply.ok(ctx, g, g.paymentStatus(Reply.customer(ctx), ctx.pathParam("paymentId"))));
    }
}
