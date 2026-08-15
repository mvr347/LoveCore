package dev.lovelace.lovecore.combat;

import dev.lovelace.lovecore.api.combat.CombatState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Боевое состояние: собственный учёт PvP плюс метки сторонних плагинов.
 *
 * <p>Метку {@code in_combat} ставит не кто-то из Love*, а внешний плагин боя, и сегодня
 * LoveTweaks со свитками телепортации и LoveTrades с проверкой торговли читают её каждый
 * по-своему. Ядро продолжает её уважать, но добавляет то, чего метка не знает: осады, войны и
 * рейды помечаются вызовом {@link #mark(UUID, Duration, String)} из соответствующих плагинов.</p>
 */
public final class CombatTracker implements CombatState, Listener {

    private record Combat(Instant endsAt, String source) {
        boolean live(Instant now) {
            return endsAt.isAfter(now);
        }
    }

    private final Plugin plugin;
    // mark()/inCombat()/combatEndsAt()/combatSource() take no Player, so unlike PhysicalEconomy
    // they carry no main-thread requirement — war/siege plugins may call them from an async
    // task, while reload() rewrites these fields on the main thread. volatile keeps that safe.
    private volatile Duration pvpDuration;
    private volatile List<String> externalKeys;
    private final Map<UUID, Combat> combats = new ConcurrentHashMap<>();
    private BukkitTask purgeTask;
    private boolean listenerRegistered;

    public CombatTracker(Plugin plugin) {
        this.plugin = plugin;
        // 0 отключает собственный учёт PvP: остаются только метки соседей и явные вызовы mark().
        this.pvpDuration = Duration.ofSeconds(Math.max(0L, plugin.getConfig().getLong("combat.duration-seconds", 15L)));
        this.externalKeys = plugin.getConfig().getStringList("combat.external-metadata-keys");
    }

    public void start() {
        if (!pvpDuration.isZero()) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
        } else {
            plugin.getLogger().info("Бой: собственный учёт PvP отключён (combat.duration-seconds: 0).");
        }
        // Записи истекают сами, но без уборки карта росла бы весь аптайм сервера.
        purgeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::purge, 20L * 60, 20L * 60);
    }

    public void shutdown() {
        if (purgeTask != null) {
            purgeTask.cancel();
            purgeTask = null;
        }
        combats.clear();
    }

    /**
     * Re-reads {@code combat.duration-seconds} and {@code combat.external-metadata-keys}.
     * Toggling duration to/from zero registers or unregisters the PvP listener live -
     * without this, flipping the setting via reload would silently do nothing until restart.
     */
    public void reload() {
        this.pvpDuration = Duration.ofSeconds(Math.max(0L, plugin.getConfig().getLong("combat.duration-seconds", 15L)));
        this.externalKeys = plugin.getConfig().getStringList("combat.external-metadata-keys");

        boolean shouldTrack = !pvpDuration.isZero();
        if (shouldTrack && !listenerRegistered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
            plugin.getLogger().info("Бой: собственный учёт PvP включён (combat.duration-seconds: " + pvpDuration.getSeconds() + ").");
        } else if (!shouldTrack && listenerRegistered) {
            HandlerList.unregisterAll(this);
            listenerRegistered = false;
            plugin.getLogger().info("Бой: собственный учёт PvP отключён (combat.duration-seconds: 0).");
        }
    }

    @Override
    public boolean inCombat(UUID playerId) {
        return current(playerId).isPresent();
    }

    @Override
    public Optional<Instant> combatEndsAt(UUID playerId) {
        return current(playerId).map(Combat::endsAt);
    }

    @Override
    public Optional<String> combatSource(UUID playerId) {
        return current(playerId).map(Combat::source);
    }

    @Override
    public void mark(UUID playerId, Duration duration, String source) {
        if (playerId == null || duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }
        Instant endsAt = Instant.now().plus(duration);
        combats.merge(playerId, new Combat(endsAt, source == null ? "unknown" : source),
                // Побеждает более поздний срок: короткая метка от одного плагина не должна
                // обрубать длинную от другого.
                (existing, incoming) -> existing.endsAt().isAfter(incoming.endsAt()) ? existing : incoming);
    }

    @Override
    public void clear(UUID playerId) {
        combats.remove(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        mark(victim.getUniqueId(), pvpDuration, "pvp");
        mark(attacker.getUniqueId(), pvpDuration, "pvp");
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player direct) {
            return direct;
        }
        // Стрела считается ударом стрелявшего, иначе лучник уходил бы из боя не помеченным.
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    /**
     * Живое боевое состояние игрока.
     *
     * <p>Если единственное свидетельство боя — метка стороннего плагина, она принимается в
     * собственный учёт: иначе {@link #inCombat(UUID)} отвечал бы «да», а
     * {@link #combatEndsAt(UUID)} — «не в бою», потому что чужая метка про свой срок ничего
     * не сообщает.</p>
     */
    private Optional<Combat> current(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        Combat combat = combats.get(playerId);
        if (combat != null && combat.live(now)) {
            return Optional.of(combat);
        }
        String external = externalMark(playerId);
        if (external == null) {
            return Optional.empty();
        }
        Combat adopted = new Combat(now.plus(pvpDuration.isZero() ? Duration.ofSeconds(15) : pvpDuration), external);
        combats.put(playerId, adopted);
        return Optional.of(adopted);
    }

    /** Имя плагина, пометившего игрока своей меткой, либо {@code null}. */
    private String externalMark(UUID playerId) {
        if (externalKeys.isEmpty()) {
            return null;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return null;
        }
        for (String key : externalKeys) {
            List<MetadataValue> values = player.getMetadata(key);
            for (MetadataValue value : values) {
                if (value.asBoolean()) {
                    return value.getOwningPlugin() == null ? key : value.getOwningPlugin().getName();
                }
            }
        }
        return null;
    }

    private void purge() {
        Instant now = Instant.now();
        combats.entrySet().removeIf(entry -> !entry.getValue().live(now));
    }
}
