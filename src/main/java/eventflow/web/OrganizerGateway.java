package eventflow.web;

import eventflow.web.dto.OrgRequests.EventDetails;
import eventflow.web.dto.OrgRequests.TierInput;
import eventflow.web.dto.OrgViews.Dashboard;
import eventflow.web.dto.OrgViews.OrgEvent;
import eventflow.web.dto.OrgViews.OrgEventRow;

import java.util.List;

/** Everything the organizer console asks of the system (UC-05, UC-06, UC-07). Refusals are GatewayExceptions. */
public interface OrganizerGateway {

    List<OrgEventRow> listEvents(String organizerId);                                            // O-01

    OrgEvent createEvent(String organizerId, EventDetails details);                              // O-02

    OrgEvent getEvent(String organizerId, String eventId);                                       // O-03

    OrgEvent updateDetails(String organizerId, String eventId, EventDetails details);            // O-04

    OrgEvent replaceTiers(String organizerId, String eventId, List<TierInput> tiers);            // O-05

    OrgEvent addTier(String organizerId, String eventId, TierInput tier);                        // O-06

    OrgEvent updateTier(String organizerId, String eventId, String tierId, TierInput tier);      // O-07

    OrgEvent publish(String organizerId, String eventId);                                        // O-08

    OrgEvent setRate(String organizerId, String eventId, Integer admitPerSecond);                // O-09

    OrgEvent closeSale(String organizerId, String eventId);                                      // O-10

    Dashboard dashboard(String organizerId, String eventId);                                     // O-11
}
