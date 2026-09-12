package dev.lovelace.lovecore.discord;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lovelace.lovecore.api.discord.DiscordEmbed;
import dev.lovelace.lovecore.api.discord.DiscordService;
import dev.lovelace.lovecore.api.discord.TicketMessageListener;
import dev.lovelace.lovecore.api.discord.TicketType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

public final class DiscordServiceImpl implements DiscordService {

    private static final String API_BASE = "https://discord.com/api/v10";
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final JavaPlugin plugin;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final SecureRandom random = new SecureRandom();

    private boolean enabled;
    private String botToken;
    private String guildId;
    private String appealsCategoryId;
    private String supportCategoryId;
    private String reportsChannelId;
    private String appealsLogChannelId;

    private final Map<UUID, String> playerToDiscord = new ConcurrentHashMap<>();
    private final Map<String, UUID> discordToPlayer = new ConcurrentHashMap<>();
    private final Map<String, LinkCode> linkCodes = new ConcurrentHashMap<>();
    private final Map<String, String> activeTickets = new ConcurrentHashMap<>(); // ticketId -> channelId
    private final Map<String, String> channelLastMessageId = new ConcurrentHashMap<>();
    private final List<TicketMessageListener> messageListeners = new CopyOnWriteArrayList<>();

    private record LinkCode(UUID playerUuid, long expiresAt) {}

    public DiscordServiceImpl(JavaPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void load() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("discord");
        this.enabled = sec != null && sec.getBoolean("enabled", false);
        this.botToken = sec != null ? sec.getString("bot-token", "").trim() : "";
        this.guildId = sec != null ? sec.getString("guild-id", "").trim() : "";
        this.appealsCategoryId = sec != null ? sec.getString("appeals-category-id", "").trim() : "";
        this.supportCategoryId = sec != null ? sec.getString("support-category-id", "").trim() : "";
        this.reportsChannelId = sec != null ? sec.getString("reports-channel-id", "").trim() : "";
        this.appealsLogChannelId = sec != null ? sec.getString("appeals-log-channel-id", "").trim() : "";

        loadStoredLinks();

        if (enabled && !botToken.isBlank()) {
            plugin.getLogger().info("✓ LoveCore DiscordService активирован (Guild ID: " + guildId + ")");
            startMessagePolling();
        } else {
            plugin.getLogger().info("ℹ LoveCore DiscordService ожидает настройки токена в config.yml (discord.bot-token)");
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled && !botToken.isBlank();
    }

    @Override
    public Optional<String> getLinkedDiscordId(UUID playerUuid) {
        return Optional.ofNullable(playerToDiscord.get(playerUuid));
    }

    @Override
    public Optional<UUID> getLinkedPlayer(String discordId) {
        return Optional.ofNullable(discordToPlayer.get(discordId));
    }

    @Override
    public String generateLinkCode(UUID playerUuid) {
        // Очищаем старые коды
        long now = System.currentTimeMillis();
        linkCodes.entrySet().removeIf(e -> e.getValue().expiresAt() < now || e.getValue().playerUuid().equals(playerUuid));

        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        String code = sb.toString();
        linkCodes.put(code, new LinkCode(playerUuid, now + Duration.ofMinutes(15).toMillis()));
        return code;
    }

    @Override
    public boolean completeLink(String discordId, String code) {
        if (code == null || discordId == null || discordId.isBlank()) return false;
        String cleanCode = code.trim().toUpperCase(Locale.ROOT);
        LinkCode entry = linkCodes.remove(cleanCode);
        if (entry == null || entry.expiresAt() < System.currentTimeMillis()) {
            return false;
        }

        UUID uuid = entry.playerUuid();
        playerToDiscord.put(uuid, discordId);
        discordToPlayer.put(discordId, uuid);
        saveStoredLinks();
        plugin.getLogger().info("Привязан аккаунт: " + uuid + " <-> Discord " + discordId);
        return true;
    }

    @Override
    public void unlink(UUID playerUuid) {
        String discordId = playerToDiscord.remove(playerUuid);
        if (discordId != null) {
            discordToPlayer.remove(discordId);
            saveStoredLinks();
        }
    }

    @Override
    public void setLink(UUID playerUuid, String discordId) {
        if (playerUuid == null) return;
        if (discordId == null || discordId.isBlank()) {
            unlink(playerUuid);
            return;
        }
        playerToDiscord.put(playerUuid, discordId);
        discordToPlayer.put(discordId, playerUuid);
        saveStoredLinks();
    }

    @Override
    public CompletableFuture<Boolean> sendDirectMessage(String discordUserId, String message, DiscordEmbed embed) {
        if (!isEnabled() || discordUserId == null || discordUserId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        return createDmChannel(discordUserId).thenCompose(dmChannelId -> {
            if (dmChannelId == null) {
                return CompletableFuture.completedFuture(false);
            }
            return sendChannelMessage(dmChannelId, message, embed);
        });
    }

    @Override
    public CompletableFuture<Boolean> sendChannelMessage(String channelId, String message, DiscordEmbed embed) {
        if (!isEnabled() || channelId == null || channelId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        JsonObject body = new JsonObject();
        if (message != null && !message.isBlank()) {
            body.addProperty("content", message);
        }
        if (embed != null) {
            JsonArray embeds = new JsonArray();
            embeds.add(serializeEmbed(embed));
            body.add("embeds", embeds);
        }

        return sendPostRequest("/channels/" + channelId + "/messages", body.toString())
                .thenApply(response -> response != null && response.statusCode() >= 200 && response.statusCode() < 300);
    }

    @Override
    public CompletableFuture<String> createTicketChannel(TicketType type, String ticketId, String playerName, String initialReason) {
        if (!isEnabled() || guildId.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        String prefix = type == TicketType.BAN_APPEAL ? "бан-" : (type == TicketType.SUPPORT ? "саппорт-" : "тикет-");
        String sanitizedName = playerName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        String channelName = "ticket-" + prefix + sanitizedName;

        JsonObject payload = new JsonObject();
        payload.addProperty("name", channelName);
        payload.addProperty("type", 0); // Guild Text Channel
        payload.addProperty("topic", "Тикет: " + ticketId + " | Игрок: " + playerName + " | Причина: " + initialReason);

        String category = type == TicketType.BAN_APPEAL ? appealsCategoryId : supportCategoryId;
        if (category != null && !category.isBlank()) {
            payload.addProperty("parent_id", category);
        }

        return sendPostRequest("/guilds/" + guildId + "/channels", payload.toString()).thenApply(resp -> {
            if (resp == null || resp.statusCode() < 200 || resp.statusCode() >= 300) {
                plugin.getLogger().warning("Не удалось создать тикет-канал Discord: " + (resp != null ? resp.body() : "null"));
                return null;
            }
            try {
                JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                String channelId = json.get("id").getAsString();
                activeTickets.put(ticketId, channelId);

                // Отправляем вступительное сообщение
                DiscordEmbed intro = new DiscordEmbed(
                        "⚔ Тикет " + (type == TicketType.BAN_APPEAL ? "апелляции бана" : "поддержки"),
                        "**Игрок:** `" + playerName + "`\n" +
                                "**Тикет ID:** `" + ticketId + "`\n" +
                                "**Описание:** " + initialReason + "\n\n" +
                                "*Сообщения из этого канала синхронизируются с панелью LoveWebAdmin.*",
                        0xE67E22,
                        List.of(),
                        "LoveCore Ticket System",
                        java.time.Instant.now()
                );
                sendChannelMessage(channelId, "@here Новое обращение от игрока `" + playerName + "`", intro);

                return channelId;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Ошибка парсинга ответа создания канала: " + e.getMessage(), e);
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> closeTicketChannel(String channelId, String reason) {
        if (!isEnabled() || channelId == null || channelId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        JsonObject farewell = new JsonObject();
        farewell.addProperty("content", "🔒 **Тикет закрыт.** Причина: " + (reason != null ? reason : "Вопрос решён."));
        sendPostRequest("/channels/" + channelId + "/messages", farewell.toString());

        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(3000); // 3 секунды чтобы увидеть закрытие
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(API_BASE + "/channels/" + channelId))
                        .header("Authorization", "Bot " + botToken)
                        .DELETE()
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                activeTickets.values().remove(channelId);
                return resp.statusCode() >= 200 && resp.statusCode() < 300;
            } catch (Exception e) {
                return false;
            }
        });
    }

    @Override
    public void registerTicketMessageListener(TicketMessageListener listener) {
        if (listener != null) {
            messageListeners.add(listener);
        }
    }

    private CompletableFuture<String> createDmChannel(String userId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("recipient_id", userId);
        return sendPostRequest("/users/@me/channels", payload.toString()).thenApply(resp -> {
            if (resp != null && resp.statusCode() >= 200 && resp.statusCode() < 300) {
                try {
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    return json.get("id").getAsString();
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        });
    }

    private CompletableFuture<HttpResponse<String>> sendPostRequest(String endpoint, String jsonBody) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(API_BASE + endpoint))
                        .header("Authorization", "Bot " + botToken)
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "DiscordBot (LoveCore, 1.0.0)")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();
                return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка запроса к Discord API (" + endpoint + "): " + e.getMessage());
                return null;
            }
        });
    }

    private JsonObject serializeEmbed(DiscordEmbed embed) {
        JsonObject json = new JsonObject();
        if (embed.title() != null) json.addProperty("title", embed.title());
        if (embed.description() != null) json.addProperty("description", embed.description());
        if (embed.color() != null) json.addProperty("color", embed.color());
        if (embed.timestamp() != null) json.addProperty("timestamp", embed.timestamp().toString());
        if (embed.footer() != null) {
            JsonObject footer = new JsonObject();
            footer.addProperty("text", embed.footer());
            json.add("footer", footer);
        }
        if (embed.fields() != null && !embed.fields().isEmpty()) {
            JsonArray fields = new JsonArray();
            for (DiscordEmbed.Field f : embed.fields()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", f.name());
                obj.addProperty("value", f.value());
                obj.addProperty("inline", f.inline());
                fields.add(obj);
            }
            json.add("fields", fields);
        }
        return json;
    }

    private void startMessagePolling() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!isEnabled() || activeTickets.isEmpty()) return;

            for (Map.Entry<String, String> entry : activeTickets.entrySet()) {
                String ticketId = entry.getKey();
                String channelId = entry.getValue();
                pollChannelMessages(ticketId, channelId);
            }
        }, 60L, 80L); // раз в 4 секунды
    }

    private void pollChannelMessages(String ticketId, String channelId) {
        try {
            String after = channelLastMessageId.get(channelId);
            String url = API_BASE + "/channels/" + channelId + "/messages?limit=10" + (after != null ? "&after=" + after : "");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bot " + botToken)
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonArray array = JsonParser.parseString(resp.body()).getAsJsonArray();
                for (int i = array.size() - 1; i >= 0; i--) {
                    JsonObject msg = array.get(i).getAsJsonObject();
                    String msgId = msg.get("id").getAsString();
                    channelLastMessageId.put(channelId, msgId);

                    JsonObject author = msg.getAsJsonObject("author");
                    boolean isBot = author.has("bot") && author.get("bot").getAsBoolean();
                    if (isBot) continue;

                    String authorName = author.get("username").getAsString();
                    String content = msg.has("content") ? msg.get("content").getAsString() : "";

                    for (TicketMessageListener listener : messageListeners) {
                        try {
                            listener.onTicketMessage(ticketId, authorName, true, content);
                        } catch (Throwable t) {
                            plugin.getLogger().warning("Ошибка listener тикета: " + t.getMessage());
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private synchronized void loadStoredLinks() {
        File file = new File(plugin.getDataFolder(), "discord-data.yml");
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yaml.getConfigurationSection("links");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String discordId = sec.getString(key);
                    if (discordId != null && !discordId.isBlank()) {
                        playerToDiscord.put(uuid, discordId);
                        discordToPlayer.put(discordId, uuid);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private synchronized void saveStoredLinks() {
        File file = new File(plugin.getDataFolder(), "discord-data.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, String> entry : playerToDiscord.entrySet()) {
            yaml.set("links." + entry.getKey().toString(), entry.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить discord-data.yml: " + e.getMessage());
        }
    }
}
