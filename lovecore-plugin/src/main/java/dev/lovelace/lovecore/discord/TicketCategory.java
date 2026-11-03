package dev.lovelace.lovecore.discord;

import com.google.gson.JsonObject;

/** Check routing before a channel POST. Diagnostics never contain tokens or user text. */
final class TicketCategory {
  static void validate(JsonObject channel, String guild) {
    if (!channel.has("type") || channel.get("type").getAsInt() != 4)
      throw new IllegalArgumentException(
          "Категория тикетов: указан текстовый канал вместо категории. Администратору нужно выбрать"
              + " категорию для обращений.");
    if (!channel.has("guild_id") || !guild.equals(channel.get("guild_id").getAsString()))
      throw new IllegalArgumentException("Категория тикетов принадлежит другому Discord-серверу");
  }
}
