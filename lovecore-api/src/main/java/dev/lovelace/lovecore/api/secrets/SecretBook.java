package dev.lovelace.lovecore.api.secrets;

import dev.lovelace.lovecore.api.provider.Named;

import java.util.Set;
import java.util.UUID;

/**
 * «Книга тайн»: общий список пасхалок, которые игрок уже нашёл. Плагин, в котором спрятана пасхалка,
 * зовёт {@link #discover} в тот момент, когда игрок её нашёл; сам каталог (названия, подсказки)
 * ведёт реализация, поэтому плагину-источнику не нужно знать, как тайны показываются.
 *
 * <p>Реализация: {@code loveessential} (модуль secrets). Все методы — только главный поток.</p>
 */
public interface SecretBook extends Named {

    /**
     * Отмечает тайну найденной.
     *
     * @return {@code true}, если игрок нашёл её впервые; {@code false}, если уже находил или такой
     *         тайны нет в каталоге (неизвестный идентификатор игнорируется)
     */
    boolean discover(UUID player, String secretId);

    /** Идентификаторы найденных тайн игрока; неизвестный игрок даёт пустое множество. */
    Set<String> found(UUID player);
}
