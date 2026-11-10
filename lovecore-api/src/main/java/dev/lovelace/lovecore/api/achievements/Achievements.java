package dev.lovelace.lovecore.api.achievements;

import java.util.Set;
import java.util.UUID;

/**
 * Общие ачивки сервера. Каталог (названия, редкости, условия) ведёт реализация; плагин-источник сообщает только факты
 * и не знает, как ачивки устроены и показываются. Награда ачивки (вход/выход, пометка) здесь не хранится: косметика сама
 * ссылается на ачивку и спрашивает {@link #unlocked}, поэтому ачивка может ничего не давать, давать одно или оба.
 *
 * <p>Реализация: {@code loveessential} (модуль achievements). Все методы - только главный поток. Неизвестный
 * идентификатор или ключ игнорируется без исключения: плагин-источник может быть новее каталога на сервере.
 * Получать сервис нужно на каждый вызов ({@code LoveCore.service(Achievements.class)}), не кэшируя его.</p>
 */
public interface Achievements {

    /**
     * Выдаёт ачивку, которую плагин-источник проверил сам (условие известно только ему).
     *
     * @return {@code true}, если игрок получил её впервые; {@code false}, если уже имел или такой ачивки нет в каталоге
     */
    boolean unlock(UUID player, String achievementId);

    /** Прибавляет к счётчику {@code key}; ачивки со счётчиком на этом ключе проверяются сами. */
    void progress(UUID player, String key, double delta);

    /** Задаёт значение счётчика {@code key} (например, текущий рейтинг); значение не уменьшает полученные ачивки. */
    void set(UUID player, String key, double value);

    /** Добавляет значение в набор {@code setKey} (например, вид напитка); ачивка набора выдаётся, когда собраны все значения. */
    void collect(UUID player, String setKey, String value);

    /** Есть ли у игрока ачивка; для офлайн-игрока и неизвестной ачивки {@code false}. */
    boolean unlocked(UUID player, String achievementId);

    /** Идентификаторы полученных ачивок игрока; неизвестный игрок даёт пустое множество. */
    Set<String> unlockedIds(UUID player);

    /** Идентификаторы всех ачивок каталога. */
    Set<String> catalogIds();
}
