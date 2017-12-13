/**
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

public final class UndertowConstants {

    public static final String CONNECTION_KEY = "websocket.connectionKey";
    public static final String CONNECTION_KEY_LIST = "websocket.connectionKey.list";
    public static final String SEND_TO_ALL = "websocket.sendToAll";
    public static final String EVENT_TYPE = "websocket.eventType";
    public static final String EVENT_TYPE_ENUM = "websocket.eventTypeEnum";

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
    };

}
