package dev.lovelace.lovecore.economy;

import dev.lovelace.lovecore.api.economy.Denomination;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import dev.lovelace.lovecore.api.economy.MoneyParser;
import dev.lovelace.lovecore.integration.Neighbour;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.List;

/**
 * Валюта как физические предметы ItemsAdder в инвентаре игрока.
 *
 * <p>Мост к ItemsAdder — рефлексия через {@link Neighbour}, тем же приёмом, что и у оракулов.
 * ItemsAdder — softdepend, его может не быть на сервере, и ядро не должно падать из-за этого.
 * Без ItemsAdder номинал ни у одного предмета не находится — баланс всегда 0, списание и
 * выдача молча ничего не делают.</p>
 *
 * <p>По той же причине, по которой оракулы в {@code LoveCorePlugin} поднимаются не в
 * {@code onEnable()}, а на {@code ServerLoadEvent}: ядро объявлено в {@code loadbefore} у
 * своих соседей, но softdepend на ItemsAdder у него намеренно нет (иначе ядро грузилось бы
 * позже тех, кто в нём нуждается) — порядок между ядром и ItemsAdder никем не гарантирован.
 * Если связка резолвится один раз в конструкторе (вызывается из {@code onEnable()}), а
 * ItemsAdder в этот момент ещё не включился, {@link Neighbour} навсегда запоминает «сосед не
 * найден» — методы отражения остаются {@code null} до перезапуска сервера, и валюта не
 * работает вообще, хотя ItemsAdder мгновением позже включается штатно. Поэтому резолвинг
 * вынесен в отдельный {@link #linkItemsAdder(Plugin)}, который {@code LoveCorePlugin} зовёт
 * повторно на {@code ServerLoadEvent} — когда включились уже все.</p>
 */
public final class PhysicalEconomy implements LoveEconomy {

    private String currencyName;
    /** Видимые номиналы: из них строятся сумма выдачи, сдача, глифы и обмен. */
    private volatile List<Denomination> denominations;
    /** Все номиналы, включая скрытые: по ним монета на руках опознаётся как деньги. */
    private volatile List<Denomination> allDenominations;
    private volatile double priceIndex = 1.0;
    private volatile int scaleVersion = 1;
    private Neighbour itemsAdder;
    private Method byItemStack;
    private Method getNamespacedId;
    private Method getInstance;
    private Method getItemStack;

    public PhysicalEconomy(Plugin plugin) {
        applyConfig(plugin);
        linkItemsAdder(plugin);
    }

    private void applyConfig(Plugin plugin) {
        this.currencyName = plugin.getConfig().getString("economy.currency-name", "монет");
        List<Denomination> all = loadDenominations(plugin);
        Set<String> hidden = new HashSet<>();
        for (String id : plugin.getConfig().getStringList("economy.hidden-denominations")) {
            hidden.add(shortId(id).toLowerCase(Locale.ROOT));
        }
        List<Denomination> visible = new ArrayList<>();
        for (Denomination d : all) {
            if (!hidden.contains(shortId(d.itemId()).toLowerCase(Locale.ROOT))) visible.add(d);
        }
        if (visible.isEmpty()) {
            // A config that hides everything would make every payout vanish: ignore it.
            plugin.getLogger().warning("economy.hidden-denominations скрывает все номиналы - игнорирую.");
            visible = new ArrayList<>(all);
        }
        this.allDenominations = List.copyOf(all);
        this.denominations = List.copyOf(visible);
        warnIfDenominationsOutdated(plugin, all);
        double index = plugin.getConfig().getDouble("economy.price-index", 1.0);
        this.priceIndex = index > 0 && Double.isFinite(index) ? index : 1.0;
        this.scaleVersion = plugin.getConfig().getInt("economy.scale-version", 1);
    }

    /**
     * (Пере)резолвит мост к ItemsAdder. Безопасно звать повторно: первый раз — из
     * конструктора (на случай, если ItemsAdder уже включён к этому моменту), второй —
     * из {@code LoveCorePlugin#onServerLoad}, когда включились уже все соседи.
     */
    public void linkItemsAdder(Plugin plugin) {
        this.itemsAdder = Neighbour.of(plugin.getLogger(), "ItemsAdder");
        Class<?> customStackClass = itemsAdder.type("dev.lone.itemsadder.api.CustomStack");
        this.byItemStack = itemsAdder.method(customStackClass, "byItemStack", ItemStack.class);
        this.getNamespacedId = itemsAdder.method(customStackClass, "getNamespacedID");
        this.getInstance = itemsAdder.method(customStackClass, "getInstance", String.class);
        this.getItemStack = itemsAdder.method(customStackClass, "getItemStack");
        itemsAdder.report("валюта");
    }

    /** Re-reads currency name and denominations from config.yml, and re-resolves the
     *  ItemsAdder bridge in case it wasn't up yet at startup (or was reloaded since). */
    public void reload(Plugin plugin) {
        applyConfig(plugin);
        linkItemsAdder(plugin);
    }

    /** Меняет индекс цен вживую и записывает его в config.yml ({@code /lovecoreadmin economy index}). */
    public void setPriceIndex(Plugin plugin, double index) {
        this.priceIndex = index;
        plugin.getConfig().set("economy.price-index", index);
        plugin.saveConfig();
    }

    /**
     * saveDefaultConfig() never touches an existing config.yml, so a server that set up LoveCore before the
     * 2026-10-03 ladder keeps the old coin values: every plugin amount (200 copper = 2 iron coins) then reads as a
     * different number of far more valuable coins. The values of the jar are compared with the ones in use and any
     * difference is logged; the file itself is never changed - the admin may have set them on purpose.
     */
    private static void warnIfDenominationsOutdated(Plugin plugin, List<Denomination> inUse) {
        try (java.io.InputStream in = plugin.getResource("config.yml")) {
            if (in == null) return;
            ConfigurationSection bundled = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getConfigurationSection("economy.denominations");
            if (bundled == null) return;
            java.util.Map<String, Long> jar = new java.util.LinkedHashMap<>();
            for (String key : bundled.getKeys(false)) jar.put(shortId(key), bundled.getLong(key));
            java.util.Map<String, Long> disk = new java.util.LinkedHashMap<>();
            for (Denomination d : inUse) disk.put(shortId(d.itemId()), d.value());
            List<String> differences = denominationDifferences(disk, jar);
            if (!differences.isEmpty()) {
                plugin.getLogger().warning("economy.denominations в config.yml отличаются от поставляемых с jar: "
                        + String.join("; ", differences) + ". Суммы плагинов (в медных) от этого выглядят другим числом "
                        + "других монет. Если это не намеренно - поправьте config.yml и перезапустите сервер.");
            }
        } catch (java.io.IOException | RuntimeException e) {
            plugin.getLogger().fine("Не удалось сравнить номиналы с поставляемыми: " + e.getMessage());
        }
    }

    /** Human-readable differences between the coin values in use and the bundled ones; empty when they match. */
    static List<String> denominationDifferences(java.util.Map<String, Long> inUse, java.util.Map<String, Long> bundled) {
        List<String> out = new ArrayList<>();
        for (java.util.Map.Entry<String, Long> e : bundled.entrySet()) {
            Long now = inUse.get(e.getKey());
            if (now == null) out.add(e.getKey() + " нет в конфиге (в jar " + e.getValue() + ")");
            else if (!now.equals(e.getValue())) out.add(e.getKey() + "=" + now + " (в jar " + e.getValue() + ")");
        }
        for (String id : inUse.keySet()) {
            if (!bundled.containsKey(id)) out.add(id + " есть в конфиге, но нет в jar");
        }
        return out;
    }

    private static List<Denomination> loadDenominations(Plugin plugin) {
        List<Denomination> list = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("economy.denominations");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                list.add(new Denomination(key, section.getLong(key)));
            }
        }
        if (list.isEmpty()) {
            list.addAll(MoneyParser.STANDARD);
        }
        list.sort((a, b) -> Long.compare(b.value(), a.value()));
        return List.copyOf(list);
    }

    @Override
    public String currencyName() {
        return currencyName;
    }

    @Override
    public List<Denomination> denominations() {
        return denominations;
    }

    @Override
    public List<Denomination> allDenominations() {
        return allDenominations;
    }

    @Override
    public double priceIndex() {
        return priceIndex;
    }

    @Override
    public int economyScaleVersion() {
        return scaleVersion;
    }

    @Override
    public long balance(Player player) {
        long total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            long unit = valueOf(stack);
            if (unit > 0) {
                total += (long) stack.getAmount() * unit;
            }
        }
        return total;
    }

    @Override
    public boolean has(Player player, long amount) {
        return amount <= 0 || balance(player) >= amount;
    }

    @Override
    public boolean charge(Player player, long amount) {
        if (amount <= 0) {
            return true;
        }
        if (balance(player) < amount) {
            return false;
        }
        removeCoins(player, amount);
        return true;
    }

    @Override
    public void give(Player player, long amount) {
        if (amount <= 0) {
            return;
        }
        for (ItemStack coin : breakdown(amount)) {
            for (ItemStack extra : player.getInventory().addItem(coin).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
        }
    }

    @Override
    public boolean canFit(Player player, long amount) {
        if (amount <= 0) {
            return true;
        }
        ItemStack[] simulated = player.getInventory().getStorageContents().clone();
        for (int i = 0; i < simulated.length; i++) {
            if (simulated[i] != null) {
                simulated[i] = simulated[i].clone();
            }
        }
        return simulateAdd(simulated, breakdown(amount));
    }

    /**
     * Списывает монеты с живого инвентаря, разменивая старший номинал при необходимости.
     * Сначала полностью применяет удаление в {@code storage} и записывает его обратно —
     * сдача выдаётся отдельным шагом {@link #give} уже после записи, а не по ходу разбора
     * снимка инвентаря: иначе выданная сдача потерялась бы под финальным {@code setStorageContents}.
     */
    private void removeCoins(Player player, long amount) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        long remaining = amount;
        long changeOwed = 0;

        List<Integer> coinSlots = new ArrayList<>();
        for (int i = 0; i < storage.length; i++) {
            if (valueOf(storage[i]) > 0) {
                coinSlots.add(i);
            }
        }
        coinSlots.sort(Comparator.comparingLong(slot -> valueOf(storage[slot])));

        for (int slot : coinSlots) {
            if (remaining <= 0) {
                break;
            }
            ItemStack stack = storage[slot];
            long unit = valueOf(stack);
            long stackValue = (long) stack.getAmount() * unit;

            if (stackValue <= remaining) {
                remaining -= stackValue;
                storage[slot] = null;
            } else {
                long itemsNeeded = (remaining + unit - 1) / unit;
                changeOwed = itemsNeeded * unit - remaining;
                int newAmount = (int) (stack.getAmount() - itemsNeeded);
                if (newAmount > 0) {
                    ItemStack shrunk = stack.clone();
                    shrunk.setAmount(newAmount);
                    storage[slot] = shrunk;
                } else {
                    storage[slot] = null;
                }
                remaining = 0;
            }
        }

        inventory.setStorageContents(storage);
        player.updateInventory();
        if (changeOwed > 0) {
            give(player, changeOwed);
        }
    }

    private List<ItemStack> breakdown(long amount) {
        List<ItemStack> result = new ArrayList<>();
        long remaining = amount;
        for (Denomination denomination : denominations) {
            long count = remaining / denomination.value();
            if (count <= 0) {
                continue;
            }
            List<ItemStack> stacks = stacksOf(denomination.itemId(), count);
            if (stacks.isEmpty()) {
                // This denomination's ItemsAdder item didn't resolve (missing/renamed in the
                // item pack, bad config id, ...) — leave `remaining` untouched so the value
                // it represented falls through to smaller denominations instead of vanishing
                // silently. Only value actually turned into real items is subtracted below.
                continue;
            }
            remaining -= count * denomination.value();
            result.addAll(stacks);
        }
        return result;
    }

    private List<ItemStack> stacksOf(String itemId, long amount) {
        List<ItemStack> result = new ArrayList<>();
        ItemStack template = templateFor(itemId);
        if (template == null) {
            return result;
        }
        int maxStack = template.getMaxStackSize();
        long left = amount;
        while (left > 0) {
            int size = (int) Math.min(left, maxStack);
            ItemStack stack = template.clone();
            stack.setAmount(size);
            result.add(stack);
            left -= size;
        }
        return result;
    }

    private ItemStack templateFor(String itemId) {
        Object customStack = itemsAdder.call(getInstance, null, itemId);
        if (customStack == null) {
            return null;
        }
        return (ItemStack) itemsAdder.call(getItemStack, customStack);
    }

    @Override
    public long valueOf(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return 0;
        }
        Object customStack = itemsAdder.call(byItemStack, null, (Object) stack);
        if (customStack == null) {
            return 0;
        }
        String id = (String) itemsAdder.call(getNamespacedId, customStack);
        if (id == null) {
            return 0;
        }
        String shortId = shortId(id);
        for (Denomination denomination : allDenominations) {
            if (shortId(denomination.itemId()).equalsIgnoreCase(shortId)) {
                return denomination.value();
            }
        }
        return 0;
    }

    private static String shortId(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    /** Симуляция добавления монет в клон инвентаря — для {@link #canFit} без реальной выдачи. */
    private boolean simulateAdd(ItemStack[] storage, List<ItemStack> items) {
        for (ItemStack item : items) {
            int remainingAmount = item.getAmount();

            for (int i = 0; i < storage.length && remainingAmount > 0; i++) {
                if (storage[i] != null && storage[i].isSimilar(item)) {
                    int space = storage[i].getMaxStackSize() - storage[i].getAmount();
                    if (space > 0) {
                        int add = Math.min(space, remainingAmount);
                        storage[i].setAmount(storage[i].getAmount() + add);
                        remainingAmount -= add;
                    }
                }
            }

            for (int i = 0; i < storage.length && remainingAmount > 0; i++) {
                if (storage[i] == null || storage[i].getType() == Material.AIR) {
                    int add = Math.min(item.getMaxStackSize(), remainingAmount);
                    ItemStack placed = item.clone();
                    placed.setAmount(add);
                    storage[i] = placed;
                    remainingAmount -= add;
                }
            }

            if (remainingAmount > 0) {
                return false;
            }
        }
        return true;
    }
}
