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

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProxyProtocolTest {

    private final DefaultCamelContext context = new DefaultCamelContext();

    private final int port = AvailablePortFinder.getNextAvailable();

    public ProxyProtocolTest() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                final int originPort = AvailablePortFinder.getNextAvailable();

                // proxy from http://localhost:port to
                // http://localhost:originPort/path
                from("netty-http:proxy://localhost:" + port)
                    .to("netty-http:http://localhost:" + originPort)
                    .process(e -> e.getMessage().setBody(e.getMessage().getBody(String.class).toUpperCase(Locale.US)));

                // origin service that serves `"origin server"` on
                // http://localhost:originPort/path
                from("netty-http:http://localhost:" + originPort + "/path").setBody()
                    .constant("origin server");
            }
        });
        context.start();
    }

    @Test
    public void shouldProvideProxyProtocolSupport() {
        final NettyHttpEndpoint endpoint = context.getEndpoint("netty-http:proxy://localhost", NettyHttpEndpoint.class);

        assertThat(endpoint.getConfiguration().isHttpProxy()).isTrue();
    }

    @Test
    public void shouldServeAsHttpProxy() throws Exception {
        final Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", port));

        // request for http://test/path will be proxied by http://localhost:port
        // and diverted to http://localhost:originPort/path
        final HttpURLConnection connection = (HttpURLConnection) new URL("http://test/path").openConnection(proxy);

        try (InputStream stream = connection.getInputStream()) {
            assertThat(IOUtils.readLines(stream, StandardCharsets.UTF_8)).containsOnly("ORIGIN SERVER");
        }
    }

    @After
    public void shutdownCamel() throws Exception {
        context.stop();
    }
}
