package dev.lovelace.lovecore.discord;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class TicketCategoryTest {
  @Test
  void acceptsOnlyACategoryFromTheCorrectServer() {
    TicketCategory.validate(
        JsonParser.parseString("{\"type\":4,\"guild_id\":\"123\"}").getAsJsonObject(), "123");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            TicketCategory.validate(
                JsonParser.parseString("{\"type\":0,\"guild_id\":\"123\"}").getAsJsonObject(),
                "123"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            TicketCategory.validate(
                JsonParser.parseString("{\"type\":4,\"guild_id\":\"456\"}").getAsJsonObject(),
                "123"));
    assertThrows(
        IllegalArgumentException.class,
        () -> TicketCategory.validate(JsonParser.parseString("{}").getAsJsonObject(), "123"));
  }
}
