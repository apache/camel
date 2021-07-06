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
package org.apache.camel.component.vertx.websocket;

public final class VertxWebsocketHelper {

    private VertxWebsocketHelper() {
        // Utility class
    }

    /**
     * Extracts the port number from the endpoint URI path or returns the Vert.x default HTTP server port (0) if one was
     * not provided
     */
    public static int extractPortNumber(String remaining) {
        int index1 = remaining.indexOf(':');
        int index2 = remaining.indexOf('/');
        if (index1 != -1 && index2 != -1) {
            String result = remaining.substring(index1 + 1, index2);
            if (result.isEmpty()) {
                throw new IllegalArgumentException("Unable to resolve port from URI: " + remaining);
            }

            try {
                return Integer.parseInt(result);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Unable to parse port: " + result);
            }
        } else {
            return VertxWebsocketConstants.DEFAULT_VERTX_SERVER_PORT;
        }
    }

    /**
     * Extracts the host name from the endpoint URI path or returns the Vert.x default HTTP server host (0.0.0.0) if one
     * was not provided
     */
    public static String extractHostName(String remaining) {
        int index = remaining.indexOf(':');
        if (index != -1) {
            return remaining.substring(0, index);
        } else {
            return VertxWebsocketConstants.DEFAULT_VERTX_SERVER_HOST;
        }
    }

    /**
     * Extracts the WebSocket path from the endpoint URI path or returns the Vert.x default HTTP server path (/) if one
     * was not provided
     */
    public static String extractPath(String remaining) {
        int index = remaining.indexOf('/');
        if (index != -1) {
            return remaining.substring(index);
        } else {
            return VertxWebsocketConstants.DEFAULT_VERTX_SERVER_PATH + remaining;
        }
    }

    /**
     * Creates a VertxWebsocketHostKey from a given VertxWebsocketConfiguration
     */
    public static VertxWebsocketHostKey createHostKey(VertxWebsocketConfiguration configuration) {
        return new VertxWebsocketHostKey(configuration.getHost(), configuration.getPort());
    }
}
