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
package org.apache.camel.component.mina;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * To test timeout.
 */
public class MinaExchangeTimeOutTest extends BaseMinaTest {

    private final CountDownLatch latch = new CountDownLatch(1);

    @Test
    public void testUsingTimeoutParameter() throws Exception {
        // use a timeout value of 2 seconds (timeout is in millis) so we should actually get a response in this test
        Endpoint endpoint = context
                .getEndpoint(String.format("mina:tcp://localhost:%1$s?textline=true&sync=true&timeout=500", getPort()));
        Producer producer = endpoint.createProducer();
        producer.start();
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("Hello World");
        assertThrows(ExchangeTimedOutException.class,
                () -> producer.process(exchange));
        producer.stop();

        latch.countDown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                fromF("mina:tcp://localhost:%1$s?textline=true&sync=true&timeout=30000", getPort())
                        .process(e -> {
                            assertEquals("Hello World", e.getIn().getBody(String.class));
                            latch.await(5, TimeUnit.SECONDS);
                        });
            }
        };
    }
}
