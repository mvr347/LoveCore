package dev.lovelace.lovecore.api.combat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Боевое состояние игрока — один ответ на всю экосистему.
 *
 * <p>Сегодня LoveTweaks (свитки телепортации) и LoveTrades (проверка торговли) читают метку
 * {@code in_combat} напрямую и каждый по-своему. Ядро сводит это к одному ответу и добавляет
 * то, чего метка не знает: участие в войне или осаде.</p>
 */
public interface CombatState {

    boolean inCombat(UUID playerId);

    /** Когда бой считается законченным; пусто — игрок не в бою. */
    Optional<Instant> combatEndsAt(UUID playerId);

    /**
     * Чем игрок помечен в бою: {@code pvp}, {@code siege}, {@code raid} или имя стороннего
     * плагина, поставившего метку. Пусто — игрок не в бою. Годится для причины отказа:
     * «нельзя торговать во время осады» читается лучше, чем «нельзя торговать в бою».
     */
    Optional<String> combatSource(UUID playerId);

    /**
     * Пометить игрока как вступившего в бой.
     *
     * <p>Повторный вызов продлевает бой, а не начинает новый: побеждает более поздний срок
     * окончания, иначе короткая метка от одного плагина обрубала бы длинную от другого.</p>
     *
     * @param source кто пометил — «pvp», «siege», «raid». Видно в отладке и в причинах отказа
     */
    void mark(UUID playerId, Duration duration, String source);

    /**
     * Снять боевое состояние досрочно. Нужно тем, кто завершает бой явно, — например,
     * при окончании осады или дуэли.
     */
    void clear(UUID playerId);
}
