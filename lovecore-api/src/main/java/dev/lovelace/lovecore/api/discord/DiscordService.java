package dev.lovelace.lovecore.api.discord;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Единый сервис интеграции с Discord экосистемы Love.
 */
public interface DiscordService {

    /** Активен ли сервис и настроен ли бот. */
    boolean isEnabled();

    /** Возвращает Discord ID привязанного игрока. */
    Optional<String> getLinkedDiscordId(UUID playerUuid);

    /** Возвращает UUID игрока по Discord ID. */
    Optional<UUID> getLinkedPlayer(String discordId);

    /** Генерирует временный 6-значный код для команды привязки. */
    String generateLinkCode(UUID playerUuid);

    /** Завершает привязку по коду. */
    boolean completeLink(String discordId, String code);

    /** Отвязывает аккаунт игрока. */
    void unlink(UUID playerUuid);

    /** Принудительно устанавливает или обновляет привязку игрока к Discord ID. */
    void setLink(UUID playerUuid, String discordId);

    /** Отправляет личное сообщение пользователю Discord. */
    CompletableFuture<Boolean> sendDirectMessage(String discordUserId, String message, DiscordEmbed embed);

    /** Отправляет сообщение в канал Discord. */
    CompletableFuture<Boolean> sendChannelMessage(String channelId, String message, DiscordEmbed embed);

    /** Создаёт приватный канал тикета на сервере Discord. */
    CompletableFuture<String> createTicketChannel(TicketType type, String ticketId, String playerName, String initialReason);

    /** Закрывает/архивирует канал тикета. */
    CompletableFuture<Boolean> closeTicketChannel(String channelId, String reason);

    /** Регистрирует слушатель сообщений из тикетов. */
    void registerTicketMessageListener(TicketMessageListener listener);

    /**
     * Снимает слушатель, зарегистрированный через {@link #registerTicketMessageListener}. Плагин-потребитель
     * должен вызывать это в своём {@code onDisable()}, иначе после каждого {@code /reload} прежний слушатель
     * продолжает срабатывать вместе с новым. По умолчанию ничего не делает, чтобы не ломать сторонние
     * реализации интерфейса.
     */
    default void unregisterTicketMessageListener(TicketMessageListener listener) {
    }
    default void registerTicketMessageListenerV2(TicketMessageListenerV2 listener) {}
    default void unregisterTicketMessageListenerV2(TicketMessageListenerV2 listener) {}
    default void registerInteractionListener(DiscordInteractionListener listener) {}
    default void unregisterInteractionListener(DiscordInteractionListener listener) {}
    /**
     * Declares that component and modal ids starting with {@code prefix} belong to the calling plugin. LoveCore then
     * answers them with "unavailable" while no listener takes them; ids nobody claimed are left to other gateways
     * sharing the bot token. Slash commands are claimed by {@link #registerCommand}.
     */
    default void claimInteractionPrefix(String prefix) {}
    default CompletableFuture<Boolean> registerCommand(String name,String description,java.util.Map<String,String> options) { return CompletableFuture.completedFuture(false); }
    /** Callback JSON uses Discord's interaction response schema; tokens must never be logged. */
    default CompletableFuture<Boolean> respond(DiscordInteraction interaction,String responseJson) { return CompletableFuture.completedFuture(false); }
    default CompletableFuture<Boolean> editResponse(DiscordInteraction interaction,String content) { return CompletableFuture.completedFuture(false); }
    default CompletableFuture<String> sendMessage(String channelId,String payloadJson,String messageId) { return CompletableFuture.completedFuture(null); }
    default CompletableFuture<String> directMessage(String userId,String payloadJson) { return CompletableFuture.completedFuture(null); }
    /** ownerDiscordId must be confirmed by the caller; null creates a staff-only channel. */
    default CompletableFuture<String> createTicketChannel(TicketType type,String ticketId,String playerName,String reason,String ownerDiscordId) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Private ticket ownership requires Discord API v2"));
    }
    default void restoreTicket(String ticketId,String channelId) {}
    /** Typed namespaces prevent support ticket IDs colliding with appeal IDs. */
    default void restoreTicket(TicketType type,String ticketId,String channelId) {restoreTicket(type.name()+":"+ticketId,channelId);}

    /** Sends a private case image. Implementations must bound the bytes and suppress mentions. */
    default CompletableFuture<String> sendImage(String channelId,String content,String filename,String mime,byte[] image) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Image uploads require Discord API v3"));
    }

    /** Unsupported providers fail closed; a preview must never create a production ticket. */
    default CompletableFuture<String> createTicketChannel(TicketRequest request) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Explicit ticket routing requires Discord API v3"));
    }
    default CompletableFuture<Boolean> unregisterCommand(String name) {
        return CompletableFuture.completedFuture(false);
    }
    default boolean supportsExplicitTicketRouting() { return false; }

  default String getDiscordGuildId() {
    return "";
  }

  /** Managed cases never inherit the generic staff-role-ids access. */
  default CompletableFuture<String> createManagedTicket(ManagedTicketRequest request) {
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("Managed tickets require Discord API v4"));
  }

  default CompletableFuture<Boolean> updateTicketAccess(
      String channelId, java.util.List<String> roles, java.util.List<String> members) {
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("Managed access unavailable"));
  }

  default CompletableFuture<Boolean> setTicketReadOnly(String channelId, boolean locked) {
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("Ticket locking unavailable"));
  }

  /** At most 100 messages, newest first; callers paginate and persist before deleting. */
  default CompletableFuture<java.util.List<TicketMessage>> getTicketHistory(
      String channelId, String beforeId) {
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("Ticket history unavailable"));
  }

  /** Only an attachment confirmed by the authenticated message endpoint may be downloaded. */
  default CompletableFuture<byte[]> getTicketAttachment(
      String channelId, String messageId, String attachmentUrl) {
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("Ticket attachments unavailable"));
  }

  default CompletableFuture<Boolean> deleteTicketChannel(String channelId) {
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("Ticket deletion unavailable"));
  }

  default CompletableFuture<java.util.Set<String>> getMemberRoleIds(String userId) {
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("Member lookup unavailable"));
  }

  default CompletableFuture<Boolean> editInteractionResponse(
      DiscordInteraction interaction, String payloadJson) {
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("Rich interaction response unavailable"));
  }

  /**
   * Read-only channel diagnostics. Does not claim that a successful GET proves send permissions.
   */
  default CompletableFuture<java.util.Map<String, Object>> inspectChannel(String channelId) {
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("Channel diagnostics unavailable"));
  }

  /** Recreate only after a confirmed 404; an unknown send result is never blindly retried. */
  default CompletableFuture<String> ensureMessage(
      String channelId, String messageId, String payloadJson) {
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("Persistent messages unavailable"));
  }

  /** Read-only reconciliation of a creation whose response was lost; never creates a channel. */
  default CompletableFuture<String> recoverManagedTicket(ManagedTicketRequest request) {
    return CompletableFuture.failedFuture(
        new UnsupportedOperationException("Managed recovery unavailable"));
  }
}

