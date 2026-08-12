package dev.lovelace.lovecore.placeholders;

import dev.lovelace.lovecore.api.notify.LoveNotify;
import dev.lovelace.lovecore.api.notify.LoveNotify.Channel;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Плейсхолдеры состояния каналов LoveNotify — чтобы владелец сервера мог собрать GUI
 * переключения уведомлений (DeluxeMenus и подобные) на условиях вида
 * {@code %lovecore_notify_actionbar% == true}, не трогая код.
 *
 * <p>{@code %lovecore_notify_chat%} / {@code _actionbar} / {@code _title} — "true"/"false".</p>
 */
public class NotifyPlaceholders extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final LoveNotify loveNotify;

    public NotifyPlaceholders(JavaPlugin plugin, LoveNotify loveNotify) {
        this.plugin = plugin;
        this.loveNotify = loveNotify;
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
        if (channel == null) {
            return null;
        }

        return String.valueOf(loveNotify.isChannelEnabled(player.getUniqueId(), channel));
    }
}
