package dev.lovelace.lovecore.economy;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.TaxOracle;
import dev.lovelace.lovecore.api.social.BehaviorLevels;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Считает единую ставку налога по ступеням LoveBehavior.
 *
 * <p>Формула: ступень вежливости "Ужасно" (0) всегда даёт {@code terrible-forced-percent} без
 * исключений — стиль игры её не трогает. На остальных ступенях берётся базовая ставка по
 * вежливости (config: {@code base-percent-by-politeness}), после чего стиль игры сдвигает её:
 * нейтральный стиль (ступень 3) ставку не меняет, агрессивный (0) поднимает линейно до
 * {@code base + aggressive-bonus-percent}, добрый (6) опускает линейно до {@code min-percent}.
 * Итог зажимается в [{@code min-percent}, {@code max-percent}].</p>
 *
 * <p>Если LoveBehavior не установлен ({@link BehaviorLevels} не зарегистрирован), обе ступени
 * считаются нейтральными (3) — игрок платит базовую ставку для середины шкалы вежливости, без
 * поправки стиля игры.</p>
 */
public final class TaxOracleImpl implements TaxOracle {

    private final Plugin plugin;
    private boolean enabled;
    private final Map<Integer, Double> basePercentByPoliteness = new HashMap<>();
    private double terribleForcedPercent;
    private double minPercent;
    private double maxPercent;
    private double aggressiveBonusPercent;
    private double tradeSofteningPercent;
    private int clanCreationBlockedBelowPoliteness;

    public TaxOracleImpl(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
        load();
    }

    private void load() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("tax");
        enabled = section == null || section.getBoolean("enabled", true);

        basePercentByPoliteness.clear();
        ConfigurationSection base = section == null ? null : section.getConfigurationSection("base-percent-by-politeness");
        for (int level = 0; level <= BehaviorLevels.MAX_LEVEL; level++) {
            double fallback = defaultBasePercent(level);
            double value = base == null ? fallback : base.getDouble(String.valueOf(level), fallback);
            basePercentByPoliteness.put(level, value);
        }

        terribleForcedPercent = section == null ? 75.0 : section.getDouble("terrible-forced-percent", 75.0);
        minPercent = section == null ? 5.0 : section.getDouble("min-percent", 5.0);
        maxPercent = section == null ? 75.0 : section.getDouble("max-percent", 75.0);
        aggressiveBonusPercent = section == null ? 12.0 : section.getDouble("aggressive-bonus-percent", 12.0);
        tradeSofteningPercent = section == null ? 50.0 : section.getDouble("trade-softening-percent", 50.0);
        clanCreationBlockedBelowPoliteness = section == null ? 2 : section.getInt("clan-creation-blocked-below-politeness", 2);
    }

    private static double defaultBasePercent(int level) {
        return switch (level) {
            case 0 -> 75.0;
            case 1 -> 45.0;
            case 2 -> 35.0;
            case 3 -> 25.0;
            case 4 -> 18.0;
            case 5 -> 12.0;
            default -> 5.0;
        };
    }

    @Override
    public double rate(UUID playerId) {
        if (!enabled) {
            return 0.0;
        }
        BehaviorLevels levels = LoveCore.service(BehaviorLevels.class).orElse(null);
        int politeness = levels == null || playerId == null ? BehaviorLevels.MAX_LEVEL / 2 : levels.politenessLevel(playerId);
        if (politeness <= 0) {
            return clampPercent(terribleForcedPercent) / 100.0;
        }
        int playstyle = levels == null || playerId == null ? BehaviorLevels.MAX_LEVEL / 2 : levels.playstyleLevel(playerId);
        double base = basePercentByPoliteness.getOrDefault(politeness, defaultBasePercent(politeness));

        double adjusted;
        int neutral = BehaviorLevels.MAX_LEVEL / 2;
        if (playstyle <= neutral) {
            double t = neutral == 0 ? 0 : (neutral - playstyle) / (double) neutral;
            adjusted = base + t * aggressiveBonusPercent;
        } else {
            double span = BehaviorLevels.MAX_LEVEL - neutral;
            double t = span == 0 ? 0 : (playstyle - neutral) / span;
            adjusted = base - t * (base - minPercent);
        }
        return clampPercent(adjusted) / 100.0;
    }

    @Override
    public double tradeRate(UUID playerId) {
        if (!enabled) {
            return 0.0;
        }
        BehaviorLevels levels = LoveCore.service(BehaviorLevels.class).orElse(null);
        int politeness = levels == null || playerId == null ? BehaviorLevels.MAX_LEVEL / 2 : levels.politenessLevel(playerId);
        double full = rate(playerId);
        if (politeness <= 0) {
            return full;
        }
        return full * (1.0 - clampPercent(tradeSofteningPercent) / 100.0);
    }

    @Override
    public boolean isGatedAction(UUID playerId, GatedAction action) {
        if (action != GatedAction.CLAN_CREATION) {
            return false;
        }
        BehaviorLevels levels = LoveCore.service(BehaviorLevels.class).orElse(null);
        if (levels == null || playerId == null) {
            return false;
        }
        return levels.politenessLevel(playerId) < clanCreationBlockedBelowPoliteness;
    }

    private double clampPercent(double value) {
        return Math.max(minPercent, Math.min(maxPercent, value));
    }
}
