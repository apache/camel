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
package org.apache.camel.component.jms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link ClassicJmsHeaderFilterStrategy} opts out of the default Camel header filtering so that
 * Camel-internal headers propagate across the JMS wire (legacy behaviour).
 */
public class ClassicJmsHeaderFilterStrategyTest {

    private final ClassicJmsHeaderFilterStrategy strategy = new ClassicJmsHeaderFilterStrategy();

    @Test
    void camelHeadersPassThroughInboundDirection() {
        // In classic mode, Camel headers must NOT be blocked when arriving from JMS
        assertFalse(strategy.applyFilterToExternalHeaders("CamelJmsDestination", "queue://foo", null),
                "CamelJmsDestination must pass through inbound in classic mode");
        assertFalse(strategy.applyFilterToExternalHeaders("CamelFileName", "report.txt", null),
                "CamelFileName must pass through inbound in classic mode");
        assertFalse(strategy.applyFilterToExternalHeaders("camelCorrelationId", "abc", null),
                "camelCorrelationId must pass through inbound in classic mode");
        assertFalse(strategy.applyFilterToExternalHeaders("org.apache.camel.foo", "bar", null),
                "org.apache.camel.foo must pass through inbound in classic mode");
    }

    @Test
    void camelHeadersPassThroughOutboundDirection() {
        // In classic mode, Camel headers must NOT be blocked when sent to JMS
        assertFalse(strategy.applyFilterToCamelHeaders("CamelJmsDestination", "queue://foo", null),
                "CamelJmsDestination must pass through outbound in classic mode");
        assertFalse(strategy.applyFilterToCamelHeaders("CamelFileName", "report.txt", null),
                "CamelFileName must pass through outbound in classic mode");
        assertFalse(strategy.applyFilterToCamelHeaders("camelCorrelationId", "abc", null),
                "camelCorrelationId must pass through outbound in classic mode");
        assertFalse(strategy.applyFilterToCamelHeaders("org.apache.camel.foo", "bar", null),
                "org.apache.camel.foo must pass through outbound in classic mode");
    }

    @Test
    void jmsProviderHeadersStillFiltered() {
        // JMSXDeliveryCount and peers are filtered in both directions (provider-set, not user headers)
        assertTrue(strategy.applyFilterToCamelHeaders("JMSXDeliveryCount", "1", null),
                "JMSXDeliveryCount must be blocked outbound");
    }

    @Test
    void nonCamelHeadersPassThrough() {
        assertFalse(strategy.applyFilterToExternalHeaders("myApp-correlationId", "123", null));
        assertFalse(strategy.applyFilterToCamelHeaders("myApp-correlationId", "123", null));
    }
}
