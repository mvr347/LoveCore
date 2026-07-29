package dev.lovelace.lovecore.economy;

import dev.lovelace.lovecore.api.economy.AccountId;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import dev.lovelace.lovecore.api.stats.Metrics;
import dev.lovelace.lovecore.api.stats.StatBus;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Кошелёк на счетах с журналом операций.
 *
 * <p>Балансы держатся в памяти целиком: строк ровно по одной на игрока и на клан, а
 * {@link #balance(AccountId)} обязан отвечать с главного потока без похода в базу. Писатель у
 * базы один — это ядро, — поэтому кэш не может разъехаться с содержимым таблицы.</p>
 */
public final class LedgerEconomy implements LoveEconomy {

    private final Plugin plugin;
    private final EconomyStorage storage;
    private final StatBus statBus;
    private final String currencyName;
    private final Map<AccountId, Long> balances = new ConcurrentHashMap<>();
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "LoveCore-Economy");
                thread.setDaemon(true);
                return thread;
            });

    public LedgerEconomy(Plugin plugin, StatBus statBus) {
        this.plugin = plugin;
        this.statBus = statBus;
        this.storage = new EconomyStorage(new File(plugin.getDataFolder(), "economy.db"));
        this.currencyName = plugin.getConfig().getString("economy.currency-name", "монет");
    }

    /** Поднимает базу и наполняет кэш. Бросает исключение, если база недоступна. */
    public void initialize() throws SQLException {
        storage.connect();
        balances.putAll(storage.loadAll());
        plugin.getLogger().info("Экономика: загружено счетов — " + balances.size() + ".");
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Экономика: операции не успели завершиться за 10 секунд.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            storage.close();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Экономика: не удалось закрыть базу.", e);
        }
    }

    @Override
    public long balance(AccountId account) {
        return balances.getOrDefault(account, 0L);
    }

    @Override
    public CompletableFuture<Boolean> withdraw(AccountId account, long amount, String reason) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(amount == 0);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                Long after = storage.withdraw(account, amount, reason);
                if (after == null) {
                    return false;
                }
                remember(account, after);
                return true;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Экономика: списание " + amount
                        + " со счёта " + account + " не удалось (" + reason + ").", e);
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deposit(AccountId account, long amount, String reason) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                remember(account, storage.deposit(account, amount, reason));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Экономика: зачисление " + amount
                        + " на счёт " + account + " не удалось (" + reason + ").", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> transfer(AccountId from, AccountId to, long amount, String reason) {
        if (amount <= 0 || from.equals(to)) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                long[] after = storage.transfer(from, to, amount, reason);
                if (after == null) {
                    return false;
                }
                remember(from, after[0]);
                remember(to, after[1]);
                return true;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Экономика: перевод " + amount + " со счёта "
                        + from + " на счёт " + to + " не удался (" + reason + ").", e);
                return false;
            }
        }, executor);
    }

    @Override
    public String currencyName() {
        return currencyName;
    }

    /**
     * Обновляет кэш и сообщает новый баланс шине статистики — «богатейший клан» в топе
     * становится настоящей величиной, а не отдельным счётчиком, который забыли обновить.
     */
    private void remember(AccountId account, long balance) {
        balances.put(account, balance);
        if (account.kind() == AccountId.Kind.CLAN) {
            statBus.setClan(account.id(), Metrics.CLAN_WEALTH, balance);
        } else {
            statBus.set(account.id(), Metrics.BALANCE, balance);
        }
    }
}
