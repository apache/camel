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
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 *
 */
public class HttpProducerTwoHeadersWithSameKeyTest extends BaseHttpTest {

    private HttpServer localServer;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/myapp", (request, response, context) -> {
                    Header[] from = request.getHeaders("from");
                    assertEquals("me", from[0].getValue());
                    Header[] to = request.getHeaders("to");
                    assertEquals("[foo, bar]", to[0].getValue());

                    response.setHeader("bar", "yes");
                    response.addHeader("foo", "123");
                    response.addHeader("foo", "456");
                    response.setEntity(new StringEntity("OK", StandardCharsets.US_ASCII));
                    response.setCode(HttpStatus.SC_OK);
                })
                .register("/myapp", (request, response, context) -> {
                    Header[] from = request.getHeaders("from");
                    assertEquals("me", from[0].getValue());
                    Header[] to = request.getHeaders("to");
                    assertEquals("[foo, bar]", to[0].getValue());

                    response.setHeader("bar", "yes");
                    response.addHeader("foo", "123");
                    response.addHeader("foo", "456");
                    response.setEntity(new StringEntity("OK", StandardCharsets.US_ASCII));
                    response.setCode(HttpStatus.SC_OK);
                }).create();
        localServer.start();

        super.setUp();
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void testTwoHeadersWithSameKeyHeader() throws Exception {
        String endpointUri
                = "http://localhost:" + localServer.getLocalPort() + "/myapp";

        Exchange out = template.request(endpointUri, exchange -> {
            exchange.getIn().setBody(null);
            exchange.getIn().setHeader("from", "me");
            List<String> list = new ArrayList<>();
            list.add("foo");
            list.add("bar");
            exchange.getIn().setHeader("to", list);
        });

        assertNotNull(out);
        assertFalse(out.isFailed(), "Should not fail");
        assertEquals("OK", out.getMessage().getBody(String.class));
        assertEquals("yes", out.getMessage().getHeader("bar"));

        List<?> foo = out.getMessage().getHeader("foo", List.class);
        assertNotNull(foo);
        assertEquals(2, foo.size());
        assertEquals("123", foo.get(0));
        assertEquals("456", foo.get(1));
    }

}
