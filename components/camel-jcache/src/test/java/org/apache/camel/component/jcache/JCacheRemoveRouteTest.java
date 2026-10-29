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
package org.apache.camel.component.jcache;

import java.util.Map;

import javax.cache.Cache;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Consumer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Removing a route that uses a jcache endpoint must not close the caches that the routes of other endpoints still use.
 */
class JCacheRemoveRouteTest extends JCacheComponentTestSupport {

    @Test
    void removingARouteKeepsTheCachesOfTheOtherRoutes() throws Exception {
        put("direct:a", "k", "v");
        put("direct:temp", "t", "x");

        context.getRouteController().stopRoute("temp");
        context.removeRoute("temp");

        assertEquals("v", get("direct:a", "k"));
    }

    @Test
    void removingTheConsumerRouteOfACacheKeepsItsProducer() throws Exception {
        MockEndpoint events = getMockEndpoint("mock:events");
        events.expectedMessageCount(1);
        put("direct:b", "k1", "v1");
        events.assertIsSatisfied();

        context.getRouteController().stopRoute("events");
        context.removeRoute("events");

        put("direct:b", "k2", "v2");
        assertEquals("v2", get("direct:b", "k2"));
    }

    /**
     * KNOWN LIMITATION, not the wanted behaviour: this pins what happens today so that a change to it is noticed.
     * Removing a route stops the endpoint of its consumer, also when another route still produces to the same uri
     * (camel-core RouteService.doShutdown), and this fix does not change that: the producer's sends then fail. When the
     * endpoint is kept for its producer (a core fix, or producers and consumers that hold the cache, as CAMEL-26302
     * does in camel-ehcache), this test must expect the sends to work instead.
     */
    @Test
    void knownLimitationRemovingTheConsumerRouteOfTheSameUriStopsTheEndpointOfItsProducer() throws Exception {
        put("direct:same", "k", "v");

        // the producer of route "same" uses the endpoint of the removed route, which stops with it (as before): its
        // sends fail, rather than opening the cache again where nothing would close it
        context.getRouteController().stopRoute("sameEvents");
        context.removeRoute("sameEvents");

        Exception e = assertThrows(CamelExecutionException.class, () -> put("direct:same", "k2", "v2"));
        assertInstanceOf(IllegalStateException.class, e.getCause());
    }

    @Test
    void consumerStopsAfterTheEndpointWasStoppedWithAnotherRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jcache://remove-route-standby").routeId("standby").autoStartup(false).to("mock:standby");
            }
        });

        // removing the route that never started stops the endpoint the running route consumes from too
        context.removeRoute("standby");

        // the running consumer still stops: a failure in its doStop would be thrown here (the route controller only
        // logs it)
        Consumer consumer = context.getRoute("standbyRunning").getConsumer();
        assertDoesNotThrow(consumer::stop);
    }

    @Test
    void theLastManagerOfACacheClosesIt() throws Exception {
        JCacheConfiguration conf = new JCacheConfiguration();
        conf.setCacheName(randomString());
        JCacheManager<Object, Object> first = new JCacheManager<>(conf);
        JCacheManager<Object, Object> second = new JCacheManager<>(conf);
        Cache<Object, Object> cache = first.getCache();
        assertEquals(cache, second.getCache());

        first.close();
        assertFalse(cache.isClosed());
        cache.put("k", "v");

        second.close();
        assertTrue(cache.isClosed());
    }

    private void put(String uri, String key, String value) {
        template.sendBodyAndHeaders(uri, value,
                Map.of(JCacheConstants.ACTION, "PUT", JCacheConstants.KEY, key));
    }

    private Object get(String uri, String key) {
        return template.requestBodyAndHeaders(uri, null,
                Map.of(JCacheConstants.ACTION, "GET", JCacheConstants.KEY, key));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").routeId("a").to("jcache://remove-route-a");
                from("direct:temp").routeId("temp").to("jcache://remove-route-temp");

                from("direct:b").routeId("b").to("jcache://remove-route-b");
                from("jcache://remove-route-b?filteredEvents=UPDATED,REMOVED,EXPIRED").routeId("events")
                        .to("mock:events");

                // a consumer and a producer of the same uri share one endpoint
                from("direct:same").routeId("same").to("jcache://remove-route-same");
                from("jcache://remove-route-same").routeId("sameEvents").to("mock:same");

                from("jcache://remove-route-standby").routeId("standbyRunning").to("mock:standbyRunning");
            }
        };
    }
}
