package dev.lovelace.lovecore.api.achievements;

/**
 * Редкость ачивки; порядок от простой к самой редкой. Совпадает по названиям с редкостью пометок LoveSubnames,
 * чтобы оформление (цвет, голова) выбиралось одинаково.
 */
public enum AchievementRarity {
    COMMON,
    RARE,
    EPIC,
    LEGENDARY;

    /** Не ниже ли эта редкость заданной (для порога объявления на весь сервер). */
    public boolean atLeast(AchievementRarity other) {
        return ordinal() >= other.ordinal();
    }
}
