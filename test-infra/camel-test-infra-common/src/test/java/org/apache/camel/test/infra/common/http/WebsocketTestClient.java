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
package org.apache.camel.test.infra.common.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A websocket client for using with tests
 */
public class WebsocketTestClient {
    private static final Logger LOG = LoggerFactory.getLogger(WebsocketTestClient.class);

    private final List<Object> received;
    private final HttpClient modernClient;
    private final String url;

    private CountDownLatch latch;
    private WebSocket websocket;

    /**
     * Creates a new client instance for the given URL
     *
     * @param url the url to connect to
     */
    public WebsocketTestClient(String url) {
        this(url, 1);
    }

    /**
     * Creates a new client instance for the given URL
     *
     * @param url   the url to connect to
     * @param count the number of expected messages
     */
    public WebsocketTestClient(String url, int count) {
        this(url, count, HttpClient.newBuilder());
    }

    /**
     * Creates a new client instance for the given URL
     *
     * @param url     the url to connect to
     * @param count   the number of expected messages
     * @param builder a builder used to configure the client
     */
    public WebsocketTestClient(String url, int count, HttpClient.Builder builder) {
        this.received = new ArrayList<>();
        this.latch = new CountDownLatch(count);
        this.modernClient = builder.build();
        this.url = url;
    }

    /**
     * Creates a new client instance for the given URL
     *
     * @param url     the url to connect to
     * @param count   the number of expected messages
     * @param context the SSL context for the client
     */

    public WebsocketTestClient(String url, int count, SSLContext context) {
        this.received = new ArrayList<>();
        this.latch = new CountDownLatch(count);
        this.modernClient = HttpClient.newBuilder().sslContext(context).build();
        this.url = url;
    }

    /**
     * Connects to the host using a builtin listener
     */
    public void connect() {
        websocket = modernClient.newWebSocketBuilder().buildAsync(URI.create(url), new TestWebSocketListener()).join();
    }

    /**
     * Sends a text message
     *
     * @param message the message to send
     */
    public void sendTextMessage(String message) {
        websocket.sendText(message, true);
    }

    /**
     * Sends a binary / bytes message
     *
     * @param message the message to send
     */
    public void sendBytesMessage(byte[] message) {
        websocket.sendBinary(ByteBuffer.wrap(message), true);
    }

    /**
     * Waits for the expected number of messages to arrive
     *
     * @param  secs                 seconds to wait
     * @return                      true if all the messages arrive in time or false otherwise
     * @throws InterruptedException
     */
    public boolean await(int secs) throws InterruptedException {
        return await(secs, TimeUnit.SECONDS);
    }

    /**
     * Waits for the expected number of messages to arrive
     *
     * @param  duration             seconds to wait
     * @param  timeUnit             the duration time unit
     * @return                      true if all the messages arrive in time or false otherwise
     * @throws InterruptedException
     */
    public boolean await(int duration, TimeUnit timeUnit) throws InterruptedException {
        return latch.await(duration, timeUnit);
    }

    public void reset(int count) {
        latch = new CountDownLatch(count);
        received.clear();
    }

    /**
     * Gets all the received messages
     *
     * @return
     */
    public List<Object> getReceived() {
        return Collections.unmodifiableList(received);
    }

    public <T> List<T> getReceived(Class<T> cls) {
        List<T> list = new ArrayList<>();
        for (Object o : received) {
            list.add(getValue(o, cls));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getValue(Object o, Class<T> cls) {
        if (cls.isInstance(o)) {
            return (T) o;
        } else if (cls == String.class) {
            if (o instanceof byte[]) {
                return (T) new String((byte[]) o);
            } else {
                return (T) o.toString();
            }
        } else if (cls == byte[].class) {
            if (o instanceof String) {
                return (T) ((String) o).getBytes();
            }
        }
        return null;
    }

    public void close() {
        websocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
    }

    private class TestWebSocketListener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket websocket) {
            LOG.info("[ws] opened");
            websocket.request(1);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            LOG.info("[ws] closed");

            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            LOG.error("[ws] error: {}", error.getMessage(), error);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            final String message = readString(data);

            received.add(message.getBytes());
            LOG.info("[ws] received bytes --> {}", message);
            latch.countDown();

            return WebSocket.Listener.super.onBinary(webSocket, data, last);
        }

        private String readString(ByteBuffer data) {
            int remaining = data.remaining();
            byte[] tmp = new byte[remaining];
            data.get(tmp);

            return new String(tmp);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            final String message = data.toString();

            received.add(message);
            LOG.info("[ws] received --> {}", message);
            latch.countDown();

            return WebSocket.Listener.super.onText(webSocket, data, last);
        }
    }
}
