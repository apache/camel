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

import java.util.concurrent.LinkedBlockingQueue;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The messages waiting in the queue of a seda endpoint are kept only in memory, unless the queue is of another kind.
 */
public class SedaInMemoryTest extends ContextTestSupport {

    @Test
    public void testDefaultQueueIsInMemory() {
        SedaEndpoint endpoint = resolveMandatoryEndpoint("seda:inMemory", SedaEndpoint.class);
        // before the queue is created, from the queue factory
        assertTrue(endpoint.isInMemory());

        endpoint.getQueue();
        assertTrue(endpoint.isInMemory());
    }

    @Test
    public void testQueueOfAnotherKindCannotTell() {
        SedaEndpoint endpoint = resolveMandatoryEndpoint("seda:otherQueue", SedaEndpoint.class);
        endpoint.setQueue(new OtherQueue());

        assertFalse(endpoint.isInMemory());
    }

    /**
     * A queue that is not one of the JDK, such as a queue that keeps the messages in a store.
     */
    private static final class OtherQueue extends LinkedBlockingQueue<Exchange> {
    }
}
