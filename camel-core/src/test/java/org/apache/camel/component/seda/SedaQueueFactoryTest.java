/**
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
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;

/**
 *
 */
public class SedaQueueFactoryTest extends ContextTestSupport {
    private final ArrayBlockingQueueFactory<Exchange> arrayQueueFactory = new ArrayBlockingQueueFactory<Exchange>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        SimpleRegistry simpleRegistry = new SimpleRegistry();
        simpleRegistry.put("arrayQueueFactory", arrayQueueFactory);
        return new DefaultCamelContext(simpleRegistry);
    }

   
    public void testArrayBlockingQueueFactory() throws Exception {
        SedaEndpoint endpoint = resolveMandatoryEndpoint("seda:arrayQueue?queueFactory=#arrayQueueFactory", SedaEndpoint.class);

        BlockingQueue<Exchange> queue = endpoint.getQueue();
        assertIsInstanceOf(ArrayBlockingQueue.class, queue);
    }

    @SuppressWarnings("unchecked")
    public void testArrayBlockingQueueFactoryAndSize() throws Exception {
        SedaEndpoint endpoint = resolveMandatoryEndpoint("seda:arrayQueue50?queueFactory=#arrayQueueFactory&size=50", SedaEndpoint.class);

        BlockingQueue<Exchange> queue = endpoint.getQueue();
        ArrayBlockingQueue<Exchange> blockingQueue = assertIsInstanceOf(ArrayBlockingQueue.class, queue);
        assertEquals("remainingCapacity", 50, blockingQueue.remainingCapacity());
    }

    
    public void testDefaultBlockingQueueFactory() throws Exception {
        SedaEndpoint endpoint = resolveMandatoryEndpoint("seda:linkedQueue", SedaEndpoint.class);
        BlockingQueue<Exchange> queue = endpoint.getQueue();
        assertIsInstanceOf(LinkedBlockingQueue.class, queue);
    }
}
