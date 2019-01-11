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
package org.apache.camel.websocket.jsr356;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Optional.ofNullable;

import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.server.ServerContainer;

import org.apache.camel.Endpoint;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.IOHelper;

@Component("websocket-jsr356")
public class JSR356WebSocketComponent extends DefaultComponent {
    // didn't find a better way to handle that unless we can assume the
    // CamelContext is in the ServletContext
    private static final Map<String, ContextBag> SERVER_CONTAINERS = new ConcurrentHashMap<>();

    @Metadata(label = "sessionCount")
    protected int sessionCount;

    @Override
    protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters) {
        return new JSR356Endpoint(this, uri);
    }

    public static void sendMessage(final Session session, final Object message) throws IOException {
        final RemoteEndpoint.Basic basicRemote = session.getBasicRemote(); // todo:
                                                                           // handle
                                                                           // async?
        synchronized (session) {
            if (String.class.isInstance(message)) {
                basicRemote.sendText(String.valueOf(message));
            } else if (ByteBuffer.class.isInstance(message)) {
                basicRemote.sendBinary(ByteBuffer.class.cast(message));
            } else if (InputStream.class.isInstance(message)) {
                IOHelper.copy(InputStream.class.cast(message), basicRemote.getSendStream());
            } else {
                throw new IllegalArgumentException("Unsupported input: " + message);
            }
        }
    }

    public static void registerServer(final String contextPath, final ServerContainer container) {
        SERVER_CONTAINERS.put(contextPath, new ContextBag(container));
    }

    public static void unregisterServer(final String contextPath) {
        SERVER_CONTAINERS.remove(contextPath);
    }

    public static ContextBag getContext(final String context) {
        return ofNullable(context).map(SERVER_CONTAINERS::get)
            .orElseGet(() -> SERVER_CONTAINERS.size() == 1 ? SERVER_CONTAINERS.values().iterator().next() : SERVER_CONTAINERS.get(""));
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
