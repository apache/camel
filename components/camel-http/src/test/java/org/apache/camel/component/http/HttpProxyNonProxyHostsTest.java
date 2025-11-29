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
package org.apache.camel.component.http;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestConnControl;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestExpectContinue;
import org.apache.hc.core5.http.protocol.ResponseConnControl;
import org.apache.hc.core5.http.protocol.ResponseContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test that verifies HTTP requests can use a proxy and honor the http.nonProxyHosts system property.
 */
public class HttpProxyNonProxyHostsTest extends BaseHttpTest {

    private HttpServer proxy;
    private HttpServer targetServer;
    private final AtomicInteger proxyRequestCount = new AtomicInteger(0);
    private final AtomicInteger targetRequestCount = new AtomicInteger(0);
    private String originalNonProxyHosts;

    public HttpProxyNonProxyHostsTest() throws UnknownHostException {
    }

    @BeforeEach
    @Override
    public void setupResources() throws Exception {
        // Target Server
        HttpRequestHandler targetHandler = new TargetServerHandler();
        targetServer = ServerBootstrap.bootstrap()
                .setHttpProcessor(getBasicHttpProcessor())
                .setRequestRouter((request, context) -> targetHandler)
                .create();
        targetServer.start();

        // Set up the proxy server
        HttpRequestHandler proxyHandler = new ProxyServerHandler();
        proxy = ServerBootstrap.bootstrap()
                .setHttpProcessor(getBasicHttpProcessor())
                .setRequestRouter((request, context) -> proxyHandler) // <--- THE FIX
                .create();
        proxy.start();
    }

    @AfterEach
    @Override
    public void cleanupResources() throws Exception {
        if (proxy != null) {
            proxy.stop();
        }
        if (targetServer != null) {
            targetServer.stop();
        }
    }

    @Override
    protected HttpProcessor getBasicHttpProcessor() {
        List<HttpRequestInterceptor> requestInterceptors = new ArrayList<>();
        requestInterceptors.add(new RequestContent());       // Handles Content-Length/Transfer-Encoding
        requestInterceptors.add(new RequestConnControl());   // Handles Connection: Keep-Alive
        requestInterceptors.add(new RequestExpectContinue()); // Handles Expect: 100-continue

        List<HttpResponseInterceptor> responseInterceptors = new ArrayList<>();
        responseInterceptors.add(new ResponseContent());     // Handles outgoing entity headers
        responseInterceptors.add(new ResponseConnControl()); // Handles outgoing connection headers

        return new DefaultHttpProcessor(requestInterceptors, responseInterceptors);
    }

    @Test
    public void testHttpRequestUsesProxyWhenConfigured() throws Exception {
        proxyRequestCount.set(0);
        targetRequestCount.set(0);

        // Make a request through the proxy to localhost
        Exchange exchange = template.request(
                "http://localhost:" + targetServer.getLocalPort() + "/test"
                                             + "?proxyHost=127.0.0.1"
                                             + "&proxyPort=" + proxy.getLocalPort(),
                exchange1 -> {
                });

        assertExchange(exchange);

        // When using proxy, the request goes to proxy which forwards to target
        // In this test setup, proxy serves the response directly
        assertTrue(proxyRequestCount.get() >= 1, "Request should have gone through proxy");
    }

    @Test
    public void testNonProxyHostsBypassesProxyForLocalhost() throws Exception {
        proxyRequestCount.set(0);
        targetRequestCount.set(0);

        // Make a request with proxy configured but target is localhost (should bypass proxy)
        Exchange exchange = template.request(
                "http://localhost:" + targetServer.getLocalPort() + "/test"
                                             + "?proxyHost=127.0.0.1"
                                             + "&proxyPort=" + proxy.getLocalPort()
                                             + "&nonProxyHosts=localhost",
                exchange1 -> {
                });

        assertExchange(exchange);

        // Verify the request bypassed the proxy and went directly to target
        assertEquals(0, proxyRequestCount.get(), "Request should NOT have gone through proxy due to nonProxyHosts");
        assertEquals(1, targetRequestCount.get(), "Request should have reached target server directly");
    }

    @Test
    public void testNonProxyHostsWithCommaSeparatedList() throws Exception {
        proxyRequestCount.set(0);
        targetRequestCount.set(0);

        // Make a request to localhost with proxy configured (should bypass proxy)
        Exchange exchange = template.request(
                "http://localhost:" + targetServer.getLocalPort() + "/test"
                                             + "?proxyHost=127.0.0.1&proxyPort=" + proxy.getLocalPort()
                                             + "&nonProxyHosts=xample.com,localhost,*.internal",
                exchange1 -> {
                });

        assertExchange(exchange);

        // Verify the request bypassed the proxy
        assertEquals(0, proxyRequestCount.get(), "Request should NOT have gone through proxy");
        assertEquals(1, targetRequestCount.get(), "Request should have reached target server directly");
    }

    /**
     * Handler for the proxy server that tracks and responds to requests
     */
    private class ProxyServerHandler implements HttpRequestHandler {
        @Override
        public void handle(
                ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
                throws HttpException, IOException {
            proxyRequestCount.incrementAndGet();

            // Proxy serves the response (in a real proxy it would forward, but for testing we respond directly)
            response.setCode(200);
            response.setEntity(new StringEntity(getExpectedContent()));
        }
    }

    /**
     * Handler for the target server that tracks and responds to requests
     */
    private class TargetServerHandler implements HttpRequestHandler {
        @Override
        public void handle(
                ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
                throws HttpException, IOException {
            targetRequestCount.incrementAndGet();

            response.setCode(200);
            response.setEntity(new StringEntity(getExpectedContent()));
        }
    }
}
