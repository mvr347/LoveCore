package dev.lovelace.lovecore.presence;

import com.destroystokyo.paper.profile.ProfileProperty;
import dev.lovelace.lovecore.api.presence.PresenceOracle;
import dev.lovelace.lovecore.api.presence.SkinTexture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * {@link PresenceOracle} поверх CMI. CMI не отдаёт стабильного API, поэтому его классы разбираются
 * рефлексией; каждый сбой разбора логируется один раз и ответ деградирует до того, что умеет сам
 * Paper (метаданные {@code vanished}, имя для показа, профиль игрока).
 */
public final class CmiPresenceOracle implements PresenceOracle {

    public static final String ID = "cmi";

    private final Logger logger;
    private Object playerManager;
    private Method getUser;
    private boolean reported;

    private CmiPresenceOracle(Logger logger) {
        this.logger = logger;
    }

    /** Поднимает адаптер, если CMI включён; иначе пусто. */
    public static Optional<CmiPresenceOracle> link(Logger logger) {
        if (!Bukkit.getPluginManager().isPluginEnabled("CMI")) {
            return Optional.empty();
        }
        CmiPresenceOracle oracle = new CmiPresenceOracle(logger);
        try {
            Class<?> cmi = Class.forName("com.Zrips.CMI.CMI");
            Object instance = cmi.getMethod("getInstance").invoke(null);
            oracle.playerManager = cmi.getMethod("getPlayerManager").invoke(instance);
            oracle.getUser = oracle.playerManager.getClass().getMethod("getUser", UUID.class);
        } catch (Throwable broken) {
            // Классы CMI могут ссылаться на необязательные зависимости, которых нет на сервере, —
            // это Error, а не Exception. В любом случае остаёмся на запасных ответах Paper.
            logger.warning("CMI найден, но менеджер игроков не разобран (" + broken
                    + "): присутствие игроков берётся из Paper.");
        }
        return Optional.of(oracle);
    }

    @Override
    public String providerId() {
        return ID;
    }

    @Override
    public boolean vanished(UUID playerId) {
        Object value = user(playerId, "isVanished");
        if (value instanceof Boolean flag) {
            return flag;
        }
        Player player = Bukkit.getPlayer(playerId);
        return player != null && player.hasMetadata("vanished")
                && player.getMetadata("vanished").stream().anyMatch(meta -> meta.asBoolean());
    }

    @Override
    public boolean afk(UUID playerId) {
        return user(playerId, "isAfk") instanceof Boolean flag && flag;
    }

    @Override
    public OptionalLong lastActivity(UUID playerId) {
        for (String name : new String[] {"getLastActivity", "getLastAction"}) {
            if (user(playerId, name) instanceof Number millis) {
                return OptionalLong.of(millis.longValue());
            }
        }
        return OptionalLong.empty();
    }

    @Override
    public Optional<Component> displayName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return Optional.empty();
        }
        Component shown = player.displayName();
        String plain = PlainTextComponentSerializer.plainText().serialize(shown);
        return plain.equals(player.getName()) ? Optional.empty() : Optional.of(shown);
    }

    @Override
    public Optional<SkinTexture> skin(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return Optional.empty();
        }
        // CMI меняет сам профиль игрока, поэтому его текстура и есть выбранный скин.
        for (ProfileProperty property : player.getPlayerProfile().getProperties()) {
            if ("textures".equalsIgnoreCase(property.getName()) && property.getSignature() != null) {
                return Optional.of(new SkinTexture(property.getValue(), property.getSignature()));
            }
        }
        return Optional.empty();
    }

    private Object user(UUID playerId, String method) {
        if (getUser == null) {
            return null;
        }
        try {
            Object user = getUser.invoke(playerManager, playerId);
            return user == null ? null : user.getClass().getMethod(method).invoke(user);
        } catch (NoSuchMethodException unknown) {
            return null;
        } catch (Throwable broken) {
            if (!reported) {
                reported = true;
                logger.warning("Вызов CMI " + method + " не удался: " + broken);
            }
            return null;
        }
    }
}
