package dev.lovelace.lovecore.api.tickets;

import java.util.Optional;
import java.util.UUID;

/**
 * Единая точка входа к тикетам (апелляции/поддержка/жалобы), которыми владеет LoveWebAdmin —
 * оно хранит данные и рисует панель, а LoveAuth зеркалит ту же переписку в Discord (создаёт
 * канал/тред на {@link #createTicket}, ретранслирует сообщения через {@link #addMessage} и
 * слушает {@link TicketMessageAddedEvent} для обратного направления). Реализацию регистрирует
 * сам LoveWebAdmin в {@code ServicesManager} — здесь только контракт, по образцу
 * {@link dev.lovelace.lovecore.api.auth.AuthOracle}.
 *
 * <p>Методы синхронные и бьют напрямую в SQLite LoveWebAdmin — это осознанный выбор для
 * низкочастотной админ-механики (тикеты вежливости), а не путь в потоке обработки урона/чата;
 * вызывающий код на Bukkit-главном потоке не должен биться в цикл на каждый твик, а Discord-код
 * (JDA callbacks) и так уже не на главном потоке.</p>
 */
public interface TicketOracle {

    /**
     * Создаёт новый тикет и возвращает его id. targetUuid/targetName обязательны только для
     * {@link TicketType#REPORT} (жалоба на игрока) — для APPEAL/SUPPORT передавайте null.
     */
    long createTicket(TicketType type, UUID playerUuid, String playerName, String subject,
                       UUID targetUuid, String targetName);

    /**
     * Добавляет сообщение в переписку тикета и уведомляет об этом через
     * {@link TicketMessageAddedEvent} (в т.ч. слушателей в других плагинах — LoveAuth реагирует
     * на source != DISCORD, чтобы не зациклить ретрансляцию).
     */
    void addMessage(long ticketId, String authorName, String body, MessageSource source);

    /** Привязывает тикет к созданному в Discord каналу/треду — вызывается один раз LoveAuth. */
    void setDiscordChannel(long ticketId, String discordChannelId);

    void closeTicket(long ticketId);

    Optional<TicketSnapshot> getTicket(long ticketId);
}
