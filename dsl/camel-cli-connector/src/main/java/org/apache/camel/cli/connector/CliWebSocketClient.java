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

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * The WebSocket client of the websocket transport (<tt>camel.cli.transport=websocket</tt>): only the socket I/O, the
 * protocol (frames, snapshots, heartbeat, reconnecting, security checks) is done by the transport.
 * <p/>
 * The transport uses the single bean of this type in the registry, if any, otherwise the JDK client
 * ({@link JdkCliWebSocketClient}). Set <tt>camel.cli.websocket.client=jdk</tt> to always use the JDK client.
 * <p/>
 * Threads: the transport never has two messages in flight on a {@link Channel}, and may wait for the returned stages on
 * its own threads. The {@link Listener} callbacks may be called on any thread, for example an event loop: they do not
 * block.
 *
 * @since 4.23
 */
public interface CliWebSocketClient {

    /**
     * The largest message the client must accept from the tool, in chars (16 MB).
     */
    int MAX_MESSAGE_SIZE = 16 * 1024 * 1024;

    /**
     * The name of the client, sent to the tool in the <tt>hello</tt> frame (<tt>transport</tt>) and logged.
     */
    String getName();

    /**
     * Opens a connection.
     *
     * @param  url      the <tt>ws://</tt> or <tt>wss://</tt> url of the tool, query string included as given
     * @param  headers  headers to send with the handshake (such as <tt>Authorization</tt>)
     * @param  listener receives what the tool sends on this connection
     * @return          completes with the connection once open, or fails with a {@link CliWebSocketHandshakeException}
     *                  when the tool rejects the handshake
     */
    CompletionStage<Channel> connect(URI url, Map<String, String> headers, Listener listener);

    /**
     * An open connection.
     */
    interface Channel {

        /**
         * Sends a whole text message.
         */
        CompletionStage<?> sendText(String text);

        /**
         * Sends a ping, the tool answers with a pong.
         */
        CompletionStage<?> sendPing();

        /**
         * Sends a close frame. The transport calls {@link #abort()} if it does not complete in time.
         */
        CompletionStage<?> close(int code, String reason);

        /**
         * Closes the connection right away, without waiting for anything.
         */
        void abort();
    }

    /**
     * Receives what the tool sends. The methods must not block.
     */
    interface Listener {

        /**
         * A whole text message (a message sent in several frames is passed once complete).
         */
        void onText(String text);

        /**
         * A pong, answering a {@link Channel#sendPing()}.
         */
        void onPong();

        /**
         * The connection was closed (by the tool, or by the network).
         */
        void onClose(int code, String reason);

        /**
         * The connection failed, it cannot be used anymore.
         */
        void onError(Throwable error);
    }
}
