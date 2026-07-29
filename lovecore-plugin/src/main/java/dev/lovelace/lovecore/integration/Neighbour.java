package dev.lovelace.lovecore.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Разрешение классов и методов соседнего плагина с внятным отчётом о том, что именно не нашлось.
 *
 * <p>Зачем вообще рефлексия в ядре. На этом этапе соседи ещё не знают про {@code lovecore-api},
 * поэтому оракулы территории и репутации приходится собирать из того, что соседи уже отдают
 * наружу. Разница с тем, что было, — не в способе, а в месте: раньше пять плагинов лезли друг
 * в друга по строковым именам и глушили ошибки пустым {@code catch}, поэтому три связки не
 * работали никогда и никто этого не видел. Теперь связка одна, лежит здесь, и на старте вслух
 * говорит, нашла она соседа или нет.</p>
 *
 * <p>Это временная конструкция. На следующем этапе соседи регистрируют свои реализации
 * контрактов сами — компилятор проверяет сигнатуры, а всё, что ниже, удаляется.</p>
 */
public final class Neighbour {

    private final Logger logger;
    private final String pluginName;
    private final Plugin plugin;
    private final ClassLoader classLoader;
    private final List<String> problems = new ArrayList<>();
    private boolean callFailureLogged;

    private Neighbour(Logger logger, String pluginName, Plugin plugin) {
        this.logger = logger;
        this.pluginName = pluginName;
        this.plugin = plugin;
        // Классы соседа берём его собственным загрузчиком: полагаться на то, что загрузчик
        // ядра увидит чужие классы, нельзя — это зависит от порядка загрузки и от того,
        // объявил ли сосед себя в softdepend.
        this.classLoader = plugin == null ? null : plugin.getClass().getClassLoader();
    }

    public static Neighbour of(Logger logger, String pluginName) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        Neighbour neighbour = new Neighbour(logger, pluginName, plugin);
        if (plugin == null) {
            neighbour.problems.add("плагин " + pluginName + " не установлен");
        } else if (!plugin.isEnabled()) {
            neighbour.problems.add("плагин " + pluginName + " установлен, но выключен");
        }
        return neighbour;
    }

    public String pluginName() {
        return pluginName;
    }

    /** Класс соседа. При отсутствии возвращает {@code null} и запоминает причину. */
    public Class<?> type(String className) {
        if (classLoader == null) {
            return null;
        }
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            problems.add("нет класса " + className);
            return null;
        }
    }

    /** Метод соседа. При отсутствии возвращает {@code null} и запоминает причину. */
    public Method method(Class<?> owner, String name, Class<?>... parameters) {
        if (owner == null) {
            return null;
        }
        try {
            Method method = owner.getMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            problems.add("нет метода " + owner.getName() + "#" + name);
            return null;
        }
    }

    /** Все ли объявленные классы и методы нашлись. */
    public boolean resolved() {
        return problems.isEmpty();
    }

    /**
     * Вызов метода соседа. Возвращает {@code null}, если вызов не удался.
     *
     * <p>Первая неудача пишется в лог с полным стеком, дальнейшие — молча: связка уже
     * объявлена сломанной, и заливать лог одинаковыми стеками каждый тик незачем.</p>
     */
    public Object call(Method method, Object target, Object... arguments) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, arguments);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            if (!callFailureLogged) {
                callFailureLogged = true;
                logger.log(Level.WARNING, "Интеграция " + pluginName + ": вызов "
                        + method.getName() + " не удался, дальнейшие ошибки этой связки "
                        + "не логируются.", e);
            }
            return null;
        }
    }

    /**
     * Отчёт о связке. Мёртвый хук должен быть виден в первую же минуту работы сервера,
     * а не через полгода.
     */
    public void report(String what) {
        if (resolved()) {
            logger.info("Интеграция " + what + " (" + pluginName + "): подключена.");
            return;
        }
        logger.warning("Интеграция " + what + " (" + pluginName + "): сосед не найден, "
                + "функция отключена. Причина: " + String.join("; ", problems) + ".");
    }
}
