package dev.lovelace.lovecore.discord;

import com.google.gson.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/** Small immutable multipart image body, bounded before admission to the network executor. */
final class DiscordUpload {
  record Body(String contentType, byte[] bytes) {}

  static Body image(String content, String filename, String mime, byte[] image) {
    if (image == null
        || image.length < 8
        || image.length > 700000
        || !Set.of("image/png", "image/jpeg", "image/webp").contains(mime))
      throw new IllegalArgumentException("Invalid image upload");
    String name = filename == null ? "evidence" : filename.replaceAll("[^\\p{L}0-9._-]", "_");
    if (name.isBlank()) name = "evidence";
    if (name.length() > 100) name = name.substring(0, 100);
    String boundary = "LoveCore" + UUID.randomUUID().toString().replace("-", "");
    var json = new JsonObject();
    json.addProperty("content", DiscordServiceImpl.cap(content, 1900));
    var mentions = new JsonObject();
    mentions.add("parse", new JsonArray());
    json.add("allowed_mentions", mentions);
    var attachments = new JsonArray();
    var attachment = new JsonObject();
    attachment.addProperty("id", 0);
    attachment.addProperty("filename", name);
    attachments.add(attachment);
    json.add("attachments", attachments);
    var out = new ByteArrayOutputStream();
    out.writeBytes(
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"payload_json\"\r\nContent-Type: application/json\r\n\r\n"
                + json
                + "\r\n--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"files[0]\"; filename=\""
                + name
                + "\"\r\nContent-Type: "
                + mime
                + "\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    out.writeBytes(image);
    out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
    return new Body("multipart/form-data; boundary=" + boundary, out.toByteArray());
  }
}
