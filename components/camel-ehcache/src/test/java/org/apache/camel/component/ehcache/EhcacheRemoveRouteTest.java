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
package org.apache.camel.component.ehcache;

import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.ehcache.CacheManager;
import org.ehcache.Status;
import org.ehcache.UserManagedCache;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Removing the route that consumes the events of a cache must not close the cache that the other routes still use.
 */
class EhcacheRemoveRouteTest extends CamelTestSupport {

    @BindToRegistry("ordersConfig")
    private final CacheConfiguration<Object, Object> ordersConfig = CacheConfigurationBuilder
            .newCacheConfigurationBuilder(Object.class, Object.class, ResourcePoolsBuilder.heap(100)).build();

    // a cache manager from the registry, with the cache "events" configured and the cache "orders" created on demand
    @BindToRegistry("myManager")
    private final CacheManager myManager = CacheManagerBuilder.newCacheManagerBuilder()
            .withCache("events", CacheConfigurationBuilder.newCacheConfigurationBuilder(Object.class, Object.class,
                    ResourcePoolsBuilder.heap(100)))
            .build(true);

    @Override
    public void doPostTearDown() {
        myManager.close();
    }

    @Test
    void removeEventRouteOfSameEndpoint() throws Exception {
        MockEndpoint events = getMockEndpoint("mock:events");
        events.expectedBodiesReceived("v1");
        put("direct:same", "k1", "v1");
        events.assertIsSatisfied();

        context.getRouteController().stopRoute("events");
        context.removeRoute("events");

        put("direct:same", "k2", "v2");
        assertEquals("v2", get("direct:same", "k2"));
    }

    @Test
    void removeEventRouteOfSharedCacheManager() throws Exception {
        MockEndpoint events = getMockEndpoint("mock:sharedEvents");
        events.expectedBodiesReceived("e1");
        put("direct:sharedEvents", "k1", "e1");
        events.assertIsSatisfied();
        put("direct:shared", "k1", "v1");

        context.getRouteController().stopRoute("sharedEvents");
        context.removeRoute("sharedEvents");

        put("direct:shared", "k2", "v2");
        assertEquals("v2", get("direct:shared", "k2"));
        assertEquals("v1", get("direct:shared", "k1"));
    }

    @Test
    void removeEventRouteThenAddItAgain() throws Exception {
        put("direct:same", "k1", "v1");

        context.getRouteController().stopRoute("events");
        context.removeRoute("events");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("ehcache:orders?configuration=#ordersConfig").routeId("events2").to("mock:events2");
            }
        });

        MockEndpoint events = getMockEndpoint("mock:events2");
        events.expectedBodiesReceived("v2");
        put("direct:same", "k2", "v2");
        events.assertIsSatisfied();
        assertEquals("v2", get("direct:same", "k2"));
    }

    @Test
    void removeEventRouteThatNeverStarted() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("ehcache:orders?configuration=#ordersConfig").routeId("standby").autoStartup(false)
                        .to("mock:standby");
            }
        });

        // Camel stops the consumer of the removed route, which never started
        context.removeRoute("standby");

        MockEndpoint events = getMockEndpoint("mock:events");
        events.expectedBodiesReceived("v1");
        put("direct:same", "k1", "v1");
        events.assertIsSatisfied();
        assertEquals("v1", get("direct:same", "k1"));
    }

    @Test
    void removeEventRouteThenStopClosesCacheCreatedOnDemand() throws Exception {
        put("direct:plain", "k1", "v1");

        context.getRouteController().stopRoute("plainEvents");
        context.removeRoute("plainEvents");

        put("direct:plain", "k2", "v2");
        assertEquals("v2", get("direct:plain", "k2"));

        // without a configuration the endpoint's own EhcacheManager creates a user managed cache
        EhcacheEndpoint endpoint = context.getEndpoint("ehcache:plain", EhcacheEndpoint.class);
        UserManagedCache<?, ?> cache
                = (UserManagedCache<?, ?>) endpoint.getManager().getCache("plain", Object.class, Object.class);
        assertEquals(Status.AVAILABLE, cache.getStatus());

        // the endpoint was shut down with the removed route while the producer still used the cache: the producer, as
        // the last user, closes it
        context.stop();
        assertEquals(Status.UNINITIALIZED, cache.getStatus());
    }

    private void put(String uri, String key, String value) {
        template.sendBodyAndHeaders(uri, value,
                Map.of(EhcacheConstants.ACTION, EhcacheConstants.ACTION_PUT, EhcacheConstants.KEY, key));
    }

    private Object get(String uri, String key) {
        return template.requestBodyAndHeaders(uri, null,
                Map.of(EhcacheConstants.ACTION, EhcacheConstants.ACTION_GET, EhcacheConstants.KEY, key));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the default cache manager of the endpoint: the consumer and the producer use the same endpoint
                from("ehcache:orders?configuration=#ordersConfig").routeId("events").to("mock:events");
                from("direct:same").to("ehcache:orders?configuration=#ordersConfig");

                // a cache manager from the registry, which the endpoints share
                from("ehcache:events?cacheManager=#myManager").routeId("sharedEvents").to("mock:sharedEvents");
                from("direct:sharedEvents").to("ehcache:events?cacheManager=#myManager");
                from("direct:shared").to("ehcache:orders?cacheManager=#myManager");

                // no cache manager option and no configuration: a cache created on demand in the endpoint's manager
                from("ehcache:plain").routeId("plainEvents").to("mock:plainEvents");
                from("direct:plain").to("ehcache:plain");
            }
        };
    }
}
