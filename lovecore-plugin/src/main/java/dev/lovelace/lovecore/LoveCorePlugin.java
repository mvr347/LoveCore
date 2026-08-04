package dev.lovelace.lovecore;

import dev.lovelace.lovecore.api.combat.CombatState;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import dev.lovelace.lovecore.api.social.ProfileOracle;
import dev.lovelace.lovecore.api.social.ReputationOracle;
import dev.lovelace.lovecore.api.stats.StatBus;
import dev.lovelace.lovecore.api.territory.TerritoryOracle;
import dev.lovelace.lovecore.combat.CombatTracker;
import dev.lovelace.lovecore.economy.PhysicalEconomy;
import dev.lovelace.lovecore.social.BehaviorReputationOracle;
import dev.lovelace.lovecore.social.ClansProfileOracle;
import dev.lovelace.lovecore.stats.BufferedStatBus;
import dev.lovelace.lovecore.territory.ClaimsTerritoryOracle;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ядро экосистемы Love*: реестр служб, кошелёк, боевое состояние, шина статистики.
 *
 * <p>Ядро ничего не решает за плагины — оно только отвечает на вопросы, которые сегодня
 * каждый плагин задаёт соседям сам, рефлексией по строковым именам классов. Правила варки,
 * механика войн, GUI и конфиги плагинов остаются у плагинов: иначе «единое ядро» превратится
 * в монолит, ради правки в котором придётся трогать всё сразу.</p>
 */
public final class LoveCorePlugin extends JavaPlugin implements Listener {

    private BufferedStatBus statBus;
    private CombatTracker combat;
    private PhysicalEconomy economy;
    private ProfileOracle profileOracle;
    private TerritoryOracle territoryOracle;
    private ReputationOracle reputationOracle;
    private final List<String> registered = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        statBus = new BufferedStatBus(this);
        statBus.start();
        register(StatBus.class, statBus, "StatBus");

        economy = new PhysicalEconomy(this);
        register(LoveEconomy.class, economy, "LoveEconomy");

        combat = new CombatTracker(this);
        combat.start();
        register(CombatState.class, combat, "CombatState");

        // Оракулы собираются из соседей, а соседи включаются после ядра — оно объявлено
        // в loadbefore. Поэтому связки поднимаются на ServerLoadEvent, когда включились все.
        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("LoveCore поднят, служб зарегистрировано: " + registered.size() + ".");
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        linkOracles();
        getLogger().info("LoveCore готов, служб зарегистрировано: " + registered.size()
                + " (" + String.join(", ", registered) + ").");
    }

    @Override
    public void onDisable() {
        if (combat != null) {
            combat.shutdown();
        }
        if (statBus != null) {
            statBus.shutdown();
        }
        Bukkit.getServicesManager().unregisterAll(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reload();
            sender.sendMessage("LoveCore перезагружен, служб зарегистрировано: " + registered.size() + ".");
            return true;
        }

        sender.sendMessage("LoveCore " + getPluginMeta().getVersion() + ", служб: " + registered.size());
        for (String service : registered) {
            sender.sendMessage(" - " + service);
        }
        if (registered.size() < 6) {
            sender.sendMessage("Часть служб не поднялась — причина написана в логе при старте.");
        }
        return true;
    }

    /**
     * Перечитывает config.yml и применяет его вживую: экономику и бой — на месте (обновляя
     * их внутренние поля), а оракулов — переподключением, раз соседи могли появиться,
     * пропасть или сами перезагрузиться с момента старта ядра.
     */
    private void reload() {
        reloadConfig();
        economy.reload(this);
        combat.reload();
        statBus.reload();
        relinkOracles();
    }

    /**
     * Поднимает оракулы поверх соседей и говорит вслух, какие связки нашлись.
     *
     * <p>Именно этого сегодня не хватило больше всего: три интеграции звали классы, которых
     * нет в природе, глушили ошибку пустым {@code catch} — и не работали никогда.</p>
     */
    private void linkOracles() {
        Optional<ClansProfileOracle> clans = ClansProfileOracle.link(getLogger());
        clans.ifPresent(oracle -> {
            profileOracle = oracle;
            register(ProfileOracle.class, oracle, "ProfileOracle");
        });

        ClaimsTerritoryOracle.link(this, clans.orElse(null))
                .ifPresent(oracle -> {
                    territoryOracle = oracle;
                    register(TerritoryOracle.class, oracle, "TerritoryOracle");
                });

        BehaviorReputationOracle.link(this)
                .ifPresent(oracle -> {
                    reputationOracle = oracle;
                    register(ReputationOracle.class, oracle, "ReputationOracle");
                });
    }

    /** Отключает уже поднятые оракулы (если были) и пытается поднять их заново. */
    private void relinkOracles() {
        if (profileOracle != null) {
            Bukkit.getServicesManager().unregister(ProfileOracle.class, profileOracle);
            registered.remove("ProfileOracle");
            profileOracle = null;
        }
        if (territoryOracle != null) {
            Bukkit.getServicesManager().unregister(TerritoryOracle.class, territoryOracle);
            registered.remove("TerritoryOracle");
            territoryOracle = null;
        }
        if (reputationOracle != null) {
            Bukkit.getServicesManager().unregister(ReputationOracle.class, reputationOracle);
            registered.remove("ReputationOracle");
            reputationOracle = null;
        }
        linkOracles();
    }

    /**
     * Регистрация с приоритетом {@code Normal}. Когда сосед научится реализовывать контракт
     * сам, он зарегистрируется выше — и его реализация вытеснит здешнюю, собранную
     * рефлексией, без правок в ядре.
     */
    private <T> void register(Class<T> type, T implementation, String name) {
        Bukkit.getServicesManager().register(type, implementation, this, ServicePriority.Normal);
        registered.add(name);
    }
}
