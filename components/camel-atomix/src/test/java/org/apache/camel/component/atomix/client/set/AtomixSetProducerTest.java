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
package org.apache.camel.component.atomix.client.set;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import io.atomix.collections.DistributedSet;
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

public class AtomixSetProducerTest extends AtomixClientTestSupport {
    private static final String SET_NAME = UUID.randomUUID().toString();
    private DistributedSet<Object> set;

    @EndpointInject("direct:start")
    private FluentProducerTemplate fluent;

    // ************************************
    // Setup
    // ************************************

    @Override
    protected Map<String, Component> createComponents() {
        AtomixSetComponent component = new AtomixSetComponent();
        component.setNodes(Collections.singletonList(getReplicaAddress()));

        return Collections.singletonMap("atomix-set", component);
    }

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();

        set = getClient().getSet(SET_NAME).join();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        set.close();

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
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixSet.Action.ADD)
            .withBody(val1)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertTrue(set.contains(val1).join());

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixSet.Action.ADD)
            .withBody(val2)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertTrue(set.contains(val2).join());
    }

    @Test
    void testSizeClearIsEmpty() {
        final String val1 = context().getUuidGenerator().generateUuid();
        final String val2 = context().getUuidGenerator().generateUuid();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixSet.Action.SIZE)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(0, result.getBody(Integer.class).intValue());
        assertEquals(set.size().join(), result.getBody(Integer.class));

        set.add(val1).join();
        set.add(val2).join();

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixSet.Action.SIZE)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(set.size().join(), result.getBody(Integer.class));

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixSet.Action.IS_EMPTY)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertFalse(result.getBody(Boolean.class));
        assertFalse(set.isEmpty().join());

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixSet.Action.CLEAR)
            .request(Message.class);

        assertFalse(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(0, set.size().join().intValue());

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixSet.Action.IS_EMPTY)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertTrue(result.getBody(Boolean.class));
        assertTrue(set.isEmpty().join());
    }

    @Test
    void testContains() {
        final String val = context().getUuidGenerator().generateUuid();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixSet.Action.CONTAINS)
            .withBody(val)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertFalse(result.getBody(Boolean.class));
        assertFalse(set.contains(val).join());

        set.add(val);

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixSet.Action.CONTAINS)
            .withBody(val)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertTrue(result.getBody(Boolean.class));
        assertTrue(set.contains(val).join());
    }

    @Test
    void testRemove() {
        final String val1 = context().getUuidGenerator().generateUuid();
        final String val2 = context().getUuidGenerator().generateUuid();

        set.add(val1).join();
        set.add(val2).join();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixSet.Action.REMOVE)
            .withHeader(AtomixClientConstants.RESOURCE_VALUE, val1)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertFalse(set.contains(val1).join());
        assertTrue(set.contains(val2).join());
    }

    // ************************************
    // Routes
    // ************************************

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .toF("atomix-set:%s", SET_NAME);
            }
        };
    }
}
