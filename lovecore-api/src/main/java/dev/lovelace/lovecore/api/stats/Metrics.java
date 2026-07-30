package dev.lovelace.lovecore.api.stats;

/**
 * Единый словарь имён метрик, чтобы «kills» и «kill» не разъехались по плагинам.
 *
 * <p>Новая категория в топе — это строка конфига LoveLeaderboards плюс константа здесь,
 * а не новый класс интеграции.</p>
 */
public final class Metrics {

    // --- Игрок ---
    public static final String KILLS = "kills";
    public static final String DEATHS = "deaths";
    public static final String BOUNTIES_COMPLETED = "bounties.completed";
    public static final String HUNTER_RATING = "hunter.rating";
    public static final String BREWER_SKILL = "brewer.skill";
    public static final String BEVERAGES_BREWED = "brewer.beverages";
    public static final String TRADES_COMPLETED = "trades.completed";
    public static final String BALANCE = "balance";
    public static final String CONTRACTS_COMPLETED = "contracts.completed";

    // --- Клан ---
    public static final String CLAN_INFLUENCE = "clan.influence";
    public static final String CLAN_WEALTH = "clan.wealth";
    public static final String CLAN_WARS_WON = "clan.wars.won";
    public static final String CLAN_TERRITORIES = "clan.territories";
    public static final String CLAN_CONTRACTS_COMPLETED = "clan.contracts.completed";

    private Metrics() {
    }
}
