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
package org.apache.camel.component.netty.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.ResourceLeakDetector;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("TODO: https://issues.apache.org/jira/projects/CAMEL/issues/CAMEL-16718")
// this test was working before due to a netty ref count exception was ignored (seems we attempt to write 2 times)
// now this real caused exception is detected by Camel
public class ProxyProtocolTest {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyProtocolTest.class);

    private static final int ORIGIN_PORT = AvailablePortFinder.getNextAvailable();

    private static final int PROXY_PORT = AvailablePortFinder.getNextAvailable();

    private DefaultCamelContext context;

    private String url;

    public void createContext(final Function<RouteBuilder, RouteDefinition> variant, final String url) throws Exception {
        this.url = url;
        context = new DefaultCamelContext();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {

                // route variation that proxies from http://localhost:port to
                // http://localhost:originPort/path
                variant.apply(this);

                // origin service that serves `"origin server"` on
                // http://localhost:originPort/path
                from("netty-http:http://localhost:" + ORIGIN_PORT + "/path")
                        .process(ProxyProtocolTest::origin);
            }
        });
        context.start();
    }

    @ParameterizedTest
    @MethodSource("routeOptions")
    public void shouldProvideProxyProtocolSupport(Function<RouteBuilder, RouteDefinition> variant, String url)
            throws Exception {
        createContext(variant, url);

        final NettyHttpEndpoint endpoint = context.getEndpoint("netty-http:proxy://localhost", NettyHttpEndpoint.class);

        assertThat(endpoint.getConfiguration().isHttpProxy()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("routeOptions")
    public void shouldServeAsHttpProxy(Function<RouteBuilder, RouteDefinition> variant, String url) throws Exception {
        createContext(variant, url);

        // request for http://test/path will be proxied by http://localhost:port
        // and diverted to http://localhost:originPort/path
        try (InputStream stream = request(url)) {
            assertThat(IOUtils.readLines(stream, StandardCharsets.UTF_8)).containsOnly("ORIGIN SERVER");
        }
    }

    @ParameterizedTest
    @MethodSource("routeOptions")
    public void shouldSupportPostingFormEncodedPayloads(Function<RouteBuilder, RouteDefinition> variant, String url)
            throws Exception {
        createContext(variant, url);

        try (InputStream stream = request(url, "hello=world", NettyHttpConstants.CONTENT_TYPE_WWW_FORM_URLENCODED)) {
            assertThat(IOUtils.readLines(stream, StandardCharsets.UTF_8)).containsOnly("ORIGIN SERVER: HELLO=WORLD");
        }
    }

    @ParameterizedTest
    @MethodSource("routeOptions")
    public void shouldSupportPostingPlaintextPayloads(Function<RouteBuilder, RouteDefinition> variant, String url)
            throws Exception {
        createContext(variant, url);

        try (InputStream stream = request(url, "hello", "text/plain")) {
            assertThat(IOUtils.readLines(stream, StandardCharsets.UTF_8)).containsOnly("ORIGIN SERVER: HELLO");
        }
    }

    @ParameterizedTest
    @MethodSource("routeOptions")
    public void shouldSupportQueryParameters(Function<RouteBuilder, RouteDefinition> variant, String url) throws Exception {
        createContext(variant, url);

        // request for http://test/path?q=... will be proxied by
        // http://localhost:port
        // and diverted to http://localhost:originPort/path?q=...
        try (InputStream stream = request(url + "?q=hello")) {
            assertThat(IOUtils.readLines(stream, StandardCharsets.UTF_8)).containsOnly("ORIGIN SERVER: HELLO");
        }
    }

    @AfterEach
    public void shutdownCamel() throws Exception {
        final ShutdownStrategy shutdownStrategy = context.getShutdownStrategy();
        shutdownStrategy.setTimeout(100);
        shutdownStrategy.setTimeUnit(TimeUnit.MILLISECONDS);
        shutdownStrategy.shutdownForced(context, context.getCamelContextExtension().getRouteStartupOrder());

        context.stop();
    }

    public static Iterable<Object[]> routeOptions() {
        final Function<RouteBuilder, RouteDefinition> single = r -> r.from("netty-http:proxy://localhost:" + PROXY_PORT)
                .process(ProxyProtocolTest::uppercase)
                .to("netty-http:http://localhost:" + ORIGIN_PORT)
                .process(ProxyProtocolTest::uppercase);

        final Function<RouteBuilder, RouteDefinition> dynamicPath = r -> r.from("netty-http:proxy://localhost:" + PROXY_PORT)
                .process(ProxyProtocolTest::uppercase)
                .toD("netty-http:http://localhost:" + ORIGIN_PORT + "/${headers." + Exchange.HTTP_PATH + "}")
                .process(ProxyProtocolTest::uppercase);

        final Function<RouteBuilder, RouteDefinition> dynamicUrl = r -> r.from("netty-http:proxy://localhost:" + PROXY_PORT)
                .process(ProxyProtocolTest::uppercase)
                .toD("netty-http:"
                     + "${headers." + Exchange.HTTP_SCHEME + "}://"
                     + "${headers." + Exchange.HTTP_HOST + "}:"
                     + "${headers." + Exchange.HTTP_PORT + "}/"
                     + "${headers." + Exchange.HTTP_PATH + "}")
                .process(ProxyProtocolTest::uppercase);

        return Arrays.asList(
                new Object[] { single, "http://test/path" },
                new Object[] { dynamicPath, "http://test/path" },
                new Object[] { dynamicUrl, "http://localhost:" + ORIGIN_PORT + "/path" });
    }

    @BeforeAll
    public static void startLeakDetection() {
        System.setProperty("io.netty.leakDetection.maxRecords", "100");
        System.setProperty("io.netty.leakDetection.acquireAndReleaseOnly", "true");
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
    }

    @AfterAll
    public static void verifyNoLeaks() {
        // Force GC to bring up leaks
        System.gc();
        // Kick leak detection logging
        ByteBufAllocator.DEFAULT.buffer(1).release();
        final Collection<LogEvent> events = LogCaptureAppender.getEvents();
        if (!events.isEmpty()) {
            final String message = "Leaks detected while running tests: " + events;
            // Just write the message into log to help debug
            for (final LogEvent event : events) {
                LOG.info(event.getMessage().getFormattedMessage());
            }
            LogCaptureAppender.reset();
            throw new AssertionError(message);
        }
    }

    private static void origin(final Exchange exchange) {
        final Message message = exchange.getMessage();

        final String q = message.getHeader("q", String.class);
        final String body = message.getBody(String.class);

        if ("text/plain".equals(message.getHeader(Exchange.CONTENT_TYPE))) {
            // when we send text/plain message we're using the route with
            // uppercase processor before the netty-http producer
            assertThat(body).isUpperCase();
        }

        if (ObjectHelper.isEmpty(q) && ObjectHelper.isEmpty(body)) {
            message.setBody("origin server");
        } else if (ObjectHelper.isEmpty(body)) {
            message.setBody("origin server: " + q);
        } else {
            message.setBody("origin server: " + body);
        }
    }

    private static InputStream request(final String url) throws IOException {
        final Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", PROXY_PORT));

        final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection(proxy);
        // when debugging comment out the following two lines otherwise
        // the test will terminate regardless of the execution being
        // paused at the breakpoint
        connection.setConnectTimeout(1000);
        connection.setReadTimeout(1000);

        return connection.getInputStream();
    }

    private static InputStream request(final String url, final String payload, final String contentType)
            throws IOException {
        final Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", PROXY_PORT));

        final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection(proxy);
        connection.addRequestProperty("Content-Type", contentType);
        connection.setDoOutput(true);
        // when debugging comment out the following two lines otherwise
        // the test will terminate regardless of the execution being
        // paused at the breakpoint
        connection.setConnectTimeout(1000);
        connection.setReadTimeout(1000);

        try (Writer writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.US_ASCII)) {
            writer.write(payload);
        }

        return connection.getInputStream();
    }

    private static void uppercase(final Exchange exchange) {
        final Message message = exchange.getMessage();
        final ByteBuf body = message.getBody(ByteBuf.class);

        if (body.capacity() != 0) {
            // only if we received a payload we'll uppercase it
            message.setBody(body.toString(StandardCharsets.US_ASCII).toUpperCase(Locale.US));
        }
        body.release();
    }
}
