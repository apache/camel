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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.junit.Test;

/**
 *
 */
public class SedaQueueFactoryTest extends ContextTestSupport {
    private final ArrayBlockingQueueFactory<Exchange> arrayQueueFactory = new ArrayBlockingQueueFactory<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("arrayQueueFactory", arrayQueueFactory);
        return context;
    }

    @Test
    public void testArrayBlockingQueueFactory() throws Exception {
        SedaEndpoint endpoint = resolveMandatoryEndpoint("seda:arrayQueue?queueFactory=#arrayQueueFactory", SedaEndpoint.class);

        BlockingQueue<Exchange> queue = endpoint.getQueue();
        ArrayBlockingQueue<Exchange> blockingQueue = assertIsInstanceOf(ArrayBlockingQueue.class, queue);
        assertEquals("remainingCapacity - default", SedaConstants.QUEUE_SIZE, blockingQueue.remainingCapacity());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testArrayBlockingQueueFactoryAndSize() throws Exception {
        SedaEndpoint endpoint = resolveMandatoryEndpoint("seda:arrayQueue100?queueFactory=#arrayQueueFactory&size=100", SedaEndpoint.class);

        BlockingQueue<Exchange> queue = endpoint.getQueue();
        ArrayBlockingQueue<Exchange> blockingQueue = assertIsInstanceOf(ArrayBlockingQueue.class, queue);
        assertEquals("remainingCapacity - custom", 100, blockingQueue.remainingCapacity());
    }

    @Test
    public void testDefaultBlockingQueueFactory() throws Exception {
        SedaEndpoint endpoint = resolveMandatoryEndpoint("seda:linkedQueue", SedaEndpoint.class);
        BlockingQueue<Exchange> queue = endpoint.getQueue();
        assertIsInstanceOf(LinkedBlockingQueue.class, queue);
    }
}
