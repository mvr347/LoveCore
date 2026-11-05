package dev.lovelace.lovecore.discord;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import dev.lovelace.lovecore.api.discord.*;

import org.junit.jupiter.api.Test;

import java.net.http.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

class DiscordGatewayTest {
    @Test
    void authenticatedMemberAndFormValuesAreExtracted() {
        AtomicReference<DiscordInteraction> found = new AtomicReference<>();
        try (var client = HttpClient.newHttpClient();
                var gateway = new DiscordGateway(client, "unused", found::set)) {
            var socket = new Socket();
            gateway.onOpen(socket);
            String payload =
                    """
                    {"op":0,"t":"INTERACTION_CREATE","s":1,"d":{"id":"1","application_id":"2","token":"secret-value","type":5,"guild_id":"3","member":{"user":{"id":"4"}},"data":{"custom_id":"lwa:appeal:submit:12","components":[{"components":[{"custom_id":"reason","value":"Проверьте блокировку"}]}]}}}
                    """;
            gateway.onText(socket, payload, true);
            assertEquals("4", found.get().userId());
            assertEquals("Проверьте блокировку", found.get().values().get("reason"));
            assertFalse(found.get().toString().contains("secret-value"));
            assertThrows(
                    UnsupportedOperationException.class, () -> found.get().values().put("x", "y"));
        }
    }

    @Test
    void dmInteractionUsesUserAndFragmentedFrames() {
        AtomicReference<DiscordInteraction> found = new AtomicReference<>();
        try (var client = HttpClient.newHttpClient();
                var gateway = new DiscordGateway(client, "unused", found::set)) {
            var socket = new Socket();
            gateway.onOpen(socket);
            String p =
                    "{\"op\":0,\"t\":\"INTERACTION_CREATE\",\"d\":{\"id\":\"1\",\"application_id\":\"2\",\"token\":\"secret\",\"type\":3,\"user\":{\"id\":\"55\"},\"data\":{\"custom_id\":\"lwa:appeal:new:1\"}}}";
            gateway.onText(socket, p.substring(0, 30), false);
            assertNull(found.get());
            gateway.onText(socket, p.substring(30), true);
            assertEquals("55", found.get().userId());
            assertEquals("", found.get().guildId());
        }
    }

    @Test
    void channelContextIsTakenFromDiscordNotSubmittedFields() {
        AtomicReference<DiscordInteraction> found = new AtomicReference<>();
        try (var client = HttpClient.newHttpClient();
                var gateway = new DiscordGateway(client, "unused", found::set)) {
            var socket = new Socket();
            gateway.onOpen(socket);
            String p =
                    """
                    {"op":0,"t":"INTERACTION_CREATE","d":{"id":"1","application_id":"2","token":"secret","type":5,"channel_id":"12345","guild_id":"33333","member":{"user":{"id":"44444"}},"data":{"custom_id":"lwa:desk:submit:close:report:1","components":[{"components":[{"custom_id":"_channelId","value":"99999"}]}]}}}
                    """;
            gateway.onText(socket, p, true);
            assertEquals("12345", found.get().values().get("_channelId"));
            gateway.onText(socket, p.replace("\"channel_id\":\"12345\",", ""), true);
            assertNull(found.get().values().get("_channelId"));
        }
    }

    @Test
    void reconnectDiscardsOldFragmentsAndIgnoresOldSocketCallbacks() {
        var found = new AtomicReference<DiscordInteraction>();
        try (var client = HttpClient.newHttpClient();
                var gateway = new DiscordGateway(client, "unused", found::set)) {
            var old = new Socket();
            gateway.onOpen(old);
            gateway.onText(old, "{invalid", false);
            var current = new Socket();
            gateway.onOpen(current);
            String payload =
                    "{\"op\":0,\"t\":\"INTERACTION_CREATE\",\"d\":{\"id\":\"1\",\"application_id\":\"2\",\"token\":\"secret\",\"type\":3,\"user\":{\"id\":\"55\"},\"data\":{\"custom_id\":\"test\"}}}";
            gateway.onText(current, payload, true);
            assertNotNull(found.get());
            assertEquals("55", found.get().userId());
            found.set(null);
            gateway.onText(old, payload, true);
            assertNull(found.get(), "a valid interaction on an old socket must not be delivered");
            gateway.onClose(old, 1000, "old connection");
            gateway.onText(current, payload, true);
            assertNotNull(found.get(), "the current socket remains active");
        }
    }

    @Test
    void aHandshakeCompletingAfterShutdownIsAbortedWithoutReceivingData() {
        try (var client = HttpClient.newHttpClient()) {
            var gateway =
                    new DiscordGateway(
                            client, "unused", i -> fail("closed gateway delivered an interaction"));
            gateway.close();
            var late = new Socket();
            gateway.onOpen(late);
            assertTrue(late.aborted);
            assertEquals(0, late.requests);
        }
    }

    static class Socket implements WebSocket {
        boolean aborted;
        long requests;

        public CompletableFuture<WebSocket> sendText(CharSequence x, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        public CompletableFuture<WebSocket> sendBinary(ByteBuffer x, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        public CompletableFuture<WebSocket> sendPing(ByteBuffer x) {
            return CompletableFuture.completedFuture(this);
        }

        public CompletableFuture<WebSocket> sendPong(ByteBuffer x) {
            return CompletableFuture.completedFuture(this);
        }

        public CompletableFuture<WebSocket> sendClose(int x, String reason) {
            return CompletableFuture.completedFuture(this);
        }

        public void request(long n) {
            requests += n;
        }

        public String getSubprotocol() {
            return "";
        }

        public boolean isOutputClosed() {
            return false;
        }

        public boolean isInputClosed() {
            return false;
        }

        public void abort() {
            aborted = true;
        }
    }
}
