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
package org.apache.camel.component.undertow.handlers;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathTemplateHandler;

/**
 * Extended PathTemplateHandler to monitor add/remove handlers. Also this enables hot swapping a default handler.
 */
public class CamelPathTemplateHandler implements HttpHandler {
    private final Lock lock = new ReentrantLock();
    private final Map<String, CamelMethodHandler> handlers = new HashMap<>();
    private final Wrapper defaultHandlerWrapper = new Wrapper();
    private final PathTemplateHandler delegate;
    private String handlerString;

    public CamelPathTemplateHandler(CamelMethodHandler defaultHandler) {
        this(defaultHandler, null);
    }

    /**
     * @param defaultHandler  the handler of the static base path, for a request that no template matches
     * @param fallbackHandler the handler for a request that no template matches while no static base path is set (the
     *                        handler of the requests that no path of the server matches), or null to let the empty
     *                        default handler answer 405
     */
    public CamelPathTemplateHandler(CamelMethodHandler defaultHandler, HttpHandler fallbackHandler) {
        defaultHandlerWrapper.set(defaultHandler);
        defaultHandlerWrapper.fallback = fallbackHandler;
        delegate = new PathTemplateHandler(defaultHandlerWrapper);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        delegate.handleRequest(exchange);
    }

    public CamelPathTemplateHandler add(final String uriTemplate, final CamelMethodHandler handler) {
        lock.lock();
        try {
            delegate.add(uriTemplate, handler);
            handlers.put(uriTemplate, handler);
            handlerString = null;
            return this;
        } finally {
            lock.unlock();
        }
    }

    public CamelPathTemplateHandler remove(final String uriTemplate) {
        lock.lock();
        try {
            delegate.remove(uriTemplate);
            handlers.remove(uriTemplate);
            handlerString = null;
            return this;
        } finally {
            lock.unlock();
        }
    }

    public CamelMethodHandler get(final String uriTemplate) {
        return handlers.get(uriTemplate);
    }

    public boolean isEmpty() {
        return handlers.isEmpty();
    }

    public CamelMethodHandler getDefault() {
        lock.lock();
        try {
            return this.defaultHandlerWrapper.get();
        } finally {
            lock.unlock();
        }
    }

    public void setDefault(final CamelMethodHandler defaultHandler) {
        lock.lock();
        try {
            this.defaultHandlerWrapper.set(defaultHandler);
            handlerString = null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        if (handlerString == null) {
            handlerString = "CamelPathTemplateHandler[default=" + defaultHandlerWrapper.get() + ", " + handlers + "]";
        }
        return handlerString;
    }

    static class Wrapper implements HttpHandler {
        private CamelMethodHandler handler;
        private HttpHandler fallback;

        public void set(CamelMethodHandler handler) {
            this.handler = handler;
        }

        public CamelMethodHandler get() {
            return this.handler;
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            CamelMethodHandler current = handler;
            HttpHandler other = fallback;
            if (other != null && current.isEmpty()) {
                // no template and no static path of this base path matches: the request is not for this base path,
                // so it goes on as if the base path was not there (for example to the rest-dsl consumers of the port)
                other.handleRequest(exchange);
            } else {
                current.handleRequest(exchange);
            }
        }
    }
}
