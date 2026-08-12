package dev.lovelace.lovecore;

import dev.lovelace.lovecore.api.combat.CombatState;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import dev.lovelace.lovecore.api.economy.TaxOracle;
import dev.lovelace.lovecore.api.notify.LoveNotify;
import dev.lovelace.lovecore.api.social.ProfileOracle;
import dev.lovelace.lovecore.api.social.ReputationOracle;
import dev.lovelace.lovecore.api.stats.StatBus;
import dev.lovelace.lovecore.api.territory.TerritoryOracle;
import dev.lovelace.lovecore.combat.CombatTracker;
import dev.lovelace.lovecore.commands.LoveCoreAdminCommand;
import dev.lovelace.lovecore.commands.NotifyCommand;
import dev.lovelace.lovecore.commands.TaxCommand;
import dev.lovelace.lovecore.economy.PhysicalEconomy;
import dev.lovelace.lovecore.economy.TaxOracleImpl;
import dev.lovelace.lovecore.notify.LoveNotifyImpl;
import dev.lovelace.lovecore.notify.NotifySettingsStore;
import dev.lovelace.lovecore.social.BehaviorReputationOracle;
import dev.lovelace.lovecore.social.ClansProfileOracle;
import dev.lovelace.lovecore.stats.BufferedStatBus;
import dev.lovelace.lovecore.territory.ClaimsTerritoryOracle;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
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
    private NotifySettingsStore notifySettingsStore;
    private TaxOracleImpl taxOracle;
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

        notifySettingsStore = new NotifySettingsStore(this);
        notifySettingsStore.load();
        LoveNotifyImpl loveNotify = new LoveNotifyImpl(this, notifySettingsStore);
        register(LoveNotify.class, loveNotify, "LoveNotify");

        NotifyCommand notifyCommand = new NotifyCommand(loveNotify);
        var notifyCmd = getCommand("lovenotify");
        if (notifyCmd != null) {
            notifyCmd.setExecutor(notifyCommand);
            notifyCmd.setTabCompleter(notifyCommand);
        }

        taxOracle = new TaxOracleImpl(this);
        register(TaxOracle.class, taxOracle, "TaxOracle");
        TaxCommand taxCommand = new TaxCommand(this, taxOracle);
        var taxCmd = getCommand("tax");
        if (taxCmd != null) {
            taxCmd.setExecutor(taxCommand);
            taxCmd.setTabCompleter(taxCommand);
        }

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new dev.lovelace.lovecore.placeholders.NotifyPlaceholders(this, loveNotify).register();
            new dev.lovelace.lovecore.placeholders.TaxPlaceholders(this, taxOracle).register();
            getLogger().info("✓ PlaceholderAPI интеграция (LoveNotify, налог) активирована.");
        }

        // Единая admin-команда ядра: /lovecoreadmin [reload], со старым /lovecore
        // алиасом в plugin.yml — как у остальных плагинов экосистемы.
        LoveCoreAdminCommand adminCommand = new LoveCoreAdminCommand(this);
        var command = getCommand("lovecoreadmin");
        if (command != null) {
            command.setExecutor(adminCommand);
            command.setTabCompleter(adminCommand);
        }

        // Оракулы собираются из соседей, а соседи включаются после ядра — оно объявлено
        // в loadbefore. Поэтому связки поднимаются на ServerLoadEvent, когда включились все.
        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("LoveCore поднят, служб зарегистрировано: " + registered.size() + ".");
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        // Экономика (мост к ItemsAdder) резолвится ещё раз здесь по той же причине, что и
        // оракулы ниже: ItemsAdder мог ещё не включиться, когда ядро строило PhysicalEconomy
        // в onEnable() — тогда номиналы монет не находились бы вообще до перезапуска сервера.
        economy.linkItemsAdder(this);
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

    /** Список зарегистрированных служб для админ-команды ({@code /lovecoreadmin}). */
    public List<String> getRegisteredServices() {
        return Collections.unmodifiableList(registered);
    }

    /**
     * Перечитывает config.yml и применяет его вживую: экономику и бой — на месте (обновляя
     * их внутренние поля), а оракулов — переподключением, раз соседи могли появиться,
     * пропасть или сами перезагрузиться с момента старта ядра.
     */
    public void reload() {
        reloadConfig();
        economy.reload(this);
        combat.reload();
        statBus.reload();
        taxOracle.reload();
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
