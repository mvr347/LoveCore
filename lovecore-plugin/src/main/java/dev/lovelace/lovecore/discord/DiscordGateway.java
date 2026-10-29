package dev.lovelace.lovecore.discord;

import com.google.gson.*;
import dev.lovelace.lovecore.api.discord.DiscordInteraction;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Gateway is used only for authenticated interactions; ticket text uses resumable REST polling. */
final class DiscordGateway implements WebSocket.Listener, AutoCloseable {
    private final String token;
    private final Consumer<DiscordInteraction> callback;
    private final ScheduledExecutorService clock=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("love-discord-gateway").factory());
    private final HttpClient client;
    private volatile WebSocket socket;
    private volatile boolean closed;
    private volatile Integer sequence;
    private volatile boolean awaitingHeartbeat;
    private ScheduledFuture<?> heartbeat;
    private final StringBuilder buffer=new StringBuilder();
    DiscordGateway(HttpClient client,String token,Consumer<DiscordInteraction> callback) {this.client=client;this.token=token;this.callback=callback;}
    void start() {connect();}
    private void connect() {
        if(closed)return;
        client.newWebSocketBuilder().connectTimeout(java.time.Duration.ofSeconds(10))
            .buildAsync(URI.create("wss://gateway.discord.gg/?v=10&encoding=json"),this)
            .whenComplete((ws,error)->{if(closed){if(ws!=null)ws.abort();}else if(error!=null)retry();});
    }
    @Override public void onOpen(WebSocket ws) {socket=ws;ws.request(1);}
    @Override public CompletionStage<?> onText(WebSocket ws,CharSequence text,boolean last) {
        try {
            if(buffer.length()+text.length()>1048576){ws.abort();buffer.setLength(0);retry();return null;}
            buffer.append(text);
            if(last){String payload=buffer.toString();buffer.setLength(0);handle(ws,JsonParser.parseString(payload).getAsJsonObject());}
        } catch(RuntimeException e){ws.abort();retry();}
        finally{ws.request(1);}
        return null;
    }
    private synchronized void handle(WebSocket ws,JsonObject event) {
        if(closed)return;
        if(event.has("s")&&!event.get("s").isJsonNull())sequence=event.get("s").getAsInt();
        int op=event.get("op").getAsInt();
        if(op==10){
            if(heartbeat!=null)heartbeat.cancel(false);
            awaitingHeartbeat=false;
            long millis=event.getAsJsonObject("d").get("heartbeat_interval").getAsLong();
            heartbeat=clock.scheduleAtFixedRate(()->{
                if(awaitingHeartbeat){ws.abort();retry();return;}
                awaitingHeartbeat=true;send(ws,1,sequence==null?JsonNull.INSTANCE:new JsonPrimitive(sequence));
            },millis/2,millis,TimeUnit.MILLISECONDS);
            JsonObject identify=new JsonObject();identify.addProperty("token",token);identify.addProperty("intents",1);
            JsonObject props=new JsonObject();props.addProperty("os","java");props.addProperty("browser","LoveCore");props.addProperty("device","LoveCore");identify.add("properties",props);send(ws,2,identify);
        } else if(op==11)awaitingHeartbeat=false;
        else if(op==1)send(ws,1,sequence==null?JsonNull.INSTANCE:new JsonPrimitive(sequence));
        else if(op==7||op==9){ws.abort();retry();}
        else if(op==0&&event.has("t")&&"INTERACTION_CREATE".equals(event.get("t").getAsString())) {
            JsonObject d=event.getAsJsonObject("d");JsonObject data=d.getAsJsonObject("data");
            JsonObject user=d.has("member")?d.getAsJsonObject("member").getAsJsonObject("user"):d.getAsJsonObject("user");
            Map<String,String> values=new HashMap<>();
            if(data.has("options"))for(JsonElement el:data.getAsJsonArray("options")){JsonObject option=el.getAsJsonObject();if(option.has("value"))values.put(option.get("name").getAsString(),option.get("value").getAsString());}
            if(data.has("components"))for(JsonElement row:data.getAsJsonArray("components"))for(JsonElement el:row.getAsJsonObject().getAsJsonArray("components")){JsonObject f=el.getAsJsonObject();if(f.has("value"))values.put(f.get("custom_id").getAsString(),f.get("value").getAsString());}
      if (data.has("values"))
        values.put("selected", data.getAsJsonArray("values").get(0).getAsString());
      if (d.has("channel_id")) values.put("_channelId", d.get("channel_id").getAsString());
      if (d.has("message"))
        values.put("_messageId", d.getAsJsonObject("message").get("id").getAsString());
            String name=data.has("custom_id")?data.get("custom_id").getAsString():data.get("name").getAsString();
            callback.accept(new DiscordInteraction(d.get("id").getAsString(),d.get("application_id").getAsString(),d.get("token").getAsString(),user.get("id").getAsString(),d.has("guild_id")?d.get("guild_id").getAsString():"",name,d.get("type").getAsInt(),values));
        }
    }
    private void send(WebSocket ws,int op,JsonElement data){JsonObject msg=new JsonObject();msg.addProperty("op",op);msg.add("d",data);ws.sendText(msg.toString(),true);}
    private synchronized void retry(){if(closed)return;if(heartbeat!=null)heartbeat.cancel(false);heartbeat=null;if(reconnecting)return;reconnecting=true;clock.schedule(()->{reconnecting=false;connect();},10,TimeUnit.SECONDS);}
    private volatile boolean reconnecting;
    @Override public CompletionStage<?> onClose(WebSocket ws,int status,String reason){retry();return null;}
    @Override public void onError(WebSocket ws,Throwable error){retry();}
    @Override public synchronized void close(){closed=true;if(socket!=null)socket.abort();clock.shutdownNow();}
}
