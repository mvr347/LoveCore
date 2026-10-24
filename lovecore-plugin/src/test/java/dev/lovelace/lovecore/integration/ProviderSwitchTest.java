package dev.lovelace.lovecore.integration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProviderSwitchTest {
    @Test
    void wordsMapToTheTwoChoices() {
        for (String word : new String[] {"love", "LoveEssential", "le"}) {
            assertEquals("loveessential", ProviderSwitch.parse(word).orElseThrow(), word);
        }
        for (String word : new String[] {"other", "external", "Citizens", "cmi"}) {
            assertEquals("external", ProviderSwitch.parse(word).orElseThrow(), word);
        }
        assertTrue(ProviderSwitch.parse("lov").isEmpty());
        assertTrue(ProviderSwitch.parse(null).isEmpty());
    }

    @Test
    void onlyExplicitValuesArePinned() {
        assertFalse(ProviderSwitch.pinned("global"));
        assertFalse(ProviderSwitch.pinned(" Global "));
        assertFalse(ProviderSwitch.pinned(""));
        assertFalse(ProviderSwitch.pinned(null));
        assertTrue(ProviderSwitch.pinned("citizens"));
        assertTrue(ProviderSwitch.pinned("loveessential"));
    }

    @Test
    void everyTargetNamesAPluginAndAKey() {
        assertFalse(ProviderSwitch.TARGETS.isEmpty());
        for (var target : ProviderSwitch.TARGETS) {
            assertFalse(target.plugin().isBlank());
            assertTrue(target.key().contains("."));
        }
    }
}
