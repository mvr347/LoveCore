package dev.lovelace.lovecore.api.presence;

import net.kyori.adventure.text.Component;

import dev.lovelace.lovecore.api.provider.Named;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Присутствие игрока: скрыт ли он, отошёл ли, как его показывать и какой у него скин.
 *
 * <p>Реализации: {@code cmi} (поверх CMI) и {@code loveessential} (модули vanish, afk, nickname,
 * skin). Плагин-потребитель выбирает одну в конфиге и находит её через
 * {@link dev.lovelace.lovecore.api.LoveCore#provider}. Все методы безопасно звать с главного
 * потока; для офлайн-игрока ответ пустой или {@code false}, а не исключение.</p>
 */
public interface PresenceOracle extends Named {

    /** Скрыт ли игрок от остальных (vanish). Скрытого нельзя упоминать в публичных списках. */
    boolean vanished(UUID playerId);

    /** Отошёл ли игрок (AFK), автоматически или вручную. */
    boolean afk(UUID playerId);

    /** Время последней активности игрока, миллисекунды Unix; пусто, если игрок не в сети. */
    OptionalLong lastActivity(UUID playerId);

    /** Отображаемое имя (ник с оформлением), если оно отличается от имени аккаунта. */
    Optional<Component> displayName(UUID playerId);

    /** Скин, который игроку выбран на сервере; пусто, если используется собственный. */
    Optional<SkinTexture> skin(UUID playerId);
}
