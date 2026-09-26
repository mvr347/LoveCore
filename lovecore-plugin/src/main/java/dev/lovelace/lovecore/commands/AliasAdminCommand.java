package dev.lovelace.lovecore.commands;

import dev.lovelace.lovecore.LoveCorePlugin;
import dev.lovelace.lovecore.alias.AliasDefinition;
import dev.lovelace.lovecore.alias.AliasManager;
import dev.lovelace.lovecore.alias.AliasRegistry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
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

/**
 * {@code /lovealias} (алиасы {@code алиас}, {@code алиасы}) — управляет алиасами LoveCore для
 * команд ЛЮБОГО плагина экосистемы, не только своих: см. {@link dev.lovelace.lovecore.alias}.
 * Каждое изменение и сохраняется в {@code aliases.yml}, и применяется в живом CommandMap
 * сразу (hot reload) — рестарт сервера не нужен ни для {@code add}/{@code remove}/{@code
 * permission}/{@code tab}, ни после ручной правки файла ({@code /lovealias reload}).
 */
public class AliasAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("add", "remove", "list", "info", "permission", "tab", "reload", "help");
    private static final List<String> TAB_MODES = List.of("delegate", "players", "fixed", "none");

    private final LoveCorePlugin plugin;
    private final AliasRegistry registry;
    private final AliasManager manager;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public AliasAdminCommand(LoveCorePlugin plugin, AliasRegistry registry, AliasManager manager) {
        this.plugin = plugin;
        this.registry = registry;
        this.manager = manager;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // lovecore.alias.admin уже объявлено на самой команде в plugin.yml.
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> handleAdd(sender, args);
            case "remove", "delete" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "permission", "perm" -> handlePermission(sender, args);
            case "tab" -> handleTab(sender, args);
            case "reload" -> handleReload(sender);
            case "help" -> sendHelp(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<red>Использование: /lovealias add <алиас> <целевая команда...></red>"));
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        String target = String.join(" ", List.of(args).subList(2, args.length));

        AliasDefinition existing = registry.get(name);
        AliasDefinition def = new AliasDefinition(name, target,
                existing != null ? existing.permission() : null,
                existing != null ? existing.tabMode() : AliasDefinition.TabMode.DELEGATE,
                existing != null ? existing.tabFixed() : List.of());

        registry.put(def);
        manager.applyOne(def);

        if (manager.isPrimarySlot(name)) {
            sender.sendMessage(mm.deserialize("<green>✔ Алиас <white>/" + name + "</white> -> <white>" + target + "</white> зарегистрирован.</green>"));
        } else {
            sender.sendMessage(mm.deserialize("<yellow>⚠ Алиас сохранён, но имя '" + name + "' уже занято другой командой — "
                    + "доступен только как <white>/lovecore:" + name + "</white>.</yellow>"));
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<red>Использование: /lovealias remove <алиас></red>"));
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        if (!registry.remove(name)) {
            sender.sendMessage(mm.deserialize("<red>Алиас '" + name + "' не найден.</red>"));
            return;
        }
        manager.removeOne(name);
        sender.sendMessage(mm.deserialize("<green>✔ Алиас <white>/" + name + "</white> удалён.</green>"));
    }

    private void handleList(CommandSender sender) {
        sendHeader(sender);
        if (registry.all().isEmpty()) {
            sender.sendMessage(mm.deserialize("<gray>Алиасов пока нет. /lovealias add <алиас> <команда></gray>"));
        }
        for (AliasDefinition def : registry.all().values()) {
            String slot = manager.isPrimarySlot(def.name()) ? "" : " <yellow>(занят, только lovecore:" + def.name() + ")</yellow>";
            sender.sendMessage(mm.deserialize("<gold>/" + def.name() + "</gold> <gray>-></gray> <white>" + def.target() + "</white>" + slot));
        }
        sendFooter(sender);
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize("<red>Использование: /lovealias info <алиас></red>"));
            return;
        }
        AliasDefinition def = registry.get(args[1]);
        if (def == null) {
            sender.sendMessage(mm.deserialize("<red>Алиас '" + args[1] + "' не найден.</red>"));
            return;
        }
        sendHeader(sender);
        sender.sendMessage(mm.deserialize("<gray>Имя: <white>/" + def.name() + "</white></gray>"));
        sender.sendMessage(mm.deserialize("<gray>Цель: <white>" + def.target() + "</white></gray>"));
        sender.sendMessage(mm.deserialize("<gray>Право: <white>" + (def.permission() == null ? "не задано" : def.permission()) + "</white></gray>"));
        sender.sendMessage(mm.deserialize("<gray>Таб: <white>" + def.tabMode().name().toLowerCase(Locale.ROOT)
                + (def.tabFixed().isEmpty() ? "" : " " + def.tabFixed()) + "</white></gray>"));
        sender.sendMessage(mm.deserialize("<gray>Слот: <white>" + (manager.isPrimarySlot(def.name()) ? "занят этим алиасом" : "конфликт, доступен только через lovecore:") + "</white></gray>"));
        sendFooter(sender);
    }

    private void handlePermission(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<red>Использование: /lovealias permission <алиас> <право|none></red>"));
            return;
        }
        AliasDefinition existing = registry.get(args[1]);
        if (existing == null) {
            sender.sendMessage(mm.deserialize("<red>Алиас '" + args[1] + "' не найден.</red>"));
            return;
        }
        String permission = args[2].equalsIgnoreCase("none") ? null : args[2];
        AliasDefinition updated = new AliasDefinition(existing.name(), existing.target(), permission, existing.tabMode(), existing.tabFixed());
        registry.put(updated);
        manager.applyOne(updated);
        sender.sendMessage(mm.deserialize("<green>✔ Право алиаса <white>/" + existing.name() + "</white> -> <white>"
                + (permission == null ? "не задано" : permission) + "</white>.</green>"));
    }

    private void handleTab(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<red>Использование: /lovealias tab <алиас> <delegate|players|none|fixed <слова...>></red>"));
            return;
        }
        AliasDefinition existing = registry.get(args[1]);
        if (existing == null) {
            sender.sendMessage(mm.deserialize("<red>Алиас '" + args[1] + "' не найден.</red>"));
            return;
        }
        AliasDefinition.TabMode mode;
        try {
            mode = AliasDefinition.TabMode.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(mm.deserialize("<red>Неизвестный режим таба: " + args[2] + " (delegate|players|fixed|none)</red>"));
            return;
        }
        if (mode == AliasDefinition.TabMode.DELEGATE && !existing.isPlainPassthrough()) {
            sender.sendMessage(mm.deserialize("<red>delegate не подходит для алиаса с переменными (%1%, %player% и т.п.) в target — "
                    + "используйте players/fixed/none.</red>"));
            return;
        }
        List<String> fixed = mode == AliasDefinition.TabMode.FIXED
                ? List.of(args).subList(3, args.length)
                : List.of();
        if (mode == AliasDefinition.TabMode.FIXED && fixed.isEmpty()) {
            sender.sendMessage(mm.deserialize("<red>Использование: /lovealias tab <алиас> fixed <слово1> <слово2> ...</red>"));
            return;
        }

        AliasDefinition updated = new AliasDefinition(existing.name(), existing.target(), existing.permission(), mode, fixed);
        registry.put(updated);
        manager.applyOne(updated);
        sender.sendMessage(mm.deserialize("<green>✔ Таб-режим алиаса <white>/" + existing.name() + "</white> -> <white>"
                + mode.name().toLowerCase(Locale.ROOT) + "</white>.</green>"));
    }

    private void handleReload(CommandSender sender) {
        registry.load();
        manager.applyAll();
        sender.sendMessage(mm.deserialize("<green>✔ aliases.yml перечитан, применено алиасов: <white>" + manager.count() + "</white>.</green>"));
    }

    private void sendHelp(CommandSender sender) {
        sendHeader(sender);
        sender.sendMessage(mm.deserialize("<gold>/lovealias add <алиас> <команда...></gold> <gray>- Создать/заменить алиас (можно с %1%, %2%, %player%)</gray>"));
        sender.sendMessage(mm.deserialize("<gold>/lovealias remove <алиас></gold> <gray>- Удалить алиас</gray>"));
        sender.sendMessage(mm.deserialize("<gold>/lovealias list</gold> <gray>- Список всех алиасов</gray>"));
        sender.sendMessage(mm.deserialize("<gold>/lovealias info <алиас></gold> <gray>- Подробности по алиасу</gray>"));
        sender.sendMessage(mm.deserialize("<gold>/lovealias permission <алиас> <право|none></gold> <gray>- Право доступа для алиаса</gray>"));
        sender.sendMessage(mm.deserialize("<gold>/lovealias tab <алиас> <delegate|players|fixed <слова...>|none></gold> <gray>- Таб-автодополнение</gray>"));
        sender.sendMessage(mm.deserialize("<gold>/lovealias reload</gold> <gray>- Перечитать aliases.yml с диска</gray>"));
        sendFooter(sender);
    }

    private void sendHeader(CommandSender sender) {
        sender.sendMessage(mm.deserialize("<dark_gray>========== <gold>LoveCore Aliases</gold> ==========</dark_gray>"));
    }

    private void sendFooter(CommandSender sender) {
        sender.sendMessage(mm.deserialize("<dark_gray>=====================================</dark_gray>"));
    }

    @Nullable
    @Override
    @SuppressWarnings("NullableProblems")
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("lovecore.alias.admin")) return Collections.emptyList();

        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, new ArrayList<>());
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && List.of("remove", "delete", "info", "permission", "perm", "tab").contains(sub)) {
            return StringUtil.copyPartialMatches(args[1], registry.all().keySet(), new ArrayList<>());
        }
        if (args.length == 3 && (sub.equals("permission") || sub.equals("perm"))) {
            return StringUtil.copyPartialMatches(args[2], List.of("none"), new ArrayList<>());
        }
        if (args.length == 3 && sub.equals("tab")) {
            return StringUtil.copyPartialMatches(args[2], TAB_MODES, new ArrayList<>());
        }
        if (args.length >= 4 && sub.equals("tab") && args[2].equalsIgnoreCase("players")) {
            return StringUtil.copyPartialMatches(args[args.length - 1],
                    Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), new ArrayList<>());
        }
        return Collections.emptyList();
    }
}
