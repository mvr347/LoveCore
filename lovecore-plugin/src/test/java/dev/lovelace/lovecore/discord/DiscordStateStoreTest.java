package dev.lovelace.lovecore.discord;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class DiscordStateStoreTest {
  @TempDir Path root;

  @Test
  void legacyImportBacksUpAndDoesNotRestoreRemovedLinks() throws Exception {
    UUID player = UUID.randomUUID();
    var file = root.resolve("discord-data.yml");
    Files.writeString(file, "links:\n  " + player + ": '123456789'\n");
    try (var store = new DiscordStateStore(root.resolve("discord.db"))) {
      store.migrate(file);
      assertEquals("123456789", store.links().get(player.toString()));
      assertEquals(
          Files.readString(file),
          Files.readString(root.resolve("discord-data.yml.pre-sqlite.bak")));
      store.unlink(player);
    }
    try (var store = new DiscordStateStore(root.resolve("discord.db"))) {
      store.migrate(file);
      assertTrue(store.links().isEmpty());
    }
  }

  @Test
  void migrationConflictRollsBackWholeFileAndPreservesSource() throws Exception {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    var file = root.resolve("legacy.yml");
    Files.writeString(file, "links:\n  " + b + ": '222222'\n  " + a + ": '333333'\n");
    try (var store = new DiscordStateStore(root.resolve("discord.db"))) {
      store.setLink(a, "111111");
      assertThrows(Exception.class, () -> store.migrate(file));
      assertEquals(Map.of(a.toString(), "111111"), store.links());
      assertTrue(Files.exists(file));
    }
  }

  @Test
  void malformedYamlIsNotMarkedAsImported() throws Exception {
    var file = root.resolve("legacy.yml");
    Files.writeString(file, "links: [broken");
    try (var store = new DiscordStateStore(root.resolve("discord.db"))) {
      assertThrows(Exception.class, () -> store.migrate(file));
      var player = UUID.randomUUID();
      Files.writeString(file, "links:\n  " + player + ": '123456'\n");
      store.migrate(file);
      assertEquals(1, store.links().size());
    }
  }

  @Test
  void ticketIdentitiesAndCursorsSurviveRestartAndDeletion() throws Exception {
    var db = root.resolve("discord.db");
    try (var store = new DiscordStateStore(db)) {
      store.routing(Map.of("REPORT:3", "123456"), Map.of("123456", "234567"));
    }
    try (var store = new DiscordStateStore(db)) {
      assertEquals("123456", store.tickets().get("REPORT:3"));
      assertEquals("234567", store.cursors().get("123456"));
      store.routing(Map.of(), Map.of());
    }
    try (var store = new DiscordStateStore(db)) {
      assertTrue(store.tickets().isEmpty());
      assertTrue(store.cursors().isEmpty());
    }
  }

  @Test
  void reassignmentIsAtomicAndCannotLeaveTwoOwners() throws Exception {
    try (var store = new DiscordStateStore(root.resolve("discord.db"))) {
      UUID a = UUID.randomUUID(), b = UUID.randomUUID();
      store.setLink(a, "123456");
      store.setLink(b, "123456");
      assertEquals(Map.of(b.toString(), "123456"), store.links());
      assertThrows(IllegalArgumentException.class, () -> store.setLink(b, "bad"));
      assertEquals(Map.of(b.toString(), "123456"), store.links());
    }
  }
}
