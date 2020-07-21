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
package org.apache.camel.component.atomix.client.queue;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import io.atomix.collections.DistributedQueue;
import org.apache.camel.Component;
import org.apache.camel.EndpointInject;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.Message;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.atomix.client.AtomixClientConstants;
import org.apache.camel.component.atomix.client.AtomixClientTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AtomixQueueProducerTest extends AtomixClientTestSupport {
    private static final String QUEUE_NAME = UUID.randomUUID().toString();
    private DistributedQueue<Object> queue;

    @EndpointInject("direct:start")
    private FluentProducerTemplate fluent;

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
    @AfterEach
    public void tearDown() throws Exception {
        queue.close();

        super.tearDown();
    }

    // ************************************
    // Test
    // ************************************

    @Test
    void testAdd() {
        final String val1 = context().getUuidGenerator().generateUuid();
        final String val2 = context().getUuidGenerator().generateUuid();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixQueue.Action.ADD)
            .withBody(val1)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertTrue(queue.contains(val1).join());

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixQueue.Action.ADD)
            .withBody(val2)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertTrue(queue.contains(val2).join());
    }

    @Test
    void testOfferPeekAndPoll() {
        final String val = context().getUuidGenerator().generateUuid();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixQueue.Action.OFFER)
            .withBody(val)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertTrue(queue.contains(val).join());

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixQueue.Action.PEEK)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertTrue(queue.contains(val).join());
        assertEquals(val, result.getBody());

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixQueue.Action.POLL)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertFalse(queue.contains(val).join());
        assertEquals(val, result.getBody());
    }

    @Test
    void testSizeClearIsEmpty() {
        final String val1 = context().getUuidGenerator().generateUuid();
        final String val2 = context().getUuidGenerator().generateUuid();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixQueue.Action.SIZE)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(0, result.getBody(Integer.class).intValue());
        assertEquals(queue.size().join(), result.getBody(Integer.class));

        queue.add(val1).join();
        queue.add(val2).join();

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixQueue.Action.SIZE)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(queue.size().join(), result.getBody(Integer.class));

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixQueue.Action.IS_EMPTY)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertFalse(result.getBody(Boolean.class));
        assertFalse(queue.isEmpty().join());

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixQueue.Action.CLEAR)
            .request(Message.class);

        assertFalse(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(0, queue.size().join().intValue());

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixQueue.Action.IS_EMPTY)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertTrue(result.getBody(Boolean.class));
        assertTrue(queue.isEmpty().join());
    }

    @Test
    void testContains() {
        final String val = context().getUuidGenerator().generateUuid();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixQueue.Action.CONTAINS)
            .withBody(val)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertFalse(result.getBody(Boolean.class));
        assertFalse(queue.contains(val).join());

        queue.add(val);

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixQueue.Action.CONTAINS)
            .withBody(val)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertTrue(result.getBody(Boolean.class));
        assertTrue(queue.contains(val).join());
    }

    @Test
    void testRemove() {
        final String val1 = context().getUuidGenerator().generateUuid();
        final String val2 = context().getUuidGenerator().generateUuid();

        queue.add(val1).join();
        queue.add(val2).join();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixQueue.Action.REMOVE)
            .withHeader(AtomixClientConstants.RESOURCE_VALUE, val1)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertFalse(queue.contains(val1).join());
        assertTrue(queue.contains(val2).join());
    }

    // ************************************
    // Routes
    // ************************************
    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .toF("atomix-queue:%s", QUEUE_NAME);
            }
        };
    }
}
