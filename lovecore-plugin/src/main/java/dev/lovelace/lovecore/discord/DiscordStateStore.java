package dev.lovelace.lovecore.discord;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Durable account links, ticket identities and polling cursors; legacy YAML is imported once. */
final class DiscordStateStore implements AutoCloseable {
  private final Connection db;
  private Map<String, String> savedTickets, savedCursors;

  DiscordStateStore(Path file) throws Exception {
    Files.createDirectories(file.toAbsolutePath().getParent());
    db = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
    try (var s = db.createStatement()) {
      s.execute("PRAGMA busy_timeout=5000");
      s.execute("PRAGMA journal_mode=WAL");
      s.execute("PRAGMA synchronous=FULL");
      s.execute(
          "CREATE TABLE IF NOT EXISTS discord_links(uuid TEXT PRIMARY KEY,discord TEXT NOT NULL"
              + " UNIQUE)");
      s.execute(
          "CREATE TABLE IF NOT EXISTS discord_tickets(id TEXT PRIMARY KEY,channel TEXT NOT NULL)");
      s.execute(
          "CREATE TABLE IF NOT EXISTS discord_cursors(channel TEXT PRIMARY KEY,message TEXT NOT"
              + " NULL)");
      s.execute("CREATE TABLE IF NOT EXISTS discord_migrations(source TEXT PRIMARY KEY)");
    } catch (Exception error) {
      db.close();
      throw error;
    }
  }

  synchronized void migrate(Path source) throws Exception {
    if (!Files.exists(source)) return;
    String key = source.getParent().getFileName() + "/" + source.getFileName();
    try (var q = db.prepareStatement("SELECT 1 FROM discord_migrations WHERE source=?")) {
      q.setString(1, key);
      try (var r = q.executeQuery()) {
        if (r.next()) return;
      }
    }
    var yaml = new YamlConfiguration();
    yaml.load(source.toFile());
    var links = new LinkedHashMap<String, String>();
    var tickets = new LinkedHashMap<String, String>();
    var cursors = new LinkedHashMap<String, String>();
    read(yaml, "links", links);
    read(yaml, "tickets", tickets);
    read(yaml, "cursors", cursors);
    for (var entry : links.entrySet()) {
      UUID.fromString(entry.getKey());
      snowflake(entry.getValue());
    }
    for (var entry : tickets.entrySet()) snowflake(entry.getValue());
    for (var entry : cursors.entrySet()) {
      snowflake(entry.getKey());
      snowflake(entry.getValue());
    }
    var backup = source.resolveSibling(source.getFileName() + ".pre-sqlite.bak");
    if (!Files.exists(backup)) Files.copy(source, backup);
    db.setAutoCommit(false);
    try {
      for (var e : links.entrySet()) {
        try (var q = db.prepareStatement("SELECT discord FROM discord_links WHERE uuid=?")) {
          q.setString(1, e.getKey());
          try (var r = q.executeQuery()) {
            if (r.next()) {
              if (!r.getString(1).equals(e.getValue()))
                throw new SQLException("Конфликт привязок в старом файле Discord");
              continue;
            }
          }
        }
        insert("discord_links", e.getKey(), e.getValue());
      }
      for (var e : tickets.entrySet()) insert("discord_tickets", e.getKey(), e.getValue());
      for (var e : cursors.entrySet()) insert("discord_cursors", e.getKey(), e.getValue());
      try (var q = db.prepareStatement("INSERT INTO discord_migrations VALUES(?)")) {
        q.setString(1, key);
        q.executeUpdate();
      }
      db.commit();
      savedTickets = null;
      savedCursors = null;
    } catch (Exception error) {
      db.rollback();
      throw error;
    } finally {
      db.setAutoCommit(true);
    }
  }

  private static void read(YamlConfiguration yaml, String section, Map<String, String> target) {
    var values = yaml.getConfigurationSection(section);
    if (values == null) {
      if (yaml.contains(section))
        throw new IllegalArgumentException("Некорректный раздел " + section);
      return;
    }
    for (String key : values.getKeys(false)) {
      String value = values.getString(key);
      if (value == null || value.isBlank())
        throw new IllegalArgumentException("Пустое значение Discord");
      target.put(key, value);
    }
  }

  private static void snowflake(String value) {
    if (!value.matches("[0-9]{5,25}"))
      throw new IllegalArgumentException("Некорректный Discord ID");
  }

  private void insert(String table, String key, String value) throws SQLException {
    try (var q = db.prepareStatement("INSERT INTO " + table + " VALUES(?,?)")) {
      q.setString(1, key);
      q.setString(2, value);
      q.executeUpdate();
    }
  }

  synchronized Map<String, String> links() throws SQLException {
    return readTable("discord_links");
  }

  synchronized Map<String, String> tickets() throws SQLException {
    if (savedTickets == null) savedTickets = readTable("discord_tickets");
    return savedTickets;
  }

  synchronized Map<String, String> cursors() throws SQLException {
    if (savedCursors == null) savedCursors = readTable("discord_cursors");
    return savedCursors;
  }

  private Map<String, String> readTable(String table) throws SQLException {
    var result = new LinkedHashMap<String, String>();
    try (var q = db.createStatement();
        var r = q.executeQuery("SELECT * FROM " + table)) {
      while (r.next()) result.put(r.getString(1), r.getString(2));
    }
    return Map.copyOf(result);
  }

  synchronized void setLink(UUID id, String discord) throws SQLException {
    snowflake(discord);
    db.setAutoCommit(false);
    try {
      try (var q = db.prepareStatement("DELETE FROM discord_links WHERE uuid=? OR discord=?")) {
        q.setString(1, id.toString());
        q.setString(2, discord);
        q.executeUpdate();
      }
      insert("discord_links", id.toString(), discord);
      db.commit();
    } catch (SQLException error) {
      db.rollback();
      throw error;
    } finally {
      db.setAutoCommit(true);
    }
  }

  synchronized void unlink(UUID id) throws SQLException {
    try (var q = db.prepareStatement("DELETE FROM discord_links WHERE uuid=?")) {
      q.setString(1, id.toString());
      q.executeUpdate();
    }
  }

  synchronized void ticket(String key, String channel) throws SQLException {
    snowflake(channel);
    try (var q =
        db.prepareStatement(
            "INSERT INTO discord_tickets VALUES(?,?) ON CONFLICT(id) DO UPDATE SET"
                + " channel=excluded.channel")) {
      q.setString(1, key);
      q.setString(2, channel);
      q.executeUpdate();
      savedTickets = null;
    }
  }

  synchronized void routing(Map<String, String> tickets, Map<String, String> cursors)
      throws SQLException {
    if (tickets.equals(tickets()) && cursors.equals(cursors())) return;
    db.setAutoCommit(false);
    try {
      try (var q = db.createStatement()) {
        q.executeUpdate("DELETE FROM discord_tickets");
        q.executeUpdate("DELETE FROM discord_cursors");
      }
      for (var e : tickets.entrySet()) insert("discord_tickets", e.getKey(), e.getValue());
      for (var e : cursors.entrySet()) insert("discord_cursors", e.getKey(), e.getValue());
      db.commit();
      savedTickets = Map.copyOf(tickets);
      savedCursors = Map.copyOf(cursors);
    } catch (SQLException error) {
      db.rollback();
      throw error;
    } finally {
      db.setAutoCommit(true);
    }
  }

  @Override
  public synchronized void close() throws SQLException {
    db.close();
  }
}
