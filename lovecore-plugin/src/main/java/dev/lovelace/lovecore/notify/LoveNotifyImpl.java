package dev.lovelace.lovecore.notify;

import dev.lovelace.lovecore.api.notify.LoveNotify;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * См. {@link LoveNotify} — контракт и правила показа/фолбэка в чат подробно описаны там.
 */
public final class LoveNotifyImpl implements LoveNotify {

    private final JavaPlugin plugin;
    private final NotifySettingsStore store;
    private volatile int overflowLength;

    public LoveNotifyImpl(JavaPlugin plugin, NotifySettingsStore store) {
        this.plugin = plugin;
        this.store = store;
        this.overflowLength = plugin.getConfig().getInt("notify.overflow-length", 60);
    }

    /**
     * Re-reads {@code notify.overflow-length}. Without this, {@code /lovecoreadmin reload}
     * silently kept using the value from server start for the lifetime of the plugin even
     * though every other setting in {@link dev.lovelace.lovecore.LoveCorePlugin#reload()} is
     * re-applied live.
     */
    public void reload() {
        this.overflowLength = plugin.getConfig().getInt("notify.overflow-length", 60);
    }

    @Override
    public Set<Channel> getEnabledChannels(UUID uuid) {
        return store.getEnabledChannels(uuid);
    }

    @Override
    public boolean isChannelEnabled(UUID uuid, Channel channel) {
        return store.getEnabledChannels(uuid).contains(channel);
    }

    @Override
    public boolean toggleChannel(UUID uuid, Channel channel) {
        boolean currentlyEnabled = isChannelEnabled(uuid, channel);
        boolean desired = !currentlyEnabled;
        boolean applied = store.setChannelEnabled(uuid, channel, desired);
        return applied ? desired : currentlyEnabled;
    }

    @Override
    public boolean setChannelEnabled(UUID uuid, Channel channel, boolean enabled) {
        return store.setChannelEnabled(uuid, channel, enabled);
    }

    @Override
    public void notify(Player player, Component message, Set<Channel> requestedChannels, boolean important) {
        UUID uuid = player.getUniqueId();
        Set<Channel> enabled = getEnabledChannels(uuid);
        boolean tooLongForBars = plainLength(message) > overflowLength;

        boolean sentSomewhere = false;

        if (requestedChannels.contains(Channel.TITLE) && enabled.contains(Channel.TITLE) && !tooLongForBars) {
            player.showTitle(Title.title(message, Component.empty(),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(3000), Duration.ofMillis(500))));
            sentSomewhere = true;
        }
        if (requestedChannels.contains(Channel.ACTION_BAR) && enabled.contains(Channel.ACTION_BAR) && !tooLongForBars) {
            player.sendActionBar(message);
            sentSomewhere = true;
        }

        // Чат — гарантированный запасной канал: важные и "не влезающие" сообщения обязаны
        // дойти, а если title/actionbar не сработали по любой причине, чат — единственный
        // оставшийся способ игроку вообще увидеть уведомление.
        boolean mustFallbackToChat = important || tooLongForBars || !sentSomewhere;
        boolean chatWantedAndAllowed = requestedChannels.contains(Channel.CHAT) && enabled.contains(Channel.CHAT);
        if (chatWantedAndAllowed || mustFallbackToChat) {
            player.sendMessage(message);
        }
    }

    @Override
    public void notifyInteractive(Player player, Component message) {
        player.sendMessage(message);
    }

    private int plainLength(Component message) {
        return PlainTextComponentSerializer.plainText().serialize(message).length();
    }
}
