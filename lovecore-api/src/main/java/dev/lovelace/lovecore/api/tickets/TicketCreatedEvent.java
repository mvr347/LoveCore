package dev.lovelace.lovecore.api.tickets;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired once by LoveWebAdmin right after {@link TicketOracle#createTicket} persists a new
 * ticket. LoveAuth listens for this to create the matching Discord channel/thread (if the
 * player has a linked Discord account) and calls back {@link TicketOracle#setDiscordChannel}
 * once it exists.
 */
public class TicketCreatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final long ticketId;
    private final TicketType type;
    private final UUID playerUuid;
    private final String playerName;
    private final String subject;
    private final UUID targetUuid;
    private final String targetName;

    public TicketCreatedEvent(long ticketId, TicketType type, UUID playerUuid, String playerName,
                               String subject, UUID targetUuid, String targetName) {
        super(true); // fired off the main thread — LoveWebAdmin's HTTP handler thread, not Bukkit's
        this.ticketId = ticketId;
        this.type = type;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.subject = subject;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
    }

    public long ticketId() {
        return ticketId;
    }

    public TicketType type() {
        return type;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public String playerName() {
        return playerName;
    }

    public String subject() {
        return subject;
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    public String targetName() {
        return targetName;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
