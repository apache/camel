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
package org.apache.camel.component.wordpress.api.service.impl;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.component.wordpress.api.model.Post;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

/**
 * The JSON sent to the API through the provider of the JAX-RS client.
 */
public class WordpressServiceAdapterJsonTest {

    private HttpServer server;
    private volatile String requestBody;

    @BeforeEach
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] body = "{\"id\":7,\"date\":\"2016-10-30T14:22:05\",\"unknown\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void writesDatesAsNumbersAndReadsThePost() {
        WordpressServicePostsAdapter adapter
                = new WordpressServicePostsAdapter("http://localhost:" + server.getAddress().getPort(), "2");
        Post post = new Post();
        post.setDate(new Date(1000L));

        Post created = adapter.create(post);

        assertThat(requestBody, containsString("\"date\":1000"));
        assertThat(created.getId(), is(7));
    }
}
