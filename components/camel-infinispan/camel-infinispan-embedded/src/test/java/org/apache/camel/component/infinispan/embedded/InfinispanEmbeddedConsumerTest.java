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
import org.apache.camel.component.infinispan.InfinispanConsumerTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.infinispan.commons.api.BasicCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

public class InfinispanEmbeddedConsumerTest extends InfinispanEmbeddedTestSupport implements InfinispanConsumerTestSupport {
    @Test
    public void consumerReceivedPreAndPostEntryCreatedEventNotifications() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:created");
        mock.expectedMessageCount(2);

        mock.message(0).header(InfinispanConstants.EVENT_TYPE).isEqualTo("CACHE_ENTRY_CREATED");
        mock.message(0).header(InfinispanConstants.IS_PRE).isEqualTo(true);
        mock.message(0).header(InfinispanConstants.CACHE_NAME).isNotNull();
        mock.message(0).header(InfinispanConstants.KEY).isEqualTo(InfinispanConsumerTestSupport.KEY_ONE);

        mock.message(1).header(InfinispanConstants.EVENT_TYPE).isEqualTo("CACHE_ENTRY_CREATED");
        mock.message(1).header(InfinispanConstants.IS_PRE).isEqualTo(false);
        mock.message(1).header(InfinispanConstants.CACHE_NAME).isNotNull();
        mock.message(1).header(InfinispanConstants.KEY).isEqualTo(InfinispanConsumerTestSupport.KEY_ONE);

        getCache().put(InfinispanConsumerTestSupport.KEY_ONE, InfinispanConsumerTestSupport.VALUE_ONE);
        mock.assertIsSatisfied();
    }

    @Test
    public void consumerReceivedPreAndPostEntryRemoveEventNotifications() throws Exception {
        getCache().put(InfinispanConsumerTestSupport.KEY_ONE, InfinispanConsumerTestSupport.VALUE_ONE);

        MockEndpoint mock = getMockEndpoint("mock:removed");
        mock.expectedMessageCount(2);

        mock.message(0).header(InfinispanConstants.EVENT_TYPE).isEqualTo("CACHE_ENTRY_REMOVED");
        mock.message(0).header(InfinispanConstants.IS_PRE).isEqualTo(true);
        mock.message(0).header(InfinispanConstants.CACHE_NAME).isNotNull();
        mock.message(0).header(InfinispanConstants.KEY).isEqualTo(InfinispanConsumerTestSupport.KEY_ONE);

        mock.message(1).header(InfinispanConstants.EVENT_TYPE).isEqualTo("CACHE_ENTRY_REMOVED");
        mock.message(1).header(InfinispanConstants.IS_PRE).isEqualTo(false);
        mock.message(1).header(InfinispanConstants.CACHE_NAME).isNotNull();
        mock.message(1).header(InfinispanConstants.KEY).isEqualTo(InfinispanConsumerTestSupport.KEY_ONE);

        getCache().remove(InfinispanConsumerTestSupport.KEY_ONE);

        mock.assertIsSatisfied();
    }

    @Test
    public void consumerReceivedPreAndPostEntryUpdateEventNotifications() throws Exception {
        getCache().put(InfinispanConsumerTestSupport.KEY_ONE, InfinispanConsumerTestSupport.VALUE_ONE);

        MockEndpoint mock = getMockEndpoint("mock:modified");
        mock.expectedMessageCount(2);

        mock.message(0).header(InfinispanConstants.EVENT_TYPE).isEqualTo("CACHE_ENTRY_MODIFIED");
        mock.message(0).header(InfinispanConstants.IS_PRE).isEqualTo(true);
        mock.message(0).header(InfinispanConstants.CACHE_NAME).isNotNull();
        mock.message(0).header(InfinispanConstants.KEY).isEqualTo(InfinispanConsumerTestSupport.KEY_ONE);

        mock.message(1).header(InfinispanConstants.EVENT_TYPE).isEqualTo("CACHE_ENTRY_MODIFIED");
        mock.message(1).header(InfinispanConstants.IS_PRE).isEqualTo(false);
        mock.message(1).header(InfinispanConstants.CACHE_NAME).isNotNull();
        mock.message(1).header(InfinispanConstants.KEY).isEqualTo(InfinispanConsumerTestSupport.KEY_ONE);

        getCache().replace(InfinispanConsumerTestSupport.KEY_ONE, InfinispanConsumerTestSupport.VALUE_TWO);

        mock.assertIsSatisfied();
    }

    @Test
    public void consumerReceivedPreAndPostEntryVisitedEventNotifications() throws Exception {
        getCache().put(InfinispanConsumerTestSupport.KEY_ONE, InfinispanConsumerTestSupport.VALUE_ONE);

        MockEndpoint mock = getMockEndpoint("mock:visited");
        mock.expectedMessageCount(2);

        mock.message(0).header(InfinispanConstants.EVENT_TYPE).isEqualTo("CACHE_ENTRY_VISITED");
        mock.message(0).header(InfinispanConstants.IS_PRE).isEqualTo(true);
        mock.message(0).header(InfinispanConstants.CACHE_NAME).isNotNull();
        mock.message(0).header(InfinispanConstants.KEY).isEqualTo(InfinispanConsumerTestSupport.KEY_ONE);

        mock.message(1).header(InfinispanConstants.EVENT_TYPE).isEqualTo("CACHE_ENTRY_VISITED");
        mock.message(1).header(InfinispanConstants.IS_PRE).isEqualTo(false);
        mock.message(1).header(InfinispanConstants.CACHE_NAME).isNotNull();
        mock.message(1).header(InfinispanConstants.KEY).isEqualTo(InfinispanConsumerTestSupport.KEY_ONE);

        getCache().get(InfinispanConsumerTestSupport.KEY_ONE);

        mock.assertIsSatisfied();
    }

    @Test
    public void consumerReceivedExpirationEventNotification() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:expired");
        mock.expectedMessageCount(1);

        mock.message(0).header(InfinispanConstants.EVENT_TYPE).isEqualTo("CACHE_ENTRY_EXPIRED");
        mock.message(0).header(InfinispanConstants.IS_PRE).isEqualTo(false);
        mock.message(0).header(InfinispanConstants.CACHE_NAME).isNotNull();
        mock.message(0).header(InfinispanConstants.KEY).isEqualTo("keyTwo");

        injectTimeService();
        getCache().put("keyTwo", "valueTwo", 1000, TimeUnit.MILLISECONDS);
        ts.advance(1001);

        //expiration events are thrown only after a get if expiration reaper thread is not enabled
        assertNull(getCache().get("keyTwo"));

        mock.assertIsSatisfied();
    }

    @Test
    public void consumerReceivedPreAndPostEntryCreatedEventNotificationsSync() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:sync");
        mock.expectedMessageCount(2);

        getCache().put(InfinispanConsumerTestSupport.KEY_ONE, InfinispanConsumerTestSupport.VALUE_ONE);
        mock.assertIsSatisfied();
    }

    // *****************************
    //
    // *****************************

    @BeforeEach
    protected void beforeEach() {
        // cleanup the default test cache before each run
        getCache().clear();
    }

    @Override
    public BasicCache<Object, Object> getCache() {
        return super.getCache();
    }

    @Override
    public BasicCache<Object, Object> getCache(String name) {
        return super.getCache(name);
    }

    @Override
    public MockEndpoint getMockEndpoint(String id) {
        return super.getMockEndpoint(id);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("infinispan-embedded:%s?sync=false&eventTypes=CACHE_ENTRY_CREATED", getCacheName())
                        .to("mock:created");
                fromF("infinispan-embedded:%s?sync=false&eventTypes=CACHE_ENTRY_REMOVED", getCacheName())
                        .to("mock:removed");
                fromF("infinispan-embedded:%s?sync=false&eventTypes=CACHE_ENTRY_MODIFIED", getCacheName())
                        .to("mock:modified");
                fromF("infinispan-embedded:%s?sync=false&eventTypes=CACHE_ENTRY_VISITED", getCacheName())
                        .to("mock:visited");
                fromF("infinispan-embedded:%s?sync=false&eventTypes=CACHE_ENTRY_EXPIRED", getCacheName())
                        .to("mock:expired");
                fromF("infinispan-embedded:%s?sync=true&eventTypes=CACHE_ENTRY_CREATED", getCacheName())
                        .to("mock:sync");

            }
        };
    }
}
