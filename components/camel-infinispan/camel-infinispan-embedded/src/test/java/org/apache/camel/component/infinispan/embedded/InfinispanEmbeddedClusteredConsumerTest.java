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
package org.apache.camel.component.infinispan.embedded;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.infinispan.commons.test.TestResourceTracker;
import org.infinispan.distribution.MagicKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

public class InfinispanEmbeddedClusteredConsumerTest extends InfinispanEmbeddedClusteredTestSupport {

    private static final long WAIT_TIMEOUT = 5000;

    @BeforeAll
    public static void beforeClass() {
        TestResourceTracker.testStarted(InfinispanEmbeddedClusteredConsumerTest.class.getName());
    }

    @AfterAll
    public static void afterClass() {
        TestResourceTracker.testFinished(InfinispanEmbeddedClusteredConsumerTest.class.getName());
    }

    @Test
    public void consumerReceivedPostEntryCreatedEventNotifications() throws Exception {
        MagicKey key = new MagicKey(getCache(1));

        MockEndpoint mockResultCreatedEvents = getMockEndpoint("mock:resultCreated");
        mockResultCreatedEvents.expectedMessageCount(1);

        mockResultCreatedEvents.message(0).header(InfinispanConstants.EVENT_TYPE).isEqualTo("CACHE_ENTRY_CREATED");
        mockResultCreatedEvents.message(0).header(InfinispanConstants.IS_PRE).isEqualTo(false);
        mockResultCreatedEvents.message(0).header(InfinispanConstants.CACHE_NAME).isNotNull();
        mockResultCreatedEvents.message(0).header(InfinispanConstants.KEY).isEqualTo(key.toString());

        getCache(1).put(key, "value");
        mockResultCreatedEvents.assertIsSatisfied();
    }

    @Test
    public void consumerReceivedExpirationEventNotifications() throws Exception {
        MagicKey key = new MagicKey(getCache(1));

        MockEndpoint mockResultCreatedEvents = getMockEndpoint("mock:resultCreated");
        mockResultCreatedEvents.expectedMessageCount(1);
        mockResultCreatedEvents.message(0).header(InfinispanConstants.EVENT_TYPE).isEqualTo("CACHE_ENTRY_CREATED");
        mockResultCreatedEvents.message(0).header(InfinispanConstants.IS_PRE).isEqualTo(false);
        mockResultCreatedEvents.message(0).header(InfinispanConstants.CACHE_NAME).isNotNull();
        mockResultCreatedEvents.message(0).header(InfinispanConstants.KEY).isEqualTo(key.toString());

        MockEndpoint mockResultExpiredEvents = getMockEndpoint("mock:resultExpired");
        mockResultExpiredEvents.expectedMessageCount(1);
        mockResultExpiredEvents.message(0).header(InfinispanConstants.EVENT_TYPE).isEqualTo("CACHE_ENTRY_EXPIRED");
        mockResultExpiredEvents.message(0).header(InfinispanConstants.IS_PRE).isEqualTo(false);
        mockResultExpiredEvents.message(0).header(InfinispanConstants.CACHE_NAME).isNotNull();
        mockResultExpiredEvents.message(0).header(InfinispanConstants.KEY).isEqualTo(key.toString());

        injectTimeService();

        getCache(1).put(key, "value", 1000, TimeUnit.MILLISECONDS);

        ts0.advance(1001);
        ts1.advance(1001);

        assertNull(getCache(1).get(key));
        mockResultCreatedEvents.assertIsSatisfied();
        mockResultExpiredEvents.assertIsSatisfied(WAIT_TIMEOUT);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("infinispan-embedded:current?clusteredListener=true&eventTypes=CACHE_ENTRY_CREATED")
                        .to("mock:resultCreated");
                from("infinispan-embedded:current?clusteredListener=true&eventTypes=CACHE_ENTRY_EXPIRED")
                        .to("mock:resultExpired");
            }
        };
    }
}
