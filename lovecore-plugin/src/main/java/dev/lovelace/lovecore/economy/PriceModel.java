package dev.lovelace.lovecore.economy;

import dev.lovelace.lovecore.api.economy.PriceOracle;
import dev.lovelace.lovecore.economy.PriceModelCore.Config;
import dev.lovelace.lovecore.economy.PriceModelCore.RoundStep;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.logging.Logger;

/**
 * Служба {@link PriceOracle}: строит {@link PriceModelCore} из реестра рецептов и {@code economy.price-model}
 * в конфиге ядра. Пересборка — на старте сервера (когда рецепты уже загружены) и по {@code /lovecoreadmin reload}.
 */
public final class PriceModel implements PriceOracle {

    private final Plugin plugin;
    private volatile PriceModelCore.Result result;

    public PriceModel(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Пересобирает модель. Главный поток (реестр рецептов). */
    public void rebuild() {
        Logger log = plugin.getLogger();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("economy.price-model");
        if (section == null || !section.getBoolean("enabled", true)) {
            result = null;
            log.info("Модель цен выключена (economy.price-model.enabled).");
            return;
        }
        Config cfg = readConfig(section, log);
        // price-overrides.yml wins over config.yml: it is written by /lovecoreadmin economy setprice
        cfg.overrides.putAll(readOverridesFile(log));
        List<PriceModelCore.Recipe> recipes = RecipeReader.read();
        PriceModelCore.Result computed = PriceModelCore.compute(cfg, recipes);
        result = computed;
        log.info("Модель цен: оценено предметов " + computed.prices().size() + " (рецептов прочитано "
                + recipes.size() + "), без цены осталось крафтовых: " + computed.unpriced().size() + ".");
    }

    private File overridesFile() {
        return new File(plugin.getDataFolder(), "price-overrides.yml");
    }

    private Map<String, Long> readOverridesFile(Logger log) {
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        File file = overridesFile();
        if (!file.isFile()) return out;
        ConfigurationSection prices = YamlConfiguration.loadConfiguration(file).getConfigurationSection("prices");
        if (prices == null) return out;
        for (String key : prices.getKeys(false)) {
            Material m = Material.matchMaterial(key);
            if (m == null) {
                log.warning("price-overrides.yml: неизвестный материал " + key);
                continue;
            }
            out.put(m.name(), prices.getLong(key));
        }
        return out;
    }

    /** Точная цена предмета (медные единицы) в price-overrides.yml; пересобирает модель. Главный поток. */
    public void setOverride(Material material, long price) throws IOException {
        writeOverride(material, price);
    }

    /** Убирает цену из price-overrides.yml; возвращает false, если её там не было. */
    public boolean removeOverride(Material material) throws IOException {
        if (!readOverridesFile(plugin.getLogger()).containsKey(material.name())) return false;
        writeOverride(material, null);
        return true;
    }

    private void writeOverride(Material material, Long price) throws IOException {
        File file = overridesFile();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.options().header("Точные цены предметов (медные единицы). Задаются командой "
                + "/lovecoreadmin economy setprice, перекрывают economy.price-model.overrides из config.yml.");
        yaml.set("prices." + material.name(), price);
        yaml.save(file);
        rebuild();
    }

    static Config readConfig(ConfigurationSection s, Logger log) {
        Config cfg = new Config();
        cfg.globalScale = s.getDouble("global-scale", 1.0);
        cfg.minPrice = Math.max(1, s.getLong("min-price", 1));
        cfg.maxPrice = Math.max(cfg.minPrice, s.getLong("max-price", 500_000));
        cfg.smeltFuelCost = s.getDouble("smelt-fuel-cost", 1);
        cfg.categoryOf = PriceCategories::of;

        ConfigurationSection markup = s.getConfigurationSection("craft-markup");
        if (markup != null) {
            cfg.defaultMarkupPercent = Math.max(0, markup.getDouble("default", 15));
            for (String key : markup.getKeys(false)) {
                if (!key.equals("default")) cfg.categoryMarkupPercent.put(key, Math.max(0, markup.getDouble(key)));
            }
        }
        ConfigurationSection items = s.getConfigurationSection("item-markup");
        if (items != null) {
            for (String key : items.getKeys(false)) cfg.itemMarkupPercent.put(key.toUpperCase(), Math.max(0, items.getDouble(key)));
        }
        readValues(s.getConfigurationSection("raw-values"), cfg, false, log);
        readValues(s.getConfigurationSection("rare-values"), cfg, true, log);
        ConfigurationSection overrides = s.getConfigurationSection("overrides");
        if (overrides != null) {
            for (String key : overrides.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                if (m == null) {
                    log.warning("economy.price-model.overrides: неизвестный материал " + key);
                    continue;
                }
                cfg.overrides.put(m.name(), overrides.getLong(key));
            }
        }
        List<RoundStep> steps = new ArrayList<>();
        for (Map<?, ?> row : s.getMapList("rounding")) {
            Object upTo = row.get("up-to");
            Object step = row.get("step");
            if (upTo instanceof Number u && step instanceof Number st && st.doubleValue() >= 1) {
                steps.add(new RoundStep(u.doubleValue(), st.doubleValue()));
            }
        }
        if (!steps.isEmpty()) {
            steps.sort((a, b) -> Double.compare(a.upTo(), b.upTo()));
            cfg.rounding = steps;
        }
        return cfg;
    }

    private static void readValues(ConfigurationSection section, Config cfg, boolean rare, Logger log) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Material m = Material.matchMaterial(key);
            if (m == null) {
                log.warning("economy.price-model: неизвестный материал " + key + " (пропущен)");
                continue;
            }
            double value = section.getDouble(key);
            if (value <= 0) continue;
            cfg.anchors.put(m.name(), value);
            if (rare) cfg.rare.add(m.name());
        }
    }

    @Override
    public boolean ready() {
        return result != null;
    }

    @Override
    public OptionalLong value(Material material) {
        PriceModelCore.Result r = result;
        if (r == null || material == null) return OptionalLong.empty();
        Long v = r.prices().get(material.name());
        return v == null ? OptionalLong.empty() : OptionalLong.of(v);
    }

    @Override
    public List<String> explain(Material material) {
        PriceModelCore.Result r = result;
        if (r == null) return List.of("Модель цен не построена.");
        return PriceModelCore.explain(r, material.name(), 3);
    }

    /** Выгрузка всех цен в {@code price-model-dump.yml} в папке ядра (для просмотра и правки overrides). */
    public File dump() throws IOException {
        PriceModelCore.Result r = result;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("Выгрузка модели цен (медные единицы). Нужное можно перенести в economy.price-model.overrides.");
        if (r != null) {
            for (Map.Entry<String, Long> e : r.prices().entrySet()) yaml.set("prices." + e.getKey(), e.getValue());
            int i = 0;
            for (String name : r.unpriced()) yaml.set("unpriced." + (i++), name);
        }
        File file = new File(plugin.getDataFolder(), "price-model-dump.yml");
        yaml.save(file);
        return file;
    }
}
