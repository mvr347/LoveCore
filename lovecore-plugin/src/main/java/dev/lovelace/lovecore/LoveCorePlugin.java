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
    private final List<String> registered = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        statBus = new BufferedStatBus(this);
        statBus.start();
        register(StatBus.class, statBus, "StatBus");

        register(LoveEconomy.class, new PhysicalEconomy(this), "LoveEconomy");

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
     * Поднимает оракулы поверх соседей и говорит вслух, какие связки нашлись.
     *
     * <p>Именно этого сегодня не хватило больше всего: три интеграции звали классы, которых
     * нет в природе, глушили ошибку пустым {@code catch} — и не работали никогда.</p>
     */
    private void linkOracles() {
        Optional<ClansProfileOracle> clans = ClansProfileOracle.link(getLogger());
        clans.ifPresent(oracle -> register(ProfileOracle.class, oracle, "ProfileOracle"));

        ClaimsTerritoryOracle.link(this, clans.orElse(null))
                .ifPresent(oracle -> register(TerritoryOracle.class, oracle, "TerritoryOracle"));

        BehaviorReputationOracle.link(this)
                .ifPresent(oracle -> register(ReputationOracle.class, oracle, "ReputationOracle"));
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
