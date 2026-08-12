package dev.lovelace.lovecore.placeholders;

import dev.lovelace.lovecore.api.economy.TaxOracle;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Плейсхолдеры ставки единого налога — чтобы владелец сервера мог показать её в GUI/scoreboard
 * (DeluxeMenus и подобные), не трогая код.
 *
 * <p>{@code %lovecore_tax_rate%} / {@code %lovecore_tax_rate_raw%} — ставка на покупки/продажи
 * NPC (магазины, аукцион и т.д.), отформатированная в процентах ("25.0%") и сырым числом
 * ("25.0") соответственно. {@code %lovecore_tax_trade_rate%} / {@code _raw} — та же пара для
 * смягчённой ставки прямых сделок между игроками (LoveTrades).</p>
 */
public class TaxPlaceholders extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final TaxOracle taxOracle;

    public TaxPlaceholders(JavaPlugin plugin, TaxOracle taxOracle) {
        this.plugin = plugin;
        this.taxOracle = taxOracle;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "lovecore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "lovelace";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return null;
        }

        return switch (params.toLowerCase(Locale.ROOT)) {
            case "tax_rate" -> format(taxOracle.rate(player.getUniqueId()));
            case "tax_rate_raw" -> raw(taxOracle.rate(player.getUniqueId()));
            case "tax_trade_rate" -> format(taxOracle.tradeRate(player.getUniqueId()));
            case "tax_trade_rate_raw" -> raw(taxOracle.tradeRate(player.getUniqueId()));
            default -> null;
        };
    }

    private String format(double rate) {
        return String.format(Locale.ROOT, "%.1f%%", rate * 100.0);
    }

    private String raw(double rate) {
        return String.format(Locale.ROOT, "%.1f", rate * 100.0);
    }
}
