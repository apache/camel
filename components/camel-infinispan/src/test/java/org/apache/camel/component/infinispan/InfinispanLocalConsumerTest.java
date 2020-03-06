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
package org.apache.camel.component.infinispan;

import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class InfinispanLocalConsumerTest extends InfinispanTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @EndpointInject("mock:result2")
    private MockEndpoint mockResult2;

    @Test
    public void consumerReceivedPreAndPostEntryCreatedEventNotifications() throws Exception {
        mockResult.expectedMessageCount(2);

        mockResult.message(0).header(InfinispanConstants.EVENT_TYPE).isEqualTo("CACHE_ENTRY_CREATED");
        mockResult.message(0).header(InfinispanConstants.IS_PRE).isEqualTo(true);
        mockResult.message(0).header(InfinispanConstants.CACHE_NAME).isNotNull();
        mockResult.message(0).header(InfinispanConstants.KEY).isEqualTo(KEY_ONE);

        mockResult.message(1).header(InfinispanConstants.EVENT_TYPE).isEqualTo("CACHE_ENTRY_CREATED");
        mockResult.message(1).header(InfinispanConstants.IS_PRE).isEqualTo(false);
        mockResult.message(1).header(InfinispanConstants.CACHE_NAME).isNotNull();
        mockResult.message(1).header(InfinispanConstants.KEY).isEqualTo(KEY_ONE);

        currentCache().put(KEY_ONE, VALUE_ONE);
        mockResult.assertIsSatisfied();
    }

    @Test
    public void consumerReceivedExpirationEventNotification() throws Exception {
        mockResult2.expectedMessageCount(1);

        mockResult2.message(0).header(InfinispanConstants.EVENT_TYPE).isEqualTo("CACHE_ENTRY_EXPIRED");
        mockResult2.message(0).header(InfinispanConstants.IS_PRE).isEqualTo(false);
        mockResult2.message(0).header(InfinispanConstants.CACHE_NAME).isNotNull();
        mockResult2.message(0).header(InfinispanConstants.KEY).isEqualTo("keyTwo");

        injectTimeService();
        currentCache().put("keyTwo", "valueTwo", 1000, TimeUnit.MILLISECONDS);
        ts.advance(1001);
        //expiration events are thrown only after a get if expiration reaper thread is not enabled
        assertNull(currentCache().get("keyTwo"));
        mockResult2.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("infinispan:default?cacheContainer=#cacheContainer&eventTypes=CACHE_ENTRY_CREATED")
                        .to("mock:result");
                from("infinispan:default?cacheContainer=#cacheContainer&eventTypes=CACHE_ENTRY_EXPIRED")
                        .to("mock:result2");
            }
        };
    }
}

