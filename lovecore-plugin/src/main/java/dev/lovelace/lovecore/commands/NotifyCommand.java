package dev.lovelace.lovecore.commands;

import dev.lovelace.lovecore.api.notify.LoveNotify;
import dev.lovelace.lovecore.api.notify.LoveNotify.Channel;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code /lovenotify} — игрок сам решает, в каких каналах (title/actionbar/chat) видеть
 * необязательные уведомления. См. {@link LoveNotify} за правилами показа/фолбэка в чат.
 */
public class NotifyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("toggle", "status");
    private static final List<String> CHANNEL_NAMES = List.of("chat", "actionbar", "title");

    private final LoveNotify loveNotify;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public NotifyCommand(LoveNotify loveNotify) {
        this.loveNotify = loveNotify;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("toggle")) {
            Channel channel = parseChannel(args[1]);
            if (channel == null) {
                player.sendMessage(mm.deserialize("<red>Неизвестный канал. Доступные: chat, actionbar, title.</red>"));
                return true;
            }
            boolean wasEnabled = loveNotify.isChannelEnabled(player.getUniqueId(), channel);
            boolean nowEnabled = loveNotify.toggleChannel(player.getUniqueId(), channel);

            if (wasEnabled && nowEnabled) {
                // Попытка выключить канал ни к чему не привела — значит это был последний
                // оставшийся включённый канал, и LoveNotify отклонил переключение.
                player.sendMessage(mm.deserialize("<red>Нельзя выключить последний оставшийся канал! "
                        + "Сначала включите другой (title/actionbar/chat), потом выключайте этот.</red>"));
                return true;
            }

            player.sendMessage(mm.deserialize(nowEnabled
                    ? "<green>✔ Уведомления в " + displayName(channel) + " включены.</green>"
                    : "<yellow>✖ Уведомления в " + displayName(channel) + " выключены.</yellow>"));
            return true;
        }

        sendStatus(player);
        return true;
    }

    private void sendStatus(Player player) {
        Set<Channel> enabled = loveNotify.getEnabledChannels(player.getUniqueId());
        player.sendMessage(mm.deserialize("<dark_gray>========== <gold>Уведомления</gold> ==========</dark_gray>"));
        for (Channel channel : Channel.values()) {
            boolean on = enabled.contains(channel);
            player.sendMessage(mm.deserialize(
                    (on ? "<green>✔ " : "<gray>✖ ") + displayName(channel) + "</gray>"
                            + " <dark_gray>— /lovenotify toggle " + channel.name().toLowerCase(Locale.ROOT).replace("_", "") + "</dark_gray>"));
        }
        player.sendMessage(mm.deserialize(
                "<dark_gray>Важные и слишком длинные сообщения всегда показываются в чате.</dark_gray>"));
        player.sendMessage(mm.deserialize("<dark_gray>=====================================</dark_gray>"));
    }

    private String displayName(Channel channel) {
        return switch (channel) {
            case CHAT -> "чате";
            case ACTION_BAR -> "action bar";
            case TITLE -> "title bar";
        };
    }

    @Nullable
    private Channel parseChannel(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "chat", "чат" -> Channel.CHAT;
            case "actionbar", "action_bar", "action-bar" -> Channel.ACTION_BAR;
            case "title", "титл", "титлбар" -> Channel.TITLE;
            default -> null;
        };
    }

    @Nullable
    @Override
    @SuppressWarnings("NullableProblems")
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, new ArrayList<>());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            return StringUtil.copyPartialMatches(args[1], CHANNEL_NAMES, new ArrayList<>());
        }
        return Collections.emptyList();
    }
}
