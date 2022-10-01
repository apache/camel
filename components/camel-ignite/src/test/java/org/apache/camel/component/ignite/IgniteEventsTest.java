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
package org.apache.camel.component.ignite;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ignite.events.IgniteEventsComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.events.Event;
import org.apache.ignite.events.EventType;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IgniteEventsTest extends AbstractIgniteTest {

    @Override
    protected String getScheme() {
        return "ignite-events";
    }

    @Override
    protected AbstractIgniteComponent createComponent() {
        return IgniteEventsComponent.fromConfiguration(createConfiguration());
    }

    @Test
    public void testConsumeAllEvents() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("ignite-events:" + resourceUid).to("mock:test1");
            }
        });

        getMockEndpoint("mock:test1").expectedMinimumMessageCount(9);

        IgniteCache<String, String> cache = ignite().getOrCreateCache(resourceUid);

        // Generate cache activity.
        cache.put(resourceUid, "123");
        cache.get(resourceUid);
        cache.remove(resourceUid);
        cache.withExpiryPolicy(CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.MILLISECONDS, 100)).create())
                .put(resourceUid, "123");

        await().atMost(150, TimeUnit.MILLISECONDS)
                .until(() -> cache.get(resourceUid), Matchers.nullValue());

        MockEndpoint.assertIsSatisfied(context);

        List<Integer> eventTypes = receivedEventTypes("mock:test1");

        Assertions.assertThat(eventTypes).containsSubsequence(EventType.EVT_CACHE_STARTED, EventType.EVT_CACHE_ENTRY_CREATED,
                EventType.EVT_CACHE_OBJECT_PUT, EventType.EVT_CACHE_OBJECT_READ,
                EventType.EVT_CACHE_OBJECT_REMOVED, EventType.EVT_CACHE_OBJECT_PUT, EventType.EVT_CACHE_OBJECT_EXPIRED);

    }

    @Test
    public void testConsumeFilteredEventsInline() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("ignite-events:" + resourceUid + "?events=EVT_CACHE_OBJECT_PUT").to("mock:test3");
            }
        });

        getMockEndpoint("mock:test3").expectedMessageCount(2);

        IgniteCache<String, String> cache = ignite().getOrCreateCache(resourceUid);

        // Generate cache activity.
        cache.put(resourceUid, "123");
        cache.get(resourceUid);
        cache.remove(resourceUid);
        cache.get(resourceUid);
        cache.put(resourceUid, "123");

        MockEndpoint.assertIsSatisfied(context);

        List<Integer> eventTypes = receivedEventTypes("mock:test3");

        Assertions.assertThat(eventTypes).containsExactly(EventType.EVT_CACHE_OBJECT_PUT, EventType.EVT_CACHE_OBJECT_PUT);

    }

    private List<Integer> receivedEventTypes(String mockEndpoint) {
        List<Integer> eventTypes = Lists
                .newArrayList(Lists.transform(getMockEndpoint(mockEndpoint).getExchanges(), new Function<Exchange, Integer>() {
                    @Override
                    public Integer apply(Exchange input) {
                        return input.getIn().getBody(Event.class).type();
                    }
                }));
        return eventTypes;
    }

    @AfterEach
    public void stopAllRoutes() throws Exception {
        for (Route route : context.getRoutes()) {
            if (context.getRouteController().getRouteStatus(route.getId()) != ServiceStatus.Started) {
                return;
            }
            context.getRouteController().stopRoute(route.getId());
        }
        MockEndpoint.resetMocks(context);
    }

    @Override
    protected IgniteConfiguration createConfiguration() {
        IgniteConfiguration config = new IgniteConfiguration();
        config.setIncludeEventTypes(EventType.EVTS_ALL_MINUS_METRIC_UPDATE);
        return config;
    }

}
