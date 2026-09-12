package dev.lovelace.lovecore.api.tickets;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired by LoveWebAdmin every time {@link TicketOracle#addMessage} persists a message, whatever
 * its {@link MessageSource}. LoveAuth relays this into the ticket's Discord channel/thread when
 * {@code source() != MessageSource.DISCORD} - checking that is what stops a Discord-originated
 * message from echoing straight back into Discord.
 *
 * <p>Async like {@link TicketCreatedEvent} - LoveWebAdmin's HTTP handlers run on Jetty's own
 * thread pool, not Bukkit's main thread. Listeners touching Bukkit API (not just JDA) must hop
 * back via {@code Bukkit.getScheduler().runTask(...)}.</p>
 */
public class TicketMessageAddedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final long ticketId;
    private final String authorName;
    private final String body;
    private final MessageSource source;

    public TicketMessageAddedEvent(long ticketId, String authorName, String body, MessageSource source) {
        super(true);
        this.ticketId = ticketId;
        this.authorName = authorName;
        this.body = body;
        this.source = source;
    }

    public long ticketId() {
        return ticketId;
    }

    public String authorName() {
        return authorName;
    }

    public String body() {
        return body;
    }

    public MessageSource source() {
        return source;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
