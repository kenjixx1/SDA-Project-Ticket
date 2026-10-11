package th.ac.kmitl.sda.ticketqueue.web.routes;

import io.javalin.Javalin;
import io.javalin.http.Context;
import th.ac.kmitl.sda.ticketqueue.web.BookingGateway;
import th.ac.kmitl.sda.ticketqueue.web.GatewayException;
import th.ac.kmitl.sda.ticketqueue.web.OrganizerGateway;
import th.ac.kmitl.sda.ticketqueue.web.dto.OrgRequests.EventDetails;
import th.ac.kmitl.sda.ticketqueue.web.dto.OrgRequests.RateInput;
import th.ac.kmitl.sda.ticketqueue.web.dto.OrgRequests.TierInput;
import th.ac.kmitl.sda.ticketqueue.web.dto.OrgRequests.TierList;

import java.util.List;

/** The organizer console's messages to Java (O-01 to O-11, UC-05 to UC-07). Same envelope as the customer's. */
public final class OrgRoutes {

    private OrgRoutes() {
    }

    public static void register(Javalin app, BookingGateway clock, OrganizerGateway org) {
        app.get("/api/org/events", ctx ->                                                   // O-01
                Reply.ok(ctx, clock, org.listEvents(Reply.customer(ctx))));
        app.post("/api/org/events", ctx -> {                                                // O-02
            EventDetails req = read(ctx, EventDetails.class, "details");
            Reply.ok(ctx, clock, 201, org.createEvent(Reply.customer(ctx), req));
        });
        app.get("/api/org/events/{eventId}", ctx ->                                         // O-03
                Reply.ok(ctx, clock, org.getEvent(Reply.customer(ctx), ctx.pathParam("eventId"))));
        app.patch("/api/org/events/{eventId}", ctx -> {                                     // O-04
            EventDetails req = read(ctx, EventDetails.class, "details");
            Reply.ok(ctx, clock, org.updateDetails(Reply.customer(ctx), ctx.pathParam("eventId"), req));
        });
        app.put("/api/org/events/{eventId}/tiers", ctx -> {                                 // O-05
            List<TierInput> tiers = read(ctx, TierList.class, "tiers").tiers();
            Reply.ok(ctx, clock, org.replaceTiers(Reply.customer(ctx), ctx.pathParam("eventId"), tiers));
        });
        app.post("/api/org/events/{eventId}/tiers", ctx -> {                                // O-06
            TierInput req = read(ctx, TierInput.class, "tier");
            Reply.ok(ctx, clock, 201, org.addTier(Reply.customer(ctx), ctx.pathParam("eventId"), req));
        });
        app.patch("/api/org/events/{eventId}/tiers/{tierId}", ctx -> {                      // O-07
            TierInput req = read(ctx, TierInput.class, "tier");
            Reply.ok(ctx, clock, org.updateTier(Reply.customer(ctx), ctx.pathParam("eventId"),
                    ctx.pathParam("tierId"), req));
        });
        app.post("/api/org/events/{eventId}/publish", ctx ->                                // O-08
                Reply.ok(ctx, clock, org.publish(Reply.customer(ctx), ctx.pathParam("eventId"))));
        app.put("/api/org/events/{eventId}/rate", ctx -> {                                  // O-09
            RateInput req = read(ctx, RateInput.class, "admitPerSecond");
            Reply.ok(ctx, clock, org.setRate(Reply.customer(ctx), ctx.pathParam("eventId"), req.admitPerSecond()));
        });
        app.post("/api/org/events/{eventId}/close", ctx ->                                  // O-10
                Reply.ok(ctx, clock, org.closeSale(Reply.customer(ctx), ctx.pathParam("eventId"))));
        app.get("/api/org/events/{eventId}/dashboard", ctx ->                               // O-11
                Reply.ok(ctx, clock, org.dashboard(Reply.customer(ctx), ctx.pathParam("eventId"))));
    }

    /**
     * Reads the body. An empty or literal null body is a 400 REQUIRED for that field;
     * a body that is not valid JSON, or has a field of the wrong type, is a 400 BAD_REQUEST.
     */
    private static <T> T read(Context ctx, Class<T> type, String field) {
        String text = ctx.body().trim();
        if (text.isEmpty() || text.equals("null")) {
            throw GatewayException.invalid("REQUIRED", field, "Required");
        }
        try {
            return ctx.bodyAsClass(type);
        } catch (Exception e) { // Jackson's parse errors are checked exceptions thrown through Kotlin
            throw new GatewayException(400, "BAD_REQUEST", "The request could not be read", null, null);
        }
    }
}
