package dev.lovelace.lovecore.api.social;

import java.util.UUID;

/**
 * Репутация игрока — тонкий прокси, чтобы плагины не знали про LoveBehavior.
 *
 * <p>Сегодня LoveShop ищет рефлексией {@code LoveBehaviorAPI} и сравнивает результат со
 * строкой {@code "good"}, отчего репутация фактически бинарна: всё, кроме «good», даёт ровно
 * ноль. {@link Tier} возвращает шкале ступени.</p>
 */
public interface ReputationOracle {

    enum Tier { OUTCAST, BAD, NEUTRAL, GOOD, RESPECTED }

    /**
     * Нормализованная репутация в диапазоне 0..100.
     *
     * <p>Шкала своя, а не сырая шкала поставщика: у LoveBehavior их две (вежливость и стиль
     * игры) по 0..7000 каждая, и вызывающему незачем знать ни про их количество, ни про их
     * границы. Кому нужны подробности — идёт в LoveBehavior напрямую.</p>
     */
    int reputation(UUID playerId);

    Tier tier(UUID playerId);

    /**
     * Изменить репутацию игрока на delta (может быть отрицательной).
     *
     * <p>Изменение применяется к шкале вежливости (politeness). Если LoveBehavior не установлен,
     * операция молча игнорируется.</p>
     *
     * @param playerId UUID игрока
     * @param delta количество баллов для добавления (отрицательное = вычитание)
     */
    void modify(UUID playerId, int delta);
}
