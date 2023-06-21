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
package org.apache.camel.component.websocket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeResponse;
import org.eclipse.jetty.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.websocket.server.JettyWebSocketServletFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.jetty.websocket.api.util.WebSocketConstants.SEC_WEBSOCKET_PROTOCOL;

public class WebsocketComponentServlet extends JettyWebSocketServlet {
    public static final String UNSPECIFIED_SUBPROTOCOL = "default";
    public static final String ANY_SUBPROTOCOL = "any";

    private static final long serialVersionUID = 1L;
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final NodeSynchronization sync;
    private WebsocketConsumer consumer;
    private String pathSpec;

    private ConcurrentMap<String, WebsocketConsumer> consumers = new ConcurrentHashMap<>();
    private Map<String, WebSocketFactory> socketFactory;

    public WebsocketComponentServlet(NodeSynchronization sync, String pathSpec, Map<String, WebSocketFactory> socketFactory) {
        this.sync = sync;
        this.socketFactory = socketFactory;
        this.pathSpec = pathSpec;
    }

    public WebsocketConsumer getConsumer() {
        return consumer;
    }

    public void setConsumer(WebsocketConsumer consumer) {
        this.consumer = consumer;
    }

    public void connect(WebsocketConsumer consumer) {
        log.debug("Connecting consumer: {}", consumer);
        consumers.put(consumer.getPath(), consumer);
    }

    public void disconnect(WebsocketConsumer consumer) {
        log.debug("Disconnecting consumer: {}", consumer);
        consumers.remove(consumer.getPath());
    }

    public DefaultWebsocket doWebSocketConnect(JettyServerUpgradeRequest request, JettyServerUpgradeResponse resp) {
        String subprotocol = negotiateSubprotocol(request, consumer);
        if (subprotocol == null) {
            return null;       // no agreeable subprotocol was found, reject the connection
        }

        // now select the WebSocketFactory implementation based upon the agreed subprotocol
        final WebSocketFactory factory;
        if (socketFactory.containsKey(subprotocol)) {
            factory = socketFactory.get(subprotocol);
        } else {
            log.debug("No factory found for the socket subprotocol: {}, using default implementation", subprotocol);
            factory = socketFactory.get(UNSPECIFIED_SUBPROTOCOL);
        }

        if (subprotocol.equals(UNSPECIFIED_SUBPROTOCOL)) {
            subprotocol = null;             // application clients should just see null if no subprotocol was actually negotiated
        } else {
            resp.setHeader(SEC_WEBSOCKET_PROTOCOL, subprotocol);    // confirm selected subprotocol to client
        }

        // if the websocket component was configured with a wildcard path, determine the releative path used by this client
        final String relativePath;
        if (pathSpec != null && pathSpec.endsWith("*")) {
            final String prefix = pathSpec.substring(0, pathSpec.length() - 1);
            final String reqPath = request.getRequestPath();
            if (reqPath.startsWith(prefix) && reqPath.length() > prefix.length()) {
                relativePath = reqPath.substring(prefix.length());
            } else {
                relativePath = null;
            }
        } else {
            relativePath = null;
        }

        return factory.newInstance(request, pathSpec, sync, consumer, subprotocol, relativePath);
    }

    private String negotiateSubprotocol(JettyServerUpgradeRequest request, WebsocketConsumer consumer) {
        final String[] supportedSubprotocols = Optional.ofNullable(consumer)
                .map(WebsocketConsumer::getEndpoint)
                .map(WebsocketEndpoint::getSubprotocol)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(subprotocols -> subprotocols.split(","))
                .orElse(new String[] { ANY_SUBPROTOCOL });         // default: all subprotocols are supported

        final List<String> proposedSubprotocols = Optional.ofNullable(request.getHeaders(SEC_WEBSOCKET_PROTOCOL))
                .map(list -> list.stream()
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .map(header -> header.split(","))
                        .map(array -> Arrays.stream(array)
                                .map(String::trim)
                                .filter(value -> !value.isEmpty())
                                .collect(Collectors.toList()))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());               // default: no subprotocols are proposed

        for (String s : supportedSubprotocols) {
            final String supportedSubprotocol = s.trim();
            if (supportedSubprotocol.equalsIgnoreCase(ANY_SUBPROTOCOL)) {
                return UNSPECIFIED_SUBPROTOCOL;             // agree to use an unspecified subprotocol
            } else {
                if (proposedSubprotocols.contains(supportedSubprotocol)) {
                    return supportedSubprotocol;                // accept this subprotocol
                }
            }
        }

        log.debug("no agreeable subprotocol could be negotiated, server supports {} but client proposes {}",
                supportedSubprotocols,
                proposedSubprotocols);
        return null;
    }

    public Map<String, WebSocketFactory> getSocketFactory() {
        return socketFactory;
    }

    public void setSocketFactory(Map<String, WebSocketFactory> socketFactory) {
        this.socketFactory = socketFactory;
    }

    @Override
    protected void configure(JettyWebSocketServletFactory factory) {
        getServletContext().setAttribute(WebSocketPolicy.class.getName(), factory);
        factory.setCreator(this::doWebSocketConnect);
    }
}
