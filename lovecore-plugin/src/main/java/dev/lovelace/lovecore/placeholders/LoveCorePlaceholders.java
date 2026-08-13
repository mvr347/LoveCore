package dev.lovelace.lovecore.placeholders;

import dev.lovelace.lovecore.api.economy.TaxOracle;
import dev.lovelace.lovecore.api.notify.LoveNotify;
import dev.lovelace.lovecore.api.notify.LoveNotify.Channel;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Единое расширение PlaceholderAPI с идентификатором {@code lovecore} — объединяет плейсхолдеры
 * состояния каналов LoveNotify и ставки единого налога. PlaceholderAPI хранит расширения по
 * идентификатору, поэтому регистрировать два разных класса с одним и тем же {@code lovecore}
 * нельзя — второй молча вытесняет первый из реестра без ошибок в консоли (баг, найденный при
 * тестировании GUI: {@code %lovecore_notify_chat%} переставал резолвиться, как только следом
 * регистрировался {@code TaxPlaceholders} с тем же identifier).
 *
 * <p>{@code %lovecore_notify_chat%} / {@code _actionbar} / {@code _title} — "true"/"false".</p>
 * <p>{@code %lovecore_tax_rate%} / {@code %lovecore_tax_rate_raw%} — ставка на покупки/продажи
 * NPC (магазины, аукцион и т.д.), отформатированная в процентах ("25.0%") и сырым числом
 * ("25.0") соответственно. {@code %lovecore_tax_trade_rate%} / {@code _raw} — та же пара для
 * смягчённой ставки прямых сделок между игроками (LoveTrades).</p>
 */
public class LoveCorePlaceholders extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final LoveNotify loveNotify;
    private final TaxOracle taxOracle;

    public LoveCorePlaceholders(JavaPlugin plugin, LoveNotify loveNotify, TaxOracle taxOracle) {
        this.plugin = plugin;
        this.loveNotify = loveNotify;
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

        Channel channel = switch (params.toLowerCase(Locale.ROOT)) {
            case "notify_chat" -> Channel.CHAT;
            case "notify_actionbar" -> Channel.ACTION_BAR;
            case "notify_title" -> Channel.TITLE;
            default -> null;
        };
        if (channel != null) {
            return String.valueOf(loveNotify.isChannelEnabled(player.getUniqueId(), channel));
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
