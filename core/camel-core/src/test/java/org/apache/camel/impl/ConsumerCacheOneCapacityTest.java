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
package org.apache.camel.impl;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.PollingConsumer;
import org.apache.camel.support.cache.DefaultConsumerCache;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class ConsumerCacheOneCapacityTest extends ContextTestSupport {

    @Test
    public void testConsumerCacheOneCapacity() throws Exception {
        DefaultConsumerCache cache = new DefaultConsumerCache(this, context, 1);
        cache.start();

        assertEquals(0, cache.size(), "Size should be 0");

        Endpoint endpoint = context.getEndpoint("file:target/data/foo?fileName=foo.txt&initialDelay=0&delay=10");
        PollingConsumer consumer = cache.acquirePollingConsumer(endpoint);
        assertNotNull(consumer);
        assertEquals("Started", ((ServiceSupport) consumer).getStatus().name());

        // let it run a poll
        consumer.receive(50);

        boolean found = Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.getName().contains("target/data/foo"));
        assertFalse(found, "Should not find file consumer thread");

        cache.releasePollingConsumer(endpoint, consumer);

        // takes a little to stop
        assertTrue(((ServiceSupport) consumer).isStarted(), "Should still be started");

        cache.stop();

        // takes a little to stop
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals("Stopped", ((ServiceSupport) consumer).getStatus().name()));

        // should not be a file consumer thread
        found = Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.getName().contains("target/data/foo"));
        assertFalse(found, "Should not find file consumer thread");
    }

}
