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
}

