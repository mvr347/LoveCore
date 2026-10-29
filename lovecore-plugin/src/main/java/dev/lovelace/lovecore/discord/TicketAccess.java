package dev.lovelace.lovecore.discord;

import com.google.gson.*;
import java.net.URI;
import java.util.*;

/** Small, testable boundary for explicit channel ACLs and authenticated attachment downloads. */
final class TicketAccess {
  static final int MAX_ATTACHMENT = 8 * 1024 * 1024;

  private TicketAccess() {}

  static JsonArray overwrites(String guild, String bot, List<String> roles, List<String> members) {
    var rows = new LinkedHashMap<String, JsonObject>();
    rows.put("role:" + guild, entry(guild, 0, "0", "1024"));
    for (String role : roles) {
      if (role.equals(guild))
        throw new IllegalArgumentException("Нельзя открыть обращение для всех");
      rows.put("role:" + role, entry(role, 0, "117760", "0"));
    }
    for (String member : members) rows.put("user:" + member, entry(member, 1, "117760", "0"));
    rows.put("user:" + bot, entry(bot, 1, "117776", "0"));
    var result = new JsonArray();
    rows.values().forEach(result::add);
    return result;
  }

  private static JsonObject entry(String id, int type, String allow, String deny) {
    var value = new JsonObject();
    value.addProperty("id", id);
    value.addProperty("type", type);
    value.addProperty("allow", allow);
    value.addProperty("deny", deny);
    return value;
  }

  static String transcriptText(JsonObject message) {
    var content =
        new StringBuilder(
            message.has("content") && !message.get("content").isJsonNull()
                ? message.get("content").getAsString()
                : "");
    if (message.has("embeds"))
      for (JsonElement item : message.getAsJsonArray("embeds")) {
        JsonObject embed = item.getAsJsonObject();
        for (String key : List.of("title", "description"))
          if (embed.has(key)) content.append("\n").append(embed.get(key).getAsString());
        if (embed.has("fields"))
          for (JsonElement field : embed.getAsJsonArray("fields")) {
            JsonObject row = field.getAsJsonObject();
            content
                .append("\n")
                .append(row.get("name").getAsString())
                .append(": ")
                .append(row.get("value").getAsString());
          }
      }
    return content.toString();
  }

  static URI authenticatedAttachment(JsonArray attachments, String supplied) {
    URI expected = attachmentUri(supplied);
    for (JsonElement item : attachments) {
      URI actual = attachmentUri(item.getAsJsonObject().get("url").getAsString());
      if (actual.getHost().equals(expected.getHost())
          && actual.getPath().equals(expected.getPath())) return actual;
    }
    throw new IllegalArgumentException("Файл не принадлежит сообщению");
  }

  static URI attachmentUri(String url) {
    var uri = URI.create(url);
    if (!"https".equals(uri.getScheme())
        || !Set.of("cdn.discordapp.com", "media.discordapp.net").contains(uri.getHost())
        || uri.getRawUserInfo() != null
        || uri.getPort() != -1
        || uri.getFragment() != null
        || !uri.getPath().startsWith("/attachments/"))
      throw new IllegalArgumentException("Недопустимый адрес вложения Discord");
    return uri;
  }
}
