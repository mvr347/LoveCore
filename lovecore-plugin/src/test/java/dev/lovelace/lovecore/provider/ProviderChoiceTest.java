package dev.lovelace.lovecore.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lovelace.lovecore.api.provider.ProviderChoice;
import dev.lovelace.lovecore.api.provider.ProviderKind;
import org.junit.jupiter.api.Test;

class ProviderChoiceTest {

    @Test
    void globalExternalGivesEachKindItsOwnExternalPlugin() {
        assertEquals("citizens", ProviderChoice.resolve(ProviderKind.NPC, "global", "external"));
        assertEquals("cmi", ProviderChoice.resolve(ProviderKind.PRESENCE, "global", "external"));
        assertEquals("reviveme", ProviderChoice.resolve(ProviderKind.REVIVE, "global", "external"));
    }

    @Test
    void globalLoveEssentialGivesLoveEssentialForEveryKind() {
        for (ProviderKind kind : ProviderKind.values()) {
            assertEquals("loveessential", ProviderChoice.resolve(kind, "global", "loveessential"));
        }
    }

    @Test
    void blankOrMissingPluginValueFollowsTheGlobalChoice() {
        assertEquals("loveessential", ProviderChoice.resolve(ProviderKind.NPC, null, "loveessential"));
        assertEquals("loveessential", ProviderChoice.resolve(ProviderKind.NPC, "  ", "loveessential"));
        assertEquals("citizens", ProviderChoice.resolve(ProviderKind.NPC, "", "external"));
        assertEquals("loveessential", ProviderChoice.resolve(ProviderKind.NPC, " GLOBAL ", "LoveEssential"));
    }

    @Test
    void anExplicitPluginValueBeatsTheGlobalChoice() {
        assertEquals("citizens", ProviderChoice.resolve(ProviderKind.NPC, "citizens", "loveessential"));
        assertEquals("loveessential", ProviderChoice.resolve(ProviderKind.NPC, "LoveEssential", "external"));
        assertEquals("cmi", ProviderChoice.resolve(ProviderKind.PRESENCE, " CMI ", "loveessential"));
    }

    @Test
    void anUnknownGlobalValueFallsBackToExternalInsteadOfSwitchingProvider() {
        assertEquals("external", ProviderChoice.normalizeGlobal("loveessentail"));
        assertEquals("external", ProviderChoice.normalizeGlobal(null));
        assertEquals("loveessential", ProviderChoice.normalizeGlobal(" LoveEssential "));
        assertEquals("citizens", ProviderChoice.resolve(ProviderKind.NPC, "global", "loveessentail"));
    }

    @Test
    void followsGlobalOnlyForGlobalBlankOrMissing() {
        assertTrue(ProviderChoice.followsGlobal(null));
        assertTrue(ProviderChoice.followsGlobal(""));
        assertTrue(ProviderChoice.followsGlobal("Global"));
        assertFalse(ProviderChoice.followsGlobal("citizens"));
    }

    @Test
    void withoutAServerTheCoreIsAbsentAndPluginsKeepTheirOwnDefaults() {
        // Plain unit tests of plugins run without Bukkit: the lookups must say "no core", not throw.
        assertTrue(dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.provider.IntegrationSettings.class).isEmpty());
        assertEquals("citizens", dev.lovelace.lovecore.api.LoveCore.providerId(ProviderKind.NPC, "global"));
        assertEquals("cmi", dev.lovelace.lovecore.api.LoveCore.providerId(ProviderKind.PRESENCE, ""));
        assertEquals("loveessential", dev.lovelace.lovecore.api.LoveCore.providerId(ProviderKind.NPC, "LoveEssential"));
        assertTrue(dev.lovelace.lovecore.api.LoveCore.displayVisible(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                dev.lovelace.lovecore.api.display.DisplayPrefs.Surface.CHAT, dev.lovelace.lovecore.api.display.DisplayPrefs.Element.SUFFIX));
        assertTrue(dev.lovelace.lovecore.api.LoveCore.provider(dev.lovelace.lovecore.api.npc.NpcProvider.class, "citizens").isEmpty());
    }
}
