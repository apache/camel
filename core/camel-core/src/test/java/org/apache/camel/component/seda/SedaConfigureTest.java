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
package org.apache.camel.component.seda;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
public class SedaConfigureTest extends ContextTestSupport {

    @SuppressWarnings("unchecked")
    @Test
    public void testBlockingQueueConfigured() throws Exception {
        SedaEndpoint endpoint = resolveMandatoryEndpoint("seda:foo?size=2000", SedaEndpoint.class);
        BlockingQueue<Exchange> queue = endpoint.getQueue();

        LinkedBlockingQueue<Exchange> blockingQueue = assertIsInstanceOf(LinkedBlockingQueue.class, queue);
        assertEquals(2000, blockingQueue.remainingCapacity(), "remainingCapacity");
    }

    @Test
    public void testConcurrentConsumersConfigured() {
        SedaEndpoint endpoint = resolveMandatoryEndpoint("seda:foo?concurrentConsumers=5", SedaEndpoint.class);
        assertEquals(5, endpoint.getConcurrentConsumers(), "concurrentConsumers");
    }

    @Test
    public void testBlockWhenFull() {
        SedaEndpoint endpoint = resolveMandatoryEndpoint("seda:foo?size=2000&blockWhenFull=true", SedaEndpoint.class);
        assertTrue(endpoint.isBlockWhenFull(), "blockWhenFull");
    }

    @Test
    public void testDefaults() {
        SedaEndpoint endpoint = resolveMandatoryEndpoint("seda:foo", SedaEndpoint.class);
        assertFalse(endpoint.isBlockWhenFull(), "blockWhenFull: wrong default");
        assertEquals(1, endpoint.getConcurrentConsumers(), "concurrentConsumers: wrong default");
        assertEquals(SedaConstants.QUEUE_SIZE, endpoint.getSize(), "size (remainingCapacity): wrong default");
        assertEquals(30000L, endpoint.getTimeout(), "timeout: wrong default");
    }
}
