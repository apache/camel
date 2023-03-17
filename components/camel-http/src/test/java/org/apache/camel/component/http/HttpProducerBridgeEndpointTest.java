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

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.HeaderValidationHandler;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HttpProducerBridgeEndpointTest extends BaseHttpTest {

    private static final Instant INSTANT = Instant.parse("2021-06-10T14:42:00Z");
    private static final String STRING = "text";
    private static final Integer INTEGER = 1;
    private static final Long LONG = 999999999999999L;
    private static final Boolean BOOLEAN = true;
    private static final String QUERY
            = "qp1=" + INSTANT + "&qp2=" + STRING + "&qp3=" + INTEGER + "&qp4=" + LONG + "&qp5=" + BOOLEAN;

    private HttpServer localServer;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        String[] absentHeaders = new String[] { "qp1", "qp2", "qp3", "qp4", "qp5" };
        Map<String, String> noBridgeExpectedHeaders = new HashMap<>();
        noBridgeExpectedHeaders.put("qp1", INSTANT.toString());
        noBridgeExpectedHeaders.put("qp2", STRING);
        noBridgeExpectedHeaders.put("qp3", INTEGER.toString());
        noBridgeExpectedHeaders.put("qp4", LONG.toString());
        noBridgeExpectedHeaders.put("qp5", BOOLEAN.toString());

        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/bridged",
                        new HeaderValidationHandler(
                                "GET",
                                QUERY,
                                null,
                                getExpectedContent(),
                                null,
                                Arrays.asList(absentHeaders)))
                .register("/notbridged",
                        new HeaderValidationHandler(
                                "GET",
                                QUERY,
                                null,
                                getExpectedContent(),
                                noBridgeExpectedHeaders))
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
    public void testHttpProducerBridgeEndpointSkipRequestHeaders() throws Exception {

        HttpComponent component = context.getComponent("http", HttpComponent.class);
        component.setConnectionTimeToLive(1000L);

        HttpEndpoint endpoint = (HttpEndpoint) component
                .createEndpoint("http://localhost:" + localServer.getLocalPort() + "/bridged?bridgeEndpoint=true");
        HttpProducer producer = new HttpProducer(endpoint);

        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody(null);
        exchange.getIn().setHeader(Exchange.HTTP_QUERY, QUERY);
        exchange.getIn().setHeader("qp1", INSTANT);
        exchange.getIn().setHeader("qp2", STRING);
        exchange.getIn().setHeader("qp3", INTEGER);
        exchange.getIn().setHeader("qp4", LONG);
        exchange.getIn().setHeader("qp5", BOOLEAN);

        producer.start();
        producer.process(exchange);
        producer.stop();

        assertExchange(exchange);
    }

    @Test
    public void testHttpProducerNoBridgeEndpointRequestHeaders() throws Exception {

        HttpComponent component = context.getComponent("http", HttpComponent.class);
        component.setConnectionTimeToLive(1000L);

        HttpEndpoint endpoint = (HttpEndpoint) component
                .createEndpoint("http://localhost:" + localServer.getLocalPort() + "/notbridged");
        HttpProducer producer = new HttpProducer(endpoint);

        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody(null);
        exchange.getIn().setHeader(Exchange.HTTP_QUERY, QUERY);
        exchange.getIn().setHeader("qp1", INSTANT);
        exchange.getIn().setHeader("qp2", STRING);
        exchange.getIn().setHeader("qp3", INTEGER);
        exchange.getIn().setHeader("qp4", LONG);
        exchange.getIn().setHeader("qp5", BOOLEAN);

        producer.start();
        producer.process(exchange);
        producer.stop();

        assertExchange(exchange);
    }
}
