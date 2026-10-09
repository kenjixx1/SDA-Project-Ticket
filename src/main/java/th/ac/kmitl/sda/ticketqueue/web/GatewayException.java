package th.ac.kmitl.sda.ticketqueue.web;

/** A refusal or error the page must show. status is the HTTP status the routes send. */
public class GatewayException extends RuntimeException {

    public final int status;
    public final String code;
    public final Integer remaining;

    public GatewayException(int status, String code, String message, Integer remaining) {
        super(message);
        this.status = status;
        this.code = code;
        this.remaining = remaining;
    }

    public static GatewayException notFound(String what) {
        return new GatewayException(404, "NOT_FOUND", what + " not found", null);
    }

    public static GatewayException badRequest(String code, String message) {
        return new GatewayException(400, code, message, null);
    }

    public static GatewayException refused(String code, String message, int remaining) {
        return new GatewayException(409, code, message, remaining);
    }
}
