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
package org.apache.camel.component.atomix.client.map;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import io.atomix.collections.DistributedMap;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AtomixMapProducerTest extends AtomixClientTestSupport {
    private static final String MAP_NAME = UUID.randomUUID().toString();
    private DistributedMap<Object, Object> map;

    @EndpointInject("direct:start")
    private FluentProducerTemplate fluent;

    // ************************************
    // Setup
    // ************************************

    @Override
    protected Map<String, Component> createComponents() {
        AtomixMapComponent component = new AtomixMapComponent();
        component.setNodes(Collections.singletonList(getReplicaAddress()));

        return Collections.singletonMap("atomix-map", component);
    }

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();

        map = getClient().getMap(MAP_NAME).join();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        map.close();

        super.tearDown();
    }

    // ************************************
    // Test
    // ************************************

    @Test
    void testPut() {
        final String key = context().getUuidGenerator().generateUuid();
        final String val = context().getUuidGenerator().generateUuid();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.PUT)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .withBody(val)
            .request(Message.class);

        assertFalse(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(val, result.getBody());
        assertEquals(val, map.get(key).join());
    }

    @Test
    void testPutWithTTL() throws Exception {
        final String key1 = context().getUuidGenerator().generateUuid();
        final String key2 = context().getUuidGenerator().generateUuid();
        final String val = context().getUuidGenerator().generateUuid();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.PUT)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key1)
            .withHeader(AtomixClientConstants.RESOURCE_TTL, "1000")
            .withBody(val)
            .request(Message.class);

        assertFalse(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(val, result.getBody());
        assertEquals(val, map.get(key1).join());

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.PUT)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key2)
            .withHeader(AtomixClientConstants.RESOURCE_TTL, "250")
            .withBody(val)
            .request(Message.class);

        assertFalse(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(val, result.getBody());
        assertEquals(val, map.get(key2).join());

        CountDownLatch latch = new CountDownLatch(2);
        map.onRemove(key1, e -> latch.countDown());
        map.onRemove(key2, e -> latch.countDown());

        latch.await();

        assertFalse(map.containsKey(key1).join());
        assertFalse(map.containsKey(key2).join());
    }

    @Test
    void testPutIfAbsent() {
        final String key = context().getUuidGenerator().generateUuid();
        final String val1 = context().getUuidGenerator().generateUuid();
        final String val2 = context().getUuidGenerator().generateUuid();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.PUT_IF_ABSENT)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .withBody(val1)
            .request(Message.class);

        assertFalse(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(val1, result.getBody());
        assertEquals(val1, map.get(key).join());

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.PUT_IF_ABSENT)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .withBody(val2)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(val1, result.getBody());
        assertEquals(val1, map.get(key).join());
    }

    @Test
    void testGet() {
        final String key = context().getUuidGenerator().generateUuid();
        final String val = context().getUuidGenerator().generateUuid();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.GET)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .request(Message.class);

        assertFalse(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertFalse(map.containsKey(key).join());

        map.put(key, val).join();

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.GET)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(val, result.getBody(String.class));
        assertTrue(map.containsKey(key).join());
    }

    @Test
    void testSizeClearIsEmpty() {
        final String key = context().getUuidGenerator().generateUuid();
        final String val = context().getUuidGenerator().generateUuid();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.SIZE)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(0, result.getBody(Integer.class).intValue());
        assertEquals(map.size().join(), result.getBody(Integer.class));

        map.put(key, val).join();

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.SIZE)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(1, result.getBody(Integer.class).intValue());
        assertEquals(map.size().join(), result.getBody(Integer.class));


        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.IS_EMPTY)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertFalse(result.getBody(Boolean.class));
        assertFalse(map.isEmpty().join());

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.CLEAR)
            .request(Message.class);

        assertFalse(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(0, map.size().join().intValue());

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.IS_EMPTY)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertTrue(result.getBody(Boolean.class));
        assertTrue(map.isEmpty().join());
    }

    @Test
    void testContainsKey() {
        final String key = context().getUuidGenerator().generateUuid();
        final String val = context().getUuidGenerator().generateUuid();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.CONTAINS_KEY)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .withBody(val)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertFalse(result.getBody(Boolean.class));
        assertFalse(map.containsKey(key).join());

        map.put(key, val).join();

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.CONTAINS_KEY)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .withBody(val)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertTrue(result.getBody(Boolean.class));
        assertTrue(map.containsKey(key).join());
    }

    @Test
    void testContainsValue() {
        final String key = context().getUuidGenerator().generateUuid();
        final String val = context().getUuidGenerator().generateUuid();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.CONTAINS_VALUE)
            .withHeader(AtomixClientConstants.RESOURCE_VALUE, val)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertFalse(result.getBody(Boolean.class));
        assertFalse(map.containsKey(key).join());

        map.put(key, val).join();

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.CONTAINS_VALUE)
            .withHeader(AtomixClientConstants.RESOURCE_VALUE, val)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertTrue(result.getBody(Boolean.class));
        assertTrue(map.containsValue(val).join());
    }

    @Test
    void testRemove() {
        final String key = context().getUuidGenerator().generateUuid();
        final String val = context().getUuidGenerator().generateUuid();

        map.put(key, val).join();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.REMOVE)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .withHeader(AtomixClientConstants.RESOURCE_VALUE, context().getUuidGenerator().generateUuid())
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertFalse(result.getBody(Boolean.class));
        assertTrue(map.containsKey(key).join());

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.REMOVE)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .withHeader(AtomixClientConstants.RESOURCE_VALUE, val)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertTrue(result.getBody(Boolean.class));
        assertFalse(map.containsKey(key).join());

        map.put(key, val).join();

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.REMOVE)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(val, result.getBody(String.class));
        assertFalse(map.containsKey(key).join());
    }

    @Test
    void testReplace() {
        final String key = context().getUuidGenerator().generateUuid();
        final String oldVal = context().getUuidGenerator().generateUuid();
        final String newVal = context().getUuidGenerator().generateUuid();

        map.put(key, oldVal).join();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.REPLACE)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .withHeader(AtomixClientConstants.RESOURCE_OLD_VALUE, context().getUuidGenerator().generateUuid())
            .withBody(newVal)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertFalse(result.getBody(Boolean.class));
        assertEquals(oldVal, map.get(key).join());

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.REPLACE)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .withHeader(AtomixClientConstants.RESOURCE_OLD_VALUE, oldVal)
            .withBody(newVal)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertTrue(result.getBody(Boolean.class));
        assertEquals(newVal, map.get(key).join());

        map.put(key, oldVal).join();

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.REPLACE)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .withBody(newVal)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(oldVal, result.getBody(String.class));
        assertEquals(newVal, map.get(key).join());
    }

    @Test
    void testValues() {
        map.put(context().getUuidGenerator().generateUuid(), context().getUuidGenerator().generateUuid()).join();
        map.put(context().getUuidGenerator().generateUuid(), context().getUuidGenerator().generateUuid()).join();
        map.put(context().getUuidGenerator().generateUuid(), context().getUuidGenerator().generateUuid()).join();

        Message result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.VALUES)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertThat(map.values().join(), is(result.getBody(Collection.class)));
    }

    @Test
    void testEntrySet() {
        map.put(context().getUuidGenerator().generateUuid(), context().getUuidGenerator().generateUuid()).join();
        map.put(context().getUuidGenerator().generateUuid(), context().getUuidGenerator().generateUuid()).join();
        map.put(context().getUuidGenerator().generateUuid(), context().getUuidGenerator().generateUuid()).join();

        Message result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.ENTRY_SET)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(map.entrySet().join().size(), result.getBody(Set.class).size());
    }

    // ************************************
    // Routes
    // ************************************

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .toF("atomix-map:%s", MAP_NAME);
            }
        };
    }
}
