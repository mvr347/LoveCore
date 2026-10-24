package dev.lovelace.lovecore.integration;

import dev.lovelace.lovecore.api.provider.ProviderChoice;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Rules of {@code /lovecoreadmin provider}: which word means which choice and which config keys of
 * the neighbouring plugins hold their own provider. Kept free of Bukkit so it is tested directly.
 */
public final class ProviderSwitch {

    /** A plugin and the config key where it chooses its provider (Citizens, CMI, ReviveMe or LoveEssential). */
    public record Target(String plugin, String key) {
    }

    /** Every key that can pin a plugin to one provider; {@code global} there follows the choice of the core. */
    public static final List<Target> TARGETS = List.of(
            new Target("LoveTweaks", "integrations.npc-provider"),
            new Target("LoveTweaks", "revive.provider"),
            new Target("LoveShops", "integrations.npc-provider"),
            new Target("LoveClans", "integrations.npc-provider"),
            new Target("LoveHunt", "integrations.npc-provider"),
            new Target("LoveBrewing", "integrations.npc-provider"),
            new Target("LoveClaims", "integrations.npc-provider"),
            new Target("LoveContracts", "integrations.npc-provider"),
            new Target("LoveLeaderboards", "integrations.npc-provider"),
            new Target("Lovechat", "integrations.skins"));

    private ProviderSwitch() {
    }

    /** {@code love}, {@code loveessential}, {@code le} or {@code other}, {@code external}, {@code citizens}, {@code cmi}. */
    public static Optional<String> parse(String word) {
        if (word == null) {
            return Optional.empty();
        }
        return switch (word.trim().toLowerCase(Locale.ROOT)) {
            case "love", "loveessential", "le", "ле" -> Optional.of(ProviderChoice.GLOBAL_LOVEESSENTIAL);
            case "other", "external", "citizens", "cmi", "reviveme" -> Optional.of(ProviderChoice.GLOBAL_EXTERNAL);
            default -> Optional.empty();
        };
    }

    /** True when the plugin pins its own provider, so it would not follow the choice of the core. */
    public static boolean pinned(String configured) {
        return !ProviderChoice.followsGlobal(configured);
    }
}
