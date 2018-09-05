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
package org.apache.camel.component.undertow.handlers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.MimeMappings;
import io.undertow.util.StatusCodes;

import org.apache.camel.component.undertow.ExchangeHeaders;

/**
 * A HttpHandler build a mapping between HTTP methods and handlers and dispatch requests along the map.
 */
public class CamelMethodHandler implements HttpHandler {
    /**
     * A key to use for handlers with no method specified
     */
    private static final String DEFAULT_HANDLER_KEY = "";
    private static final String[] DEFAULT_METHODS;
    static {
        DEFAULT_METHODS = new String[] {DEFAULT_HANDLER_KEY};
    }

    private final Map<String, MethodEntry> methodMap = new ConcurrentHashMap<>();
    private String handlerString;

    CamelMethodHandler() {
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        HttpHandler handler = null;
        /* No need to lock methodMap for read access in this method */
        MethodEntry entry = methodMap.get(exchange.getRequestMethod().toString());
        if (entry != null && (handler = entry.handler) != null) {
            handler.handleRequest(exchange);
        } else {
            entry = methodMap.get(DEFAULT_HANDLER_KEY);
            if (entry != null && (handler = entry.handler) != null) {
                handler.handleRequest(exchange);
            } else {
                exchange.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
                exchange.getResponseHeaders().put(ExchangeHeaders.CONTENT_TYPE, MimeMappings.DEFAULT_MIME_MAPPINGS.get("txt"));
                exchange.getResponseHeaders().put(ExchangeHeaders.CONTENT_LENGTH, 0);
                exchange.endExchange();
            }
        }
    }

    public HttpHandler add(String methods, HttpHandler handler) {
        HttpHandler result = null;
        synchronized (methodMap) { // we lock on methodMap to get a reliable sum of refCounts in remove(String)
            for (String method : splitMethods(methods)) {
                MethodEntry en = methodMap.computeIfAbsent(method, m -> new MethodEntry());
                result = en.addRef(handler, method);
            }
        }
        handlerString = null;
        return result;
    }


    public boolean remove(String methods) {
        boolean result;
        synchronized (methodMap) { // we lock on methodMap to get a reliable sum of refCounts
            for (String method : splitMethods(methods)) {
                final MethodEntry en = methodMap.get(method);
                if (en != null) {
                    en.removeRef();
                }
            }
            result = methodMap.values().stream().mapToInt(en -> en.refCount).sum() == 0;
        }
        handlerString = null;
        return result;
    }

    public String toString() {
        if (handlerString == null) {
            handlerString = "CamelMethodHandler[" + methodMap + "]";
        }
        return handlerString;
    }

    private String[] splitMethods(String methods) {
        String[] result = methods != null ? methods.split(",") : DEFAULT_METHODS;
        return result.length == 0 ? DEFAULT_METHODS : result;
    }

    static class MethodEntry {

        /**
         * The number of references pointing to {@link #handler}
         */
        private int refCount;
        private HttpHandler handler;

        MethodEntry() {
        }

        public HttpHandler addRef(HttpHandler handler, String method) {
            if (this.handler == null) {
                this.handler = handler;
                refCount++;
                return handler;
            } else if ("OPTIONS".equals(method) || CamelWebSocketHandler.class == this.handler.getClass() && CamelWebSocketHandler.class == handler.getClass()) {
                refCount++;
                return this.handler;
            } else {
                throw new IllegalArgumentException(String.format(
                        "Duplicate handler for %s method: '%s', '%s'", method, this.handler, handler));
            }
        }

        public void removeRef() {
            if (--refCount == 0) {
                this.handler = null;
            }
        }

        @Override
        public String toString() {
            return handler == null ? "null" : handler.toString();
        }

    }
}
