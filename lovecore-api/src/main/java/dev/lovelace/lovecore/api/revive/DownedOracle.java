package dev.lovelace.lovecore.api.revive;

import dev.lovelace.lovecore.api.provider.Named;

import java.util.UUID;

/**
 * Состояние «ранен и ждёт помощи». Дуэли, охота и бой должны учитывать раненых: добивать
 * лежащего, засчитывать его как живого цель или начислять ему боевую метку нельзя.
 *
 * <p>Реализации: {@code reviveme} (поверх ReviveMe) и {@code loveessential} (модуль revive).</p>
 */
public interface DownedOracle extends Named {

    /** Лежит ли игрок раненым. */
    boolean downed(UUID playerId);

    /** Поднимает ли игрока кто-то прямо сейчас. */
    boolean reviving(UUID playerId);
}
