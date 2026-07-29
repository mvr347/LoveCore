package dev.lovelace.lovecore.api.stats;

import java.util.Objects;
import java.util.UUID;

/** Чья метрика изменилась: игрока или клана. */
public record StatSubject(Kind kind, UUID id) {

    public enum Kind { PLAYER, CLAN }

    public StatSubject {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }

    public static StatSubject player(UUID playerId) {
        return new StatSubject(Kind.PLAYER, playerId);
    }

    public static StatSubject clan(UUID clanId) {
        return new StatSubject(Kind.CLAN, clanId);
    }
}
