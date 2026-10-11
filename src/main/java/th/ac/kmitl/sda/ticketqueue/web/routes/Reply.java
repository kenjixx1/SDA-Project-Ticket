package th.ac.kmitl.sda.ticketqueue.web.routes;

import io.javalin.http.Context;
import th.ac.kmitl.sda.ticketqueue.web.BookingGateway;
import th.ac.kmitl.sda.ticketqueue.web.GatewayException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Wraps every answer as {serverNow, clockRate, data} or {serverNow, clockRate, error}, and reads who is buying. */
public final class Reply {

    private Reply() {
    }

    public static String customer(Context ctx) {
        String raw = ctx.header("X-Customer");
        String id = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
        return id.isEmpty() ? "kenji" : id;
    }

    public static void ok(Context ctx, BookingGateway g, Object data) {
        ok(ctx, g, 200, data);
    }

    public static void ok(Context ctx, BookingGateway g, int status, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serverNow", g.now());
        body.put("clockRate", g.clockRate());
        body.put("data", data);
        ctx.status(status).json(body);
    }

    public static void error(Context ctx, BookingGateway g, GatewayException e) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", e.code);
        err.put("message", e.getMessage());
        err.put("remaining", e.remaining);
        err.put("field", e.field);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serverNow", g.now());
        body.put("clockRate", g.clockRate());
        body.put("error", err);
        ctx.status(e.status).json(body);
    }
}
