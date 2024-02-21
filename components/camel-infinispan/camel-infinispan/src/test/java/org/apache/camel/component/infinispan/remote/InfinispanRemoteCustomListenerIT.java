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
package org.apache.camel.component.infinispan.remote;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanConsumerTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryCreated;
import org.infinispan.client.hotrod.annotation.ClientListener;
import org.infinispan.client.hotrod.event.ClientCacheEntryCreatedEvent;
import org.infinispan.commons.api.BasicCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class InfinispanRemoteCustomListenerIT extends InfinispanRemoteTestSupport implements InfinispanConsumerTestSupport {
    @Test
    public void consumerReceivedEventNotifications() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(InfinispanConstants.EVENT_TYPE, "CLIENT_CACHE_ENTRY_CREATED");

        getCache().put(InfinispanConsumerTestSupport.KEY_ONE, InfinispanConsumerTestSupport.VALUE_ONE);
        getCache().put(InfinispanConsumerTestSupport.KEY_ONE, InfinispanConsumerTestSupport.VALUE_TWO);

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
                fromF("infinispan:%s?customListener=#myCustomListener", getCacheName())
                        .to("mock:result");
            }
        };
    }

    @BindToRegistry
    public InfinispanRemoteCustomListener myCustomListener() {
        return new InfinispanRemoteCustomListenerIT.MyRemoteCustomListener();
    }

    @ClientListener
    public static class MyRemoteCustomListener extends InfinispanRemoteCustomListener {
        @ClientCacheEntryCreated
        public void entryCreated(ClientCacheEntryCreatedEvent<?> event) {
            if (isAccepted(event.getType())) {
                getEventProcessor().processEvent(
                        event.getType().toString(),
                        getCacheName(),
                        event.getKey(),
                        null,
                        null);
            }
        }
    }
}
