package eventflow.web;

/** A refusal or error the page must show. status is the HTTP status the routes send. */
public class GatewayException extends RuntimeException {

    public final int status;
    public final String code;
    public final Integer remaining;
    /** The form field the page must mark, or null when the refusal is not about one field. */
    public final String field;

    public GatewayException(int status, String code, String message, Integer remaining) {
        this(status, code, message, remaining, null);
    }

    public GatewayException(int status, String code, String message, Integer remaining, String field) {
        super(message);
        this.status = status;
        this.code = code;
        this.remaining = remaining;
        this.field = field;
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

    public static GatewayException invalid(String code, String field, String message) {
        return new GatewayException(400, code, message, null, field);
    }

    public static GatewayException conflict(String code, String field, String message, Integer remaining) {
        return new GatewayException(409, code, message, remaining, field);
    }
}
