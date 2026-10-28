package dev.lovelace.lovecore.api.discord;

/** Explicit routing for isolated integration tests. No fallback to a production category. */
public record TicketRequest(
    TicketType type,
    String ticketId,
    String playerName,
    String reason,
    String ownerDiscordId,
    String categoryId,
    boolean preview) {
  public TicketRequest {
    java.util.Objects.requireNonNull(type);
    if (ticketId == null || ticketId.isBlank() || ticketId.length() > 160)
      throw new IllegalArgumentException("Ticket ID required");
    if (preview
        && (!ticketId.startsWith("preview:")
            || categoryId == null
            || !categoryId.matches("[0-9]{5,25}")))
      throw new IllegalArgumentException("Preview requires a namespace and explicit test category");
    if (!preview && ticketId.startsWith("preview:"))
      throw new IllegalArgumentException("Preview namespace cannot route to production");
    java.util.Objects.requireNonNull(playerName, "playerName");
    if (reason == null) reason = "";
    if (ownerDiscordId != null && !ownerDiscordId.matches("[0-9]{5,25}"))
      throw new IllegalArgumentException("Invalid Discord owner");
  }
}
