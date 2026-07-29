package dev.lovelace.lovecore.api.economy;

import java.util.Objects;
import java.util.UUID;

/**
 * Счёт: кошелёк игрока или казна клана.
 *
 * <p>Абстракция счёта важнее самой суммы: клановая казна и кошелёк игрока обязаны быть одной
 * сущностью, иначе перевод между ними снова окажется частным случаем с отдельным кодом.</p>
 */
public record AccountId(Kind kind, UUID id) {

    public enum Kind { PLAYER, CLAN }

    public AccountId {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }

    public static AccountId player(UUID playerId) {
        return new AccountId(Kind.PLAYER, playerId);
    }

    public static AccountId clan(UUID clanId) {
        return new AccountId(Kind.CLAN, clanId);
    }
}
