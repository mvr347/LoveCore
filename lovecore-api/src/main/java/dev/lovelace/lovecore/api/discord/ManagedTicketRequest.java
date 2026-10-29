package dev.lovelace.lovecore.api.discord;

import java.util.List;
import java.util.Objects;

/** Explicit private case membership. Ordinary staff roles are deliberately not inherited. */
public record ManagedTicketRequest(
    TicketRequest routing,
    List<String> supervisorRoleIds,
    List<String> memberIds,
    String channelName) {
  public ManagedTicketRequest {
    Objects.requireNonNull(routing);
    supervisorRoleIds = ids(supervisorRoleIds, 20);
    memberIds = ids(memberIds, 50);
    if (channelName == null || !channelName.matches("(?:test-)?mine-ticket-[0-9]{1,10}(?:-[ar])?"))
      throw new IllegalArgumentException("Invalid case channel name");
  }

  private static List<String> ids(List<String> values, int max) {
    var result = List.copyOf(Objects.requireNonNull(values));
    if (result.size() > max || result.stream().anyMatch(id -> !id.matches("[0-9]{5,25}")))
      throw new IllegalArgumentException("Invalid Discord membership");
    return result.stream().distinct().toList();
  }
}
