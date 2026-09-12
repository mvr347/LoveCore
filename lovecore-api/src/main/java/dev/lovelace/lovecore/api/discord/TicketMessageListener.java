package dev.lovelace.lovecore.api.discord;

@FunctionalInterface
public interface TicketMessageListener {
    void onTicketMessage(String ticketId, String authorName, boolean isStaff, String message);
}
