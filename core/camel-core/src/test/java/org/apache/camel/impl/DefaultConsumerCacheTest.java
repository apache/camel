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
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DefaultConsumerCacheTest extends ContextTestSupport {

    @Test
    public void testCacheConsumers() throws Exception {
        DefaultConsumerCache cache = new DefaultConsumerCache(this, context, 0);
        cache.start();

        assertEquals(0, cache.size(), "Size should be 0");

        // test that we cache at most 1000 consumers to avoid it eating to much memory
        for (int i = 0; i < 1003; i++) {
            Endpoint e = context.getEndpoint("direct:queue:" + i);
            PollingConsumer p = cache.acquirePollingConsumer(e);
            assertNotNull(p, "the polling consumer should not be null");
        }

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            // the eviction is async so force cleanup
            cache.cleanUp();
            assertEquals(1000, cache.size(), "Size should be 1000");
        });

        cache.stop();
    }

}
