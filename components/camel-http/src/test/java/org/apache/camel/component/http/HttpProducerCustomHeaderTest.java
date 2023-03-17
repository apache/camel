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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.HeaderValidationHandler;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HttpProducerCustomHeaderTest extends BaseHttpTest {

    private static final String CUSTOM_HOST = "test";

    private HttpServer localServer;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put(HttpHeaders.HOST, CUSTOM_HOST);

        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .registerVirtual(CUSTOM_HOST, "*",
                        new HeaderValidationHandler(
                                "GET",
                                null,
                                null,
                                getExpectedContent(),
                                expectedHeaders))
                .register("*",
                        new HeaderValidationHandler(
                                "GET",
                                null,
                                null,
                                getExpectedContent(),
                                null))
                .create();

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
    public void testHttpProducerGivenCustomHostHeaderQuerySetCustomHost() throws Exception {

        HttpComponent component = context.getComponent("http", HttpComponent.class);
        component.setConnectionTimeToLive(1000L);

        HttpEndpoint endpoint = (HttpEndpoint) component
                .createEndpoint(
                        "http://localhost:" + localServer.getLocalPort() + "/myget?customHostHeader=" + CUSTOM_HOST);
        HttpProducer producer = new HttpProducer(endpoint);

        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody(null);

        producer.start();
        producer.process(exchange);
        producer.stop();

        assertExchange(exchange);
    }

    @Test
    public void testHttpProducerGivenEmptyQueryShouldNotSetCustomHost() throws Exception {

        HttpComponent component = context.getComponent("http", HttpComponent.class);
        component.setConnectionTimeToLive(1000L);

        HttpEndpoint endpoint
                = (HttpEndpoint) component.createEndpoint("http://localhost:"
                                                          + localServer.getLocalPort() + "/myget");
        HttpProducer producer = new HttpProducer(endpoint);

        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody(null);

        producer.start();
        producer.process(exchange);
        producer.stop();

        assertExchange(exchange);
    }
}
