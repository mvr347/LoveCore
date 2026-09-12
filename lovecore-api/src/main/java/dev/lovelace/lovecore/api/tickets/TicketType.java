package dev.lovelace.lovecore.api.tickets;

/** Три вида тикетов, ведущихся по общему шаблону (см. {@link TicketOracle}). */
public enum TicketType {
    /** Апелляция на бан — единственный тип, требующий targetUuid/targetName нет, но подаётся забаненным. */
    APPEAL,
    /** Обращение в поддержку по любому вопросу. */
    SUPPORT,
    /** Жалоба на другого игрока — единственный тип, где targetUuid/targetName обязательны. */
    REPORT
}
