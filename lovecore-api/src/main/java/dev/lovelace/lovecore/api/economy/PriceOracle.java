package dev.lovelace.lovecore.api.economy;

import org.bukkit.Material;

import java.util.List;
import java.util.OptionalLong;

/**
 * Единая модель цен: стоимость предмета в медных единицах, выведенная из сырья по рецептам
 * (сырьё и редкий лут — якоря из конфига ядра). Магазины берут отсюда базовую стоимость и применяют свои
 * наценки из собственных конфигов, поэтому «алмаз» и «алмазная броня» не расходятся между плагинами.
 *
 * <p>Возвращаемая стоимость — до общего индекса цен ({@link LoveEconomy#scaled(long)}); индекс применяет
 * тот, кто потом ставит цену игроку.</p>
 */
public interface PriceOracle {

    /** Модель построена и отвечает (до {@code ServerLoadEvent} рецептов ещё нет). */
    boolean ready();

    /** Стоимость предмета; пусто — у предмета нет цены (неоцениваемое сырьё, нельзя скрафтить из оценённого). */
    OptionalLong value(Material material);

    /** Разбор «как посчитана цена» — строки для показа администратору. */
    List<String> explain(Material material);
}
