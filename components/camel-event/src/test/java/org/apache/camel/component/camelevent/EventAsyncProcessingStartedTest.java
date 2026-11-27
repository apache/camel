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
package org.apache.camel.component.camelevent;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EventHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An endpoint that subscribes only to the deprecated ExchangeAsyncProcessingStarted type gets these events, and a
 * wildcard does not turn them on.
 */
class EventAsyncProcessingStartedTest extends CamelTestSupport {

    @Test
    @SuppressWarnings("deprecation") // the event type is deprecated, but it can still be subscribed to
    void theOnlySubscribedTypeIsReceived() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:started");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(CamelEventConstants.HEADER_EVENT_TYPE, "ExchangeAsyncProcessingStarted");

        Exchange exchange = new DefaultExchange(context);
        EventHelper.notifyExchangeAsyncProcessingStartedEvent(context, exchange);

        mock.assertIsSatisfied();
        // the consumers are not async, so the events are routed before the notify returns
        assertTrue(getMockEndpoint("mock:wildcard").getExchanges().stream()
                .noneMatch(e -> "ExchangeAsyncProcessingStarted"
                        .equals(e.getMessage().getHeader(CamelEventConstants.HEADER_EVENT_TYPE))),
                "Exchange* got the deprecated event");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("event:ExchangeAsyncProcessingStarted").to("mock:started");
                // a wildcard does not turn the deprecated type on
                from("event:Exchange*").to("mock:wildcard");
            }
        };
    }
}
