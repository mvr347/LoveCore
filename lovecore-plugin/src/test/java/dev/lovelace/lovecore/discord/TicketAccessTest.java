package dev.lovelace.lovecore.discord;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TicketAccessTest {
  @Test
  void onlyExplicitPeopleAndSupervisorsCanRead() {
    var rows =
        TicketAccess.overwrites("10000", "20000", List.of("30000"), List.of("40000", "40000"));
    assertEquals(4, rows.size());
    var everyone = rows.get(0).getAsJsonObject();
    assertEquals("1024", everyone.get("deny").getAsString());
    assertEquals("0", everyone.get("allow").getAsString());
    assertEquals("30000", rows.get(1).getAsJsonObject().get("id").getAsString());
    assertEquals("40000", rows.get(2).getAsJsonObject().get("id").getAsString());
    assertThrows(
        IllegalArgumentException.class,
        () -> TicketAccess.overwrites("10000", "20000", List.of("10000"), List.of()));
  }

  @Test
  void attachmentAddressesCannotFetchArbitraryUrls() {
    assertDoesNotThrow(
        () ->
            TicketAccess.attachmentUri(
                "https://cdn.discordapp.com/attachments/1/2/screen.png?ex=123"));
    for (String url :
        List.of(
            "http://cdn.discordapp.com/attachments/1",
            "https://cdn.discordapp.com.evil.test/attachments/1",
            "https://cdn.discordapp.com@127.0.0.1/attachments/1",
            "https://cdn.discordapp.com:8443/attachments/1",
            "https://cdn.discordapp.com/not-attachments/1",
            "http://127.0.0.1/secret"))
      assertThrows(IllegalArgumentException.class, () -> TicketAccess.attachmentUri(url), url);
  }

  @Test
  void downloadsUseFreshAuthenticatedUrlsNotSuppliedSignatures() {
    var files =
        com.google.gson.JsonParser.parseString(
                "[{\"url\":\"https://cdn.discordapp.com/attachments/1/2/a.png?fresh=1\"}]")
            .getAsJsonArray();
    assertEquals(
        "fresh=1",
        TicketAccess.authenticatedAttachment(
                files, "https://cdn.discordapp.com/attachments/1/2/a.png?old=1")
            .getQuery());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            TicketAccess.authenticatedAttachment(
                files, "https://cdn.discordapp.com/attachments/1/2/other.png"));
  }

  @Test
  void transcriptIncludesEmbedCards() {
    var message =
        com.google.gson.JsonParser.parseString(
                "{\"content\":\"Текст\",\"embeds\":[{\"title\":\"Решение\",\"description\":\"Объяснение\",\"fields\":[{\"name\":\"Источник\",\"value\":\"Безопасность\"}]}]}")
            .getAsJsonObject();
    assertEquals(
        "Текст\nРешение\nОбъяснение\nИсточник: Безопасность", TicketAccess.transcriptText(message));
  }

  @Test
  void recoveryMatchesExactPrivateCaseAndOwner() {
    var request =
        new dev.lovelace.lovecore.api.discord.ManagedTicketRequest(
            new dev.lovelace.lovecore.api.discord.TicketRequest(
                dev.lovelace.lovecore.api.discord.TicketType.REPORT,
                "report:12",
                "Player",
                "Reason",
                "40000",
                null,
                false),
            List.of(),
            List.of(),
            "mine-ticket-12-r");
    var channel =
        com.google.gson.JsonParser.parseString(
                "{\"id\":\"50000\",\"guild_id\":\"10000\",\"type\":0,\"name\":\"mine-ticket-12-r\",\"topic\":\"Тикет: report:12 | Игрок: Player\"}")
            .getAsJsonObject();
    channel.add(
        "permission_overwrites",
        TicketAccess.overwrites("10000", "20000", List.of(), List.of("40000")));
    assertTrue(TicketAccess.matchesManaged(channel, "10000", request));
    assertFalse(TicketAccess.matchesManaged(channel, "90000", request));
    channel.addProperty("topic", "Тикет: report:13 | Игрок: Player");
    assertFalse(TicketAccess.matchesManaged(channel, "10000", request));
    channel.addProperty("topic", "Тикет: report:12 | Игрок: Player");
    channel.add(
        "permission_overwrites",
        TicketAccess.overwrites("10000", "20000", List.of(), List.of("60000")));
    assertFalse(TicketAccess.matchesManaged(channel, "10000", request));
  }
}
