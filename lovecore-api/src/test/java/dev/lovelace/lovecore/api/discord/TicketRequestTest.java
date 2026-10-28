package dev.lovelace.lovecore.api.discord;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TicketRequestTest {
  @Test
  void previewRequiresExplicitTestCategoryAndNamespace() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TicketRequest(TicketType.REPORT, "12", "Test", "reason", null, "123456", true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TicketRequest(TicketType.REPORT, "preview:12", "Test", "reason", null, null, true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TicketRequest(
                TicketType.REPORT, "preview:12", "Test", "reason", null, "production", true));
  }

  @Test
  void previewNamespaceCannotBeRoutedByLegacyProductionOverload() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TicketRequest(
                TicketType.BAN_APPEAL, "preview:12", "Test", "reason", null, null, false));
  }

  @Test
  void ownerMustBeDiscordIdentity() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TicketRequest(
                TicketType.REPORT, "12", "Test", "reason", "MinecraftNick", null, false));
  }

  @Test
  void validPrivatePreviewAndExistingProductionRoutesRemainAvailable() {
    assertTrue(
        new TicketRequest(
                TicketType.REPORT,
                "preview:abc:report:12",
                "Test",
                "reason",
                "123456",
                "987654",
                true)
            .preview());
    assertFalse(
        new TicketRequest(TicketType.BAN_APPEAL, "12", "Test", "reason", null, null, false)
            .preview());
  }
}
