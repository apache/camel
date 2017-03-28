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

import java.util.HashMap;
import java.util.Map;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.MimeMappings;
import io.undertow.util.StatusCodes;
import org.apache.camel.component.undertow.ExchangeHeaders;

/**
 * A HttpHandler build a mapping between HTTP methods and handlers and dispatch requests along the map.
 */
public class CamelMethodHandler implements HttpHandler {
    private Map<String, HttpHandler> methodMap = new HashMap<String, HttpHandler>();
    private HttpHandler defaultHandler;
    private String handlerString;

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        HttpHandler handler = methodMap.get(exchange.getRequestMethod().toString());
        if (handler != null) {
            handler.handleRequest(exchange);
        } else if (defaultHandler != null) {
            defaultHandler.handleRequest(exchange);
        } else {
            exchange.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
            exchange.getResponseHeaders().put(ExchangeHeaders.CONTENT_TYPE, MimeMappings.DEFAULT_MIME_MAPPINGS.get("txt"));
            exchange.getResponseHeaders().put(ExchangeHeaders.CONTENT_LENGTH, 0);
            exchange.endExchange();
        }
    }

    public synchronized void add(String[] methods, HttpHandler handler) {
        Map<String, HttpHandler> adding = new HashMap<String, HttpHandler>();
        for (String method : methods) {
            adding.put(method, handler);
        }
        methodMap.putAll(adding);
        handlerString = null;
    }

    public synchronized void remove(String[] methods) {
        for (String method : methods) {
            methodMap.remove(method);
        }
        handlerString = null;
    }

    public synchronized void addDefault(HttpHandler handler) {
        if (defaultHandler != null) {
            throw new IllegalArgumentException(String.format(
                "Duplicate default handler: '%s', '%s'", defaultHandler, handler));
        }
        defaultHandler = handler;
        handlerString = null;
    }

    public synchronized void removeDefault() {
        defaultHandler = null;
        handlerString = null;
    }

    public boolean isEmpty() {
        return defaultHandler == null && methodMap.isEmpty();
    }

    public String toString() {
        if (handlerString == null) {
            handlerString = "CamelMethodHandler[default=" + defaultHandler + ", " + methodMap + "]";
        }
        return handlerString;
    }
}
