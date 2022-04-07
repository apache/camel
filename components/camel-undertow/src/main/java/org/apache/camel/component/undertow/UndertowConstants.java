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
package org.apache.camel.component.undertow;

import io.undertow.util.Headers;
import io.undertow.websockets.core.WebSocketChannel;
import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

public final class UndertowConstants {

    /**
     * An identifier of {@link WebSocketChannel} through which the {@code message} was received or should be sent.
     */
    @Metadata(javaType = "String")
    public static final String CONNECTION_KEY = "websocket.connectionKey";
    @Metadata(label = "producer", description = "The list of websocket connection keys", javaType = "List<String>")
    public static final String CONNECTION_KEY_LIST = "websocket.connectionKey.list";
    /**
     * To send to all websocket subscribers. Can be used to configure on endpoint level, instead of having to use the
     * {@code UndertowConstants.SEND_TO_ALL} header on the message.
     */
    @Metadata(javaType = "Boolean")
    public static final String SEND_TO_ALL = "websocket.sendToAll";
    @Metadata(label = "consumer", description = "The numeric identifier of the type of websocket event", javaType = "Integer")
    public static final String EVENT_TYPE = "websocket.eventType";
    @Metadata(label = "consumer", description = "The type of websocket event",
              javaType = "org.apache.camel.component.undertow.UndertowConstants.EventType")
    public static final String EVENT_TYPE_ENUM = "websocket.eventTypeEnum";
    /**
     * The {@link WebSocketChannel} through which the {@code message} was received
     */
    @Metadata(label = "consumer", javaType = "io.undertow.websockets.core.WebSocketChannel")
    public static final String CHANNEL = "websocket.channel";
    @Metadata(label = "consumer", description = "The exchange for the websocket transport, only available for ON_OPEN events",
              javaType = "io.undertow.websockets.spi.WebSocketHttpExchange")
    public static final String EXCHANGE = "websocket.exchange";

    @Metadata(description = "The http response code", javaType = "Integer")
    public static final String HTTP_RESPONSE_CODE = Exchange.HTTP_RESPONSE_CODE;
    @Metadata(description = "The content type", javaType = "String")
    public static final String CONTENT_TYPE = Exchange.CONTENT_TYPE;
    @Metadata(label = "consumer", description = "The http character encoding", javaType = "String")
    public static final String HTTP_CHARACTER_ENCODING = Exchange.HTTP_CHARACTER_ENCODING;
    @Metadata(description = "The http path", javaType = "String")
    public static final String HTTP_PATH = Exchange.HTTP_PATH;
    @Metadata(description = "The http query", javaType = "String")
    public static final String HTTP_QUERY = Exchange.HTTP_QUERY;
    @Metadata(description = "The http URI", javaType = "String")
    public static final String HTTP_URI = Exchange.HTTP_URI;
    @Metadata(label = "producer", description = "The http method", javaType = "String")
    public static final String HTTP_METHOD = Exchange.HTTP_METHOD;
    @Metadata(label = "producer", description = "The host http header", javaType = "String")
    public static final String HOST_STRING = Headers.HOST_STRING;

    /**
     * WebSocket peers related events the {@link UndertowConsumer} sends to the Camel route.
     */
    public enum EventType {
        /**
         * A new peer has connected.
         */
        ONOPEN(1),

        /**
         * A peer has disconnected.
         */
        ONCLOSE(0),

        /**
         * Unused in Undertow component. Kept for compatibility with Camel websocket component.
         */
        ONERROR(-1);

        private final int code;

        EventType(int code) {
            this.code = code;
        }

        /**
         * @return a numeric identifier of this {@link EventType}. Kept for compatibility with Camel websocket
         *         component.
         */
        public int getCode() {
            return code;
        }

        public static EventType ofCode(int code) {
            switch (code) {
                case 1:
                    return ONOPEN;
                case 0:
                    return ONCLOSE;
                case -1:
                    return ONERROR;
                default:
                    throw new IllegalArgumentException("Cannot find an " + EventType.class.getName() + " for code " + code);
            }
        }
    }

    public static final String WS_PROTOCOL = "ws";
    public static final String WSS_PROTOCOL = "wss";

    private UndertowConstants() {
    }

}
