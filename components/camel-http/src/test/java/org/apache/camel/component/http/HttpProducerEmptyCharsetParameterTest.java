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

import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A response with an empty charset parameter in the Content-Type has no charset, so the exchange charset is not set to
 * an empty name and the body is read with the default charset (UTF-8).
 */
class HttpProducerEmptyCharsetParameterTest extends BaseHttpTest {

    private static final String TEXT = "Grüße aus Köln";

    private HttpServer localServer;

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost").setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/text", (request, response, context) -> {
                    response.setHeader(HttpHeaders.CONTENT_TYPE, request.getFirstHeader("X-Response-Content-Type").getValue());
                    response.setEntity(new ByteArrayEntity(TEXT.getBytes(StandardCharsets.UTF_8), null));
                    response.setCode(HttpStatus.SC_OK);
                }).create();
        localServer.start();
    }

    @Override
    public void cleanupResources() throws Exception {
        if (localServer != null) {
            localServer.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "text/plain; charset=", "text/plain; charset=\"\"", "text/plain; charset= ;format=flowed" })
    void responseWithEmptyCharsetParameter(String contentType) {
        Exchange exchange = template.request("http://localhost:" + localServer.getLocalPort() + "/text",
                e -> e.getIn().setHeader("X-Response-Content-Type", contentType));

        assertNull(exchange.getException());
        assertEquals(TEXT, exchange.getMessage().getBody(String.class));
    }
}
