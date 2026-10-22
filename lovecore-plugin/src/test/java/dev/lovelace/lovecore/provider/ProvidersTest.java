package dev.lovelace.lovecore.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lovelace.lovecore.api.provider.Named;
import dev.lovelace.lovecore.api.provider.Providers;
import dev.lovelace.lovecore.api.npc.NpcSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProvidersTest {

    private record Fake(String providerId) implements Named {
    }

    @Test
    void picksTheProviderNamedInTheConfigAmongSeveral() {
        var citizens = new Fake("citizens");
        var essential = new Fake("loveessential");
        assertEquals(essential, Providers.choose(List.of(citizens, essential), "loveessential").orElseThrow());
        assertEquals(citizens, Providers.choose(List.of(citizens, essential), "citizens").orElseThrow());
    }

    @Test
    void matchingIgnoresCase() {
        var essential = new Fake("loveessential");
        assertEquals(essential, Providers.choose(List.of(essential), "LoveEssential").orElseThrow());
    }

    @Test
    void anUnknownOrMissingNameGivesNothingInsteadOfAnotherProvider() {
        var citizens = new Fake("citizens");
        assertTrue(Providers.choose(List.of(citizens), "cmi").isEmpty());
        assertTrue(Providers.choose(List.of(citizens), null).isEmpty());
        assertTrue(Providers.choose(List.<Fake>of(), "citizens").isEmpty());
    }

    @Test
    void specCopiesAreImmutableAndDefaultToAPlayerNpc() {
        var tags = new java.util.HashMap<String, String>();
        tags.put("owner", "loveshop");
        var spec = new NpcSpec(net.kyori.adventure.text.Component.text("Shop"), new org.bukkit.Location(null, 0, 0, 0), null, null, null, false, null, tags);
        tags.put("late", "x");
        assertEquals(org.bukkit.entity.EntityType.PLAYER, spec.type());
        assertEquals(1, spec.tags().size());
        assertTrue(spec.hologram().isEmpty());
    }
}
