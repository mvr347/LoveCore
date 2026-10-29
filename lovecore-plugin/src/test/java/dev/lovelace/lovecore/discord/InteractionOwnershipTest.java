package dev.lovelace.lovecore.discord;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InteractionOwnershipTest {

    @Test
    void ownsOnlyRegisteredSlashCommands() {
        InteractionOwnership o = new InteractionOwnership();
        o.addCommand("жалоба");
        assertTrue(o.owns(2, "жалоба"));
        assertFalse(o.owns(2, "menu"));
        o.removeCommand("жалоба");
        assertFalse(o.owns(2, "жалоба"));
    }

    @Test
    void componentsAreOursOnlyUnderClaimedPrefix() {
        InteractionOwnership o = new InteractionOwnership();
        assertFalse(o.owns(3, "lwa:report:claim:1"));
        o.claimPrefix("lwa:");
        assertTrue(o.owns(3, "lwa:report:claim:1"));
        assertTrue(o.owns(5, "lwa:report:submit"));
        assertFalse(o.owns(3, "login_confirm:abc:def"));
        assertFalse(o.owns(3, "panel:1:x"));
    }

    @Test
    void blankAndNullAreIgnored() {
        InteractionOwnership o = new InteractionOwnership();
        o.claimPrefix("");
        o.claimPrefix(null);
        o.addCommand(null);
        assertFalse(o.owns(3, "anything"));
        assertFalse(o.owns(3, null));
    }
}
