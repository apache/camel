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

import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.apache.camel.component.http.HttpMethods.POST;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit test to verify the algorithm for selecting either GET or POST.
 */
public class HttpProducerSelectMethodTest extends BaseHttpTest {

    private HttpServer localServer;

    private String baseUrl;

    private Exchange exchange;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();

        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/myget", new BasicValidationHandler(GET.name(), null, null, getExpectedContent()))
                .register("/mypost", new BasicValidationHandler(POST.name(), null, null, getExpectedContent()))
                .register("/myget2", new BasicValidationHandler(GET.name(), "q=Camel", null, getExpectedContent()))
                .create();
        localServer.start();

        baseUrl = "http://localhost:" + localServer.getLocalPort();

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
    public void noDataDefaultIsGet() throws Exception {
        HttpProducer producer = createProducer("/myget");
        exchange = producer.createExchange();
        exchange.getIn().setBody(null);
        assertDoesNotThrow(() -> runProducer(producer));

        assertExchange(exchange);
    }

    private void runProducer(HttpProducer producer) throws Exception {
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

    @Test
    public void dataDefaultIsPost() throws Exception {
        HttpProducer producer = createProducer("/mypost");

        exchange = producer.createExchange();
        exchange.getIn().setBody("This is some data to post");
        assertDoesNotThrow(() -> runProducer(producer));

        assertExchange(exchange);
    }

    @Test
    public void withMethodPostInHeader() throws Exception {
        HttpProducer producer = createProducer("/mypost");

        exchange = producer.createExchange();
        exchange.getIn().setBody("");
        exchange.getIn().setHeader(Exchange.HTTP_METHOD, POST);
        assertDoesNotThrow(() -> runProducer(producer));
    }

    @Test
    public void withMethodGetInHeader() throws Exception {
        HttpProducer producer = createProducer("/myget");

        exchange = producer.createExchange();
        exchange.getIn().setBody("");
        exchange.getIn().setHeader(Exchange.HTTP_METHOD, GET);
        assertDoesNotThrow(() -> runProducer(producer));
    }

    private HttpProducer createProducer(String path) throws Exception {
        HttpComponent component = context.getComponent("http", HttpComponent.class);

        HttpEndpoint endpoint = (HttpEndpoint) component.createEndpoint(baseUrl + path);
        return new HttpProducer(endpoint);
    }

    @Test
    public void withMethodCommonHttpGetInHeader() throws Exception {
        HttpProducer producer = createProducer("/myget");

        exchange = producer.createExchange();
        exchange.getIn().setBody("");
        exchange.getIn().setHeader(Exchange.HTTP_METHOD, org.apache.camel.http.common.HttpMethods.GET);
        assertDoesNotThrow(() -> runProducer(producer));
    }

    @Test
    public void withEndpointQuery() throws Exception {
        HttpProducer producer = createProducer("/myget2?q=Camel");

        exchange = producer.createExchange();
        exchange.getIn().setBody("");
        assertDoesNotThrow(() -> runProducer(producer));
    }

    @Test
    public void withQueryInHeader() throws Exception {
        HttpProducer producer = createProducer("/myget2");

        exchange = producer.createExchange();
        exchange.getIn().setBody("");
        exchange.getIn().setHeader(Exchange.HTTP_QUERY, "q=Camel");
        assertDoesNotThrow(() -> runProducer(producer));
    }

    @Test
    public void withHttpURIInHeader() throws Exception {
        HttpProducer producer = createProducer("/myget2");

        exchange = producer.createExchange();
        exchange.getIn().setBody("");
        exchange.getIn().setHeader(Exchange.HTTP_URI, baseUrl + "/myget2?q=Camel");
        assertDoesNotThrow(() -> runProducer(producer));
    }

    @Test
    public void withQueryInHeaderOverrideEndpoint() throws Exception {
        HttpProducer producer = createProducer("/myget2?q=Donkey");

        exchange = producer.createExchange();
        exchange.getIn().setBody("");
        exchange.getIn().setHeader(Exchange.HTTP_QUERY, "q=Camel");
        assertDoesNotThrow(() -> runProducer(producer));
    }
}
