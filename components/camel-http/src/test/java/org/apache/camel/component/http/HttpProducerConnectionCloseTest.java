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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.http.common.HttpHeaderFilterStrategy;
import org.apache.camel.spi.Registry;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test that show custom header filter useful to send Connection Close header
 */
public class HttpProducerConnectionCloseTest extends BaseHttpTest {

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    private HttpServer localServer;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/myget", new BasicValidationHandler(GET.name(), null, null, getExpectedContent())).create();
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
    public void noDataDefaultIsGet() throws Exception {
        HttpComponent component = context.getComponent("http", HttpComponent.class);
        component.setConnectionTimeToLive(1000L);
        HttpEndpoint endpoint = (HttpEndpoint) component
                .createEndpoint("http://localhost:"
                                + localServer.getLocalPort() + "/myget?headerFilterStrategy=#myFilter");
        HttpProducer producer = new HttpProducer(endpoint);
        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody(null);
        exchange.getIn().setHeader("connection", HeaderElements.CLOSE);
        producer.start();
        producer.process(exchange);
        producer.stop();

        assertEquals(HeaderElements.CLOSE, exchange.getMessage().getHeader("connection"));
        assertExchange(exchange);
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        ConnectionCloseHeaderFilter connectionCloseFilterStrategy = new ConnectionCloseHeaderFilter();
        registry.bind("myFilter", connectionCloseFilterStrategy);
    }

    static class ConnectionCloseHeaderFilter extends HttpHeaderFilterStrategy {
        @Override
        protected void initialize() {
            super.initialize();
            getOutFilter().remove("connection");
        }
    }
}
