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
package org.apache.camel.component.wordpress.api.test;

import java.io.IOException;
import java.net.BindException;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.wordpress.api.WordpressServiceProvider;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class WordpressMockServerTestSupport {

    protected static HttpServer localServer;
    protected static WordpressServiceProvider serviceProvider;

    private static final Logger LOGGER = LoggerFactory.getLogger(WordpressMockServerTestSupport.class);
    private static final int PORT = 9009;

    public WordpressMockServerTestSupport() {

    }

    @BeforeAll
    public static void setUpMockServer() throws IOException {
        // @formatter:off
        int i = 0;
        while (true) {
            try {
                localServer = createServer(PORT + i);
                localServer.start();
                break;
            } catch (BindException ex) {
                LOGGER.warn("Port {} already in use, trying next one", PORT + i);
                i++;
            }
        }
        serviceProvider = WordpressServiceProvider.getInstance();
        serviceProvider.init(getServerBaseUrl());
        // @formatter:on
        LOGGER.info("Local server up and running on address {} and port {}", localServer.getInetAddress(),
                localServer.getLocalPort());

    }

    private static HttpServer createServer(int port) {
        final Map<String, String> postsListCreateRequestHandlers = new HashMap<>();
        postsListCreateRequestHandlers.put("GET", "/data/posts/list.json");
        postsListCreateRequestHandlers.put("POST", "/data/posts/create.json");

        final Map<String, String> postsSingleUpdateRequestHandlers = new HashMap<>();
        postsSingleUpdateRequestHandlers.put("GET", "/data/posts/single.json");
        postsSingleUpdateRequestHandlers.put("POST", "/data/posts/update.json");
        postsSingleUpdateRequestHandlers.put("DELETE", "/data/posts/delete.json");

        final Map<String, String> usersListCreateRequestHandlers = new HashMap<>();
        usersListCreateRequestHandlers.put("GET", "/data/users/list.json");
        usersListCreateRequestHandlers.put("POST", "/data/users/create.json");

        final Map<String, String> usersSingleUpdateRequestHandlers = new HashMap<>();
        usersSingleUpdateRequestHandlers.put("GET", "/data/users/single.json");
        usersSingleUpdateRequestHandlers.put("POST", "/data/users/update.json");
        usersSingleUpdateRequestHandlers.put("DELETE", "/data/users/delete.json");

        // @formatter:off
        return ServerBootstrap.bootstrap().setListenerPort(port)
                .register("/wp/v2/posts", new WordpressServerHttpRequestHandler(postsListCreateRequestHandlers))
                .register("/wp/v2/posts/*", new WordpressServerHttpRequestHandler(postsSingleUpdateRequestHandlers))
                .register("/wp/v2/users", new WordpressServerHttpRequestHandler(usersListCreateRequestHandlers))
                .register("/wp/v2/users/*", new WordpressServerHttpRequestHandler(usersSingleUpdateRequestHandlers))
                .create();
        // @formatter:on
    }

    @AfterAll
    public static void tearDownMockServer() {
        LOGGER.info("Stopping local server");
        if (localServer != null) {
            localServer.stop();
        }
    }

    public static WordpressServiceProvider getServiceProvider() {
        return serviceProvider;
    }

    public static String getServerBaseUrl() {
        return "http://localhost:" + localServer.getLocalPort();
    }
}
