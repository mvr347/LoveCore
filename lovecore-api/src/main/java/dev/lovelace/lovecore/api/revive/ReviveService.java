package dev.lovelace.lovecore.api.revive;

import dev.lovelace.lovecore.api.provider.Named;

import java.util.Optional;
import java.util.UUID;

/**
 * Поднять раненого извне — например, эликсиром LoveBrew. {@link DownedOracle} только отвечает на
 * вопросы, а подъём делает модуль, который положил игрока: он же снимает его состояние, выдаёт
 * защиту после подъёма и шлёт свои события.
 *
 * <p>Реализация: {@code loveessential} (модуль revive). Все методы — только главный поток.</p>
 */
public interface ReviveService extends Named {

    /**
     * Поднимает раненого сразу, без обычного ожидания.
     *
     * @param downed         кто лежит
     * @param helper         кто поднял; {@code null}, если игрок поднялся сам
     * @param healthFraction доля максимального здоровья после подъёма, 0..1; реализация не даёт
     *                       опуститься ниже своего обычного минимума
     * @return {@code false}, если игрок не ранен или подъём отменён
     */
    boolean revive(UUID downed, UUID helper, double healthFraction);

    /**
     * Какого раненого изображает сущность, по которой кликнули. Тело раненого — невидимая сущность
     * взаимодействия, и знает её только реализация; без этого метода по клику не понять, кого поднимать.
     */
    Optional<UUID> downedAt(UUID entityId);
}
