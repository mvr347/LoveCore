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
}
