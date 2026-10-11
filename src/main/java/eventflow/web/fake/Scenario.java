package eventflow.web.fake;

/** A hard case the fake can be told to act out, so every screen variant can be shown. */
public enum Scenario {
    RACE_LOST,          // next hold is refused: someone else took the seats (SCR-08f)
    SOLD_OUT_ALL,       // every tier of The 1975 goes to 0 (SCR-05d, SCR-08g)
    PROVIDER_DOWN,      // the card provider stops answering (SCR-12c)
    ALL_PROVIDERS_DOWN, // every provider stops answering (SCR-12d)
    BANK_SLOW,          // payments take 12 seconds to settle instead of 2 (SCR-15b)
    TURN_PASSED,        // the buyer's place in line ends now (SCR-07)
    EVENT_CANCELLED,    // The 1975 is cancelled (SCR-05f, SCR-17b)
    SALE_CLOSED         // The 1975 sale is closed (SCR-05e)
}
