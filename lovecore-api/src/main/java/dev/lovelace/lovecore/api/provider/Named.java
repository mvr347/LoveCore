package dev.lovelace.lovecore.api.provider;

/**
 * Контракт, у которого реализация находится по имени среди зарегистрированных в {@code ServicesManager}
 * ({@code NpcProvider}, {@code PresenceOracle}, {@code DownedOracle}).
 *
 * <p>В экосистеме поддерживается один провайдер этих контрактов — LoveEssential
 * ({@link Providers#LOVE_ESSENTIAL}); потребитель находит его через
 * {@link dev.lovelace.lovecore.api.LoveCore#loveEssential}.</p>
 */
public interface Named {

    /** Стабильный идентификатор в нижнем регистре: {@code loveessential}. */
    String providerId();
}
