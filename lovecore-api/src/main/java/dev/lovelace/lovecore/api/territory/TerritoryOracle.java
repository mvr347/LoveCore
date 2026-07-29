package dev.lovelace.lovecore.api.territory;

import org.bukkit.Location;

import java.util.Optional;
import java.util.UUID;

/**
 * Владелец точки. LoveClaims знает приваты, LoveClans — территории; ядро отвечает на вопрос
 * один раз для всех.
 */
public interface TerritoryOracle {

    enum OwnerKind { PLAYER_CLAIM, CLAN, SERVER }

    record Owner(OwnerKind kind, UUID id, String displayName) {}

    /**
     * Владелец точки. Ответ всегда один: приват клана и приват игрока не пересекаются и не
     * вкладываются друг в друга — это одна сущность в одном реестре, различаемая типом.
     *
     * <p>Пустой ответ означает ничью землю, а не «источник недоступен»: когда реестра приватов
     * нет, оракул вообще не регистрируется, и вызывающий получает пустой
     * {@code Optional} от {@code LoveCore.service(...)}, а не ложное «ничья».</p>
     */
    Optional<Owner> ownerAt(Location location);

    /**
     * Враждебна ли точка для игрока: чужой клан во вражде, война, осада или чужой приват
     * без доверия. Нужно голубю LoveTweaks, свиткам телепортации и размещению NPC.
     */
    boolean hostileFor(UUID playerId, Location location);
}
