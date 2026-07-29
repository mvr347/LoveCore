package dev.lovelace.lovecore.economy;

import dev.lovelace.lovecore.api.economy.AccountId;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Счета и журнал операций.
 *
 * <p>Одно соединение, все обращения через {@code synchronized}: писатель у базы один — ядро,
 * — и очередь операций дешевле пула соединений и надёжнее его на SQLite.</p>
 */
final class EconomyStorage {

    private final File databaseFile;
    private Connection connection;

    EconomyStorage(File databaseFile) {
        this.databaseFile = databaseFile;
    }

    synchronized void connect() throws SQLException {
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Не удалось создать каталог для базы: " + parent);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS lovecore_accounts (
                        account_kind VARCHAR(16) NOT NULL,
                        account_id   VARCHAR(36) NOT NULL,
                        balance      BIGINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (account_kind, account_id)
                    )
                    """);
            // Журнал: без него на вопрос «куда делись деньги» ответить нечем.
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS lovecore_ledger (
                        id            INTEGER PRIMARY KEY AUTOINCREMENT,
                        account_kind  VARCHAR(16) NOT NULL,
                        account_id    VARCHAR(36) NOT NULL,
                        delta         BIGINT NOT NULL,
                        balance_after BIGINT NOT NULL,
                        reason        VARCHAR(64) NOT NULL,
                        created_at    BIGINT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_ledger_account
                        ON lovecore_ledger(account_kind, account_id, created_at)
                    """);
        }
    }

    synchronized void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    /** Все счета разом: их немного (по одному на игрока и на клан), а кэш обязан быть полным. */
    synchronized Map<AccountId, Long> loadAll() throws SQLException {
        Map<AccountId, Long> balances = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT account_kind, account_id, balance FROM lovecore_accounts")) {
            while (rows.next()) {
                AccountId.Kind kind = AccountId.Kind.valueOf(rows.getString("account_kind"));
                AccountId account = new AccountId(kind, java.util.UUID.fromString(rows.getString("account_id")));
                balances.put(account, rows.getLong("balance"));
            }
        }
        return balances;
    }

    /**
     * Списание. Возвращает новый баланс либо {@code null}, если денег не хватило.
     *
     * <p>Баланс не проверяется отдельным запросом: между проверкой и списанием влезла бы
     * вторая операция. Условие живёт в самом {@code UPDATE}, а признаком отказа служит
     * число затронутых строк.</p>
     */
    synchronized Long withdraw(AccountId account, long amount, String reason) throws SQLException {
        return inTransaction(() -> {
            ensureAccount(account);
            if (!withdrawAtomic(account, amount)) {
                return null;
            }
            long after = readBalance(account);
            appendLedger(account, -amount, after, reason);
            return after;
        });
    }

    synchronized long deposit(AccountId account, long amount, String reason) throws SQLException {
        Long after = inTransaction(() -> {
            ensureAccount(account);
            addAtomic(account, amount);
            long balance = readBalance(account);
            appendLedger(account, amount, balance, reason);
            return balance;
        });
        return after == null ? 0L : after;
    }

    /** Перевод: либо оба счёта изменились, либо ни один. */
    synchronized long[] transfer(AccountId from, AccountId to, long amount, String reason) throws SQLException {
        return inTransaction(() -> {
            ensureAccount(from);
            ensureAccount(to);
            if (!withdrawAtomic(from, amount)) {
                return null;
            }
            addAtomic(to, amount);
            long fromBalance = readBalance(from);
            long toBalance = readBalance(to);
            appendLedger(from, -amount, fromBalance, reason);
            appendLedger(to, amount, toBalance, reason);
            return new long[]{fromBalance, toBalance};
        });
    }

    // --- Внутреннее ---

    private interface Work<T> {
        T run() throws SQLException;
    }

    private <T> T inTransaction(Work<T> work) throws SQLException {
        connection.setAutoCommit(false);
        try {
            T result = work.run();
            if (result == null) {
                // Отказ (не хватило денег) — состояние базы должно остаться прежним.
                connection.rollback();
                return null;
            }
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void ensureAccount(AccountId account) throws SQLException {
        String sql = "INSERT OR IGNORE INTO lovecore_accounts(account_kind, account_id, balance) VALUES (?, ?, 0)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, account.kind().name());
            statement.setString(2, account.id().toString());
            statement.executeUpdate();
        }
    }

    private boolean withdrawAtomic(AccountId account, long amount) throws SQLException {
        String sql = """
                UPDATE lovecore_accounts
                   SET balance = balance - ?
                 WHERE account_kind = ? AND account_id = ? AND balance >= ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, amount);
            statement.setString(2, account.kind().name());
            statement.setString(3, account.id().toString());
            statement.setLong(4, amount);
            // Ноль затронутых строк означает ровно одно: денег не хватило.
            return statement.executeUpdate() == 1;
        }
    }

    private void addAtomic(AccountId account, long amount) throws SQLException {
        String sql = """
                UPDATE lovecore_accounts
                   SET balance = balance + ?
                 WHERE account_kind = ? AND account_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, amount);
            statement.setString(2, account.kind().name());
            statement.setString(3, account.id().toString());
            statement.executeUpdate();
        }
    }

    private long readBalance(AccountId account) throws SQLException {
        String sql = "SELECT balance FROM lovecore_accounts WHERE account_kind = ? AND account_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, account.kind().name());
            statement.setString(2, account.id().toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : 0L;
            }
        }
    }

    private void appendLedger(AccountId account, long delta, long balanceAfter, String reason) throws SQLException {
        String sql = """
                INSERT INTO lovecore_ledger(account_kind, account_id, delta, balance_after, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, account.kind().name());
            statement.setString(2, account.id().toString());
            statement.setLong(3, delta);
            statement.setLong(4, balanceAfter);
            statement.setString(5, reason == null || reason.isBlank() ? "unspecified" : reason);
            statement.setLong(6, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }
}
