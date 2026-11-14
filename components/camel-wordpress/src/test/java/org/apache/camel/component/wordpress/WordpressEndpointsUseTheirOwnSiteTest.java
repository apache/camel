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
package org.apache.camel.component.wordpress;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.wordpress.api.model.Content;
import org.apache.camel.component.wordpress.api.model.Post;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

/**
 * Every WordPress endpoint talks to the site of its own url option, with its own user and password, whatever the order
 * in which the endpoints and their producers and consumers are created.
 */
public class WordpressEndpointsUseTheirOwnSiteTest extends CamelTestSupport {

    private static final String NO_USER = "none";

    private static Site siteA;
    private static Site siteB;

    @BeforeAll
    public static void startSites() throws IOException {
        siteA = Site.start();
        siteB = Site.start();
    }

    @AfterAll
    public static void stopSites() {
        siteA.stop();
        siteB.stop();
    }

    @BeforeEach
    public void clearRequests() {
        siteA.requests.clear();
        siteB.requests.clear();
    }

    @Test
    public void testProducersUseTheirOwnSite() {
        final String uriA = "wordpress:post?url=" + siteA.url() + "&user=alice&password=secretA";
        final String uriB = "wordpress:post?url=" + siteB.url() + "&user=bob&password=secretB";
        context.getEndpoint(uriA);
        context.getEndpoint(uriB);

        template.requestBody(uriA, newPost("from A"));
        template.requestBody(uriB, newPost("from B"));

        assertThat(siteA.requests, contains("POST /wp/v2/posts alice"));
        assertThat(siteB.requests, contains("POST /wp/v2/posts bob"));
    }

    @Test
    public void testProducersUseTheirOwnSiteInReverseCreationOrder() {
        final String uriA = "wordpress:post?url=" + siteA.url() + "&user=alice&password=secretA";
        final String uriB = "wordpress:post?url=" + siteB.url() + "&user=bob&password=secretB";
        context.getEndpoint(uriB);
        context.getEndpoint(uriA);

        template.requestBody(uriA, newPost("from A"));
        template.requestBody(uriB, newPost("from B"));

        assertThat(siteA.requests, contains("POST /wp/v2/posts alice"));
        assertThat(siteB.requests, contains("POST /wp/v2/posts bob"));
    }

    @Test
    public void testConsumerWithoutUserSendsNoCredentials() throws Exception {
        // one site: a consumer without user next to a producer with a user, the consumer endpoint created first
        final String readUri = "wordpress:post?url=" + siteA.url() + "&id=1";
        final String writeUri = "wordpress:post?url=" + siteA.url() + "&user=alice&password=secretA";
        context.getEndpoint(readUri);
        context.getEndpoint(writeUri);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(readUri).to("mock:read");
            }
        });
        MockEndpoint mock = getMockEndpoint("mock:read");
        mock.expectedMinimumMessageCount(1);
        mock.allMessages().body().isInstanceOf(Post.class);
        MockEndpoint.assertIsSatisfied(context);

        template.requestBody(writeUri, newPost("from A"));

        final List<String> reads = siteA.requests.stream().filter(r -> r.startsWith("GET")).toList();
        assertThat(reads, everyItem(is("GET /wp/v2/posts/1 " + NO_USER)));
        assertThat(siteA.requests, hasItem("POST /wp/v2/posts alice"));
        assertThat(siteB.requests.isEmpty(), is(true));
    }

    private static Post newPost(String title) {
        final Post post = new Post();
        post.setAuthor(2);
        post.setTitle(new Content(title));
        return post;
    }

    /**
     * A local WordPress site that answers posts requests and records each one as "METHOD path user".
     */
    private static final class Site implements HttpRequestHandler {

        private final List<String> requests = new CopyOnWriteArrayList<>();
        private final Map<String, String> responses;
        private HttpServer server;

        private Site(Map<String, String> responses) {
            this.responses = responses;
        }

        static Site start() throws IOException {
            final Site site = new Site(
                    Map.of(
                            "GET /wp/v2/posts", "/data/posts/list.json",
                            "POST /wp/v2/posts", "/data/posts/create.json",
                            "GET /wp/v2/posts/1", "/data/posts/single.json"));
            site.server = ServerBootstrap.bootstrap().setCanonicalHostName("localhost").setListenerPort(0)
                    .register("/wp/v2/posts", site)
                    .register("/wp/v2/posts/*", site)
                    .create();
            site.server.start();
            return site;
        }

        String url() {
            return "http://localhost:" + server.getLocalPort();
        }

        void stop() {
            if (server != null) {
                server.stop();
            }
        }

        @Override
        public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
                throws IOException {
            final String path = request.getPath().split("\\?", 2)[0];
            final String key = request.getMethod() + " " + path;
            requests.add(key + " " + user(request));
            if (request.getEntity() != null) {
                EntityUtils.consume(request.getEntity());
            }
            final String resource = responses.get(key);
            if (resource == null) {
                response.setCode(HttpStatus.SC_NOT_FOUND);
                return;
            }
            try (InputStream is = WordpressEndpointsUseTheirOwnSiteTest.class.getResourceAsStream(resource)) {
                response.setCode(HttpStatus.SC_OK);
                response.setEntity(new StringEntity(
                        new String(is.readAllBytes(), StandardCharsets.UTF_8), ContentType.APPLICATION_JSON));
            }
        }

        private static String user(ClassicHttpRequest request) {
            final Header header = request.getFirstHeader("Authorization");
            if (header == null) {
                return NO_USER;
            }
            final String encoded = header.getValue().substring("Basic".length()).trim();
            final String credentials = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            return credentials.split(":", 2)[0];
        }
    }
}
