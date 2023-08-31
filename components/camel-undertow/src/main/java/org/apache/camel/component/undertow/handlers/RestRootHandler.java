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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.apache.camel.component.undertow.UndertowConsumer;
import org.apache.camel.support.RestConsumerContextPathMatcher;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

/**
 * Root handler for Camel rest-dsl.
 *
 * @see CamelRootHandler
 */
public class RestRootHandler implements HttpHandler {

    private static final List<String> METHODS
            = Arrays.asList("GET", "HEAD", "POST", "PUT", "DELETE", "TRACE", "OPTIONS", "CONNECT", "PATCH");

    private final Set<UndertowConsumer> consumers = new CopyOnWriteArraySet<>();

    //private int port; // unread field
    private String token;
    private int len;

    /**
     * Initializes this handler with the given port.
     */
    public void init(int port) {
        //this.port = port;
        this.token = ":" + port;
        this.len = token.length();
    }

    /**
     * Adds the given consumer.
     */
    public void addConsumer(UndertowConsumer consumer) {
        consumers.add(consumer);
    }

    /**
     * Removes the given consumer
     */
    public void removeConsumer(UndertowConsumer consumer) {
        consumers.remove(consumer);
    }

    /**
     * Number of active consumers
     */
    public int consumers() {
        return consumers.size();
    }

    @Override
    public void handleRequest(HttpServerExchange httpServerExchange) throws Exception {
        String method = httpServerExchange.getRequestMethod().toString();

        HttpHandler handler = getHandler(httpServerExchange, method);
        if (handler != null) {
            handler.handleRequest(httpServerExchange);
        } else {
            // okay we cannot process this requires so return either 404 or 405.
            // to know if its 405 then we need to check if any other HTTP method would have a consumer for the "same" request
            boolean hasAnyMethod = METHODS.stream().anyMatch(m -> isHttpMethodAllowed(httpServerExchange, m));
            if (hasAnyMethod) {
                //method match error, return 405
                httpServerExchange.setStatusCode(405);
                httpServerExchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                httpServerExchange.getResponseSender().send("Method not allowed");
            } else {
                // this resource is not found, return 404
                httpServerExchange.setStatusCode(404);
                httpServerExchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                httpServerExchange.getResponseSender().send("No matching path found");
            }
        }
    }

    private HttpHandler getHandler(HttpServerExchange httpServerExchange, String method) {
        HttpHandler answer = null;

        // need to strip out host and port etc, as we only need the context-path for matching
        if (method == null) {
            return null;
        }

        String path = httpServerExchange.getRequestURI();
        int idx = path.indexOf(token);
        if (idx > -1) {
            path = path.substring(idx + len);
        }
        // use the path as key to find the consumer handler to use
        path = pathAsKey(path);

        List<RestConsumerContextPathMatcher.ConsumerPath<UndertowConsumer>> paths = new ArrayList<>();
        for (final UndertowConsumer consumer : consumers) {
            paths.add(new RestConsumerPath(consumer));
        }

        RestConsumerContextPathMatcher.ConsumerPath<UndertowConsumer> best
                = RestConsumerContextPathMatcher.matchBestPath(method, path, paths);
        if (best != null) {
            answer = best.getConsumer();
        }

        // fallback to regular matching
        List<UndertowConsumer> candidates = new ArrayList<>();
        if (answer == null) {
            for (final UndertowConsumer consumer : consumers) {

                String consumerPath = consumer.getEndpoint().getHttpURI().getPath();
                boolean matchOnUriPrefix = consumer.getEndpoint().isMatchOnUriPrefix();
                // Just make sure that we get the right consumer path first
                if (RestConsumerContextPathMatcher.matchPath(path, consumerPath, matchOnUriPrefix)) {
                    candidates.add(consumer);
                }
            }
        }

        // extra filter by restrict
        candidates = candidates.stream().filter(c -> matchRestMethod(method, c.getEndpoint().getHttpMethodRestrict()))
                .collect(Collectors.toList());
        if (candidates.size() == 1) {
            answer = candidates.get(0);
        }

        return answer;
    }

    private static String pathAsKey(String path) {
        // cater for default path
        if (path == null || path.equals("/")) {
            path = "";
        }

        // strip out query parameters
        path = StringHelper.before(path, "?", path);

        // strip of ending /
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return UnsafeUriCharactersEncoder.encodeHttpURI(path);
    }

    private static boolean matchRestMethod(String method, String restrict) {
        return restrict == null || restrict.toLowerCase(Locale.ENGLISH).contains(method.toLowerCase(Locale.ENGLISH));
    }

    private boolean isHttpMethodAllowed(HttpServerExchange httpServerExchange, String method) {
        return getHandler(httpServerExchange, method) != null;
    }

}
