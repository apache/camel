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
package org.apache.camel.component.atomix.client.multimap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import io.atomix.collections.DistributedMultiMap;
import org.apache.camel.Component;
import org.apache.camel.EndpointInject;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.Message;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.atomix.client.AtomixClientConstants;
import org.apache.camel.component.atomix.client.AtomixClientTestSupport;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class AtomixMultiMapProducerTest extends AtomixClientTestSupport {
    private static final String MAP_NAME = UUID.randomUUID().toString();
    private DistributedMultiMap<Object, Object> map;

    @EndpointInject(uri = "direct:start")
    private FluentProducerTemplate fluent;

    // ************************************
    // Setup
    // ************************************

    @Override
    protected Map<String, Component> createComponents() {
        AtomixMultiMapComponent component = new AtomixMultiMapComponent();
        component.setNodes(Collections.singletonList(getReplicaAddress()));

        return Collections.singletonMap("atomix-multimap", component);
    }

    @Override
    protected void doPreSetup() throws Exception {
        super.doPreSetup();

        map = getClient().getMultiMap(MAP_NAME).join();
    }

    @Override
    public void tearDown() throws Exception {
        map.close();

        super.tearDown();
    }

    // ************************************
    // Test
    // ************************************

    @Test
    public void testPut() throws Exception {
        final String key  = context().getUuidGenerator().generateUuid();
        final String val1 = context().getUuidGenerator().generateUuid();
        final String val2 = context().getUuidGenerator().generateUuid();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMultiMap.Action.PUT)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .withBody(val1)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertTrue(result.getBody(Boolean.class));
        assertEquals(Arrays.asList(val1), map.get(key).join());

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMultiMap.Action.PUT)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .withBody(val2)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertTrue(result.getBody(Boolean.class));
        assertEquals(Arrays.asList(val1, val2), map.get(key).join());
    }

    @Test
    public void testGet() throws Exception {
        final String key = context().getUuidGenerator().generateUuid();
        final String val = context().getUuidGenerator().generateUuid();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMultiMap.Action.GET)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertTrue(result.getBody(Collection.class).isEmpty());
        assertFalse(map.containsKey(key).join());

        map.put(key, val).join();

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMultiMap.Action.GET)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(Arrays.asList(val), result.getBody(Collection.class));
        assertTrue(map.containsKey(key).join());
    }

    @Test
    public void testSizeClearIsEmpty() throws Exception {
        final String key1 = context().getUuidGenerator().generateUuid();
        final String key2 = context().getUuidGenerator().generateUuid();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMultiMap.Action.SIZE)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(0, result.getBody(Integer.class).intValue());
        assertEquals(map.size().join(), result.getBody(Integer.class));

        map.put(key1, context().getUuidGenerator().generateUuid()).join();
        map.put(key1, context().getUuidGenerator().generateUuid()).join();
        map.put(key2, context().getUuidGenerator().generateUuid()).join();

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMultiMap.Action.SIZE)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(map.size().join(), result.getBody(Integer.class));

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMultiMap.Action.SIZE)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key1)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(map.size(key1).join(), result.getBody(Integer.class));

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMultiMap.Action.IS_EMPTY)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertFalse(result.getBody(Boolean.class));
        assertFalse(map.isEmpty().join());

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMultiMap.Action.CLEAR)
            .request(Message.class);

        assertFalse(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(0, map.size().join().intValue());

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMultiMap.Action.IS_EMPTY)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertTrue(result.getBody(Boolean.class));
        assertTrue(map.isEmpty().join());
    }

    @Test
    public void testContainsKey() throws Exception {
        final String key = context().getUuidGenerator().generateUuid();
        final String val = context().getUuidGenerator().generateUuid();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMultiMap.Action.CONTAINS_KEY)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .withBody(val)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertFalse(result.getBody(Boolean.class));
        assertFalse(map.containsKey(key).join());

        map.put(key, val).join();

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMultiMap.Action.CONTAINS_KEY)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .withBody(val)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertTrue(result.getBody(Boolean.class));
        assertTrue(map.containsKey(key).join());
    }

//    @Test
//    public void testContainsValue() throws Exception {
//        final String key = context().getUuidGenerator().generateUuid();
//        final String val1 = context().getUuidGenerator().generateUuid();
//        final String val2 = context().getUuidGenerator().generateUuid();
//
//        Message result;
//
//        result = fluent.clearAll()
//            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixClientMultiMapAction.CONTAINS_VALUE)
//            .withHeader(AtomixClientConstants.RESOURCE_VALUE, val1)
//            .request(Message.class);
//
//        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
//        assertFalse(result.getBody(Boolean.class));
//        assertFalse(map.containsValue(val1).join());
//
//        map.put(key, val1).join();
//        map.put(key, val2).join();
//
//        result = fluent.clearAll()
//            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixClientMultiMapAction.CONTAINS_VALUE)
//            .withHeader(AtomixClientConstants.RESOURCE_VALUE, val1)
//            .request(Message.class);
//
//        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
//        assertTrue(result.getBody(Boolean.class));
//        assertTrue(map.containsValue(val1).join());
//
//        result = fluent.clearAll()
//            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixClientMultiMapAction.CONTAINS_VALUE)
//            .withHeader(AtomixClientConstants.RESOURCE_VALUE, val2)
//            .request(Message.class);
//
//        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
//        assertTrue(result.getBody(Boolean.class));
//        assertTrue(map.containsValue(val2).join());
//    }

//
//    @Test
//    public void testContainsEntry() throws Exception {
//        final String key = context().getUuidGenerator().generateUuid();
//        final String val1 = context().getUuidGenerator().generateUuid();
//        final String val2 = context().getUuidGenerator().generateUuid();
//        map.put(key, val1).join();
//        map.put(key, val2).join();
//
//        Message result;
//
//        result = fluent.clearAll()
//            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixClientMultiMapAction.CONTAINS_ENTRY)
//            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
//            .withHeader(AtomixClientConstants.RESOURCE_VALUE, val1)
//            .request(Message.class);
//
//        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
//        assertTrue(result.getBody(Boolean.class));
//        assertTrue(map.containsEntry(key, val1).join());
//
//        result = fluent.clearAll()
//            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixClientMultiMapAction.CONTAINS_ENTRY)
//            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
//            .withHeader(AtomixClientConstants.RESOURCE_VALUE, val2)
//            .request(Message.class);
//
//        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
//        assertTrue(result.getBody(Boolean.class));
//        assertTrue(map.containsEntry(key, val2).join());
//
//    }

    @Test
    public void testRemove() throws Exception {
        final String key = context().getUuidGenerator().generateUuid();
        final String val1 = context().getUuidGenerator().generateUuid();
        final String val2 = context().getUuidGenerator().generateUuid();
        final String val3 = context().getUuidGenerator().generateUuid();

        map.put(key, val1).join();
        map.put(key, val2).join();
        map.put(key, val3).join();

        Message result;

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMultiMap.Action.REMOVE)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .withHeader(AtomixClientConstants.RESOURCE_VALUE, val1)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(Arrays.asList(val2, val3), map.get(key).join());

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMultiMap.Action.REMOVE_VALUE)
            .withHeader(AtomixClientConstants.RESOURCE_VALUE, val2)
            .request(Message.class);

        assertFalse(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(Arrays.asList(val3), map.get(key).join());
        assertTrue(map.containsKey(key).join());

        result = fluent.clearAll()
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMultiMap.Action.REMOVE)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .request(Message.class);

        assertTrue(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertFalse(map.containsKey(key).join());
    }

    @Ignore
    @Test
    public void test() {
        //Assert.assertFalse(map.containsValue("abc").join());
        Assert.assertFalse(map.containsEntry("abc", "abc").join());
    }

    // ************************************
    // Routes
    // ************************************

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .toF("atomix-multimap:%s", MAP_NAME);
            }
        };
    }
}
