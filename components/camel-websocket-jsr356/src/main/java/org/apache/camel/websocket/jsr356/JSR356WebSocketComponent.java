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
package org.apache.camel.websocket.jsr356;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.server.ServerContainer;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

import static java.util.Optional.ofNullable;

@Component("websocket-jsr356")
public class JSR356WebSocketComponent extends DefaultComponent {

    private static final Map<String, ContextBag> SERVER_CONTAINERS = new ConcurrentHashMap<>();

    @Metadata(label = "advanced")
    private ServerEndpointDeploymentStrategy serverEndpointDeploymentStrategy;

    @Override
    protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters) throws Exception {
        JSR356Endpoint endpoint = new JSR356Endpoint(this, uri);
        endpoint.setUri(new URI(remaining));
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public static void registerServer(final String contextPath, final ServerContainer container) {
        SERVER_CONTAINERS.put(contextPath, new ContextBag(container));
    }

    public static void unregisterServer(final String contextPath) {
        SERVER_CONTAINERS.remove(contextPath);
    }

    public static ContextBag getContext(final String context) {
        return ofNullable(context)
                .map(SERVER_CONTAINERS::get)
                .orElseGet(() -> SERVER_CONTAINERS.size() == 1 ? SERVER_CONTAINERS.values().iterator().next() : SERVER_CONTAINERS.get(""));
    }

    public ServerEndpointDeploymentStrategy getServerEndpointDeploymentStrategy() {
        if (serverEndpointDeploymentStrategy == null) {
            serverEndpointDeploymentStrategy = new DefaultServerEndpointDeploymentStrategy();
        }
        return serverEndpointDeploymentStrategy;
    }

    /**
     * To enable customization of how a WebSocket ServerEndpoint is configured and deployed.
     *
     * By default {@link DefaultServerEndpointDeploymentStrategy} is used.
     */
    public void setServerEndpointDeploymentStrategy(ServerEndpointDeploymentStrategy serverEndpointDeploymentStrategy) {
        this.serverEndpointDeploymentStrategy = serverEndpointDeploymentStrategy;
    }

    public static final class ContextBag {
        private final ServerContainer container;
        private final Map<String, CamelServerEndpoint> endpoints = new HashMap<>();

        private ContextBag(final ServerContainer container) {
            this.container = container;
        }

        public ServerContainer getContainer() {
            return container;
        }

        public Map<String, CamelServerEndpoint> getEndpoints() {
            return endpoints;
        }
    }
}
