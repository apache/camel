/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.cli.connector;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * The default {@link CliWebSocketClient}, with the JDK client (<tt>java.net.http</tt>), so no extra dependency is
 * needed.
 *
 * @since 4.23
 */
public class JdkCliWebSocketClient implements CliWebSocketClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    @Override
    public String getName() {
        return "jdk";
    }

    @Override
    public CompletionStage<Channel> connect(URI url, Map<String, String> headers, Listener listener) {
        WebSocket.Builder builder = client.newWebSocketBuilder().connectTimeout(CONNECT_TIMEOUT);
        headers.forEach(builder::header);
        CompletableFuture<Channel> answer = new CompletableFuture<>();
        builder.buildAsync(url, new Adapter(listener)).whenComplete((ws, e) -> {
            if (e != null) {
                answer.completeExceptionally(translate(e));
            } else {
                answer.complete(new JdkChannel(ws));
            }
        });
        return answer;
    }

    private static Throwable translate(Throwable e) {
        Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
        if (cause instanceof WebSocketHandshakeException he) {
            return new CliWebSocketHandshakeException(he.getResponse().statusCode(), he);
        }
        return cause;
    }

    private record JdkChannel(WebSocket ws) implements Channel {

        @Override
        public CompletionStage<?> sendText(String text) {
            return ws.sendText(text, true);
        }

        @Override
        public CompletionStage<?> sendPing() {
            return ws.sendPing(ByteBuffer.allocate(0));
        }

        @Override
        public CompletionStage<?> close(int code, String reason) {
            return ws.sendClose(code, reason);
        }

        @Override
        public void abort() {
            ws.abort();
        }
    }

    /**
     * Reassembles the text messages sent in several frames, one frame at a time.
     */
    private static final class Adapter implements WebSocket.Listener {

        private final Listener listener;
        private final StringBuilder partial = new StringBuilder();

        Adapter(Listener listener) {
            this.listener = listener;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (partial.length() > MAX_MESSAGE_SIZE) {
                partial.setLength(0);
                webSocket.abort();
                listener.onError(new IOException("Message larger than " + MAX_MESSAGE_SIZE + " chars"));
                return null;
            }
            if (last) {
                String text = partial.toString();
                partial.setLength(0);
                listener.onText(text);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            // not part of the protocol
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            // the JDK replies with a pong
            return WebSocket.Listener.super.onPing(webSocket, message);
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            listener.onPong();
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            listener.onClose(statusCode, reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            listener.onError(error);
        }
    }
}
