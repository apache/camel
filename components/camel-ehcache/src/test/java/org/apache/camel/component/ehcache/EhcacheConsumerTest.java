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

package org.apache.camel.component.ehcache;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.ehcache.Cache;
import org.ehcache.event.EventType;
import org.junit.Test;

public class EhcacheConsumerTest extends EhcacheTestSupport {

    @Test
    public void testEvents() throws Exception {
        String key = generateRandomString();
        String[] values = generateRandomArrayOfStrings(2);

        MockEndpoint created = getMockEndpoint("mock:created");
        created.expectedMinimumMessageCount(1);
        created.expectedHeaderReceived(EhcacheConstants.KEY, key);
        created.expectedHeaderReceived(EhcacheConstants.EVENT_TYPE, EventType.CREATED);

        MockEndpoint updated = getMockEndpoint("mock:updated");
        updated.expectedMinimumMessageCount(1);
        updated.expectedHeaderReceived(EhcacheConstants.KEY, key);
        updated.expectedHeaderReceived(EhcacheConstants.OLD_VALUE, values[0]);
        updated.expectedHeaderReceived(EhcacheConstants.EVENT_TYPE, EventType.UPDATED);

        MockEndpoint all = getMockEndpoint("mock:all");
        all.expectedMinimumMessageCount(2);
        all.expectedHeaderValuesReceivedInAnyOrder(EhcacheConstants.KEY, key, key);
        all.expectedHeaderValuesReceivedInAnyOrder(EhcacheConstants.OLD_VALUE, null, values[0]);
        all.expectedHeaderValuesReceivedInAnyOrder(EhcacheConstants.EVENT_TYPE, EventType.CREATED, EventType.UPDATED);
        all.expectedBodiesReceived(values);

        Cache<Object, Object> cache = getCache(TEST_CACHE_NAME);
        cache.put(key, values[0]);
        cache.put(key, values[1]);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                fromF("ehcache://%s?cacheManager=#cacheManager&eventTypes=CREATED", TEST_CACHE_NAME)
                    .to("mock:created");
                fromF("ehcache://%s?cacheManager=#cacheManager&eventTypes=UPDATED", TEST_CACHE_NAME)
                    .to("mock:updated");
                fromF("ehcache://%s?cacheManager=#cacheManager", TEST_CACHE_NAME)
                    .to("mock:all");
            }
        };
    }
}
