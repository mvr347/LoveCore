package dev.lovelace.lovecore.api.stats;

import java.util.UUID;

/**
 * Шина статистики: плагин сам сообщает о событии вместо того, чтобы LoveLeaderboards лез в
 * него рефлексией по именам классов.
 *
 * <p>Именно этим отменяется целый класс сегодняшних багов. Три интеграции LoveLeaderboards
 * звали классы, которых нет в природе, и молчали об этом — тут звать нечего, а имена метрик
 * объявлены константами в {@link Metrics}, так что опечатка ловится компилятором.</p>
 *
 * <p>Вызовы безопасны из любого потока: доставка слушателям происходит на главном.</p>
 */
public interface StatBus {

    /** Прибавить к накопительной метрике: убийство, выполненный контракт, сваренный напиток. */
    void record(UUID playerId, String metric, double delta);

    /** Задать точное значение метрики-состояния: рейтинг, уровень навыка, баланс. */
    void set(UUID playerId, String metric, double value);

    void setClan(UUID clanId, String metric, double value);

    /** Прибавить к накопительной метрике клана: выигранная война, взятая территория. */
    void recordClan(UUID clanId, String metric, double delta);
}
