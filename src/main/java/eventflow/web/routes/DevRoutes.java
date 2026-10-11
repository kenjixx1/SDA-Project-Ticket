package eventflow.web.routes;

import io.javalin.Javalin;
import eventflow.web.BookingGateway;
import eventflow.web.GatewayException;
import eventflow.web.dto.Requests.AdvanceRequest;
import eventflow.web.dto.Requests.ScenarioRequest;
import eventflow.web.dto.Requests.SpeedRequest;
import eventflow.web.fake.DevControls;
import eventflow.web.fake.Scenario;

import java.util.Map;

/** Demo-only. Registered only while the fake is switched on. */
public final class DevRoutes {

    private DevRoutes() {
    }

    public static void register(Javalin app, BookingGateway g, DevControls dev) {
        app.post("/dev/scenario", ctx -> {
            String name = ctx.bodyAsClass(ScenarioRequest.class).scenario();
            Scenario scenario;
            try {
                scenario = Scenario.valueOf(name);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw GatewayException.badRequest("BAD_SCENARIO", "Unknown scenario " + name);
            }
            dev.scenario(scenario);
            Reply.ok(ctx, g, Map.of("scenario", scenario.name()));
        });
        app.post("/dev/speed", ctx -> {
            int times = ctx.bodyAsClass(SpeedRequest.class).times();
            dev.speed(times);
            Reply.ok(ctx, g, Map.of("speed", times));
        });
        app.post("/dev/advance", ctx -> {
            long seconds = ctx.bodyAsClass(AdvanceRequest.class).seconds();
            if (seconds < 0) {
                throw GatewayException.badRequest("BAD_ADVANCE", "Seconds must be 0 or more");
            }
            dev.advance(seconds);
            Reply.ok(ctx, g, Map.of("advanced", seconds));
        });
        app.post("/dev/reset", ctx -> {
            dev.reset();
            Reply.ok(ctx, g, Map.of("reset", true));
        });
    }
}
