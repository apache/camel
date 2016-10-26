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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.PathTemplateHandler;
import io.undertow.util.PathTemplate;
import org.apache.camel.component.undertow.handlers.CamelRootHandler;
import org.apache.camel.component.undertow.handlers.NotFoundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default UndertowHost which manages standalone Undertow server.
 */
public class DefaultUndertowHost implements UndertowHost {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultUndertowHost.class);

    private UndertowHostKey key;
    private CamelRootHandler rootHandler;
    private Undertow undertow;
    private String hostString;

    public DefaultUndertowHost(UndertowHostKey key) {
        this.key = key;
        rootHandler = new CamelRootHandler(new NotFoundHandler());
    }

    @Override
    public void validateEndpointURI(URI httpURI) {
        // all URIs are good
    }

    @Override
    public synchronized void registerHandler(HttpHandlerRegistrationInfo registrationInfo, HttpHandler handler) {
        if (undertow == null) {
            Undertow.Builder builder = Undertow.builder();
            if (key.getSslContext() != null) {
                builder.addHttpsListener(key.getPort(), key.getHost(), key.getSslContext());
            } else {
                builder.addHttpListener(key.getPort(), key.getHost());
            }

            undertow = builder.setHandler(rootHandler).build();
            LOG.info("Starting Undertow server on {}://{}:{}", key.getSslContext() != null ? "https" : "http", key.getHost(), key.getPort());
            undertow.start();
        }

        String path = registrationInfo.getUri().getPath();
        String methods = registrationInfo.getMethodRestrict();
        boolean prefixMatch = registrationInfo.isMatchOnUriPrefix();
        rootHandler.add(path, methods != null ? methods.split(",") : null, prefixMatch, handler);
    }

    @Override
    public synchronized void unregisterHandler(HttpHandlerRegistrationInfo registrationInfo) {
        if (undertow == null) {
            return;
        }

        String path = registrationInfo.getUri().getPath();
        String methods = registrationInfo.getMethodRestrict();
        boolean prefixMatch = registrationInfo.isMatchOnUriPrefix();
        rootHandler.remove(path, methods != null ? methods.split(",") : null, prefixMatch);

        if (rootHandler.isEmpty()) {
            LOG.info("Stopping Undertow server on {}://{}:{}", key.getSslContext() != null ? "https" : "http", key.getHost(), key.getPort());
            undertow.stop();
            undertow = null;
        }
    }

    public String toString() {
        if (hostString == null) {
            hostString = String.format("DefaultUndertowHost[%s://%s:%s]", key.getSslContext() != null ? "https" : "http", key.getHost(), key.getPort());
        }
        return hostString;
    }
}