package dev.lovelace.lovecore.api.provider;

/**
 * Вид интеграции, у которой есть «внешний» провайдер и провайдер LoveEssential.
 *
 * <p>Общий выбор ядра ({@code integrations.provider}) говорит только «LoveEssential или внешние плагины».
 * Какой именно внешний плагин имеется в виду, зависит от вида интеграции, поэтому у каждого вида
 * свой идентификатор внешнего провайдера.</p>
 */
public enum ProviderKind {
    /** NPC: внешний провайдер — Citizens. */
    NPC("citizens"),
    /** Присутствие и скины игроков: внешний провайдер — CMI. */
    PRESENCE("cmi"),
    /** Раненые игроки: внешний провайдер — ReviveMe. */
    REVIVE("reviveme");

    /** Идентификатор провайдера LoveEssential — один для всех видов. */
    public static final String LOVE_ESSENTIAL = "loveessential";

    private final String externalId;

    ProviderKind(String externalId) {
        this.externalId = externalId;
    }

    /** Идентификатор внешнего провайдера этого вида: {@code citizens}, {@code cmi} или {@code reviveme}. */
    public String externalId() {
        return externalId;
    }
}
