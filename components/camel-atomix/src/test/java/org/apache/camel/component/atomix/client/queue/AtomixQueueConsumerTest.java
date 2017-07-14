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
package org.apache.camel.component.atomix.client.queue;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import io.atomix.collections.DistributedMap;
import io.atomix.collections.DistributedQueue;
import org.apache.camel.Component;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.atomix.client.AtomixClientConstants;
import org.apache.camel.component.atomix.client.AtomixClientTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class AtomixQueueConsumerTest extends AtomixClientTestSupport {
    private static final String QUEUE_NAME = UUID.randomUUID().toString();
    private DistributedQueue<Object> queue;

    // ************************************
    // Setup
    // ************************************

    @Override
    protected Map<String, Component> createComponents() {
        AtomixQueueComponent component = new AtomixQueueComponent();
        component.setNodes(Collections.singletonList(getReplicaAddress()));

        return Collections.singletonMap("atomix-queue", component);
    }

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();

        queue = getClient().getQueue(QUEUE_NAME).join();
    }

    @Override
    public void tearDown() throws Exception {
        queue.close();

        super.tearDown();
    }

    // ************************************
    // Test
    // ************************************

    @Test
    public void testEvents() throws Exception {
        String val1 = context().getUuidGenerator().generateUuid();
        String val2 = context().getUuidGenerator().generateUuid();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(4);

        mock.message(0).body().isEqualTo(val1);
        mock.message(0).header(AtomixClientConstants.EVENT_TYPE).isEqualTo(DistributedQueue.Events.ADD);

        mock.message(1).body().isEqualTo(val2);
        mock.message(1).header(AtomixClientConstants.EVENT_TYPE).isEqualTo(DistributedQueue.Events.ADD);

        mock.message(2).body().isEqualTo(val2);
        mock.message(2).header(AtomixClientConstants.EVENT_TYPE).isEqualTo(DistributedQueue.Events.REMOVE);

        mock.message(3).body().isEqualTo(val1);
        mock.message(3).header(AtomixClientConstants.EVENT_TYPE).isEqualTo(DistributedMap.Events.REMOVE);

        queue.add(val1).join();
        queue.add(val2).join();
        queue.remove(val2).join();
        queue.remove(val1).join();

        mock.assertIsSatisfied();
    }

    // ************************************
    // Routes
    // ************************************

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                fromF("atomix-queue:%s", QUEUE_NAME)
                    .to("mock:result");
            }
        };
    }
}
