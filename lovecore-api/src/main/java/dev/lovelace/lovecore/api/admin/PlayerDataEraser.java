package dev.lovelace.lovecore.api.admin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Стирание игровых данных конкретного плагина для одного игрока — часть команды
 * очистки базы данных ({@code /lovecoreadmin cleardata &lt;игрок&gt;}), которая проходится по
 * всем зарегистрированным реализациям сразу.
 *
 * <p>Каждый плагин регистрирует свою реализацию через {@code ServicesManager} (несколько
 * провайдеров одного интерфейса — штатный случай для {@link org.bukkit.plugin.ServicesManager},
 * см. {@code getRegistrations}), решает сам, какие из своих таблиц стирать, и явно оставляет то,
 * что должно пережить очистку (например, настройки чата/скорборда — это предпочтения, а не
 * игровой прогресс). LoveAuth в этой системе не участвует вообще: логин/пароль не стирается
 * командой очистки данных ни при каких обстоятельствах.</p>
 */
public interface PlayerDataEraser {

    /** Имя плагина для отчёта админу (например {@code "LoveBehavior"}). */
    String pluginName();

    /**
     * Что именно стирается (и что осознанно сохраняется), в одну строку — вывод команды
     * показывает это админу, чтобы он не гадал, что скрывается за "готово".
     */
    String scopeDescription();

    /** Стирает данные игрока, специфичные для этого плагина. Безопасно для игрока не в кэше/БД. */
    CompletableFuture<EraseResult> erase(UUID playerId);

    record EraseResult(boolean success, String detail) {
        public static EraseResult ok(String detail) {
            return new EraseResult(true, detail);
        }

        public static EraseResult failed(String detail) {
            return new EraseResult(false, detail);
        }
    }
}
