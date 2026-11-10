package dev.lovelace.lovecore.api.achievements;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AchievementRarityTest {

    @Test
    void ratingsAreOrderedFromCommonToLegendary() {
        assertTrue(AchievementRarity.LEGENDARY.atLeast(AchievementRarity.EPIC));
        assertTrue(AchievementRarity.EPIC.atLeast(AchievementRarity.EPIC));
        assertFalse(AchievementRarity.RARE.atLeast(AchievementRarity.EPIC));
        assertFalse(AchievementRarity.COMMON.atLeast(AchievementRarity.RARE));
    }
}
