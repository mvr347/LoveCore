package dev.lovelace.lovecore.discord;

import com.google.gson.JsonParser;
import dev.lovelace.lovecore.api.discord.DiscordRequestRejectedException;

/** Separate explicit rejection from an ambiguous transport/server result. */
final class DiscordMessageResult {
  static String id(int status, String body) {
    if (java.util.Set.of(400, 401, 403, 404, 413, 422, 429).contains(status))
      throw new DiscordRequestRejectedException(status);
    if (status != 200 && status != 201)
      throw new IllegalStateException("Результат Discord не подтверждён (HTTP " + status + ")");
    String id = JsonParser.parseString(body).getAsJsonObject().get("id").getAsString();
    if (!id.matches("[0-9]{5,25}"))
      throw new IllegalStateException("Discord не подтвердил ID сообщения");
    return id;
  }
}
