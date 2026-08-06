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
package org.apache.camel.telemetry.decorators;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.telemetry.SpanContextPropagationExtractor;

public class VertxWebsocketSpanDecorator extends AbstractSpanDecorator {

    // Constants from VertxWebsocketConstants - duplicated to avoid compile-time dependency
    private static final String HANDSHAKE_SPAN_CONTEXT_KEY = "CamelVertxWebsocketHandshakeSpanContext";
    private static final String SEND_TO_ALL = "CamelVertxWebsocket.sendToAll";
    private static final String CONNECTION_KEY = "CamelVertxWebsocket.connectionKey";

    @Override
    public String getComponent() {
        return "vertx-websocket";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.vertx.websocket.VertxWebsocketComponent";
    }

    @Override
    public SpanContextPropagationExtractor getExtractor(Exchange exchange) {
        return new VertxWebsocketSpanContextPropagationExtractor(exchange);
    }

    /**
     * Custom extractor for vertx-websocket that ensures the handshake span context header is visible to OpenTelemetry
     * when creating span links, even for producer spans.
     */
    private static class VertxWebsocketSpanContextPropagationExtractor implements SpanContextPropagationExtractor {
        private final Map<String, Object> headers;
        private final Exchange exchange;

        VertxWebsocketSpanContextPropagationExtractor(Exchange exchange) {
            this.exchange = exchange;
            this.headers = exchange.getIn().getHeaders();
            // Proactively collect span context for producers
            collectProducerSpanContext();
        }

        /**
         * For producer scenarios, collect the handshake span contexts from target WebSocket peers and store them in
         * headers so they're available when OpenTelemetryTracer creates the span.
         * <p>
         * Supports multiple span links for scenarios like sendToAll to multiple connections from different HTTP
         * requests.
         * <p>
         * Only collects span contexts for peers that will actually receive the message based on sendToAll and
         * connectionKey settings.
         */
        private void collectProducerSpanContext() {
            if (headers.containsKey(HANDSHAKE_SPAN_CONTEXT_KEY)) {
                // Already set by Consumer, nothing to do
                return;
            }

            // This is a Producer - collect span contexts from target peers using reflection
            try {
                // Get the endpoint from exchange context
                Object endpoint = exchange.getContext().hasEndpoint(exchange.getProperty(Exchange.TO_ENDPOINT, String.class));
                if (endpoint == null) {
                    return;
                }

                // Get all peers
                Method findPeersMethod = endpoint.getClass().getMethod("findPeerObjectsForHostPort");
                @SuppressWarnings("unchecked")
                List<Object> allPeers = (List<Object>) findPeersMethod.invoke(endpoint);

                if (allPeers == null || allPeers.isEmpty()) {
                    return;
                }

                // Determine which peers will actually receive the message
                Message message = exchange.getMessage();

                // Read sendToAll from message header or endpoint configuration
                Boolean sendToAll = message.getHeader(SEND_TO_ALL, Boolean.class);
                if (sendToAll == null) {
                    // Fallback to endpoint configuration
                    try {
                        Object config = endpoint.getClass().getMethod("getConfiguration").invoke(endpoint);
                        sendToAll = (Boolean) config.getClass().getMethod("isSendToAll").invoke(config);
                    } catch (Exception e) {
                        sendToAll = false;  // Default to false
                    }
                }

                List<Object> targetPeers;
                if (Boolean.TRUE.equals(sendToAll)) {
                    // Send to all peers
                    targetPeers = allPeers;
                } else {
                    // Send only to specific connection(s)
                    String connectionKey = message.getHeader(CONNECTION_KEY, String.class);
                    if (connectionKey == null || connectionKey.isEmpty()) {
                        // No specific connection key - might be external client
                        return;
                    }

                    // Filter peers by connection key(s) - can be comma-separated
                    targetPeers = new ArrayList<>();
                    String[] keys = connectionKey.split(",");
                    for (Object peer : allPeers) {
                        Method getConnectionKeyMethod = peer.getClass().getMethod("getConnectionKey");
                        String peerKey = (String) getConnectionKeyMethod.invoke(peer);
                        for (String key : keys) {
                            if (key.trim().equals(peerKey)) {
                                targetPeers.add(peer);
                                break;
                            }
                        }
                    }
                }

                // Collect span contexts only from target peers
                List<String> serializedContexts = new ArrayList<>();
                for (Object peer : targetPeers) {
                    Method getSpanContextMethod = peer.getClass().getMethod("getHandshakeSpanContext");
                    Object spanContext = getSpanContextMethod.invoke(peer);

                    if (spanContext != null) {
                        Class<?> spanContextClass = Class.forName("io.opentelemetry.api.trace.SpanContext");
                        Boolean isValid = (Boolean) spanContextClass.getMethod("isValid").invoke(spanContext);

                        if (Boolean.TRUE.equals(isValid)) {
                            String traceId = (String) spanContextClass.getMethod("getTraceId").invoke(spanContext);
                            String spanId = (String) spanContextClass.getMethod("getSpanId").invoke(spanContext);
                            serializedContexts.add(traceId + ":" + spanId);
                        }
                    }
                }

                // Store all span contexts as comma-separated string
                // Format: "traceId1:spanId1,traceId2:spanId2,..."
                if (!serializedContexts.isEmpty()) {
                    headers.put(HANDSHAKE_SPAN_CONTEXT_KEY, String.join(",", serializedContexts));
                }
            } catch (Exception e) {
                // Silently ignore - this is best-effort for span links
            }
        }

        @Override
        public Object get(String key) {
            return headers.get(key);
        }

        @Override
        public Set<String> keys() {
            return headers.keySet();
        }

        @Override
        public Iterator<Map.Entry<String, Object>> iterator() {
            return headers.entrySet().iterator();
        }
    }
}
