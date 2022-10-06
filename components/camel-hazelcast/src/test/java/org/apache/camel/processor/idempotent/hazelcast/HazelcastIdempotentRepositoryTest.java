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
package org.apache.camel.processor.idempotent.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HazelcastIdempotentRepositoryTest extends CamelTestSupport {

    private IMap<String, Boolean> cache;
    private HazelcastIdempotentRepository repo;
    private HazelcastInstance hazelcastInstance;

    private String key01 = "123";
    private String key02 = "456";

    @BeforeAll
    void setupHazelcast() throws Exception {
        Config config = new Config();
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        hazelcastInstance = Hazelcast.newHazelcastInstance(null);
        cache = hazelcastInstance.getMap("myRepo");
        repo = new HazelcastIdempotentRepository(hazelcastInstance, "myRepo");
    }

    @AfterAll
    void teardownHazelcast() {
        hazelcastInstance.getLifecycleService().terminate();
    }

    @BeforeEach
    void clearCache() {
        cache.clear();
    }

    @Test
    public void testAdd() throws Exception {
        // ADD first key
        assertTrue(repo.add(key01));
        assertTrue(cache.containsKey(key01));

        // try to ADD the same key again
        assertFalse(repo.add(key01));
        assertEquals(1, cache.size());

        // try to ADD an other one
        assertTrue(repo.add(key02));
        assertEquals(2, cache.size());
    }

    @Test
    public void testConfirm() throws Exception {
        // ADD first key and confirm
        assertTrue(repo.add(key01));
        assertTrue(repo.confirm(key01));

        // try to confirm a key that isn't there
        assertFalse(repo.confirm(key02));
    }

    @Test
    public void testContains() throws Exception {
        assertFalse(repo.contains(key01));

        // ADD key and check again
        assertTrue(repo.add(key01));
        assertTrue(repo.contains(key01));

    }

    @Test
    public void testRemove() throws Exception {
        // ADD key to remove
        assertTrue(repo.add(key01));
        assertTrue(repo.add(key02));
        assertEquals(2, cache.size());

        // CLEAR repo
        repo.clear();
        assertEquals(0, cache.size());
    }

    @Test
    public void testClear() throws Exception {
        // ADD key to remove
        assertTrue(repo.add(key01));
        assertEquals(1, cache.size());

        // remove key
        assertTrue(repo.remove(key01));
        assertEquals(0, cache.size());

        // try to remove a key that isn't there
        assertFalse(repo.remove(key02));
    }

    @Test
    public void testRepositoryInRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:out");
        mock.expectedBodiesReceived("a", "b");
        // c is a duplicate

        // should be started
        assertTrue(repo.getStatus().isStarted(), "Should be started");

        // send 3 message with one duplicated key (key01)
        template.sendBodyAndHeader("direct://in", "a", "messageId", key01);
        template.sendBodyAndHeader("direct://in", "b", "messageId", key02);
        template.sendBodyAndHeader("direct://in", "c", "messageId", key01);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct://in")
                        .idempotentConsumer(header("messageId"), repo)
                        .to("mock://out");
            }
        };
    }

}
