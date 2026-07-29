package dev.lovelace.lovecore.social;

import dev.lovelace.lovecore.api.social.ReputationOracle;
import dev.lovelace.lovecore.integration.Neighbour;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Репутация поверх LoveBehavior.
 *
 * <p>У соседа две шкалы по 0..7000 — вежливость и стиль игры, — и семь уровней на каждой.
 * Наружу отдаётся одно нормализованное число 0..100 и ступень: вызывающему незачем знать ни
 * про количество шкал, ни про их границы, а знай он их — привязался бы к ним намертво.</p>
 *
 * <p>Заодно репутация перестаёт быть бинарной. LoveShop сегодня сравнивает результат со
 * строкой {@code "good"}, поэтому всё, кроме одного значения, даёт ровно ноль скидки —
 * и хамство, и образцовое поведение стоят одинаково.</p>
 */
public final class BehaviorReputationOracle implements ReputationOracle {

    private static final String API_CLASS = "me.lovelace.lovebehavior.api.LoveBehaviorAPI";

    private final Neighbour neighbour;
    private final Object api;
    private final Method playerData;
    private final Method politenessPoints;
    private final Method playstylePoints;
    private final int maxPoints;
    private final int unknownReputation;
    private final int badFrom;
    private final int neutralFrom;
    private final int goodFrom;
    private final int respectedFrom;

    private BehaviorReputationOracle(Plugin plugin, Neighbour neighbour, Class<?> apiClass, Object api) {
        this.neighbour = neighbour;
        this.api = api;
        this.playerData = neighbour.method(apiClass, "getPlayerData", UUID.class);
        this.politenessPoints = neighbour.method(apiClass, "getPolitenessPoints", UUID.class);
        this.playstylePoints = neighbour.method(apiClass, "getPlaystylePoints", UUID.class);

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("reputation");
        this.maxPoints = Math.max(1, section == null ? 7000 : section.getInt("max-points", 7000));
        this.unknownReputation = section == null ? 50 : section.getInt("unknown", 50);
        this.badFrom = section == null ? 15 : section.getInt("tier-from.bad", 15);
        this.neutralFrom = section == null ? 35 : section.getInt("tier-from.neutral", 35);
        this.goodFrom = section == null ? 55 : section.getInt("tier-from.good", 55);
        this.respectedFrom = section == null ? 80 : section.getInt("tier-from.respected", 80);
    }

    public static Optional<BehaviorReputationOracle> link(Plugin plugin) {
        Neighbour neighbour = Neighbour.of(plugin.getLogger(), "LoveBehavior");
        Class<?> apiClass = neighbour.type(API_CLASS);
        Object api = apiClass == null ? null : Bukkit.getServicesManager().load(apiClass);
        if (apiClass != null && api == null) {
            // Плагин есть, а служба не зарегистрирована — это не «соседа нет», а сломанный
            // сосед, и молчать об этом нельзя.
            plugin.getLogger().warning("Интеграция репутация (LoveBehavior): плагин установлен, "
                    + "но LoveBehaviorAPI не зарегистрирован в ServicesManager.");
            return Optional.empty();
        }
        BehaviorReputationOracle oracle = new BehaviorReputationOracle(plugin, neighbour, apiClass, api);
        neighbour.report("репутация");
        return neighbour.resolved() ? Optional.of(oracle) : Optional.empty();
    }

    @Override
    public int reputation(UUID playerId) {
        if (playerId == null) {
            return unknownReputation;
        }
        // Про игрока не в кэше LoveBehavior сказать нечего, а его getPolitenessPoints отдаёт
        // на такого ровно 0 — то есть «данных нет» неотличимо от «полный изгой». Отличить их
        // умеет только getPlayerData, и спросить надо именно его: иначе всякий, кто ни разу
        // не заходил (и всякий онлайновый, чьи данные ещё грузятся), получал бы худшую
        // ступень и максимальный штраф к цене у скупщика.
        Object data = neighbour.call(playerData, api, playerId);
        if (!(data instanceof Optional<?> loaded) || loaded.isEmpty()) {
            return unknownReputation;
        }
        Integer politeness = points(politenessPoints, playerId);
        Integer playstyle = points(playstylePoints, playerId);
        if (politeness == null || playstyle == null) {
            return unknownReputation;
        }
        // Среднее по обеим шкалам: игрок вежливый, но агрессивный, не должен считаться
        // образцовым — и наоборот.
        double average = (politeness + playstyle) / 2.0;
        int normalized = (int) Math.round(average / maxPoints * 100.0);
        return Math.clamp(normalized, 0, 100);
    }

    @Override
    public Tier tier(UUID playerId) {
        int reputation = reputation(playerId);
        if (reputation >= respectedFrom) {
            return Tier.RESPECTED;
        }
        if (reputation >= goodFrom) {
            return Tier.GOOD;
        }
        if (reputation >= neutralFrom) {
            return Tier.NEUTRAL;
        }
        if (reputation >= badFrom) {
            return Tier.BAD;
        }
        return Tier.OUTCAST;
    }

    private Integer points(Method method, UUID playerId) {
        Object value = neighbour.call(method, api, playerId);
        return value instanceof Number number ? number.intValue() : null;
    }
}
