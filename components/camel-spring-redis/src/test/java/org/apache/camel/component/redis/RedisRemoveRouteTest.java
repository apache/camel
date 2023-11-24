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
package org.apache.camel.component.redis;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.data.redis.core.RedisTemplate;

public class RedisRemoveRouteTest extends CamelTestSupport {

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = new SimpleRegistry();
        redisTemplate = new RedisTemplate<>();

        registry.bind("redisTemplate", redisTemplate);
        return registry;
    }

    @Mock
    protected RedisTemplate<String, String> redisTemplate;

    private static final String ROUTE_ID = "spring-redis-test-route";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .routeId(ROUTE_ID)
                        .to("spring-redis://localhost:6379?redisTemplate=#redisTemplate");
            }
        };
    }

    @Test
    public void testRemoveRouteShouldRemoveAllEndpoints() throws Exception {
        // two endpoints available after starting the route
        Assertions.assertEquals(2, context.getEndpoints().size());

        // stop it before trying to remove it, otherwise it won't get removed
        context.getRouteController().stopRoute(ROUTE_ID);
        context.removeRoute(ROUTE_ID);

        // after the route ahs been removed all endpoints should be removed
        Assertions.assertEquals(0, context.getEndpoints().size());
    }
}
