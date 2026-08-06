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
package org.apache.camel.component.statestore.redis;

import java.util.Map;
import java.util.Properties;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.statestore.StateStoreBackend;
import org.apache.camel.component.statestore.StateStoreConstants;
import org.apache.camel.test.infra.redis.services.RedisService;
import org.apache.camel.test.infra.redis.services.RedisServiceFactory;
import org.apache.camel.test.main.junit5.CamelMainTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests that a Redis backend can be fully configured via properties (camel.beans.*) and auto-discovered without
 * explicit backend=# reference on the endpoint.
 */
class RedisStateStorePropertiesIT extends CamelMainTestSupport {

    @RegisterExtension
    static RedisService service = RedisServiceFactory.createService();

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        Properties props = new Properties();
        props.setProperty("camel.beans.redisBackend",
                "#class:" + RedisStateStoreBackend.class.getName());
        props.setProperty("camel.beans.redisBackend.redisUrl", "redis://" + service.getServiceAddress());
        props.setProperty("camel.beans.redisBackend.mapName", "test-props-" + System.nanoTime());
        return props;
    }

    @Test
    void testBackendConfiguredViaProperties() {
        StateStoreBackend backend = context.getRegistry().lookupByNameAndType("redisBackend", StateStoreBackend.class);
        assertNotNull(backend);
        assertInstanceOf(RedisStateStoreBackend.class, backend);
    }

    @Test
    void testAutoDiscoveryPutAndGet() {
        // No backend=#redisBackend in the URI — auto-discovery should find it
        Object previous = template.requestBodyAndHeaders(
                "direct:put", "hello",
                Map.of(StateStoreConstants.KEY, "key1"));
        assertNull(previous);

        Object result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "key1"));
        assertEquals("hello", result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // No backend=# reference — relies on auto-discovery
                from("direct:put").to("state-store:redisStore?operation=put");
                from("direct:get").to("state-store:redisStore?operation=get");
            }
        };
    }
}
