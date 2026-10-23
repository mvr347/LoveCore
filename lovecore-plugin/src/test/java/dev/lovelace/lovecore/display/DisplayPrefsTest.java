package dev.lovelace.lovecore.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lovelace.lovecore.api.display.DisplayPrefs;
import dev.lovelace.lovecore.api.display.DisplayPrefs.Element;
import dev.lovelace.lovecore.api.display.DisplayPrefs.Scope;
import dev.lovelace.lovecore.api.display.DisplayPrefs.Surface;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DisplayPrefsTest {

    private static final class Memory implements DisplayPrefs {
        private final Map<String, Boolean> flags = new HashMap<>();

        private static String key(UUID id, Surface s, Element e, Scope sc) {
            return id + "/" + s + "/" + e + "/" + sc;
        }

        @Override
        public boolean shown(UUID player, Surface surface, Element element, Scope scope) {
            return flags.getOrDefault(key(player, surface, element, scope), true);
        }

        @Override
        public boolean set(UUID player, Surface surface, Element element, Scope scope, boolean shown) {
            Boolean old = flags.put(key(player, surface, element, scope), shown);
            return old == null || old;
        }
    }

    private final UUID owner = UUID.randomUUID();
    private final UUID viewer = UUID.randomUUID();
    private final Memory prefs = new Memory();

    @Test
    void everythingIsShownUntilThePlayerChangesSomething() {
        for (Surface surface : Surface.values()) {
            for (Element element : Element.values()) {
                assertTrue(prefs.visible(owner, viewer, surface, element));
            }
        }
    }

    @Test
    void ownersFlagHidesTheirTagFromEveryoneIncludingThemselves() {
        prefs.set(owner, Surface.CHAT, Element.CLAN_TAG, Scope.OWN, false);
        assertFalse(prefs.visible(owner, viewer, Surface.CHAT, Element.CLAN_TAG));
        assertFalse(prefs.visible(owner, owner, Surface.CHAT, Element.CLAN_TAG));
    }

    @Test
    void viewersFlagHidesOnlyOtherPeoplesTags() {
        prefs.set(viewer, Surface.NAMETAG, Element.SUFFIX, Scope.OTHERS, false);
        assertFalse(prefs.visible(owner, viewer, Surface.NAMETAG, Element.SUFFIX));
        assertTrue(prefs.visible(viewer, viewer, Surface.NAMETAG, Element.SUFFIX), "own tag stays visible to its owner");
        assertTrue(prefs.visible(owner, viewer, Surface.NAMETAG, Element.CLAN_TAG), "other element is unaffected");
        assertTrue(prefs.visible(owner, viewer, Surface.CHAT, Element.SUFFIX), "other surface is unaffected");
    }

    @Test
    void toggleFlipsAndReturnsTheNewValue() {
        assertFalse(prefs.toggle(owner, Surface.TAB, Element.SUFFIX, Scope.OWN));
        assertFalse(prefs.shown(owner, Surface.TAB, Element.SUFFIX, Scope.OWN));
        assertTrue(prefs.toggle(owner, Surface.TAB, Element.SUFFIX, Scope.OWN));
    }

    @Test
    void thereAreTwelveIndependentFlagsPerPlayer() {
        assertEquals(12, Surface.values().length * Element.values().length * Scope.values().length);
    }
}
