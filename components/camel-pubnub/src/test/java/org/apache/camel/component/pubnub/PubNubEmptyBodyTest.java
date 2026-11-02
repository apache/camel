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
package org.apache.camel.component.pubnub;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FIRE and SETSTATE with an empty body fail without calling PubNub, like PUBLISH.
 */
public class PubNubEmptyBodyTest extends PubNubTestBase {

    @Test
    public void testFireNullBody() {
        Exchange exchange = template.request("direct:fire", e -> e.getIn().setBody(null));

        CamelException cause = assertInstanceOf(CamelException.class, exchange.getException());
        assertEquals("Can not fire empty message", cause.getMessage());
        verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    public void testSetStateEmptyBody() {
        Exchange exchange = template.request("direct:setstate", e -> e.getIn().setBody(Map.of()));

        CamelException cause = assertInstanceOf(CamelException.class, exchange.getException());
        assertEquals("Can not set state with an empty body", cause.getMessage());
        verify(0, anyRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "FIRE", "SETSTATE" })
    public void testEmptyBodyCompletesSynchronouslyOnce(String operation) throws Exception {
        AsyncProducer producer = context.getEndpoint("pubnub:someChannel?uuid=myuuid&pubnub=#pubnub").createAsyncProducer();
        producer.start();
        try {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setHeader(PubNubConstants.OPERATION, operation);
            AtomicInteger doneSync = new AtomicInteger();
            AtomicInteger doneAsync = new AtomicInteger();

            boolean sync = producer.process(exchange, ds -> (ds ? doneSync : doneAsync).incrementAndGet());

            assertTrue(sync, "process must return true as the callback was called synchronously");
            assertEquals(1, doneSync.get());
            assertEquals(0, doneAsync.get());
            assertInstanceOf(CamelException.class, exchange.getException());
        } finally {
            producer.stop();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:fire").to("pubnub:someChannel?operation=fire&pubnub=#pubnub");
                from("direct:setstate").to("pubnub:someChannel?operation=setstate&uuid=myuuid&pubnub=#pubnub");
            }
        };
    }
}
