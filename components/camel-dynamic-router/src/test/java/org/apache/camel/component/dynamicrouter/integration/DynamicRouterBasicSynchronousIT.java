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
package org.apache.camel.component.dynamicrouter.integration;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.dynamicrouter.DynamicRouterComponent;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlMessage;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlMessage.SubscribeMessageBuilder;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlMessage.UnsubscribeMessageBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.COMPONENT_SCHEME;
import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.CONTROL_CHANNEL_URI;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * This test verifies basic functionality with the Dynamic Router in synchronous mode. This configuration is entirely
 * manual. For Spring XML, refer to {@link DynamicRouterSingleRouteTwoParticipantsIT}.
 */
public class DynamicRouterBasicSynchronousIT extends CamelTestSupport {

    private final Predicate matchAllPredicate = new MatchAllPredicate();

    /**
     * Tests participant subscription, and that messages are received at their registered destination endpoints.
     *
     * @throws Exception if interrupted while waiting for mocks to be satisfied
     */
    @Test
    public void testDynamicRouter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        Predicate spyPredicate = spy(matchAllPredicate);

        DynamicRouterControlMessage subscribeMsg = new SubscribeMessageBuilder()
                .id("testSubscriptionId")
                .channel("test")
                .priority(1)
                .endpointUri(mock.getEndpointUri())
                .predicate(spyPredicate)
                .build();
        template.sendBody(CONTROL_CHANNEL_URI, subscribeMsg);

        // Trigger events to subscribers
        template.sendBody("direct:start", "testMessage");

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);

        // The predicate is called twice:
        //   1. when the FilterProcessor parent class tests predicate matches
        //   2. when the DynamicRouterProcessor sub-class tests predicate matches
        // Each pair constitutes one Exchange evaluation
        verify(spyPredicate, times(2)).matches(any(Exchange.class));
    }

    /**
     * Tests unsubscribe.
     *
     * @throws Exception if interrupted while waiting for mocks to be satisfied
     */
    @Test
    void testUnsubscribe() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        Predicate spyPredicate = spy(matchAllPredicate);

        DynamicRouterControlMessage subscribeMessage = new SubscribeMessageBuilder()
                .id("testSubscriptionId")
                .channel("test")
                .priority(1)
                .endpointUri(mock.getEndpointUri())
                .predicate(spyPredicate)
                .build();
        template.sendBody(CONTROL_CHANNEL_URI, subscribeMessage);

        // Trigger events to subscribers
        template.sendBody("direct:start", "testMessage");

        // Subscription should lead to the mock endpoint receiving one message
        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);

        // The predicate is called twice:
        //   1. when the FilterProcessor parent class tests predicate matches
        //   2. when the DynamicRouterProcessor sub-class tests predicate matches
        // Each pair constitutes one Exchange evaluation
        verify(spyPredicate, times(2)).matches(any(Exchange.class));

        // Reset the interactions for the predicate spy before unsubscribing
        reset(spyPredicate);

        // Now unsubscribe
        DynamicRouterControlMessage unsubscribeMessage = new UnsubscribeMessageBuilder()
                .id("testSubscriptionId")
                .channel("test")
                .build();
        template.sendBody(CONTROL_CHANNEL_URI, unsubscribeMessage);

        // Sends another message that should not be received
        template.sendBody("direct:start", "testMessage");

        // The unsubscribe message should mean that the expected count
        // of received messages to remain unchanged
        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);

        // The test predicate should not have been called after subscription removal
        verify(spyPredicate, never()).matches(any(Exchange.class));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent(COMPONENT_SCHEME, new DynamicRouterComponent());
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to("dynamic-router://test?synchronous=true");
            }
        };
    }

    /**
     * A constant predicate for testing that matches all exchanges.
     */
    static class MatchAllPredicate implements Predicate {

        @Override
        public boolean matches(Exchange exchange) {
            return true;
        }
    }
}
