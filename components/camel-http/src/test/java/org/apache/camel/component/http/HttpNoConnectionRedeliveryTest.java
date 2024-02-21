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

import java.net.ConnectException;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.apache.camel.http.common.HttpMethods.GET;
import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpNoConnectionRedeliveryTest extends BaseHttpTest {

    private HttpServer localServer;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/search", new BasicValidationHandler(GET.name(), null, null, getExpectedContent())).create();
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
    public void httpConnectionOk() {
        Exchange exchange = template.request("direct:start", null);

        assertExchange(exchange);
    }

    @Test
    @Disabled
    public void httpConnectionNotOk() throws Exception {
        // stop server so there are no connection
        // and wait for it to terminate
        localServer.stop();
        localServer.awaitTermination(TimeValue.ofSeconds(5));

        Exchange exchange = template.request("direct:start", null);
        assertTrue(exchange.isFailed());

        ConnectException cause = assertIsInstanceOf(ConnectException.class, exchange.getException());
        assertTrue(cause.getMessage().contains("failed"));

        assertEquals(true, exchange.getIn().getHeader(Exchange.REDELIVERED));
        assertEquals(4, exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .onException(ConnectException.class)
                        .maximumRedeliveries(4)
                        .backOffMultiplier(2)
                        .redeliveryDelay(100)
                        .maximumRedeliveryDelay(5000)
                        .useExponentialBackOff()
                        .end()
                        .to("http://localhost:" + localServer.getLocalPort()
                            + "/search");
            }
        };
    }
}
