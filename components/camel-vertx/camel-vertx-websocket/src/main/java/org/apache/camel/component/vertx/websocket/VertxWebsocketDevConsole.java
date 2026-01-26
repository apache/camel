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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.SocketAddress;
import org.apache.camel.Route;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "vertx-websocket", displayName = "Vert.x WebSocket", description = "Vert.x WebSocket consumer details")
public class VertxWebsocketDevConsole extends AbstractDevConsole {
    /**
     * Whether to include WebSocket peer connection header details in the output
     */
    public static final String INCLUDE_HEADERS = "includeHeaders";

    public VertxWebsocketDevConsole() {
        super("camel", "vertx-websocket", "Vert.x WebSocket", "Vert.x WebSocket consumer details");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        boolean includeHeaders = "true".equals(options.getOrDefault(INCLUDE_HEADERS, "true"));

        StringBuilder sb = new StringBuilder();

        Map<VertxWebsocketHostKey, List<VertxWebsocketConsumer>> consumersByHost = getConsumersByHost();

        for (Map.Entry<VertxWebsocketHostKey, List<VertxWebsocketConsumer>> hostEntry : consumersByHost.entrySet()) {
            VertxWebsocketHostKey hostKey = hostEntry.getKey();
            sb.append(String.format("%n    Host: %s", hostKey.toString()));

            for (VertxWebsocketConsumer consumer : hostEntry.getValue()) {
                String path = consumer.getEndpoint().getConfiguration().getWebsocketURI().getPath();
                sb.append(String.format("%n        Path: %s", path));

                List<VertxWebsocketPeer> pathPeers = consumer.getEndpoint().getVertxHostRegistry()
                        .values()
                        .stream()
                        .flatMap(host -> host.getConnectedPeers().stream())
                        .filter(peer -> peer.getRawPath().equals(path))
                        .toList();

                sb.append(String.format("%n            Connected Peers (%d): ", pathPeers.size()));

                for (VertxWebsocketPeer peer : pathPeers) {
                    sb.append(String.format("%n                ID: %s", peer.getConnectionKey()));
                    sb.append(String.format("%n                Path: %s", peer.getPath()));
                    sb.append(String.format("%n                Raw Path: %s", peer.getRawPath()));

                    ServerWebSocket webSocket = peer.getWebSocket();
                    SocketAddress socketAddress = webSocket.localAddress();
                    String hostAddress = socketAddress == null ? "Unknown" : socketAddress.hostAddress();
                    sb.append(String.format("%n                Host Address: %s", hostAddress));

                    if (webSocket.subProtocol() != null) {
                        sb.append(String.format("%n                Sub Protocol: %s", webSocket.subProtocol()));
                    }

                    if (includeHeaders) {
                        sb.append(String.format("%n                Headers: %s", webSocket.headers().entries().stream()
                                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", "))));
                    }
                }
            }
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        boolean includeHeaders = "true".equals(options.getOrDefault(INCLUDE_HEADERS, "true"));

        JsonObject root = new JsonObject();
        JsonArray hosts = new JsonArray();

        Map<VertxWebsocketHostKey, List<VertxWebsocketConsumer>> consumersByHost = getConsumersByHost();

        for (Map.Entry<VertxWebsocketHostKey, List<VertxWebsocketConsumer>> hostEntry : consumersByHost.entrySet()) {
            VertxWebsocketHostKey hostKey = hostEntry.getKey();
            JsonObject host = new JsonObject();
            host.put("host", hostKey.toString());

            JsonArray paths = new JsonArray();
            for (VertxWebsocketConsumer consumer : hostEntry.getValue()) {
                String path = consumer.getEndpoint().getConfiguration().getWebsocketURI().getPath();

                JsonObject pathJson = new JsonObject();
                pathJson.put("path", path);

                List<VertxWebsocketPeer> pathPeers = consumer.getEndpoint().getVertxHostRegistry()
                        .values()
                        .stream()
                        .flatMap(h -> h.getConnectedPeers().stream())
                        .filter(peer -> peer.getRawPath().equals(path))
                        .toList();

                JsonArray peers = new JsonArray();
                for (VertxWebsocketPeer peer : pathPeers) {
                    JsonObject peerJson = new JsonObject();
                    peerJson.put("id", peer.getConnectionKey());
                    peerJson.put("path", peer.getPath());
                    peerJson.put("rawPath", peer.getRawPath());

                    ServerWebSocket webSocket = peer.getWebSocket();
                    SocketAddress socketAddress = webSocket.localAddress();
                    String hostAddress = socketAddress == null ? "Unknown" : socketAddress.hostAddress();

                    peerJson.put("hostAddress", hostAddress);
                    peerJson.put("subProtocol", webSocket.subProtocol());

                    if (includeHeaders) {
                        JsonObject headers = new JsonObject();
                        webSocket.headers()
                                .entries()
                                .forEach(e -> headers.put(e.getKey(), e.getValue()));
                        peerJson.put("headers", headers);
                    }

                    peers.add(peerJson);
                }

                pathJson.put("peers", peers);
                paths.add(pathJson);
            }

            host.put("paths", paths);
            hosts.add(host);
        }

        root.put("hosts", hosts);
        return root;
    }

    Map<VertxWebsocketHostKey, List<VertxWebsocketConsumer>> getConsumersByHost() {
        Map<VertxWebsocketHostKey, List<VertxWebsocketConsumer>> consumersByHost = new java.util.LinkedHashMap<>();
        for (Route route : getCamelContext().getRoutes()) {
            if (route.getConsumer() instanceof VertxWebsocketConsumer consumer) {
                VertxWebsocketHostKey hostKey = new VertxWebsocketHostKey(
                        consumer.getEndpoint().getConfiguration().getWebsocketURI().getHost(),
                        consumer.getEndpoint().getConfiguration().getWebsocketURI().getPort());
                consumersByHost.computeIfAbsent(hostKey, k -> new java.util.ArrayList<>()).add(consumer);
            }
        }
        return consumersByHost;
    }
}
