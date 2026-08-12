package dev.lovelace.lovecore.commands;

import dev.lovelace.lovecore.LoveCorePlugin;
import dev.lovelace.lovecore.api.admin.PlayerDataEraser;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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

    private static final List<String> SUBCOMMANDS = List.of("reload", "help", "cleardata");

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
        if (args.length > 0 && args[0].equalsIgnoreCase("cleardata")) {
            handleClearData(sender, args);
            return true;
        }
        sendStatus(sender);
        return true;
    }

    /**
     * Стирает данные игрока во всех плагинах, зарегистрировавших {@link PlayerDataEraser}
     * (несколько провайдеров одного интерфейса — штатный случай ServicesManager). LoveAuth в
     * этом не участвует вообще — он не реализует PlayerDataEraser и не может быть затронут
     * этой командой, даже случайно.
     */
    private void handleClearData(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<red>Использование: /lovecoreadmin cleardata <игрок> confirm</red>"));
            return;
        }
        String playerName = args[1];
        boolean confirmed = args.length >= 3 && args[2].equalsIgnoreCase("confirm");
        if (!confirmed) {
            sender.sendMessage(mm.deserialize(
                    "<red>Необратимо. LoveAuth, настройки чата/скорборда и метки LoveSubnames не трогает — "
                            + "остальное стирается безвозвратно. Повторите с confirm: <white>/lovecoreadmin cleardata "
                            + playerName + " confirm</white></red>"));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            UUID uuid = target.getUniqueId();
            boolean known = target.isOnline() || target.hasPlayedBefore();

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!known) {
                    sender.sendMessage(mm.deserialize("<red>Игрок " + playerName + " никогда не заходил на сервер.</red>"));
                    return;
                }

                Collection<RegisteredServiceProvider<PlayerDataEraser>> providers =
                        Bukkit.getServicesManager().getRegistrations(PlayerDataEraser.class);

                if (providers.isEmpty()) {
                    sender.sendMessage(mm.deserialize(
                            "<yellow>Ни один плагин не зарегистрировал PlayerDataEraser — стирать нечего.</yellow>"));
                    return;
                }

                sender.sendMessage(mm.deserialize("<yellow>Стираю данные игрока <white>" + playerName + "</white> ("
                        + providers.size() + " плагин(ов))...</yellow>"));

                for (RegisteredServiceProvider<PlayerDataEraser> registration : providers) {
                    PlayerDataEraser eraser = registration.getProvider();
                    eraser.erase(uuid).whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (throwable != null) {
                            sender.sendMessage(mm.deserialize("<red>✖ " + eraser.pluginName() + ": ошибка — "
                                    + throwable.getMessage() + "</red>"));
                            return;
                        }
                        String prefix = result.success() ? "<green>✔ " : "<red>✖ ";
                        sender.sendMessage(mm.deserialize(prefix + eraser.pluginName() + ": " + result.detail() + "</>"));
                    }));
                }
            });
        });
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
        sender.sendMessage(mm.deserialize(
                "<gold>/lovecoreadmin cleardata <игрок> confirm</gold> <gray>- Стереть данные игрока во всех "
                        + "плагинах, кроме LoveAuth (необратимо)</gray>"));
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
        if (args.length == 2 && args[0].equalsIgnoreCase("cleardata")) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            return StringUtil.copyPartialMatches(args[1], names, new ArrayList<>());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("cleardata")) {
            return StringUtil.copyPartialMatches(args[2], List.of("confirm"), new ArrayList<>());
        }
        return Collections.emptyList();
    }
}
