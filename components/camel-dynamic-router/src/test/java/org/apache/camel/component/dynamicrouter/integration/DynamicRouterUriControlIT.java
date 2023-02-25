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
import org.apache.camel.ExchangePattern;
import org.apache.camel.Predicate;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.dynamicrouter.DynamicRouterComponent;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlMessage;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.COMPONENT_SCHEME;
import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.CONTROL_ACTION_SUBSCRIBE;
import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.CONTROL_ACTION_UNSUBSCRIBE;
import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.CONTROL_CHANNEL_URI;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test verifies basic functionality with the Dynamic Router in synchronous mode. Instead of using
 * {@link DynamicRouterControlMessage}s for subscribing and unsubscribing, this test uses the control channel URI.
 */
public class DynamicRouterUriControlIT extends CamelTestSupport {

    /**
     * Tests participant subscription, and that messages are received at their registered destination endpoints.
     *
     * @throws Exception if interrupted while waiting for mocks to be satisfied
     */
    @Test
    public void testSubscribeWithUri() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        String subscribeUri = createControlChannelUri(CONTROL_ACTION_SUBSCRIBE, "test", "testSubscriptionId",
                mock.getEndpointUri(), 1, "${body} contains 'test'");

        template.sendBody(subscribeUri, "");

        // Trigger events to subscribers
        template.sendBody("direct:start", "testMessage");

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);
    }

    /**
     * Tests participant subscription, and that messages are received at their registered destination endpoints.
     * Subscription ID is omitted to check generated ID.
     *
     * @throws Exception if interrupted while waiting for mocks to be satisfied
     */
    @Test
    public void testSubscribeWithUriAndWithoutSubscriptionId() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        String subscribeUri = createControlChannelUri(CONTROL_ACTION_SUBSCRIBE, "test", null,
                mock.getEndpointUri(), 1, "${body} contains 'test'");

        String generatedId = (String) template.sendBody(subscribeUri, ExchangePattern.InOut, "");

        // Trigger events to subscribers
        template.sendBody("direct:start", "testMessage");

        assertTrue(generatedId.matches("[a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8}"));

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);
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

        // Initially subscribe
        String subscribeUri = createControlChannelUri(CONTROL_ACTION_SUBSCRIBE, "test", "testId",
                mock.getEndpointUri(), 1, "${body} contains 'test'");

        template.sendBody(subscribeUri, "");
        template.sendBody("direct:start", "testMessage");

        // Subscription should lead to the mock endpoint receiving one message
        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);

        // Now unsubscribe
        String unsubscribeUri = createControlChannelUri(CONTROL_ACTION_UNSUBSCRIBE, "test", "testId",
                null, null, null);
        template.sendBody(unsubscribeUri, "");

        // Sends another message that should not be received
        template.sendBody("direct:start", "testMessage");

        // The unsubscribe message should mean that the expected count
        // of received messages to remain unchanged
        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);
    }

    @Test
    void testSubscribeUriWithBodyPredicate() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        Predicate predicate = PredicateBuilder.constant(true);
        String subscribeUri = createControlChannelUri(CONTROL_ACTION_SUBSCRIBE, "test", "testSubscriptionId",
                mock.getEndpointUri(), 1, null);

        template.sendBody(subscribeUri, predicate);

        // Trigger events to subscribers
        template.sendBody("direct:start", "testMessage");

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);
    }

    @Test
    void testSubscribeUriWithBeanPredicate() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        context.getRegistry().bind("predicateBean", Predicate.class, PredicateBuilder.constant(true));
        String subscribeUri = createControlChannelUri(CONTROL_ACTION_SUBSCRIBE, "test", "testSubscriptionId",
                mock.getEndpointUri(), 1, "#bean:predicateBean");

        template.sendBody(subscribeUri, null);

        // Trigger events to subscribers
        template.sendBody("direct:start", "testMessage");

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);
    }

    String createControlChannelUri(
            final String action,
            final String subscribeChannel,
            final String subscriptionId,
            final String endpointUri,
            final Integer priority,
            final String predicate) {
        StringBuilder builder = new StringBuilder(
                String.format("%s/%s/%s",
                        CONTROL_CHANNEL_URI, action, subscribeChannel));
        if (subscriptionId != null) {
            builder.append("&subscriptionId=").append(subscriptionId);
        }
        if (endpointUri != null) {
            builder.append("&destinationUri=").append(endpointUri);
        }
        if (priority != null) {
            builder.append("&priority=").append(priority);
        }
        if (predicate != null) {
            builder.append(
                    predicate.startsWith("#bean:") ? "&predicateBean=" : "&predicate=")
                    .append(predicate);
        }
        String uriString = builder.toString();
        if (uriString.contains("&") && !uriString.contains("?")) {
            uriString = uriString.replaceFirst("&", "?");
        }
        return uriString;
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
}
