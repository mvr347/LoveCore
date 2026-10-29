package dev.lovelace.lovecore.api.discord;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ManagedTicketRequestTest {
  private TicketRequest route() {
    return new TicketRequest(
        TicketType.REPORT, "report:12", "Player", "Reason", "12345", null, false);
  }

  @Test
  void membershipIsCopiedAndValidated() {
    var members = new ArrayList<>(List.of("23456", "23456"));
    var request = new ManagedTicketRequest(route(), List.of("34567"), members, "mine-ticket-12-r");
    members.clear();
    assertEquals(List.of("23456"), request.memberIds());
    assertThrows(UnsupportedOperationException.class, () -> request.memberIds().clear());
    assertThrows(
        IllegalArgumentException.class,
        () -> new ManagedTicketRequest(route(), List.of("@everyone"), List.of(), "mine-ticket-1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ManagedTicketRequest(route(), List.of(), List.of(), "public-chat"));
  }
}
