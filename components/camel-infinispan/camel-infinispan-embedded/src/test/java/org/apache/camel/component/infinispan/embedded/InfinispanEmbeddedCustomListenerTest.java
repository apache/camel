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

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanConsumerTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.event.CacheEntryCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InfinispanEmbeddedCustomListenerTest extends InfinispanEmbeddedTestSupport
        implements InfinispanConsumerTestSupport {

    @Test
    public void customListener() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        getCache().put(InfinispanConsumerTestSupport.KEY_ONE, InfinispanConsumerTestSupport.VALUE_ONE);

        mock.message(0).header(InfinispanConstants.IS_PRE).isEqualTo(true);
        mock.message(0).header(InfinispanConstants.KEY).isEqualTo("newKey");
        mock.message(1).header(InfinispanConstants.IS_PRE).isEqualTo(false);
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
                fromF("infinispan-embedded:%s?sync=true&customListener=#myCustomListener", getCacheName())
                        .to("mock:result");

            }
        };
    }

    @BindToRegistry
    public InfinispanEmbeddedCustomListener myCustomListener() {
        return new MyEmbeddedCustomListener(getCacheName());
    }

    @Listener(sync = true)
    public static class MyEmbeddedCustomListener extends InfinispanEmbeddedCustomListener {
        public MyEmbeddedCustomListener(String cacheName) {
            super.setCacheName(cacheName);
        }

        @CacheEntryCreated
        public void entryCreated(CacheEntryCreatedEvent<?, ?> event) {
            getEventProcessor().processEvent(
                    event.getType().toString(),
                    event.getCache().getName(),
                    event.getKey(),
                    null,
                    e -> e.getMessage().setHeader(InfinispanConstants.IS_PRE, event.isPre()));

            assertEquals(getCacheName(), event.getCache().getName());
        }
    }

}
