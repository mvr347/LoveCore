package dev.lovelace.lovecore.alias;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Одно определение алиаса команды. {@code target} — шаблон команды, на которую алиас
 * ссылается, с необязательными переменными {@code %1%}, {@code %2%}, ... (позиционные
 * аргументы вызова) и {@code %player%} (ник вызвавшего, только для игроков). Без переменных
 * {@code target} — просто имя/префикс реальной команды, и все аргументы алиаса дописываются
 * в конец как есть (обычный passthrough-алиас).
 */
public record AliasDefinition(String name, String target, String permission, TabMode tabMode, List<String> tabFixed) {

    /** Токены переменных внутри {@link #target}: %1%, %2%, ... или %player%. Используется и здесь, и в DynamicAliasCommand. */
    public static final Pattern TOKEN = Pattern.compile("%(\\d+|player)%");

    public enum TabMode { DELEGATE, PLAYERS, FIXED, NONE }

    public AliasDefinition {
        name = name.toLowerCase(Locale.ROOT);
        tabFixed = tabFixed == null ? List.of() : List.copyOf(tabFixed);
    }

    /** {@code target} без переменных — обычный passthrough, где делегирование таба реальной команде имеет смысл. */
    public boolean isPlainPassthrough() {
        return !TOKEN.matcher(target).find();
    }
}
