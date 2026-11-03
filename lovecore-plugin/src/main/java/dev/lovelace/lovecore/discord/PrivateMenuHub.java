package dev.lovelace.lovecore.discord;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/** One durable navigation card per Discord user; security notices use the normal DM transport. */
final class PrivateMenuHub implements AutoCloseable {
  record Section(
      String label,
      java.util.function.Predicate<String> visible,
      Function<String, Map<String, Object>> render) {}

  record Message(String channel, String id) {}

  private final Map<String, Section> sections = new ConcurrentHashMap<>();
  private final Set<String> busy = ConcurrentHashMap.newKeySet();
  private final Connection db;

  PrivateMenuHub(Path file) throws SQLException {
    db = DriverManager.getConnection("jdbc:sqlite:" + file);
    try (var s = db.createStatement()) {
      s.execute("PRAGMA busy_timeout=3000");
      s.execute(
          "CREATE TABLE IF NOT EXISTS private_menus(owner TEXT PRIMARY KEY,channel TEXT NOT"
              + " NULL,message TEXT NOT NULL)");
    }
  }

  void register(String id, String label, Function<String, Map<String, Object>> render) {
    register(id, label, owner -> true, render);
  }

  void register(
      String id,
      String label,
      java.util.function.Predicate<String> visible,
      Function<String, Map<String, Object>> render) {
    if (!id.matches("[a-z][a-z0-9_-]{0,30}")) throw new IllegalArgumentException("menu id");
    sections.put(id, new Section(label, visible, render));
  }

  void unregister(String id) {
    sections.remove(id);
  }

  boolean begin(String user) {
    return busy.add(user);
  }

  void end(String user) {
    busy.remove(user);
  }

  Map<String, Object> render(String owner, String section) {
    if (!section.equals("home")) {
      var provider = sections.get(section);
      if (provider != null && provider.visible.test(owner)) {
        var result = new LinkedHashMap<>(provider.render.apply(owner));
        var rows = new ArrayList<Object>();
        Object controls = result.get("components");
        if (controls instanceof List<?> list) rows.addAll(list);
        if (rows.size() < 5) rows.add(row(button("lc:menu:home", "Главное меню")));
        result.put("components", rows);
        result.put("allowed_mentions", Map.of("parse", List.of()));
        return result;
      }
    }
    var buttons =
        sections.entrySet().stream()
            .filter(e -> e.getValue().visible.test(owner))
            .sorted(Map.Entry.comparingByKey())
            .limit(25)
            .map(e -> button("lc:menu:" + e.getKey(), e.getValue().label))
            .toList();
    var rows = new ArrayList<Object>();
    for (int i = 0; i < buttons.size(); i += 5)
      rows.add(
          Map.of("type", 1, "components", buttons.subList(i, Math.min(i + 5, buttons.size()))));
    return Map.of(
        "content",
        "**Личный кабинет**\nАккаунт, безопасность и обращения — выберите раздел.",
        "components",
        rows,
        "allowed_mentions",
        Map.of("parse", List.of()));
  }

  static Map<String, Object> button(String id, String label) {
    return Map.of("type", 2, "style", id.equals("lc:menu:home") ? 2 : 1, "custom_id", id, "label", label);
  }

  static Map<String, Object> row(Map<String, Object> button) {
    return Map.of("type", 1, "components", List.of(button));
  }

  synchronized Message get(String owner) throws SQLException {
    try (var q = db.prepareStatement("SELECT channel,message FROM private_menus WHERE owner=?")) {
      q.setString(1, owner);
      try (var r = q.executeQuery()) {
        return r.next() ? new Message(r.getString(1), r.getString(2)) : null;
      }
    }
  }

  synchronized void save(String owner, String channel, String message) throws SQLException {
    try (var q =
        db.prepareStatement(
            "INSERT INTO private_menus VALUES(?,?,?) ON CONFLICT(owner) DO UPDATE SET"
                + " channel=excluded.channel,message=excluded.message")) {
      q.setString(1, owner);
      q.setString(2, channel);
      q.setString(3, message);
      q.executeUpdate();
    }
  }

  @Override
  public synchronized void close() throws SQLException {
    db.close();
    sections.clear();
  }
}
