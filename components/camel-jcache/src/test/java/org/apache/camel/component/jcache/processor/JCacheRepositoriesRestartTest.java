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
package org.apache.camel.component.jcache.processor;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jcache.processor.aggregate.JCacheAggregationRepository;
import org.apache.camel.component.jcache.processor.idempotent.JCacheIdempotentRepository;
import org.apache.camel.component.jcache.support.HazelcastTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A repository that opens its cache from its configuration (cacheName) can be stopped and started again, as when its
 * route is removed and added again (route reload): it does not go on with the cache it closed when it stopped.
 */
@HazelcastTest
class JCacheRepositoriesRestartTest extends CamelTestSupport {

    @Test
    void idempotentRepositoryOfReloadedRoute() throws Exception {
        JCacheIdempotentRepository repository = new JCacheIdempotentRepository();
        repository.setCacheName("restart-idempotent");
        context.getRegistry().bind("repo", repository);

        context.addRoutes(idempotentRoute());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a");
        template.sendBodyAndHeader("direct:start", "a", "id", "1");
        mock.assertIsSatisfied();

        // route reload: the route is removed (which stops its idempotent repository) and added again
        context.getRouteController().stopRoute("idempotent");
        context.removeRoute("idempotent");
        context.addRoutes(idempotentRoute());

        // the removal of the route removed its mock endpoint too
        mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("b");
        template.sendBodyAndHeader("direct:start", "b", "id", "2");
        mock.assertIsSatisfied();
    }

    @Test
    void aggregationRepositoryStartedAgain() throws Exception {
        JCacheAggregationRepository repository = new JCacheAggregationRepository();
        repository.setCamelContext(context);
        repository.setCacheName("restart-aggregation");
        repository.start();
        repository.add(context, "k1", exchange("one"));
        repository.stop();

        repository.start();
        repository.add(context, "k2", exchange("two"));
        Exchange stored = repository.get(context, "k2");
        assertNotNull(stored);
        assertEquals("two", stored.getMessage().getBody());
        repository.stop();
    }

    @Test
    void keyValueRepositoryStartedAgain() throws Exception {
        JCacheKeyValueRepository repository = new JCacheKeyValueRepository();
        repository.setCamelContext(context);
        repository.setCacheName("restart-kv");
        repository.start();
        repository.put("k1", "one", null);
        repository.stop();

        repository.start();
        repository.put("k2", "two", null);
        assertEquals("two", repository.get("k2"));
        repository.stop();
    }

    @Test
    void idempotentRepositoryStartedAgain() throws Exception {
        JCacheIdempotentRepository repository = new JCacheIdempotentRepository();
        repository.setCamelContext(context);
        repository.setCacheName("restart-idempotent-2");
        repository.start();
        assertTrue(repository.add("k1"));
        repository.stop();

        repository.start();
        assertTrue(repository.add("k2"));
        assertFalse(repository.add("k2"));
        repository.stop();
    }

    private static RouteBuilder idempotentRoute() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("idempotent")
                        .idempotentConsumer(header("id")).idempotentRepository("repo")
                        .to("mock:result");
            }
        };
    }

    private Exchange exchange(String body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);
        return exchange;
    }
}
