package dev.lovelace.lovecore.api.provider;

import java.util.Optional;

/**
 * Выбор реализации по имени среди нескольких зарегистрированных. Отдельным классом, чтобы выбор
 * проверялся без запущенного сервера: {@link dev.lovelace.lovecore.api.LoveCore#provider} лишь
 * достаёт кандидатов из {@code ServicesManager} и передаёт сюда.
 */
public final class Providers {

    private Providers() {
    }

    /** Первая реализация с таким идентификатором (без учёта регистра); пусто, если её нет. */
    public static <T extends Named> Optional<T> choose(Iterable<? extends T> candidates, String providerId) {
        if (providerId == null) {
            return Optional.empty();
        }
        for (T candidate : candidates) {
            if (providerId.equalsIgnoreCase(candidate.providerId())) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
