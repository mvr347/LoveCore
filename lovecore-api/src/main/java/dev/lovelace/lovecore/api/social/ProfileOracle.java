package dev.lovelace.lovecore.api.social;

import java.util.Optional;
import java.util.UUID;

/**
 * Клановая принадлежность игрока — тонкий прокси, чтобы плагины не знали про LoveClans.
 */
public interface ProfileOracle {

    Optional<UUID> clanId(UUID playerId);

    Optional<String> clanTag(UUID playerId);

    /** Отображаемое имя клана. Пусто, если игрок не в клане. */
    Optional<String> clanName(UUID playerId);

    /**
     * Враждуют ли кланы этих игроков. В модели LoveClans вражда односторонняя — клан мог
     * объявить врагом, не будучи объявленным в ответ, — поэтому оракул проверяет обе стороны
     * и наружу отдаёт уже симметричный ответ.
     *
     * <p>Идущая война считается враждой независимо от объявленных отношений: воевать с тем,
     * кто числится нейтральным, экосистема позволяет, и охотнику это знать надо.</p>
     */
    boolean areEnemies(UUID first, UUID second);

    /** В одном ли клане игроки. Игроки без клана союзниками не считаются. */
    boolean areClanmates(UUID first, UUID second);
}
