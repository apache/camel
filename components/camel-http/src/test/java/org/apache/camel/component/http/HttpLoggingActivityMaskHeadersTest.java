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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The values of the HTTP headers that carry a credential are masked in the activity log, also without logMask.
 */
public class HttpLoggingActivityMaskHeadersTest extends BaseHttpTest {

    @BindToRegistry("listener")
    private final CapturingListener listener = new CapturingListener();

    private HttpServer localServer;

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost").setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/secure", (request, response, context) -> {
                    response.setCode(HttpStatus.SC_OK);
                    response.addHeader("Set-Cookie", "SESSIONID=response-session-secret; Path=/");
                    response.addHeader("X-Trace", "trace-123");
                    response.setEntity(new StringEntity(getExpectedContent(), ContentType.TEXT_PLAIN));
                })
                .create();
        localServer.start();
    }

    @Override
    public void cleanupResources() throws Exception {
        if (localServer != null) {
            localServer.stop();
        }
    }

    @BeforeEach
    void clearLog() {
        listener.logged.clear();
    }

    @Test
    public void credentialHeadersAreMasked() {
        Exchange exchange = template.request(
                "http://localhost:" + localServer.getLocalPort()
                                             + "/secure?logHttpActivity=true&httpActivityListener=#listener",
                e -> {
                    e.getIn().setHeader("Authorization", "Bearer secret-token");
                    e.getIn().setHeader("Proxy-Authorization", "Basic proxy-secret");
                    e.getIn().setHeader("Cookie", "SESSIONID=request-session-secret");
                    e.getIn().setHeader("X-Api-Key", "api-key-secret");
                    e.getIn().setHeader("X-Trace", "trace-123");
                });

        assertNull(exchange.getException());
        assertEquals(getExpectedContent(), exchange.getMessage().getBody(String.class));

        String log = String.join(System.lineSeparator(), listener.logged);
        List<String> lines = List.of(log.split("\\R"));
        // the request and the response are both logged
        assertTrue(log.contains("Sending HTTP Request"), log);
        assertTrue(log.contains("Received HTTP Response"), log);

        // the header names are kept, the credentials are masked
        assertTrue(lines.contains("Authorization: xxxxx"), log);
        assertTrue(lines.contains("Proxy-Authorization: xxxxx"), log);
        assertTrue(lines.contains("Cookie: xxxxx"), log);
        assertTrue(lines.contains("X-Api-Key: xxxxx"), log);
        assertTrue(lines.contains("Set-Cookie: xxxxx"), log);
        assertFalse(log.contains("secret-token"), log);
        assertFalse(log.contains("proxy-secret"), log);
        assertFalse(log.contains("request-session-secret"), log);
        assertFalse(log.contains("api-key-secret"), log);
        assertFalse(log.contains("response-session-secret"), log);

        // other headers are logged as is
        assertTrue(lines.contains("X-Trace: trace-123"), log);
    }

    @Test
    public void headersFlaggedSensitiveAreMasked() {
        ServiceHelper.startService(listener);

        BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/secure");
        // HttpClient flags a header as sensitive, such as the Authorization header of its own authentication
        request.addHeader(new BasicHeader("X-Custom-Credential", "Bearer flagged-secret", true));
        request.addHeader(new BasicHeader("X-Trace", "trace-123"));
        listener.onRequestSubmitted(this, new DefaultExchange(context), new HttpHost("localhost"), request, null);

        String log = String.join(System.lineSeparator(), listener.logged);
        List<String> lines = List.of(log.split("\\R"));
        assertTrue(lines.contains("X-Custom-Credential: xxxxx"), log);
        assertFalse(log.contains("flagged-secret"), log);
        assertTrue(lines.contains("X-Trace: trace-123"), log);
    }

    /**
     * Captures what the listener logs, instead of writing it to the logger.
     */
    private static final class CapturingListener extends LoggingHttpActivityListener {

        private final List<String> logged = new CopyOnWriteArrayList<>();

        @Override
        protected void onActivity(
                CamelLogger logger, Exchange exchange, HttpHost host, HttpRequest request, HttpResponse response,
                HttpEntity entity, long elapsed) {
            CamelLogger capturing = new CamelLogger(logger.getLog(), logger.getLevel()) {
                @Override
                public void log(String message) {
                    logged.add(message);
                }
            };
            super.onActivity(capturing, exchange, host, request, response, entity, elapsed);
        }
    }
}
