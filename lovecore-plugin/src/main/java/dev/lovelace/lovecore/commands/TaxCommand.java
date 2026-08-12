package dev.lovelace.lovecore.commands;

import dev.lovelace.lovecore.LoveCorePlugin;
import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.TaxOracle;
import dev.lovelace.lovecore.api.social.BehaviorLevels;
import dev.lovelace.lovecore.economy.TaxOracleImpl;
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

/**
 * {@code /tax} — игрок смотрит свою текущую ставку налога, админ настраивает параметры
 * формулы ({@link TaxOracleImpl}) без правки config.yml руками.
 */
public class TaxCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("info", "set", "reload");
    private static final List<String> SET_KEYS = List.of(
            "politeness", "terrible", "min", "max", "aggressive-bonus", "trade-softening");
    private static final List<String> POLITENESS_LEVELS = List.of("0", "1", "2", "3", "4", "5", "6");

    private final LoveCorePlugin plugin;
    private final TaxOracleImpl taxOracle;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public TaxCommand(LoveCorePlugin plugin, TaxOracleImpl taxOracle) {
        this.plugin = plugin;
        this.taxOracle = taxOracle;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            return handleInfo(sender);
        }
        if (args[0].equalsIgnoreCase("set")) {
            return handleSet(sender, args);
        }
        if (args[0].equalsIgnoreCase("reload")) {
            return handleReload(sender);
        }
        sender.sendMessage(mm.deserialize("<red>Использование: /tax [info|set <ключ> <значение>|reload]</red>"));
        return true;
    }

    private boolean handleInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        TaxOracle oracle = LoveCore.service(TaxOracle.class).orElse(taxOracle);
        double rate = oracle.rate(player.getUniqueId()) * 100.0;
        double tradeRate = oracle.tradeRate(player.getUniqueId()) * 100.0;
        player.sendMessage(mm.deserialize("<dark_gray>========== <gold>Налог</gold> ==========</dark_gray>"));
        player.sendMessage(mm.deserialize(String.format(Locale.ROOT,
                "<gray>Ставка на покупки/продажи NPC: <yellow>%.1f%%</yellow></gray>", rate)));
        player.sendMessage(mm.deserialize(String.format(Locale.ROOT,
                "<gray>Ставка на прямые сделки с игроками: <yellow>%.1f%%</yellow></gray>", tradeRate)));
        LoveCore.service(BehaviorLevels.class).ifPresent(levels -> {
            player.sendMessage(mm.deserialize(String.format(Locale.ROOT,
                    "<dark_gray>Вежливость: ступень %d, стиль игры: ступень %d.</dark_gray>",
                    levels.politenessLevel(player.getUniqueId()), levels.playstyleLevel(player.getUniqueId()))));
        });
        player.sendMessage(mm.deserialize("<dark_gray>===============================</dark_gray>"));
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lovecore.tax.admin")) {
            sender.sendMessage(mm.deserialize("<red>Недостаточно прав.</red>"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(mm.deserialize("<red>Использование: /tax set <ключ> [ступень] <значение></red>"));
            return true;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        try {
            switch (key) {
                case "politeness" -> {
                    if (args.length < 4) {
                        sender.sendMessage(mm.deserialize("<red>Использование: /tax set politeness <0-6> <процент></red>"));
                        return true;
                    }
                    int level = Integer.parseInt(args[2]);
                    if (level < 0 || level > BehaviorLevels.MAX_LEVEL) {
                        sender.sendMessage(mm.deserialize("<red>Ступень должна быть 0..6.</red>"));
                        return true;
                    }
                    double percent = Double.parseDouble(args[3]);
                    plugin.getConfig().set("tax.base-percent-by-politeness." + level, percent);
                }
                case "terrible" -> plugin.getConfig().set("tax.terrible-forced-percent", Double.parseDouble(args[2]));
                case "min" -> plugin.getConfig().set("tax.min-percent", Double.parseDouble(args[2]));
                case "max" -> plugin.getConfig().set("tax.max-percent", Double.parseDouble(args[2]));
                case "aggressive-bonus" -> plugin.getConfig().set("tax.aggressive-bonus-percent", Double.parseDouble(args[2]));
                case "trade-softening" -> plugin.getConfig().set("tax.trade-softening-percent", Double.parseDouble(args[2]));
                default -> {
                    sender.sendMessage(mm.deserialize("<red>Неизвестный ключ. Доступные: "
                            + String.join(", ", SET_KEYS) + "</red>"));
                    return true;
                }
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(mm.deserialize("<red>Значение должно быть числом.</red>"));
            return true;
        }
        plugin.saveConfig();
        taxOracle.reload();
        sender.sendMessage(mm.deserialize("<green>Налог обновлён и применён.</green>"));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("lovecore.tax.admin")) {
            sender.sendMessage(mm.deserialize("<red>Недостаточно прав.</red>"));
            return true;
        }
        plugin.reloadConfig();
        taxOracle.reload();
        sender.sendMessage(mm.deserialize("<green>Конфиг налога перечитан.</green>"));
        return true;
    }

    @Nullable
    @Override
    @SuppressWarnings("NullableProblems")
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, new ArrayList<>());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return StringUtil.copyPartialMatches(args[1], SET_KEYS, new ArrayList<>());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set") && args[1].equalsIgnoreCase("politeness")) {
            return StringUtil.copyPartialMatches(args[2], POLITENESS_LEVELS, new ArrayList<>());
        }
        return Collections.emptyList();
    }
}
