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
package org.apache.camel.component.graphql;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.component.http.BaseHttpTest;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verify the 'AuthClientConfigurer' 'proxyHost' configuration.
 */
public class GraphqlProxyNonProxyHostsTest extends BaseHttpTest {

    private final AtomicInteger proxyRequestCount = new AtomicInteger(0);
    private HttpServer proxy;

    @BeforeEach
    @Override
    public void setupResources() throws Exception {
        proxy = ServerBootstrap.bootstrap()
                .setHttpProcessor(getBasicHttpProcessor())
                .setRequestRouter((request, context) -> new ProxyServerHandler())
                .create();
        proxy.start();
    }

    @AfterEach
    @Override
    public void cleanupResources() {
        if (proxy != null) {
            proxy.stop();
        }
    }

    @Test
    public void usesProxyWhenConfigured() {
        proxyRequestCount.set(0);

        Exchange exchange = template.request(
                // placeholder localhost port since proxy returns a dummy response
                "graphql://http://localhost:8080"
                                             + "/graphql?query={books{id name}}"
                                             + "&proxyHost=127.0.0.1:" + proxy.getLocalPort(),
                exchange1 -> {
                });

        assertExchange(exchange);
        assertEquals(1, proxyRequestCount.get());
    }

    private class ProxyServerHandler implements HttpRequestHandler {
        @Override
        public void handle(
                ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) {
            proxyRequestCount.incrementAndGet();
            response.setCode(HttpStatus.SC_SUCCESS);
            response.setEntity(new StringEntity(getExpectedContent()));
        }
    }
}
