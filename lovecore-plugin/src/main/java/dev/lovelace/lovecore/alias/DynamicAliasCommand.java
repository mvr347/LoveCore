package dev.lovelace.lovecore.alias;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Один зарегистрированный в {@link org.bukkit.command.CommandMap} алиас. Не хранит ссылку на
 * плагин-владелец целевой команды и не кэширует саму целевую {@link Command} — на каждом
 * вызове резолвит её заново через {@code Bukkit.getCommandMap()}/{@code Bukkit.dispatchCommand},
 * поэтому переживает /reload или переустановку целевого плагина без переигрывания алиасов.
 */
public class DynamicAliasCommand extends Command {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final AliasDefinition definition;

    public DynamicAliasCommand(AliasDefinition definition) {
        super(definition.name());
        this.definition = definition;
        setDescription("Алиас LoveCore -> " + definition.target());
        setUsage("/" + definition.name() + " " + placeholderUsage());
        if (definition.permission() != null) {
            setPermission(definition.permission());
        }
    }

    public AliasDefinition definition() {
        return definition;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (definition.permission() != null && !sender.hasPermission(definition.permission())) {
            sender.sendMessage(MM.deserialize("<red>У вас нет прав на эту команду.</red>"));
            return true;
        }

        String dispatch = resolve(sender, args);
        if (dispatch == null) {
            // resolve() уже отправило игроку сообщение об ошибке (нехватка аргументов / не-игрок).
            return true;
        }
        if (dispatch.isBlank()) {
            sender.sendMessage(MM.deserialize("<red>Алиас '" + definition.name() + "' ссылается в никуда (пустой target).</red>"));
            return true;
        }

        Bukkit.dispatchCommand(sender, dispatch);
        return true;
    }

    /** Подставляет %1%, %2%, ... и %player%; непотреблённые хвостовые аргументы дописываются в конец через пробел. */
    private String resolve(CommandSender sender, String[] args) {
        Matcher matcher = AliasDefinition.TOKEN.matcher(definition.target());
        StringBuilder sb = new StringBuilder();
        int maxIndexUsed = 0;

        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement;
            if (token.equals("player")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(MM.deserialize("<red>Эта команда доступна только игрокам.</red>"));
                    return null;
                }
                replacement = player.getName();
            } else {
                int index = Integer.parseInt(token);
                maxIndexUsed = Math.max(maxIndexUsed, index);
                if (index < 1 || index > args.length) {
                    sender.sendMessage(MM.deserialize(
                            "<red>Недостаточно аргументов. Использование: /" + definition.name() + " " + placeholderUsage() + "</red>"));
                    return null;
                }
                replacement = args[index - 1];
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        for (int i = maxIndexUsed; i < args.length; i++) {
            sb.append(' ').append(args[i]);
        }
        return sb.toString();
    }

    private String placeholderUsage() {
        Matcher matcher = AliasDefinition.TOKEN.matcher(definition.target());
        List<String> parts = new ArrayList<>();
        while (matcher.find()) {
            parts.add("<" + matcher.group(1) + ">");
        }
        return String.join(" ", parts);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 0) return Collections.emptyList();
        if (definition.permission() != null && !sender.hasPermission(definition.permission())) {
            return Collections.emptyList();
        }
        return switch (definition.tabMode()) {
            case PLAYERS -> StringUtil.copyPartialMatches(args[args.length - 1],
                    Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), new ArrayList<>());
            case FIXED -> StringUtil.copyPartialMatches(args[args.length - 1], definition.tabFixed(), new ArrayList<>());
            case NONE -> Collections.emptyList();
            case DELEGATE -> delegateTabComplete(sender, args);
        };
    }

    /**
     * Делегирование таба реальной команде имеет однозначный смысл только для чистого
     * passthrough-алиаса (target без %N%/%player%) — у шаблонного target'а неясно, какому
     * количеству "чужих" аргументов соответствует текущий ввод, поэтому для него используется
     * PLAYERS/FIXED/NONE, а не DELEGATE (см. AliasAdminCommand — там это проверяется при задании tab-режима).
     */
    private List<String> delegateTabComplete(CommandSender sender, String[] args) {
        String[] targetParts = definition.target().trim().split(" ", 2);
        Command real = Bukkit.getCommandMap().getCommand(targetParts[0]);
        if (real == null || real == this) return Collections.emptyList();
        try {
            return real.tabComplete(sender, targetParts[0], args);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
