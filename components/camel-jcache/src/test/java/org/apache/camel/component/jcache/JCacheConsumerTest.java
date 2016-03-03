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
package org.apache.camel.component.jcache;

import javax.cache.Cache;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.event.EventType;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

public class JCacheConsumerTest extends JCacheComponentTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("myFilter", new CacheEntryEventFilter<Object, Object>() {
            @Override
            public boolean evaluate(CacheEntryEvent<?, ?> event) throws CacheEntryListenerException {
                if (event.getEventType() == EventType.REMOVED) {
                    return false;
                }

                return !event.getValue().toString().startsWith("to-filter-");
            }
        });

        return registry;
    }

    @Test
    public void testFilters() throws Exception {
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        final String key  = randomString();
        final String val1 = "to-filter-" + randomString();
        final String val2 = randomString();

        cache.put(key, val1);
        cache.put(key, val2);
        cache.remove(key);

        MockEndpoint mockCreated = getMockEndpoint("mock:created");
        mockCreated.expectedMinimumMessageCount(1);
        mockCreated.expectedHeaderReceived(JCacheConstants.KEY, key);
        mockCreated.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                return exchange.getIn().getBody(String.class).equals(val1);
            }
        });

        MockEndpoint mockUpdated = getMockEndpoint("mock:updated");
        mockUpdated.expectedMinimumMessageCount(1);
        mockUpdated.expectedHeaderReceived(JCacheConstants.KEY, key);
        mockUpdated.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                return exchange.getIn().getBody(String.class).equals(val2);
            }
        });

        MockEndpoint mockRemoved = getMockEndpoint("mock:removed");
        mockRemoved.expectedMinimumMessageCount(1);
        mockRemoved.expectedHeaderReceived(JCacheConstants.KEY, key);
        mockRemoved.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                return exchange.getIn().getBody(String.class).equals(val2);
            }
        });

        MockEndpoint mockMyFilter = getMockEndpoint("mock:my-filter");
        mockMyFilter.expectedMinimumMessageCount(1);
        mockMyFilter.expectedHeaderReceived(JCacheConstants.KEY, key);
        mockMyFilter.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                return exchange.getIn().getBody(String.class).equals(val2);
            }
        });

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("jcache://test-cache?filteredEvents=UPDATED,REMOVED,EXPIRED")
                    .to("mock:created");
                from("jcache://test-cache?filteredEvents=CREATED,REMOVED,EXPIRED")
                    .to("mock:updated");
                from("jcache://test-cache?filteredEvents=CREATED,UPDATED,EXPIRED")
                    .to("mock:removed");
                from("jcache://test-cache?eventFilters=#myFilter")
                    .to("mock:my-filter");
            }
        };
    }
}
