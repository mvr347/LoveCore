package dev.lovelace.lovecore.api.social;

import java.util.UUID;

/**
 * Прямой доступ к ступеням (0..6) обеих шкал LoveBehavior — вежливости и стиля игры.
 *
 * <p>В отличие от {@link ReputationOracle}, который отдаёт одно усреднённое число 0..100 и
 * не годится там, где нужна именно ступень конкретной шкалы (например, налог считается по
 * вежливости отдельно от стиля игры). Реализацию регистрирует сам LoveBehavior в
 * {@code ServicesManager} — здесь только контракт.</p>
 */
public interface BehaviorLevels {

    /** Наибольший индекс ступени на любой из шкал (ступени: 0..6). */
    int MAX_LEVEL = 6;

    /**
     * Ступень вежливости игрока (0 = Ужасно, 6 = Максимум).
     *
     * @return ступень 0..6, либо {@code neutralLevel()} если данных об игроке ещё нет
     */
    int politenessLevel(UUID playerId);

    /**
     * Ступень стиля игры игрока (0 = Агрессивный, 6 = Добрый).
     *
     * @return ступень 0..6, либо {@code neutralLevel()} если данных об игроке ещё нет
     */
    int playstyleLevel(UUID playerId);

    /** Нейтральная ступень, которую возвращают методы выше, если игрок ещё не в кэше. */
    default int neutralLevel() {
        return 3;
    }
}
