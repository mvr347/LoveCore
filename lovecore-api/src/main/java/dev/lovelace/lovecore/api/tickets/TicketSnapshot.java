package dev.lovelace.lovecore.api.tickets;

import java.util.UUID;

/**
 * Read-only view of a ticket at the moment it was fetched - callers needing live state should
 * re-fetch via {@link TicketOracle#getTicket(long)} rather than cache this.
 */
public record TicketSnapshot(
        long id,
        TicketType type,
        TicketStatus status,
        UUID playerUuid,
        String playerName,
        String subject,
        UUID targetUuid,
        String targetName,
        String discordChannelId,
        long createdAt,
        long closedAt
) {}
