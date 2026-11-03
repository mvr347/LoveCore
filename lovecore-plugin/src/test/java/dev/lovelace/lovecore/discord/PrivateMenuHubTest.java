package dev.lovelace.lovecore.discord;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrivateMenuHubTest {
  @TempDir Path directory;

  @Test
  void legacyProvidersClearTheDashboardEmbedInsteadOfKeepingTwoScreens() throws Exception {
    try(var hub=new PrivateMenuHub(directory.resolve("cards.db"))) {
      hub.register("account","Мой аккаунт",owner->Map.of("content","Аккаунт", "components",List.of()));
      assertFalse(((List<?>)hub.render("owner","home").get("embeds")).isEmpty());
      assertEquals(List.of(),hub.render("owner","account").get("embeds"));
    }
  }

  @Test
  void durableMessagePointersAreIsolatedByOwner() throws Exception {
    var file = directory.resolve("menus.db");
    try (var h = new PrivateMenuHub(file)) {
      h.save("owner-a", "channel-a", "message-a");
      h.save("owner-b", "channel-b", "message-b");
    }
    try (var h = new PrivateMenuHub(file)) {
      assertEquals("message-a", h.get("owner-a").id());
      assertEquals("channel-b", h.get("owner-b").channel());
      assertNull(h.get("stranger"));
    }
  }

  @Test
  void concurrentClicksAreCoalescedAndThePermitIsReusable() throws Exception {
    try (var h = new PrivateMenuHub(directory.resolve("menus.db"))) {
      assertTrue(h.begin("a"));
      assertFalse(h.begin("a"));
      assertTrue(h.begin("b"));
      h.end("a");
      assertTrue(h.begin("a"));
    }
  }

  @Test
  void providersAlwaysReceiveTheActualOwnerAndUnregisterRemovesAccess() throws Exception {
    try (var h = new PrivateMenuHub(directory.resolve("menus.db"))) {
      h.register(
          "cases", "Мои обращения", owner -> Map.of("content", owner, "components", List.of()));
      assertEquals("user-a", h.render("user-a", "cases").get("content"));
      assertEquals("user-b", h.render("user-b", "cases").get("content"));
      assertFalse(((List<?>) h.render("user-a", "cases").get("components")).isEmpty());
      h.unregister("cases");
      assertNotEquals("user-a", h.render("user-a", "cases").get("content"));
    }
  }

  @org.junit.jupiter.api.Test
  void staffSectionsCheckActualOwnerOnEveryRender() throws Exception {
    try (var hub = new PrivateMenuHub(directory.resolve("visibility.db"))) {
      hub.register(
          "staff",
          "Мои дела",
          owner -> owner.equals("senior"),
          owner -> java.util.Map.of("content", "Секрет"));
      org.junit.jupiter.api.Assertions.assertFalse(
          hub.render("player", "home").toString().contains("Мои дела"));
      org.junit.jupiter.api.Assertions.assertFalse(
          hub.render("player", "staff").toString().contains("Секрет"));
      org.junit.jupiter.api.Assertions.assertTrue(
          hub.render("senior", "staff").toString().contains("Секрет"));
    }
  }
}
