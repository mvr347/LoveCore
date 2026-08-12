package dev.lovelace.lovecore.api.auth;

import java.util.UUID;

/**
 * Состояние входа игрока — тонкий прокси, чтобы плагины не знали про LoveAuth.
 *
 * <p>Если LoveAuth не установлен, сервис просто не найдётся в {@link
 * dev.lovelace.lovecore.api.LoveCore#service}: пустой {@code Optional} вызывающий код обязан
 * трактовать как «на сервере авторизации нет», то есть ничего не блокировать.</p>
 */
public interface AuthOracle {

    /** Прошёл ли игрок вход (пароль/Discord/восстановленная сессия/premium-skip). */
    boolean isAuthenticated(UUID playerId);
}
