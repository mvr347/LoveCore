package dev.lovelace.lovecore.territory;

import dev.lovelace.lovecore.api.territory.TerritoryOracle;
import dev.lovelace.lovecore.integration.Neighbour;
import dev.lovelace.lovecore.social.ClansProfileOracle;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Владелец точки поверх реестра приватов LoveClaims.
 *
 * <p>Источник намеренно один. Клановая территория — не отдельная сущность, а тот же
 * {@code Claim} с {@code claimType = CLAN}: оба вида приватов лежат в одном реестре и по
 * правилу игры не пересекаются и не вкладываются друг в друга. Поэтому «чья это земля» — это
 * один запрос, а не согласование двух источников с приоритетом между ними.</p>
 *
 * <p>LoveClans при этом держит свою запись {@code ClanTerritory} о том же участке. Оракул её
 * не читает намеренно: две записи об одном участке могут разъехаться, и тогда у вопроса
 * «чья земля» окажется два разных ответа в зависимости от того, кого спросили.</p>
 */
public final class ClaimsTerritoryOracle implements TerritoryOracle {

    private static final String API_CLASS = "me.lovelace.loveclaims.api.LoveClaimsAPI";
    private static final String CLAIM_CLASS = "me.lovelace.loveclaims.model.Claim";
    private static final String TRUST_NONE = "NONE";

    private final Logger logger;
    private final Neighbour neighbour;
    private final ClansProfileOracle clans;
    private final Method isInitialized;
    private final Method getInstance;
    private final Method getClaimAt;
    private final Method claimType;
    private final Method ownerUuid;
    private final Method claimName;
    private final Method ownerDisplayName;
    private final Method getTrust;
    private final Method underSiege;

    private ClaimsTerritoryOracle(Logger logger, Neighbour neighbour, ClansProfileOracle clans,
                                  Class<?> apiClass, Class<?> claimClass) {
        this.logger = logger;
        this.neighbour = neighbour;
        this.clans = clans;
        this.isInitialized = neighbour.method(apiClass, "isInitialized");
        this.getInstance = neighbour.method(apiClass, "getInstance");
        this.getClaimAt = neighbour.method(apiClass, "getClaimAt", Location.class);
        this.claimType = neighbour.method(claimClass, "getClaimType");
        this.ownerUuid = neighbour.method(claimClass, "getOwnerUuid");
        this.claimName = neighbour.method(claimClass, "getName");
        this.ownerDisplayName = neighbour.method(claimClass, "getOwnerDisplayName");
        this.getTrust = neighbour.method(claimClass, "getTrust", UUID.class);
        this.underSiege = neighbour.method(claimClass, "isUnderSiege");
    }

    /**
     * @param clans оракул кланов; без него вражда и война неизвестны, и оракул территории
     *              отвечает только про владельца — о чём говорит на старте
     */
    public static Optional<ClaimsTerritoryOracle> link(Plugin plugin, ClansProfileOracle clans) {
        Neighbour neighbour = Neighbour.of(plugin.getLogger(), "LoveClaims");
        Class<?> apiClass = neighbour.type(API_CLASS);
        Class<?> claimClass = neighbour.type(CLAIM_CLASS);
        ClaimsTerritoryOracle oracle =
                new ClaimsTerritoryOracle(plugin.getLogger(), neighbour, clans, apiClass, claimClass);
        neighbour.report("территория");
        if (!neighbour.resolved()) {
            return Optional.empty();
        }
        if (clans == null) {
            plugin.getLogger().warning("Территория: кланы не подключены — вражда и война "
                    + "не учитываются, hostileFor() отвечает только про чужие приваты.");
        }
        return Optional.of(oracle);
    }

    @Override
    public Optional<Owner> ownerAt(Location location) {
        return claimAt(location).flatMap(this::ownerOf);
    }

    @Override
    public boolean hostileFor(UUID playerId, Location location) {
        if (playerId == null || location == null) {
            return false;
        }
        Optional<Object> claim = claimAt(location);
        if (claim.isEmpty()) {
            return false;
        }
        Optional<Owner> owner = ownerOf(claim.get());
        if (owner.isEmpty()) {
            return false;
        }
        return switch (owner.get().kind()) {
            case CLAN -> hostileClanLand(playerId, claim.get(), owner.get().id());
            case PLAYER_CLAIM -> hostilePlayerClaim(playerId, claim.get(), owner.get().id());
            case SERVER -> false;
        };
    }

    /** Клановая земля враждебна во время осады и при вражде или войне с её владельцем. */
    private boolean hostileClanLand(UUID playerId, Object claim, UUID ownerClanId) {
        if (clans == null) {
            return false;
        }
        Optional<UUID> playerClanId = clans.clanId(playerId);
        if (playerClanId.isPresent() && playerClanId.get().equals(ownerClanId)) {
            return false;
        }
        if (Boolean.TRUE.equals(neighbour.call(underSiege, claim))) {
            return true;
        }
        Optional<Object> playerClan = clans.clanOf(playerId);
        Optional<Object> ownerClan = clans.clanById(ownerClanId);
        if (playerClan.isEmpty() || ownerClan.isEmpty()) {
            return false;
        }
        return clans.areClansEnemies(playerClan.get(), ownerClan.get());
    }

    /** Чужой приват враждебен, пока владелец не выдал хоть какое-то доверие. */
    private boolean hostilePlayerClaim(UUID playerId, Object claim, UUID ownerId) {
        if (playerId.equals(ownerId)) {
            return false;
        }
        Object trust = neighbour.call(getTrust, claim, playerId);
        if (!(trust instanceof Enum<?> level)) {
            // Уровень доверия не прочитался: считать чужой приват дружелюбным опаснее,
            // чем наоборот, поэтому отвечаем «враждебен».
            return true;
        }
        return TRUST_NONE.equals(level.name());
    }

    private Optional<Owner> ownerOf(Object claim) {
        Object type = neighbour.call(claimType, claim);
        UUID owner = (UUID) neighbour.call(ownerUuid, claim);
        if (!(type instanceof Enum<?> kind) || owner == null) {
            return Optional.empty();
        }
        return switch (kind.name()) {
            case "CLAN" -> Optional.of(new Owner(OwnerKind.CLAN, owner, displayName(claim, "Клан")));
            case "PLAYER" -> Optional.of(new Owner(OwnerKind.PLAYER_CLAIM, owner, claimName(claim)));
            default -> {
                // Новый тип привата у соседа — это рассогласование, а не повод молча угадать.
                logger.warning("Территория: неизвестный тип привата " + kind.name()
                        + ", владелец точки не определён.");
                yield Optional.empty();
            }
        };
    }

    private String displayName(Object claim, String fallback) {
        Object value = neighbour.call(ownerDisplayName, claim);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private String claimName(Object claim) {
        Object value = neighbour.call(this.claimName, claim);
        return value instanceof String text && !text.isBlank() ? text : "Приват";
    }

    private Optional<Object> claimAt(Location location) {
        if (location == null || !Boolean.TRUE.equals(neighbour.call(isInitialized, null))) {
            return Optional.empty();
        }
        Object api = neighbour.call(getInstance, null);
        if (api == null) {
            return Optional.empty();
        }
        Object result = neighbour.call(getClaimAt, api, location);
        if (result instanceof Optional<?> optional) {
            return Optional.ofNullable(optional.orElse(null));
        }
        return Optional.ofNullable(result);
    }
}
