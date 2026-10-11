package eventflow.web.fake;

/** Demo-only controls. Exposed under /dev only while the fake is switched on. */
public interface DevControls {

    void scenario(Scenario scenario);

    /** Allowed values: 0 (paused), 1, 10, 30, 100. */
    void speed(int times);

    void advance(long seconds);

    void reset();
}
