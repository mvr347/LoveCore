package dev.lovelace.lovecore.api.economy;

import java.util.UUID;

/**
 * Единый налог на все денежные операции экосистемы (кроме чёрного/секретного рынка).
 *
 * <p>Ставка считается по ступени вежливости игрока (0..6), скорректированной ступенью стиля
 * игры: ужасная вежливость (0) всегда даёт фиксированные 75% и стиль игры на неё не влияет,
 * во всех остальных случаях ставка складывается из базовой по вежливости плюс сдвиг от стиля
 * игры (агрессивный поднимает, добрый опускает к минимуму 5%). Подробности формулы — в
 * реализации; вызывающей стороне достаточно контракта ниже.</p>
 *
 * <p>Ставка едина для покупок и продаж: {@link #applyToCost} увеличивает то, что платит игрок,
 * {@link #applyToPayout} уменьшает то, что он получает — обе операции берут одну и ту же
 * {@link #rate(UUID)}. Обмен между игроками ({@link #tradeRate(UUID)}) смягчён вдвое, кроме
 * ужасной вежливости, где смягчения нет.</p>
 */
public interface TaxOracle {

    /** Полная ставка налога 0.0..1.0 для операций игрока с NPC/сервером (без смягчения). */
    double rate(UUID playerId);

    /**
     * Ставка для прямых сделок между игроками (трейды): половина {@link #rate(UUID)} —
     * кроме ужасной вежливости, где смягчения нет и ставка равна полной.
     */
    double tradeRate(UUID playerId);

    /** Запрещено ли игроку данное действие из-за низкой вежливости (например, создание клана). */
    boolean isGatedAction(UUID playerId, GatedAction action);

    /** Действия, которые полностью блокируются при низкой вежливости, а не облагаются налогом. */
    enum GatedAction {
        /** Создание клана — недоступно при вежливости «Ужасно» и «Плохо» (ступени 0-1). */
        CLAN_CREATION
    }

    /** Применяет налог к сумме, которую игрок платит (увеличивает стоимость). */
    default long applyToCost(UUID playerId, long baseCost) {
        return Math.round(baseCost * (1.0 + rate(playerId)));
    }

    /** Применяет налог к сумме, которую игрок получает (уменьшает выплату). */
    default long applyToPayout(UUID playerId, long basePayout) {
        return Math.round(basePayout * (1.0 - rate(playerId)));
    }

    /** То же самое, смягчённое для прямой сделки между двумя игроками. */
    default long applyTradeCost(UUID playerId, long baseCost) {
        return Math.round(baseCost * (1.0 + tradeRate(playerId)));
    }

    /** То же самое, смягчённое для прямой сделки между двумя игроками. */
    default long applyTradePayout(UUID playerId, long basePayout) {
        return Math.round(basePayout * (1.0 - tradeRate(playerId)));
    }
}
