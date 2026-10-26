package dev.lovelace.lovecore.api.discord;
import java.util.List;
/** Discord author IDs are authenticated by Discord; a display name is never an identity. */
public record TicketMessage(String ticketId,String channelId,String messageId,String authorId,
        String authorName,String content,List<Attachment> attachments) {
    public TicketMessage { attachments=List.copyOf(attachments); }
    public record Attachment(String name,String url) {}
}
