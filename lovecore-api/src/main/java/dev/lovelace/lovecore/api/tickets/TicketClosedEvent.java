package dev.lovelace.lovecore.api.tickets;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired by LoveWebAdmin when {@link TicketOracle#closeTicket} closes a ticket - LoveAuth
 * listens for this to lock/archive the matching Discord channel/thread when the ticket was
 * closed from the panel side (a Discord-side close instead calls closeTicket directly, so
 * there's nothing to loop back for that direction).
 */
public class TicketClosedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final long ticketId;

    public TicketClosedEvent(long ticketId) {
        super(true);
        this.ticketId = ticketId;
    }

    public long ticketId() {
        return ticketId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
