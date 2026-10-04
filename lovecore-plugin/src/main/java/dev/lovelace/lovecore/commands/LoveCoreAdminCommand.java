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

    private static final List<String> SUBCOMMANDS = List.of("reload", "help", "cleardata", "economy");

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
        if (args.length > 0 && (args[0].equalsIgnoreCase("economy") || args[0].equalsIgnoreCase("экономика"))) {
            handleEconomy(sender, args);
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

    /** {@code /lovecoreadmin economy <index|price|dump-prices|status>}: ручки общей экономики. */
    private void handleEconomy(@NotNull CommandSender sender, @NotNull String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "status";
        var eco = plugin.getEconomy();
        switch (sub) {
            case "index" -> {
                if (args.length < 3 || args[2].equalsIgnoreCase("show")) {
                    sender.sendMessage(mm.deserialize("<gray>Индекс цен: <white>" + eco.priceIndex() + "</white></gray>"));
                    return;
                }
                double value;
                if (args[2].equalsIgnoreCase("reset") || args[2].equalsIgnoreCase("сброс")) {
                    value = 1.0;
                } else {
                    try {
                        value = Double.parseDouble(args[2].replace(',', '.'));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(mm.deserialize("<red>Использование: /lovecoreadmin economy index <число|reset|show></red>"));
                        return;
                    }
                }
                if (!(value >= 0.1 && value <= 100.0)) {
                    sender.sendMessage(mm.deserialize("<red>Индекс цен должен быть от 0.1 до 100.</red>"));
                    return;
                }
                double old = eco.priceIndex();
                eco.setPriceIndex(plugin, value);
                sender.sendMessage(mm.deserialize("<green>Индекс цен: <white>" + old + " → " + value
                        + "</white>. Плагины применяют его к конфигурационным ценам и наградам; суммы в их базах не меняются.</green>"));
            }
            case "price" -> {
                if (args.length < 3) {
                    sender.sendMessage(mm.deserialize("<red>Использование: /lovecoreadmin economy price <предмет></red>"));
                    return;
                }
                org.bukkit.Material material = org.bukkit.Material.matchMaterial(args[2]);
                if (material == null) {
                    sender.sendMessage(mm.deserialize("<red>Неизвестный предмет: " + args[2] + "</red>"));
                    return;
                }
                for (String line : plugin.getPriceModel().explain(material)) {
                    sender.sendMessage(net.kyori.adventure.text.Component.text(line));
                }
                plugin.getPriceModel().value(material).ifPresent(v -> sender.sendMessage(mm.deserialize(
                        "<gray>С индексом цен: <white>" + eco.scaled(v) + "</white> (индекс " + eco.priceIndex() + ")</gray>")));
            }
            case "setprice" -> {
                if (args.length < 4) {
                    sender.sendMessage(mm.deserialize(
                            "<red>Использование: /lovecoreadmin economy setprice <предмет|held> <цена, напр. 3i 50c></red>"));
                    return;
                }
                org.bukkit.Material material = resolveMaterial(sender, args[2]);
                if (material == null) return;
                long price;
                try {
                    price = eco.parse(String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)));
                } catch (RuntimeException e) {
                    sender.sendMessage(mm.deserialize("<red>Не удалось разобрать цену: " + e.getMessage() + "</red>"));
                    return;
                }
                if (price < 1) {
                    sender.sendMessage(mm.deserialize("<red>Цена должна быть не меньше 1 медной единицы.</red>"));
                    return;
                }
                try {
                    plugin.getPriceModel().setOverride(material, price);
                } catch (java.io.IOException e) {
                    sender.sendMessage(mm.deserialize("<red>Не удалось записать price-overrides.yml: " + e.getMessage() + "</red>"));
                    return;
                }
                sender.sendMessage(mm.deserialize("<green>Цена <white>" + material.name() + "</white> = <white>" + price
                        + "</white> мед. Плагины подхватят её после своего reload.</green>"));
            }
            case "resetprice" -> {
                if (args.length < 3) {
                    sender.sendMessage(mm.deserialize("<red>Использование: /lovecoreadmin economy resetprice <предмет|held></red>"));
                    return;
                }
                org.bukkit.Material material = resolveMaterial(sender, args[2]);
                if (material == null) return;
                try {
                    boolean removed = plugin.getPriceModel().removeOverride(material);
                    sender.sendMessage(mm.deserialize(removed
                            ? "<green>Ручная цена <white>" + material.name() + "</white> снята, действует модель.</green>"
                            : "<yellow>Для " + material.name() + " ручной цены нет (price-overrides.yml).</yellow>"));
                } catch (java.io.IOException e) {
                    sender.sendMessage(mm.deserialize("<red>Не удалось записать price-overrides.yml: " + e.getMessage() + "</red>"));
                }
            }
            case "dump-prices", "dump" -> {
                try {
                    java.io.File file = plugin.getPriceModel().dump();
                    sender.sendMessage(mm.deserialize("<green>Цены выгружены: <white>" + file.getName() + "</white></green>"));
                } catch (java.io.IOException e) {
                    sender.sendMessage(mm.deserialize("<red>Не удалось записать файл: " + e.getMessage() + "</red>"));
                }
            }
            default -> {
                sender.sendMessage(mm.deserialize("<gray>Индекс цен: <white>" + eco.priceIndex() + "</white>, версия масштаба: <white>"
                        + eco.economyScaleVersion() + "</white>, модель цен: <white>"
                        + (plugin.getPriceModel().ready() ? "построена" : "не построена") + "</white></gray>"));
                sender.sendMessage(mm.deserialize("<gray>/lovecoreadmin economy <index|price|setprice|resetprice|dump-prices></gray>"));
            }
        }
    }

    /** Material by name, or the item in the sender's main hand for {@code held}; reports the error itself. */
    @Nullable
    private org.bukkit.Material resolveMaterial(@NotNull CommandSender sender, @NotNull String arg) {
        if (arg.equalsIgnoreCase("held") || arg.equalsIgnoreCase("рука")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(mm.deserialize("<red>«held» доступно только игроку.</red>"));
                return null;
            }
            org.bukkit.Material held = player.getInventory().getItemInMainHand().getType();
            if (held.isAir()) {
                sender.sendMessage(mm.deserialize("<red>Возьмите предмет в основную руку.</red>"));
                return null;
            }
            return held;
        }
        org.bukkit.Material material = org.bukkit.Material.matchMaterial(arg);
        if (material == null) sender.sendMessage(mm.deserialize("<red>Неизвестный предмет: " + arg + "</red>"));
        return material;
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
                "<gold>/lovecoreadmin economy <index|price|setprice|resetprice|dump-prices></gold> <gray>- Индекс цен, разбор цены, "
                        + "точная цена предмета, выгрузка модели цен</gray>"));
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
        if (args.length == 2 && args[0].equalsIgnoreCase("economy")) {
            return StringUtil.copyPartialMatches(args[1], List.of("status", "index", "price", "setprice", "resetprice", "dump-prices"), new ArrayList<>());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("economy") && args[1].equalsIgnoreCase("index")) {
            return StringUtil.copyPartialMatches(args[2], List.of("show", "reset", "0.8", "1.0", "1.2"), new ArrayList<>());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("economy")
                && List.of("price", "setprice", "resetprice").contains(args[1].toLowerCase(java.util.Locale.ROOT))) {
            List<String> names = new ArrayList<>(List.of("held"));
            java.util.Arrays.stream(org.bukkit.Material.values())
                    .map(m -> m.name().toLowerCase(java.util.Locale.ROOT)).forEach(names::add);
            return StringUtil.copyPartialMatches(args[2], names, new ArrayList<>());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("cleardata")) {
            return StringUtil.copyPartialMatches(args[2], List.of("confirm"), new ArrayList<>());
        }
        return Collections.emptyList();
    }
}
