package dev.lovelace.lovecore.notify;

import dev.lovelace.lovecore.api.notify.LoveNotify.Channel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotifySettingsStoreTest {

    @Test
    void builtInDefaultIsActionBarOnly() {
        assertEquals(EnumSet.of(Channel.ACTION_BAR), NotifySettingsStore.BUILT_IN_DEFAULT_CHANNELS);
    }

    @Test
    void emptyOrMissingListFallsBackToBuiltIn() {
        assertEquals(EnumSet.of(Channel.ACTION_BAR), NotifySettingsStore.parseDefaultChannels(List.of(), w -> {}));
        assertEquals(EnumSet.of(Channel.ACTION_BAR), NotifySettingsStore.parseDefaultChannels(null, w -> {}));
    }

    @Test
    void parsesConfiguredChannelsAndWarnsOnUnknown() {
        List<String> warnings = new ArrayList<>();
        var parsed = NotifySettingsStore.parseDefaultChannels(List.of("chat", "Action_Bar", "bogus"), warnings::add);
        assertEquals(EnumSet.of(Channel.CHAT, Channel.ACTION_BAR), parsed);
        assertEquals(1, warnings.size());
    }

    @Test
    void allInvalidFallsBackToBuiltIn() {
        assertEquals(EnumSet.of(Channel.ACTION_BAR),
                NotifySettingsStore.parseDefaultChannels(List.of("nope"), w -> {}));
    }
}
