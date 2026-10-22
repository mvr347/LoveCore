package dev.lovelace.lovecore.api.provider;

/**
 * Контракт, у которого на сервере может быть несколько реализаций одновременно.
 *
 * <p>Обычные оракулы ядра ({@code AuthOracle}, {@code TerritoryOracle}) однозначны: реализация
 * одна, побеждает старший приоритет. С NPC и присутствием игрока иначе: Citizens и LoveEssential
 * живут рядом, и какой из них использовать, решает конфиг каждого плагина-потребителя строкой
 * вроде {@code integrations.npc-provider: citizens}. Для этого каждая реализация называет себя, а
 * потребитель находит нужную через {@link dev.lovelace.lovecore.api.LoveCore#provider}.</p>
 */
public interface Named {

    /** Стабильный идентификатор в нижнем регистре: {@code citizens}, {@code cmi}, {@code loveessential}. */
    String providerId();
}
