package th.ac.kmitl.sda.ticketqueue.web;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import java.util.Map;

/** Starts the Java doorway: serves the page files and answers the page's messages. */
public final class WebServer {

    private WebServer() {
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));
        create().start(port);
    }

    public static Javalin create() {
        Javalin app = Javalin.create(config -> config.staticFiles.add("/public", Location.CLASSPATH));
        app.get("/api/health", ctx -> ctx.json(Map.of("ok", true)));
        return app;
    }
}
