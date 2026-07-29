package dev.lovelace.lovecore.social;

import dev.lovelace.lovecore.api.social.ProfileOracle;
import dev.lovelace.lovecore.integration.Neighbour;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Клановая принадлежность поверх LoveClans.
 *
 * <p>Сигнатуры сверены с исходниками LoveClans: {@code Clan} — не record, но отдаёт данные
 * методами в стиле record ({@code id()}, {@code tag()}, {@code name()}), а вражда живёт в
 * {@code relationTo(UUID)} и возвращает {@code DiplomacyRelation}. Именно на этих именах и
 * ошиблась интеграция LoveLeaderboards, звавшая {@code getId}/{@code getName} у класса из
 * пакета {@code dev.lovelace.loveclans}, которого никогда не существовало.</p>
 */
public final class ClansProfileOracle implements ProfileOracle {

    private static final String API_CLASS = "me.lovelace.loveclans.api.LoveClansAPI";
    private static final String CLAN_CLASS = "me.lovelace.loveclans.model.Clan";
    private static final String ENEMY = "ENEMY";

    private final Neighbour neighbour;
    private final Method getInstance;
    private final Method getPlayerClan;
    private final Method getClanById;
    private final Method isAtWar;
    private final Method clanId;
    private final Method clanTag;
    private final Method clanName;
    private final Method relationTo;

    private ClansProfileOracle(Neighbour neighbour, Class<?> apiClass, Class<?> clanClass) {
        this.neighbour = neighbour;
        this.getInstance = neighbour.method(apiClass, "getInstance");
        this.getPlayerClan = neighbour.method(apiClass, "getPlayerClan", UUID.class);
        this.getClanById = neighbour.method(apiClass, "getClanById", UUID.class);
        this.isAtWar = neighbour.method(apiClass, "isAtWar", clanClass, clanClass);
        this.clanId = neighbour.method(clanClass, "id");
        this.clanTag = neighbour.method(clanClass, "tag");
        this.clanName = neighbour.method(clanClass, "name");
        this.relationTo = neighbour.method(clanClass, "relationTo", UUID.class);
    }

    /** Оракул либо собран целиком, либо не собран вовсе — полурабочих связок быть не должно. */
    public static Optional<ClansProfileOracle> link(Logger logger) {
        Neighbour neighbour = Neighbour.of(logger, "LoveClans");
        Class<?> apiClass = neighbour.type(API_CLASS);
        Class<?> clanClass = neighbour.type(CLAN_CLASS);
        ClansProfileOracle oracle = new ClansProfileOracle(neighbour, apiClass, clanClass);
        neighbour.report("кланы");
        return neighbour.resolved() ? Optional.of(oracle) : Optional.empty();
    }

    @Override
    public Optional<UUID> clanId(UUID playerId) {
        return clanOf(playerId).map(clan -> (UUID) neighbour.call(this.clanId, clan));
    }

    @Override
    public Optional<String> clanTag(UUID playerId) {
        return clanOf(playerId).map(clan -> (String) neighbour.call(clanTag, clan));
    }

    @Override
    public Optional<String> clanName(UUID playerId) {
        return clanOf(playerId).map(clan -> (String) neighbour.call(clanName, clan));
    }

    @Override
    public boolean areEnemies(UUID first, UUID second) {
        if (first == null || second == null || first.equals(second)) {
            return false;
        }
        Optional<Object> firstClan = clanOf(first);
        Optional<Object> secondClan = clanOf(second);
        if (firstClan.isEmpty() || secondClan.isEmpty()) {
            return false;
        }
        return areClansEnemies(firstClan.get(), secondClan.get());
    }

    @Override
    public boolean areClanmates(UUID first, UUID second) {
        if (first == null || second == null) {
            return false;
        }
        Optional<UUID> firstClan = clanId(first);
        Optional<UUID> secondClan = clanId(second);
        // Двое без клана — не соклановцы: Optional.empty().equals(Optional.empty()) дало бы true.
        return firstClan.isPresent() && firstClan.equals(secondClan);
    }

    /**
     * Враждуют ли два клана.
     *
     * <p>Вражда в модели LoveClans односторонняя — клан мог объявить врагом, не будучи
     * объявленным в ответ, — поэтому проверяются обе стороны. Идущая война считается враждой
     * независимо от объявленных отношений: воевать с формально нейтральным экосистема
     * позволяет, и знать об этом надо и охотнику, и голубю.</p>
     */
    public boolean areClansEnemies(Object firstClan, Object secondClan) {
        if (firstClan == null || secondClan == null) {
            return false;
        }
        UUID firstId = (UUID) neighbour.call(clanId, firstClan);
        UUID secondId = (UUID) neighbour.call(clanId, secondClan);
        if (firstId == null || secondId == null || firstId.equals(secondId)) {
            return false;
        }
        if (Boolean.TRUE.equals(neighbour.call(isAtWar, api(), firstClan, secondClan))) {
            return true;
        }
        return isEnemyRelation(firstClan, secondId) || isEnemyRelation(secondClan, firstId);
    }

    /** Клан игрока как непрозрачный объект LoveClans — нужен тем, кто спрашивает про территорию. */
    public Optional<Object> clanOf(UUID playerId) {
        return lookup(getPlayerClan, playerId);
    }

    /** Клан по идентификатору: у привата клановой территории записан именно он, а не игрок. */
    public Optional<Object> clanById(UUID clanId) {
        return lookup(getClanById, clanId);
    }

    private Optional<Object> lookup(Method method, UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        Object api = api();
        if (api == null) {
            return Optional.empty();
        }
        Object result = neighbour.call(method, api, id);
        if (result instanceof Optional<?> optional) {
            return Optional.ofNullable(optional.orElse(null));
        }
        return Optional.ofNullable(result);
    }

    private boolean isEnemyRelation(Object clan, UUID otherClanId) {
        Object relation = neighbour.call(relationTo, clan, otherClanId);
        return relation instanceof Enum<?> value && ENEMY.equals(value.name());
    }

    /**
     * Экземпляр API LoveClans. Он инициализируется в {@code onEnable} соседа, а
     * {@code getInstance()} до этого бросает исключение — поэтому вызов идёт через
     * {@link Neighbour#call}, который такое переживает и отвечает {@code null}.
     */
    private Object api() {
        return neighbour.call(getInstance, null);
    }
}
