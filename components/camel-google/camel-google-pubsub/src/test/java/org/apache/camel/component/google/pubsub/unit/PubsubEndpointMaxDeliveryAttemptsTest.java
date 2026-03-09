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
package org.apache.camel.component.google.pubsub.unit;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.GooglePubsubEndpoint;
import org.apache.camel.component.google.pubsub.PubsubTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PubsubEndpointMaxDeliveryAttemptsTest extends PubsubTestSupport {

    private static final String SUBSCRIPTION_WITH_MAX = "test-max-delivery?maxDeliveryAttempts=5";
    private static final String SUBSCRIPTION_WITHOUT_MAX = "test-no-max-delivery";

    @EndpointInject("google-pubsub://{{project.id}}:" + SUBSCRIPTION_WITH_MAX)
    private Endpoint endpointWithMax;

    @EndpointInject("google-pubsub://{{project.id}}:" + SUBSCRIPTION_WITHOUT_MAX)
    private Endpoint endpointWithoutMax;

    @Test
    public void testMaxDeliveryAttemptsExplicitlySet() {
        Endpoint endpoint
                = context.hasEndpoint(String.format("google-pubsub:%s:%s", PROJECT_ID, SUBSCRIPTION_WITH_MAX));
        assertNotNull(endpoint);

        assertTrue(endpoint instanceof GooglePubsubEndpoint);
        GooglePubsubEndpoint pubsubEndpoint = (GooglePubsubEndpoint) endpoint;

        assertEquals(5, pubsubEndpoint.getMaxDeliveryAttempts());
        assertTrue(pubsubEndpoint.isMaxDeliveryAttemptsExplicitlySet());
    }

    @Test
    public void testMaxDeliveryAttemptsDefaultValue() {
        Endpoint endpoint
                = context.hasEndpoint(String.format("google-pubsub:%s:%s", PROJECT_ID, SUBSCRIPTION_WITHOUT_MAX));
        assertNotNull(endpoint);

        assertTrue(endpoint instanceof GooglePubsubEndpoint);
        GooglePubsubEndpoint pubsubEndpoint = (GooglePubsubEndpoint) endpoint;

        assertEquals(0, pubsubEndpoint.getMaxDeliveryAttempts());
        assertFalse(pubsubEndpoint.isMaxDeliveryAttemptsExplicitlySet());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(endpointWithMax).to("direct:to1");
                from(endpointWithoutMax).to("direct:to2");
            }
        };
    }
}
