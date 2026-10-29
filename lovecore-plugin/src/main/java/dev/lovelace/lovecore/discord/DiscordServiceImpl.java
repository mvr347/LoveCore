package dev.lovelace.lovecore.discord;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
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
import org.bukkit.scheduler.BukkitTask;

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

public final class DiscordServiceImpl implements DiscordService, AutoCloseable {

    private static final String API_BASE = "https://discord.com/api/v10";
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final JavaPlugin plugin;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final SecureRandom random = new SecureRandom();

    private volatile boolean enabled;
    private volatile String botToken;
    private volatile String guildId;
    private volatile String appealsCategoryId;
    private volatile String supportCategoryId;
    private volatile String reportsChannelId;
    private volatile String appealsLogChannelId;

    private final Map<UUID, String> playerToDiscord = new ConcurrentHashMap<>();
    private final Map<String, UUID> discordToPlayer = new ConcurrentHashMap<>();
    private final Map<String, LinkCode> linkCodes = new ConcurrentHashMap<>();
    private final java.util.concurrent.Semaphore ticketAdmission=new java.util.concurrent.Semaphore(64);
    private final Map<String, CompletableFuture<String>> creatingTickets = new ConcurrentHashMap<>();
    private final Map<String, String> activeTickets = new ConcurrentHashMap<>(); // ticketId -> channelId
    private final Map<String, String> channelLastMessageId = new ConcurrentHashMap<>();
    private final List<TicketMessageListener> messageListeners = new CopyOnWriteArrayList<>();
    private BukkitTask pollingTask;
    private DiscordGateway gateway;
    private final java.util.concurrent.Semaphore requestLimit = new java.util.concurrent.Semaphore(8);
    private final java.util.concurrent.Semaphore admission = new java.util.concurrent.Semaphore(264);
    private final List<dev.lovelace.lovecore.api.discord.TicketMessageListenerV2> listenersV2 = new CopyOnWriteArrayList<>();
    private final List<dev.lovelace.lovecore.api.discord.DiscordInteractionListener> interactions = new CopyOnWriteArrayList<>();
    private final InteractionOwnership ownership = new InteractionOwnership();

    // Blocking HTTP calls (including the 3 s sleep in closeTicketChannel) run on virtual threads instead of
    // ForkJoinPool.commonPool(), which is shared by every plugin on the JVM and would be starved by them.
    private final DiscordIoExecutor httpExecutor = new DiscordIoExecutor("love-discord-http-",8,256);

    private record LinkCode(UUID playerUuid, long expiresAt) {}

    public DiscordServiceImpl(JavaPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void load() {
        if(gateway!=null){gateway.close();gateway=null;}
        if(pollingTask!=null){pollingTask.cancel();pollingTask=null;}
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("discord");
        this.enabled = sec != null && sec.getBoolean("enabled", false);
        this.botToken = sec != null ? sec.getString("bot-token", "").trim() : "";
        this.guildId = sec != null ? sec.getString("guild-id", "").trim() : "";
        this.appealsCategoryId = sec != null ? sec.getString("appeals-category-id", "").trim() : "";
        this.supportCategoryId = sec != null ? sec.getString("support-category-id", "").trim() : "";
        this.reportsChannelId = sec != null ? sec.getString("reports-channel-id", "").trim() : "";
        this.appealsLogChannelId = sec != null ? sec.getString("appeals-log-channel-id", "").trim() : "";

        loadStoredLinks();
        File tickets=new File(plugin.getDataFolder(),"discord-tickets.yml");
        if(tickets.exists()) {
            var saved=YamlConfiguration.loadConfiguration(tickets);
            var ticketSection=saved.getConfigurationSection("tickets");
            if(ticketSection!=null)for(String id:ticketSection.getKeys(false))if(id.contains(":"))activeTickets.put(id,ticketSection.getString(id));
            var section=saved.getConfigurationSection("cursors");
            if(section!=null)for(String channel:section.getKeys(false))channelLastMessageId.put(channel,section.getString(channel));
        }

        if (enabled && !botToken.isBlank()) {
            plugin.getLogger().info("✓ LoveCore DiscordService активирован (Guild ID: " + guildId + ")");
            startMessagePolling();
            if(gateway!=null)gateway.close();
            gateway=new DiscordGateway(httpClient,botToken,this::onInteraction);
            gateway.start();
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
    public synchronized boolean completeLink(String discordId, String code) {
        if (code == null || discordId == null || discordId.isBlank()) return false;
        String cleanCode = code.trim().toUpperCase(Locale.ROOT);
        LinkCode entry = linkCodes.remove(cleanCode);
        if (entry == null || entry.expiresAt() < System.currentTimeMillis()) {
            return false;
        }

        UUID uuid = entry.playerUuid();
        setLink(uuid,discordId);
        plugin.getLogger().info("Привязан аккаунт: " + uuid + " <-> Discord " + discordId);
        return true;
    }

    @Override
    public synchronized void unlink(UUID playerUuid) {
        String discordId = playerToDiscord.remove(playerUuid);
        if (discordId != null) {
            discordToPlayer.remove(discordId);
            saveStoredLinks();
        }
    }

    @Override
    public synchronized void setLink(UUID playerUuid, String discordId) {
        if (playerUuid == null) return;
        if (discordId == null || discordId.isBlank()) {
            unlink(playerUuid);
            return;
        }
        String old=playerToDiscord.remove(playerUuid);if(old!=null)discordToPlayer.remove(old);
        UUID previous=discordToPlayer.remove(discordId);if(previous!=null)playerToDiscord.remove(previous);
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
        body.add("allowed_mentions", noMentions());
        if (message != null && !message.isBlank()) {
            body.addProperty("content", cap(message,2000));
        }
        if (embed != null) {
            JsonArray embeds = new JsonArray();
            embeds.add(serializeEmbed(embed));
            body.add("embeds", embeds);
        }

        return sendPostRequest("/channels/" + channelId + "/messages", body.toString())
                .thenApply(response -> response != null && response.statusCode() >= 200 && response.statusCode() < 300);
    }

    private JsonObject noMentions(){JsonObject a=new JsonObject();a.add("parse",new JsonArray());return a;}
    @Override public void registerTicketMessageListenerV2(dev.lovelace.lovecore.api.discord.TicketMessageListenerV2 listener){listenersV2.add(listener);}
    @Override public void unregisterTicketMessageListenerV2(dev.lovelace.lovecore.api.discord.TicketMessageListenerV2 listener){listenersV2.remove(listener);}
    @Override public void registerInteractionListener(dev.lovelace.lovecore.api.discord.DiscordInteractionListener listener){interactions.add(listener);}
    @Override public void unregisterInteractionListener(dev.lovelace.lovecore.api.discord.DiscordInteractionListener listener){interactions.remove(listener);}
    private void onInteraction(dev.lovelace.lovecore.api.discord.DiscordInteraction interaction) {
        for(var listener:interactions)try{if(listener.onInteraction(interaction))return;}catch(RuntimeException e){plugin.getLogger().warning("Ошибка обработчика Discord");}
        // Not ours: another gateway on the same token (LoveAuth) answers it, and a second answer would break it.
        if(!ownership.owns(interaction.type(),interaction.name()))return;
        respond(interaction,"{\"type\":4,\"data\":{\"content\":\"Действие временно недоступно\",\"flags\":64}}");
    }
    @Override public void claimInteractionPrefix(String prefix){ownership.claimPrefix(prefix);}
    @Override public CompletableFuture<Boolean> registerCommand(String name,String description,Map<String,String> options) {
        ownership.addCommand(name);
        JsonObject body=new JsonObject();body.addProperty("name",name);body.addProperty("description",description);
        JsonArray opts=new JsonArray();options.forEach((key,value)->{JsonObject o=new JsonObject();o.addProperty("type",3);o.addProperty("name",key);o.addProperty("description",value);o.addProperty("required",false);opts.add(o);});body.add("options",opts);
        return request("GET","/users/@me",null).thenCompose(resp->{
            if(resp==null||resp.statusCode()!=200)return CompletableFuture.completedFuture(false);
            String app=JsonParser.parseString(resp.body()).getAsJsonObject().get("id").getAsString();
            return sendPostRequest("/applications/"+app+"/guilds/"+guildId+"/commands",body.toString()).thenApply(r->r!=null&&r.statusCode()<300);
        });
    }
    @Override public CompletableFuture<Boolean> unregisterCommand(String name) {
        ownership.removeCommand(name);
        if(name==null||!name.matches("[\\p{L}0-9_-]{1,32}"))return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid command"));
        return request("GET","/users/@me",null).thenCompose(identity->{
            if(identity==null||identity.statusCode()!=200)return CompletableFuture.completedFuture(false);
            String app=JsonParser.parseString(identity.body()).getAsJsonObject().get("id").getAsString();
            String path="/applications/"+app+"/guilds/"+guildId+"/commands";
            return request("GET",path,null).thenCompose(commands->{
                if(commands==null||commands.statusCode()!=200)return CompletableFuture.completedFuture(false);
                for(var element:JsonParser.parseString(commands.body()).getAsJsonArray()) {
                    var command=element.getAsJsonObject();
                    if(name.equals(command.get("name").getAsString()))return request("DELETE",path+"/"+command.get("id").getAsString(),null).thenApply(response->response!=null&&response.statusCode()<300);
                }
                return CompletableFuture.completedFuture(true);
            });
        });
    }
    @Override public CompletableFuture<Boolean> respond(dev.lovelace.lovecore.api.discord.DiscordInteraction i,String json) {
        return sendPostRequest("/interactions/"+i.id()+"/"+i.token()+"/callback",json).thenApply(r->r!=null&&r.statusCode()<300);
    }
    @Override public CompletableFuture<Boolean> editResponse(dev.lovelace.lovecore.api.discord.DiscordInteraction i,String content) {
        JsonObject p=new JsonObject();p.addProperty("content",cap(content,2000));p.add("allowed_mentions",noMentions());
        return request("PATCH","/webhooks/"+i.applicationId()+"/"+i.token()+"/messages/@original",p.toString()).thenApply(r->r!=null&&r.statusCode()<300);
    }
    @Override public CompletableFuture<String> sendMessage(String channel,String json,String messageId) {
        JsonObject data=JsonParser.parseString(json).getAsJsonObject();data.add("allowed_mentions",noMentions());
        if(data.has("content"))data.addProperty("content",cap(data.get("content").getAsString(),2000));
        return request(messageId==null?"POST":"PATCH","/channels/"+channel+"/messages"+(messageId==null?"":"/"+messageId),data.toString()).thenApply(r->{if(r==null||r.statusCode()>=300)return null;return JsonParser.parseString(r.body()).getAsJsonObject().get("id").getAsString();});
    }
    @Override public CompletableFuture<String> sendImage(String channel,String content,String filename,String mime,byte[] image) {
        if(channel==null||!channel.matches("[0-9]{5,25}"))return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid channel"));
        var upload=DiscordUpload.image(content,filename,mime,image);
        return request("POST","/channels/"+channel+"/messages",upload.contentType(),HttpRequest.BodyPublishers.ofByteArray(upload.bytes())).thenApply(response->{
            if(response==null||response.statusCode()<200||response.statusCode()>=300)return null;return JsonParser.parseString(response.body()).getAsJsonObject().get("id").getAsString();
        });
    }
    @Override public CompletableFuture<String> directMessage(String user,String json){return createDmChannel(user).thenCompose(c->c==null?CompletableFuture.completedFuture(null):sendMessage(c,json,null));}
    static String ticketKey(TicketType type,String id){return type.name()+":"+id;}
    @Override public void restoreTicket(String id,String channel){activeTickets.put(id.contains(":")?id:ticketKey(TicketType.BAN_APPEAL,id),channel);}

    @Override
    public CompletableFuture<String> createTicketChannel(TicketType type, String ticketId, String playerName, String initialReason) {
        return createTicketChannel(type,ticketId,playerName,initialReason,null);
    }

    @Override public CompletableFuture<String> createTicketChannel(TicketType type,String ticketId,String playerName,String initialReason,String ownerDiscordId) {
        return createTicketChannel(new dev.lovelace.lovecore.api.discord.TicketRequest(type,ticketId,playerName,initialReason,ownerDiscordId,null,false));
    }
    @Override public boolean supportsExplicitTicketRouting() { return true; }
    @Override public CompletableFuture<String> createTicketChannel(dev.lovelace.lovecore.api.discord.TicketRequest request) {
        var type=request.type();var ticketId=request.ticketId();
        if(request.preview()&&(request.categoryId().equals(appealsCategoryId)||request.categoryId().equals(supportCategoryId)))
            return CompletableFuture.failedFuture(new IllegalArgumentException("Тестовая категория совпадает с рабочей"));
        String key=ticketKey(type,ticketId);
        CompletableFuture<String> result=new CompletableFuture<>();
        var previous=creatingTickets.putIfAbsent(key,result);if(previous!=null)return previous;
        if(!ticketAdmission.tryAcquire()){creatingTickets.remove(key,result);return CompletableFuture.failedFuture(new java.util.concurrent.RejectedExecutionException("Too many ticket creations"));}
        try{createPrivateTicket(request).whenComplete((channel,error)->{
            if(error!=null)result.completeExceptionally(error);else result.complete(channel);
            creatingTickets.remove(key,result);ticketAdmission.release();
        });}catch(RuntimeException e){creatingTickets.remove(key,result);ticketAdmission.release();result.completeExceptionally(e);}
        return result;
    }
    private CompletableFuture<String> createPrivateTicket(dev.lovelace.lovecore.api.discord.TicketRequest request) {
    return createPrivateTicket(request, null);
  }

  private CompletableFuture<String> createPrivateTicket(
      dev.lovelace.lovecore.api.discord.TicketRequest request,
      dev.lovelace.lovecore.api.discord.ManagedTicketRequest managed) {
    if (managed != null && managed.supervisorRoleIds().contains(guildId))
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Нельзя открыть обращение для всех"));
        var type=request.type();var ticketId=request.ticketId();var playerName=request.playerName();
        var initialReason=request.reason();var ownerDiscordId=request.ownerDiscordId();
        if (!isEnabled() || guildId.isBlank()) return CompletableFuture.completedFuture(null);
        String existing=activeTickets.get(ticketKey(type,ticketId));if(existing!=null)return CompletableFuture.completedFuture(existing);
        JsonArray permissions = new JsonArray();
        JsonObject everyone = new JsonObject();everyone.addProperty("id",guildId);everyone.addProperty("type",0);everyone.addProperty("deny","1024");permissions.add(everyone);
    for (String role :
        managed == null
            ? plugin.getConfig().getStringList("discord.staff-role-ids")
            : managed.supervisorRoleIds()) permissions.add(overwrite(role, 0, "101376", "0"));
    if (managed != null)
      for (String member : managed.memberIds())
        if (!member.equals(ownerDiscordId)) permissions.add(overwrite(member, 1, "101376", "0"));
        if(ownerDiscordId!=null&&!ownerDiscordId.isBlank())permissions.add(overwrite(ownerDiscordId,1,"101376","0"));
        String prefix = type == TicketType.BAN_APPEAL ? "бан-" : (type == TicketType.SUPPORT ? "саппорт-" : "тикет-");
        String sanitizedName = playerName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    String channelName =
        managed == null
            ? (request.preview() ? "test-" : "ticket-") + prefix + sanitizedName
            : managed.channelName();

        JsonObject payload = new JsonObject();
        payload.addProperty("name", channelName);
        payload.addProperty("type", 0); // Guild Text Channel
        payload.add("permission_overwrites",permissions);
        payload.addProperty("topic", cap("Тикет: " + ticketId + " | Игрок: " + playerName + " | Причина: " + initialReason,1024));

        String category = request.categoryId()!=null&&!request.categoryId().isBlank()?request.categoryId():(type == TicketType.BAN_APPEAL ? appealsCategoryId : supportCategoryId);
        if (category != null && !category.isBlank()) {
            payload.addProperty("parent_id", category);
        }

    return request("GET", "/users/@me", null)
        .thenCompose(
            identity -> {
              if (identity == null || identity.statusCode() != 200)
                return CompletableFuture.<HttpResponse<String>>completedFuture(null);
              String bot =
                  JsonParser.parseString(identity.body()).getAsJsonObject().get("id").getAsString();
              permissions.add(overwrite(bot, 1, "117776", "0"));
              return sendPostRequest("/guilds/" + guildId + "/channels", payload.toString());
            })
        .thenApply(
            resp -> {
              if (resp == null || resp.statusCode() < 200 || resp.statusCode() >= 300) {
                plugin
                    .getLogger()
                    .warning(
                        "Не удалось создать тикет-канал Discord: "
                            + (resp != null ? resp.body() : "null"));
                return null;
              }
              try {
                JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                String channelId = json.get("id").getAsString();
                activeTickets.put(ticketKey(type, ticketId), channelId);
                saveCursors();

                // Отправляем вступительное сообщение
                DiscordEmbed intro =
                    new DiscordEmbed(
                        (request.preview() ? "[ТЕСТ] " : "")
                            + "⚔ Тикет "
                            + (type == TicketType.BAN_APPEAL
                                ? "апелляции бана"
                                : type == TicketType.REPORT ? "жалобы" : "поддержки"),
                        "**Игрок:** `"
                            + playerName
                            + "`\n"
                            + "**Тикет ID:** `"
                            + ticketId
                            + "`\n"
                            + "**Описание:** "
                            + initialReason
                            + "\n\n"
                            + "*Переписка сохраняется в деле. Закрытие обращения не отменяет наказание.*",
                        0xE67E22,
                        List.of(),
                        "Обращения",
                        java.time.Instant.now());
                sendChannelMessage(
                    channelId,
                    (request.preview() ? "[ТЕСТ] " : "")
                        + "Новое обращение от игрока `"
                        + playerName
                        + "`",
                    intro);

                return channelId;
              } catch (Exception e) {
                plugin
                    .getLogger()
                    .log(
                        Level.WARNING,
                        "Ошибка парсинга ответа создания канала: " + e.getMessage(),
                        e);
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

        return request("GET","/channels/"+channelId,null).thenCompose(existing->{
            if(existing==null||existing.statusCode()!=200)return CompletableFuture.completedFuture(false);
            JsonObject channel=JsonParser.parseString(existing.body()).getAsJsonObject();
            JsonObject patch=new JsonObject();patch.addProperty("name","закрыт-"+channel.get("name").getAsString().replaceFirst("^закрыт-", ""));
            JsonArray overwrites=channel.getAsJsonArray("permission_overwrites");
            for(JsonElement el:overwrites){JsonObject o=el.getAsJsonObject();long allow=Long.parseLong(o.get("allow").getAsString());long deny=Long.parseLong(o.get("deny").getAsString());o.addProperty("allow",Long.toString(allow&~2048L));o.addProperty("deny",Long.toString(deny|2048L));}
            patch.add("permission_overwrites",overwrites);
            return request("PATCH","/channels/"+channelId,patch.toString()).thenApply(resp->{
                boolean ok=resp!=null&&resp.statusCode()<300;
                if(ok){activeTickets.values().remove(channelId);channelLastMessageId.remove(channelId);saveCursors();}return ok;
            });
        });
    }

  @Override
  public CompletableFuture<String> createManagedTicket(
      dev.lovelace.lovecore.api.discord.ManagedTicketRequest managed) {
    var route = managed.routing();
    if (route.preview()
        && (route.categoryId().equals(appealsCategoryId)
            || route.categoryId().equals(supportCategoryId)))
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Тестовая категория совпадает с рабочей"));
    String key = ticketKey(route.type(), route.ticketId());
    var result = new CompletableFuture<String>();
    var previous = creatingTickets.putIfAbsent(key, result);
    if (previous != null) return previous;
    if (!ticketAdmission.tryAcquire()) {
      creatingTickets.remove(key, result);
      return CompletableFuture.failedFuture(
          new java.util.concurrent.RejectedExecutionException("Too many ticket creations"));
    }
    try {
      recoverManagedTicket(managed)
          .thenCompose(
              found ->
                  found == null
                      ? createPrivateTicket(route, managed)
                      : CompletableFuture.completedFuture(found))
          .whenComplete(
              (channel, error) -> {
                if (error != null) result.completeExceptionally(error);
                else result.complete(channel);
                creatingTickets.remove(key, result);
                ticketAdmission.release();
              });
    } catch (RuntimeException error) {
      creatingTickets.remove(key, result);
      ticketAdmission.release();
      result.completeExceptionally(error);
    }
    return result;
  }

  @Override
  public CompletableFuture<String> recoverManagedTicket(
      dev.lovelace.lovecore.api.discord.ManagedTicketRequest managed) {
    var route = managed.routing();
    if (route.preview()
        && (route.categoryId().equals(appealsCategoryId)
            || route.categoryId().equals(supportCategoryId)))
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Тестовая категория совпадает с рабочей"));
    String key = ticketKey(route.type(), route.ticketId());
    String known = activeTickets.get(key);
    if (known != null) return CompletableFuture.completedFuture(known);
    return request("GET", "/guilds/" + guildId + "/channels", null)
        .thenApply(
            response -> {
              confirmed(response, 200);
              String found = null;
              for (JsonElement item : JsonParser.parseString(response.body()).getAsJsonArray())
                if (TicketAccess.matchesManaged(item.getAsJsonObject(), guildId, managed)) {
                  if (found != null)
                    throw new IllegalStateException(
                        "Найдено несколько каналов обращения; требуется ручная сверка");
                  found = item.getAsJsonObject().get("id").getAsString();
                }
              if (found != null) {
                activeTickets.putIfAbsent(key, found);
                saveCursors();
                return activeTickets.get(key);
              }
              return null;
            });
  }

  private void managedChannel(String channel) {
    if (channel == null || !channel.matches("[0-9]{5,25}") || !activeTickets.containsValue(channel))
      throw new IllegalArgumentException("Канал не является активным обращением");
  }

  private static void confirmed(HttpResponse<?> response, int expected) {
    if (response == null || response.statusCode() != expected)
      throw new java.util.concurrent.CompletionException(
          new IllegalStateException(
              "Discord не подтвердил операцию ("
                  + (response == null ? "нет ответа" : response.statusCode())
                  + ")"));
  }

  @Override
  public CompletableFuture<Boolean> updateTicketAccess(
      String channel, List<String> roles, List<String> members) {
    try {
      managedChannel(channel);
      var validated =
          new dev.lovelace.lovecore.api.discord.ManagedTicketRequest(
              new dev.lovelace.lovecore.api.discord.TicketRequest(
                  TicketType.SUPPORT, "access", "", "", null, null, false),
              roles,
              members,
              "mine-ticket-1");
      return request("GET", "/users/@me", null)
          .thenCompose(
              identity -> {
                confirmed(identity, 200);
                String bot =
                    JsonParser.parseString(identity.body())
                        .getAsJsonObject()
                        .get("id")
                        .getAsString();
                JsonArray access =
                    TicketAccess.overwrites(
                        guildId, bot, validated.supervisorRoleIds(), validated.memberIds());
                JsonObject payload = new JsonObject();
                payload.add("permission_overwrites", access);
                return request("PATCH", "/channels/" + channel, payload.toString())
                    .thenApply(
                        response -> {
                          confirmed(response, 200);
                          return true;
                        });
              });
    } catch (RuntimeException error) {
      return CompletableFuture.failedFuture(error);
    }
  }

  @Override
  public CompletableFuture<Boolean> setTicketReadOnly(String channel, boolean locked) {
    try {
      managedChannel(channel);
    } catch (RuntimeException error) {
      return CompletableFuture.failedFuture(error);
    }
    return request("GET", "/users/@me", null)
        .thenCompose(
            identity -> {
              confirmed(identity, 200);
              String bot =
                  JsonParser.parseString(identity.body()).getAsJsonObject().get("id").getAsString();
              return request("GET", "/channels/" + channel, null)
                  .thenCompose(
                      existing -> {
                        confirmed(existing, 200);
                        JsonObject value =
                            JsonParser.parseString(existing.body()).getAsJsonObject();
                        if (!guildId.equals(value.get("guild_id").getAsString()))
                          throw new IllegalStateException("Другая гильдия");
                        JsonArray access = value.getAsJsonArray("permission_overwrites");
                        for (JsonElement item : access) {
                          JsonObject o = item.getAsJsonObject();
                          if (o.get("type").getAsInt() == 1
                              && bot.equals(o.get("id").getAsString())) continue;
                          long allow = Long.parseLong(o.get("allow").getAsString()),
                              deny = Long.parseLong(o.get("deny").getAsString());
                          long writing =
                              2048L | 32768L | 34359738368L | 68719476736L | 274877906944L;
                          o.addProperty("allow", Long.toString(locked ? allow & ~writing : allow));
                          o.addProperty(
                              "deny", Long.toString(locked ? deny | writing : deny & ~writing));
                        }
                        JsonObject payload = new JsonObject();
                        payload.add("permission_overwrites", access);
                        return request("PATCH", "/channels/" + channel, payload.toString())
                            .thenApply(
                                response -> {
                                  confirmed(response, 200);
                                  return true;
                                });
                      });
            });
  }

  @Override
  public CompletableFuture<List<dev.lovelace.lovecore.api.discord.TicketMessage>> getTicketHistory(
      String channel, String before) {
    try {
      managedChannel(channel);
      if (before != null && !before.matches("[0-9]{5,25}"))
        throw new IllegalArgumentException("Invalid cursor");
    } catch (RuntimeException error) {
      return CompletableFuture.failedFuture(error);
    }
    String key =
        activeTickets.entrySet().stream()
            .filter(e -> channel.equals(e.getValue()))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow();
    return request(
            "GET",
            "/channels/"
                + channel
                + "/messages?limit=100"
                + (before == null ? "" : "&before=" + before),
            null)
        .thenApply(
            response -> {
              confirmed(response, 200);
              var messages = new ArrayList<dev.lovelace.lovecore.api.discord.TicketMessage>();
              for (JsonElement item : JsonParser.parseString(response.body()).getAsJsonArray()) {
                JsonObject message = item.getAsJsonObject(),
                    author = message.getAsJsonObject("author");
                var attachments =
                    new ArrayList<dev.lovelace.lovecore.api.discord.TicketMessage.Attachment>();
                for (JsonElement file : message.getAsJsonArray("attachments")) {
                  JsonObject data = file.getAsJsonObject();
                  attachments.add(
                      new dev.lovelace.lovecore.api.discord.TicketMessage.Attachment(
                          data.get("filename").getAsString(), data.get("url").getAsString()));
                }
                messages.add(
                    new dev.lovelace.lovecore.api.discord.TicketMessage(
                        key,
                        channel,
                        message.get("id").getAsString(),
                        author.get("id").getAsString(),
                        author.get("username").getAsString(),
                        TicketAccess.transcriptText(message),
                        attachments));
              }
              return List.copyOf(messages);
            });
  }

  @Override
  public CompletableFuture<byte[]> getTicketAttachment(String channel, String message, String url) {
    try {
      managedChannel(channel);
      if (!message.matches("[0-9]{5,25}")) throw new IllegalArgumentException("Invalid message");
      TicketAccess.attachmentUri(url);
    } catch (RuntimeException error) {
      return CompletableFuture.failedFuture(error);
    }
    return request("GET", "/channels/" + channel + "/messages/" + message, null)
        .thenCompose(
            response -> {
              confirmed(response, 200);
              URI authenticated =
                  TicketAccess.authenticatedAttachment(
                      JsonParser.parseString(response.body())
                          .getAsJsonObject()
                          .getAsJsonArray("attachments"),
                      url);
              return httpExecutor.supply(
                  () -> {
                    try {
                      var req =
                          HttpRequest.newBuilder(authenticated)
                              .timeout(Duration.ofSeconds(30))
                              .GET()
                              .build();
                      var res = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
                      try (var input = res.body()) {
                        confirmed(res, 200);
                        if (res.headers().firstValueAsLong("Content-Length").orElse(0)
                            > TicketAccess.MAX_ATTACHMENT)
                          throw new IllegalArgumentException("Вложение больше 8 МиБ");
                        byte[] bytes = input.readNBytes(TicketAccess.MAX_ATTACHMENT + 1);
                        if (bytes.length > TicketAccess.MAX_ATTACHMENT)
                          throw new IllegalArgumentException("Вложение больше 8 МиБ");
                        return bytes;
                      }
                    } catch (InterruptedException error) {
                      Thread.currentThread().interrupt();
                      throw new java.util.concurrent.CompletionException(error);
                    } catch (IOException error) {
                      throw new java.util.concurrent.CompletionException(error);
                    }
                  });
            });
  }

  @Override
  public CompletableFuture<Boolean> deleteTicketChannel(String channel) {
    try {
      managedChannel(channel);
    } catch (RuntimeException error) {
      return CompletableFuture.failedFuture(error);
    }
    return request("DELETE", "/channels/" + channel, null)
        .thenApply(
            response -> {
              if (response == null
                  || !(response.statusCode() == 200
                      || response.statusCode() == 204
                      || response.statusCode() == 404))
                throw new IllegalStateException("Удаление канала не подтверждено");
              activeTickets.values().removeIf(channel::equals);
              channelLastMessageId.remove(channel);
              saveCursors();
              return true;
            });
  }

  @Override
  public String getDiscordGuildId() {
    return guildId;
  }

  @Override
  public CompletableFuture<java.util.Set<String>> getMemberRoleIds(String user) {
    if (user == null || !user.matches("[0-9]{5,25}"))
      return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid member"));
    return request("GET", "/guilds/" + guildId + "/members/" + user, null)
        .thenApply(
            response -> {
              if (response != null && response.statusCode() == 404) return java.util.Set.of();
              confirmed(response, 200);
              var roles = new java.util.HashSet<String>();
              for (JsonElement role :
                  JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("roles"))
                roles.add(role.getAsString());
              return java.util.Set.copyOf(roles);
            });
  }

  @Override
  public CompletableFuture<Boolean> editInteractionResponse(
      dev.lovelace.lovecore.api.discord.DiscordInteraction interaction, String json) {
    JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
    payload.add("allowed_mentions", noMentions());
    return request(
            "PATCH",
            "/webhooks/"
                + interaction.applicationId()
                + "/"
                + interaction.token()
                + "/messages/@original",
            payload.toString())
        .thenApply(
            response -> {
              confirmed(response, 200);
              return true;
            });
  }

  @Override
  public CompletableFuture<String> ensureMessage(String channel, String message, String json) {
    if (channel == null
        || !channel.matches("[0-9]{5,25}")
        || message != null && !message.matches("[0-9]{5,25}"))
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Invalid message destination"));
    JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
    payload.add("allowed_mentions", noMentions());
    if (message == null)
      return sendMessage(channel, payload.toString(), null)
          .thenApply(
              id -> {
                if (id == null) throw new IllegalStateException("Отправка не подтверждена");
                return id;
              });
    return request("PATCH", "/channels/" + channel + "/messages/" + message, payload.toString())
        .thenCompose(
            response -> {
              if (response != null && response.statusCode() == 404)
                return sendMessage(channel, payload.toString(), null)
                    .thenApply(
                        id -> {
                          if (id == null)
                            throw new IllegalStateException("Отправка не подтверждена");
                          return id;
                        });
              confirmed(response, 200);
              return CompletableFuture.completedFuture(
                  JsonParser.parseString(response.body())
                      .getAsJsonObject()
                      .get("id")
                      .getAsString());
            });
  }

    private JsonObject overwrite(String id,int type,String allow,String deny){JsonObject o=new JsonObject();o.addProperty("id",id);o.addProperty("type",type);o.addProperty("allow",allow);o.addProperty("deny",deny);return o;}

    @Override
    public void registerTicketMessageListener(TicketMessageListener listener) {
        if (listener != null) {
            messageListeners.add(listener);
        }
    }

    @Override
    public void unregisterTicketMessageListener(TicketMessageListener listener) {
        if (listener != null) {
            messageListeners.remove(listener);
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

    private CompletableFuture<HttpResponse<String>> sendPostRequest(String endpoint,String jsonBody) {return request("POST",endpoint,jsonBody);}
    private CompletableFuture<HttpResponse<String>> request(String method,String endpoint,String body) {
        return request(method,endpoint,"application/json",body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
    }
    private CompletableFuture<HttpResponse<String>> request(String method,String endpoint,String contentType,HttpRequest.BodyPublisher body) {
        if(!isEnabled())return CompletableFuture.completedFuture(null);
        if(!admission.tryAcquire())return CompletableFuture.failedFuture(new java.util.concurrent.RejectedExecutionException("Discord queue full"));
        CompletableFuture<HttpResponse<String>> result;
        try { result=httpExecutor.supply(()->{
            boolean acquired=false;
            try {
                requestLimit.acquire();acquired=true;
                for(int attempt=0;attempt<4;attempt++) {
                    var b=HttpRequest.newBuilder().uri(URI.create(API_BASE+endpoint)).timeout(Duration.ofSeconds(10))
                        .header("Authorization","Bot "+botToken).header("Content-Type",contentType)
                        .method(method,body);
                    var response=httpClient.send(b.build(),HttpResponse.BodyHandlers.ofString());
                    if(response.statusCode()!=429)return response;
                    double delay=JsonParser.parseString(response.body()).getAsJsonObject().get("retry_after").getAsDouble();
                    Thread.sleep(Math.min(30000,Math.max(100,(long)(delay*1000))));
                }
                return null;
            } catch(InterruptedException e){Thread.currentThread().interrupt();return null;}
            catch(Exception e){plugin.getLogger().warning("Не удалось выполнить запрос Discord");return null;}
            finally{if(acquired)requestLimit.release();}
        }); }
        catch(RuntimeException e){admission.release();return CompletableFuture.failedFuture(e);}
        return result.whenComplete((v,e)->admission.release());
    }

    static String cap(String text,int limit) {
        if(text==null)return "";int end=Math.min(text.length(),Math.max(0,limit));
        if(end>0&&end<text.length()&&Character.isHighSurrogate(text.charAt(end-1)))end--;
        return text.substring(0,end);
    }
    JsonObject serializeEmbed(DiscordEmbed embed) {
        JsonObject json=new JsonObject();int left=6000;
        if(embed.title()!=null){String title=cap(embed.title(),256);json.addProperty("title",title);left-=title.length();}
        if(embed.description()!=null){String desc=cap(embed.description(),Math.min(left,4096));json.addProperty("description",desc);left-=desc.length();}
        if(embed.color()!=null)json.addProperty("color",embed.color());
        if(embed.timestamp()!=null)json.addProperty("timestamp",embed.timestamp().toString());
        JsonArray fields=new JsonArray();
        if(embed.fields()!=null)for(var field:embed.fields()) {
            if(fields.size()>=25||left<2)break;
            String name=cap(field.name()==null||field.name().isBlank()?"—":field.name(),Math.min(256,left-1));left-=name.length();
            String value=cap(field.value()==null||field.value().isBlank()?"—":field.value(),Math.min(1024,left));left-=value.length();
            if(name.isEmpty()||value.isEmpty())break;
            JsonObject f=new JsonObject();f.addProperty("name",name);f.addProperty("value",value);f.addProperty("inline",field.inline());fields.add(f);
        }
        if(!fields.isEmpty())json.add("fields",fields);
        if(embed.footer()!=null&&left>0){JsonObject footer=new JsonObject();footer.addProperty("text",cap(embed.footer(),Math.min(2048,left)));json.add("footer",footer);}
        return json;
    }

    private void startMessagePolling() {
        // load() переисполняется на /lovecoreadmin reload — без отмены здесь каждый reload
        // навешивал бы ещё один параллельный таймер, и каждое сообщение тикета долетало бы
        // до слушателей столько раз, сколько было reload'ов.
        if (pollingTask != null) {
            pollingTask.cancel();
        }
        pollingTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!isEnabled() || activeTickets.isEmpty()) return;

            for (Map.Entry<String, String> entry : activeTickets.entrySet()) {
                String ticketId = entry.getKey();
                String channelId = entry.getValue();
                String externalId=ticketId.contains(":")?ticketId.substring(ticketId.indexOf(':')+1):ticketId;
                pollChannelMessages(externalId, channelId);
            }
            saveCursors();
        }, 60L, 80L); // раз в 4 секунды
    }

    private synchronized void saveCursors() {
        var yaml=new YamlConfiguration();activeTickets.forEach((key,value)->yaml.set("tickets."+key,value));channelLastMessageId.forEach((key,value)->yaml.set("cursors."+key,value));
        try{yaml.save(new File(plugin.getDataFolder(),"discord-tickets.yml"));}catch(IOException e){plugin.getLogger().warning("Не сохранены позиции сообщений Discord");}
    }

    private void pollChannelMessages(String ticketId, String channelId) {
        try {
            String after = channelLastMessageId.get(channelId);
            String url = API_BASE + "/channels/" + channelId + "/messages?limit=100" + (after != null ? "&after=" + after : "");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bot " + botToken)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonArray array = JsonParser.parseString(resp.body()).getAsJsonArray();
                for (int i = array.size() - 1; i >= 0; i--) {
                    JsonObject msg = array.get(i).getAsJsonObject();
                    String msgId = msg.get("id").getAsString();


                    JsonObject author = msg.getAsJsonObject("author");
                    boolean isBot = author.has("bot") && author.get("bot").getAsBoolean();
                    if (isBot) { channelLastMessageId.put(channelId,msgId); continue; }

                    String authorName = author.get("username").getAsString();
                    String content = msg.has("content") ? msg.get("content").getAsString() : "";

                    var attachments=new ArrayList<dev.lovelace.lovecore.api.discord.TicketMessage.Attachment>();
                    if(msg.has("attachments"))for(JsonElement el:msg.getAsJsonArray("attachments")){JsonObject item=el.getAsJsonObject();attachments.add(new dev.lovelace.lovecore.api.discord.TicketMessage.Attachment(item.get("filename").getAsString(),item.get("url").getAsString()));}
                    var incoming=new dev.lovelace.lovecore.api.discord.TicketMessage(ticketId,channelId,msgId,author.get("id").getAsString(),authorName,content,attachments);
                    for(var listener:listenersV2)listener.onTicketMessage(incoming);
                    for (TicketMessageListener listener : messageListeners) {
                        try {
                            listener.onTicketMessage(ticketId, authorName, false, content);
                        } catch (Throwable t) {
                            plugin.getLogger().warning("Ошибка listener тикета: " + t.getMessage());
                        }
                    }
                    channelLastMessageId.put(channelId,msgId);
                }
            }
        } catch (Exception e) { plugin.getLogger().fine("Повтор загрузки сообщений Discord"); }
    }

    private synchronized void loadStoredLinks() {
        File file = new File(plugin.getDataFolder(), "discord-data.yml");
        playerToDiscord.clear();discordToPlayer.clear();
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

    public void shutdown(){close();}

    @Override public void close() {
        enabled=false;
        if(gateway!=null)gateway.close();
        if(pollingTask!=null)pollingTask.cancel();
        messageListeners.clear();listenersV2.clear();interactions.clear();
        creatingTickets.values().forEach(f->f.cancel(true));creatingTickets.clear();
        httpExecutor.stop(Duration.ofSeconds(2));httpClient.shutdownNow();
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
