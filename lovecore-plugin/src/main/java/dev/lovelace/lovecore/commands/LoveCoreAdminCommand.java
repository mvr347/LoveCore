package dev.lovelace.lovecore.commands;

import dev.lovelace.lovecore.LoveCorePlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Единая административная команда ядра: {@code /lovecoreadmin [reload]}.
 * <p>
 * Раньше вся логика жила прямо в {@code onCommand} главного класса плагина под именем
 * {@code /lovecore} — единственной командой ядра и так, но не в стиле остальной экосистемы
 * Love*, где административные команды выделены в свой класс под именем {@code <plugin>admin}.
 * Старое имя {@code /lovecore} оставлено алиасом в plugin.yml, чтобы у админов, набирающих
 * его по привычке, ничего не «немело».
 */
public class LoveCoreAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "help");

    private final LoveCorePlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public LoveCoreAdminCommand(@NotNull LoveCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Разрешение "lovecore.admin" уже объявлено на самой команде в plugin.yml —
        // Bukkit отклонит вызов до onCommand() и сам покажет сообщение об отказе.
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            handleReload(sender);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        sendStatus(sender);
        return true;
    }

    private void handleReload(@NotNull CommandSender sender) {
        plugin.reload();
        int count = plugin.getRegisteredServices().size();
        sender.sendMessage(mm.deserialize(
                "<green>✔ LoveCore перезагружен, служб зарегистрировано: <white>" + count + "</white>.</green>"));
    }

    private void sendStatus(@NotNull CommandSender sender) {
        List<String> services = plugin.getRegisteredServices();

        sendHeader(sender);
        sender.sendMessage(mm.deserialize(
                "<gray>Версия: <white>" + plugin.getPluginMeta().getVersion() + "</white></gray>"));
        sender.sendMessage(mm.deserialize(
                "<gray>Служб зарегистрировано: <white>" + services.size() + "</white></gray>"));
        for (String service : services) {
            sender.sendMessage(mm.deserialize("<gray>  • <white>" + service + "</white></gray>"));
        }
        if (services.size() < 6) {
            sender.sendMessage(mm.deserialize(
                    "<yellow>⚠ Часть служб не поднялась — причина написана в логе при старте.</yellow>"));
        }
        sendFooter(sender);
    }

    private void sendHelp(@NotNull CommandSender sender) {
        sendHeader(sender);
        sender.sendMessage(mm.deserialize(
                "<gold>/lovecoreadmin</gold> <gray>- Показать, какие службы ядра подняты</gray>"));
        sender.sendMessage(mm.deserialize(
                "<gold>/lovecoreadmin reload</gold> <gray>- Перезагрузить конфигурацию ядра</gray>"));
        sendFooter(sender);
    }

    private void sendHeader(@NotNull CommandSender sender) {
        sender.sendMessage(mm.deserialize("<dark_gray>========== <gold>LoveCore Admin</gold> ==========</dark_gray>"));
    }

    private void sendFooter(@NotNull CommandSender sender) {
        sender.sendMessage(mm.deserialize("<dark_gray>=========================================</dark_gray>"));
    }

    @Nullable
    @Override
    @SuppressWarnings("NullableProblems")
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("lovecore.admin")) return Collections.emptyList();

        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, new ArrayList<>());
        }
        return Collections.emptyList();
    }
}
