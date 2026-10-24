package dev.lovelace.lovecore.notify;

import static org.junit.jupiter.api.Assertions.*;

import dev.lovelace.lovecore.api.notify.LoveNotify.Channel;
import java.io.File;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NotifySettingsStoreDbTest {
    @TempDir File dir;

    private NotifySettingsStore store() {
        var store = new NotifySettingsStore(dir, EnumSet.of(Channel.ACTION_BAR), Logger.getAnonymousLogger());
        store.load();
        return store;
    }

    @Test
    void newPlayersGetTheDefaults() {
        assertEquals(Set.of(Channel.ACTION_BAR), store().getEnabledChannels(UUID.randomUUID()));
    }

    @Test
    void choicesSurviveARestart() {
        UUID player = UUID.randomUUID();
        var first = store();
        assertTrue(first.setChannelEnabled(player, Channel.CHAT, true));
        assertTrue(first.setChannelEnabled(player, Channel.ACTION_BAR, false));
        first.flush();
        assertEquals(Set.of(Channel.CHAT), store().getEnabledChannels(player));
        assertTrue(new File(dir, "lovecore.db").isFile());
    }

    @Test
    void theLastChannelCannotBeSwitchedOff() {
        UUID player = UUID.randomUUID();
        var store = store();
        assertFalse(store.setChannelEnabled(player, Channel.ACTION_BAR, false));
        assertEquals(Set.of(Channel.ACTION_BAR), store.getEnabledChannels(player));
    }

    @Test
    void theOldYamlFileMovesIntoTheDatabaseOnce() throws Exception {
        UUID player = UUID.randomUUID();
        Files.writeString(new File(dir, "notify-settings.yml").toPath(),
                player + ":\n  TITLE: true\n  ACTION_BAR: false\n  CHAT: true\n");
        var first = store();
        assertEquals(Set.of(Channel.TITLE, Channel.CHAT), first.getEnabledChannels(player));
        first.flush();
        assertFalse(new File(dir, "notify-settings.yml").exists());
        assertTrue(new File(dir, "notify-settings.yml.migrated").exists());
        assertEquals(Set.of(Channel.TITLE, Channel.CHAT), store().getEnabledChannels(player));
    }
}
