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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.SSLContext;

import io.undertow.Undertow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to hold Undertow instances during runtime.
 * One of the benefits is reuse of same TCP port for more endpoints.
 */
public class UndertowRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(UndertowRegistry.class);

    int port;
    SSLContext sslContext;
    String host;
    Undertow server;
    Map<URI, UndertowConsumer> consumersRegistry = new HashMap<URI, UndertowConsumer>();

    public UndertowRegistry(UndertowConsumer consumer, int port) {
        registerConsumer(consumer);
        this.port = port;
        if (consumer.getEndpoint().getSslContext() != null) {
            sslContext = consumer.getEndpoint().getSslContext();
        }
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void registerConsumer(UndertowConsumer consumer) {
        URI httpUri = consumer.getEndpoint().getHttpURI();
        if (host != null && !host.equals(httpUri.getHost())) {
            throw new IllegalArgumentException("Cannot register UndertowConsumer on different host and same port: {}" + host + " " + httpUri.getHost());
        } else {
            host = httpUri.getHost();
        }
        LOG.info("Adding consumer to consumerRegistry: {}", httpUri);
        consumersRegistry.put(httpUri, consumer);
        if (sslContext != null && consumer.getEndpoint().getSslContext() != null) {
            throw new IllegalArgumentException("Cannot register UndertowConsumer with different SSL config");
        }

    }

    public void unregisterConsumer(UndertowConsumer consumer) {
        URI httpUri = consumer.getEndpoint().getHttpURI();
        if (consumersRegistry.containsKey(httpUri)) {
            consumersRegistry.remove(httpUri);
        } else {
            LOG.debug("Cannot unregister consumer {} as it was not registered", consumer);
        }
    }

    public boolean isEmpty() {
        return consumersRegistry.isEmpty();
    }

    public Undertow getServer() {
        return server;
    }

    public void setServer(Undertow server) {
        this.server = server;
    }

    public Map<URI, UndertowConsumer> getConsumersRegistry() {
        return consumersRegistry;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public void setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }
}
