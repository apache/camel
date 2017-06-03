/**
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

import java.util.UUID;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

import static org.hamcrest.core.Is.is;

public class InfinispanComponentTest extends InfinispanTestSupport {
    private final String cacheName = UUID.randomUUID().toString();

    @Test
    public void consumerReceivedEntryCreatedEventNotifications() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);

        namedCache(cacheName).put(KEY_ONE, VALUE_ONE);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void producerPublishesKeyAndValue() throws Exception {
        fluentTemplate()
            .to("direct:start")
            .withHeader(InfinispanConstants.KEY, KEY_ONE)
            .withHeader(InfinispanConstants.VALUE, VALUE_ONE)
            .send();

        assertThat(namedCache(cacheName).get(KEY_ONE).toString(), is(VALUE_ONE));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("infinispan:%s?cacheContainer=#cacheContainer&eventTypes=CACHE_ENTRY_CREATED", cacheName)
                    .to("mock:result");
                from("direct:start")
                    .toF("infinispan:%s?cacheContainer=#cacheContainer", cacheName);
            }
        };
    }
}
