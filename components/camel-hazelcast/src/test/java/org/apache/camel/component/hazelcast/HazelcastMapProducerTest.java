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
package org.apache.camel.component.hazelcast;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.query.impl.predicates.SqlPredicate;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hazelcast.testutil.Dummy;
import org.junit.After;
import org.junit.Test;
import org.mockito.Mock;

import static org.mockito.Mockito.*;

public class HazelcastMapProducerTest extends HazelcastCamelTestSupport implements Serializable {

    private static final long serialVersionUID = 1L;
    @Mock
    private IMap<Object, Object> map;

    @Override
    protected void trainHazelcastInstance(HazelcastInstance hazelcastInstance) {
        when(hazelcastInstance.getMap("foo")).thenReturn(map);
    }

    @Override
    protected void verifyHazelcastInstance(HazelcastInstance hazelcastInstance) {
        verify(hazelcastInstance, atLeastOnce()).getMap("foo");
    }

    @After
    public void verifyMapMock() {
        verifyNoMoreInteractions(map);
    }

    @Test(expected = CamelExecutionException.class)
    public void testWithInvalidOperation() {
        template.sendBody("direct:putInvalid", "my-foo");
    }

    @Test
    public void testPut() throws InterruptedException {
        template.sendBodyAndHeader("direct:put", "my-foo", HazelcastConstants.OBJECT_ID, "4711");
        verify(map).put("4711", "my-foo");
    }

    @Test
    public void testPutWithOperationNumber() throws InterruptedException {
        template.sendBodyAndHeader("direct:putWithOperationNumber", "my-foo", HazelcastConstants.OBJECT_ID, "4711");
        verify(map).put("4711", "my-foo");
    }

    @Test
    public void testPutWithOperationName() throws InterruptedException {
        template.sendBodyAndHeader("direct:putWithOperationName", "my-foo", HazelcastConstants.OBJECT_ID, "4711");
        verify(map).put("4711", "my-foo");
    }

    @Test
    public void testPutWithTTL() throws InterruptedException {
        Map<String, Object> headers = new HashMap<>();
        headers.put(HazelcastConstants.OBJECT_ID, "4711");
        headers.put(HazelcastConstants.TTL_VALUE, new Long(1));
        headers.put(HazelcastConstants.TTL_UNIT, TimeUnit.MINUTES);
        template.sendBodyAndHeaders("direct:put", "test", headers);
        verify(map).put("4711", "test", 1, TimeUnit.MINUTES);
    }
    
    @Test
    public void testUpdate() {
        template.sendBodyAndHeader("direct:update", "my-fooo", HazelcastConstants.OBJECT_ID, "4711");
        verify(map).lock("4711");
        verify(map).replace("4711", "my-fooo");
        verify(map).unlock("4711");
    }

    @Test
    public void testGet() {
        when(map.get("4711")).thenReturn("my-foo");
        template.sendBodyAndHeader("direct:get", null, HazelcastConstants.OBJECT_ID, "4711");
        String body = consumer.receiveBody("seda:out", 5000, String.class);
        verify(map).get("4711");
        assertEquals("my-foo", body);
    }
    
    @Test
    public void testGetAllEmptySet() {
        Set<Object> l = new HashSet<>();
        Map t = new HashMap();
        t.put("key1", "value1");
        t.put("key2", "value2");
        t.put("key3", "value3");
        when(map.getAll(anySet())).thenReturn(t);
        template.sendBodyAndHeader("direct:getAll", null, HazelcastConstants.OBJECT_ID, l);
        String body = consumer.receiveBody("seda:out", 5000, String.class);
        verify(map).getAll(l);
        assertTrue(body.contains("key1=value1"));
        assertTrue(body.contains("key2=value2"));
        assertTrue(body.contains("key3=value3"));
    }
    
    @Test
    public void testGetAllOnlyOneKey() {
        Set<Object> l = new HashSet<>();
        l.add("key1");
        Map t = new HashMap();
        t.put("key1", "value1");
        when(map.getAll(l)).thenReturn(t);
        template.sendBodyAndHeader("direct:getAll", null, HazelcastConstants.OBJECT_ID, l);
        String body = consumer.receiveBody("seda:out", 5000, String.class);
        verify(map).getAll(l);
        assertEquals("{key1=value1}", body);
    }

    @Test
    public void testDelete() {
        template.sendBodyAndHeader("direct:delete", null, HazelcastConstants.OBJECT_ID, 4711);
        verify(map).remove(4711);
    }

    @Test
    public void testQuery() {
        String sql = "bar > 1000";

        when(map.values(any(SqlPredicate.class))).thenReturn(Arrays.<Object>asList(new Dummy("beta", 2000), new Dummy("gamma", 3000)));
        template.sendBodyAndHeader("direct:queue", null, HazelcastConstants.QUERY, sql);
        verify(map).values(any(SqlPredicate.class));

        Collection<?> b1 = consumer.receiveBody("seda:out", 5000, Collection.class);

        assertNotNull(b1);
        assertEquals(2, b1.size());
    }
    
    @Test
    public void testEmptyQuery() {
        when(map.values()).thenReturn(Arrays.<Object>asList(new Dummy("beta", 2000), new Dummy("gamma", 3000), new Dummy("delta", 4000)));
        template.sendBody("direct:queue", null);
        verify(map).values();

        Collection<?> b1 = consumer.receiveBody("seda:out", 5000, Collection.class);

        assertNotNull(b1);
        assertEquals(3, b1.size());
    }
    
    @Test
    public void testUpdateOldValue() throws InterruptedException {
        Map<String, Object> headers = new HashMap<>();
        headers.put(HazelcastConstants.OBJECT_ID, "4711");
        headers.put(HazelcastConstants.OBJECT_VALUE, "my-foo");
        template.sendBodyAndHeaders("direct:update", "replaced", headers);
        verify(map).lock("4711");
        verify(map).replace("4711", "my-foo", "replaced");
        verify(map).unlock("4711");
    }
    
    @Test
    public void testPutIfAbsent() throws InterruptedException {
        Map<String, Object> headers = new HashMap<>();
        headers.put(HazelcastConstants.OBJECT_ID, "4711");
        template.sendBodyAndHeaders("direct:putIfAbsent", "replaced", headers);
        verify(map).putIfAbsent("4711", "replaced");
    }
    
    @Test
    public void testPutIfAbsentWithTtl() throws InterruptedException {
        Map<String, Object> headers = new HashMap<>();
        headers.put(HazelcastConstants.OBJECT_ID, "4711");
        headers.put(HazelcastConstants.TTL_VALUE, new Long(1));
        headers.put(HazelcastConstants.TTL_UNIT, TimeUnit.MINUTES);
        template.sendBodyAndHeaders("direct:putIfAbsent", "replaced", headers);
        verify(map).putIfAbsent("4711", "replaced", new Long(1), TimeUnit.MINUTES);
    }
    
    @Test
    public void testEvict() throws InterruptedException {
        Map<String, Object> headers = new HashMap<>();
        headers.put(HazelcastConstants.OBJECT_ID, "4711");
        template.sendBodyAndHeaders("direct:evict", "", headers);
        verify(map).evict("4711");
    }
    
    @Test
    public void testEvictAll() throws InterruptedException {
        Map<String, Object> headers = new HashMap<>();
        template.sendBodyAndHeaders("direct:evictAll", "", headers);
        verify(map).evictAll();
    }
    
    @Test
    public void testClear() throws InterruptedException {
        template.sendBody("direct:clear", "test");
        verify(map).clear();
    }
    
    @Test
    public void testContainsKey() {
        when(map.containsKey("testOk")).thenReturn(true);
        when(map.containsKey("testKo")).thenReturn(false);
        template.sendBodyAndHeader("direct:containsKey", null, HazelcastConstants.OBJECT_ID, "testOk");
        Boolean body = consumer.receiveBody("seda:out", 5000, Boolean.class);
        verify(map).containsKey("testOk");
        assertEquals(true, body);
        template.sendBodyAndHeader("direct:containsKey", null, HazelcastConstants.OBJECT_ID, "testKo");
        body = consumer.receiveBody("seda:out", 5000, Boolean.class);
        verify(map).containsKey("testKo");
        assertEquals(false, body);
    }
    
    @Test
    public void testContainsValue() {
        when(map.containsValue("testOk")).thenReturn(true);
        when(map.containsValue("testKo")).thenReturn(false);
        template.sendBody("direct:containsValue", "testOk");
        Boolean body = consumer.receiveBody("seda:out", 5000, Boolean.class);
        verify(map).containsValue("testOk");
        assertEquals(true, body);
        template.sendBody("direct:containsValue", "testKo");
        body = consumer.receiveBody("seda:out", 5000, Boolean.class);
        verify(map).containsValue("testKo");
        assertEquals(false, body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:putInvalid").setHeader(HazelcastConstants.OPERATION, constant("bogus")).to(String.format("hazelcast-%sfoo", HazelcastConstants.MAP_PREFIX));

                from("direct:put").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.PUT)).to(String.format("hazelcast-%sfoo", HazelcastConstants.MAP_PREFIX));

                from("direct:putIfAbsent").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.PUT_IF_ABSENT))
                         .to(String.format("hazelcast-%sfoo", HazelcastConstants.MAP_PREFIX));

                from("direct:update").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.UPDATE)).to(String.format("hazelcast-%sfoo", HazelcastConstants.MAP_PREFIX));

                from("direct:get").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.GET)).to(String.format("hazelcast-%sfoo", HazelcastConstants.MAP_PREFIX))
                        .to("seda:out");

                from("direct:getAll").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.GET_ALL)).to(String.format("hazelcast-%sfoo", HazelcastConstants.MAP_PREFIX))
                        .to("seda:out");

                from("direct:delete").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.DELETE)).to(String.format("hazelcast-%sfoo", HazelcastConstants.MAP_PREFIX));

                from("direct:queue").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.QUERY)).to(String.format("hazelcast-%sfoo", HazelcastConstants.MAP_PREFIX))
                        .to("seda:out");

                from("direct:clear").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.CLEAR)).to(String.format("hazelcast-%sfoo", HazelcastConstants.MAP_PREFIX));

                from("direct:evict").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.EVICT)).to(String.format("hazelcast-%sfoo", HazelcastConstants.MAP_PREFIX))
                        .to("seda:out");

                from("direct:evictAll").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.EVICT_ALL)).to(String.format("hazelcast-%sfoo", HazelcastConstants.MAP_PREFIX))
                        .to("seda:out");

                from("direct:containsKey").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.CONTAINS_KEY))
                        .to(String.format("hazelcast-%sfoo", HazelcastConstants.MAP_PREFIX))
                        .to("seda:out");

                from("direct:containsValue").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.CONTAINS_VALUE))
                        .to(String.format("hazelcast-%sfoo", HazelcastConstants.MAP_PREFIX))
                        .to("seda:out");

                from("direct:putWithOperationNumber").toF("hazelcast-%sfoo?operation=%s", HazelcastConstants.MAP_PREFIX, HazelcastOperation.PUT);
                from("direct:putWithOperationName").toF("hazelcast-%sfoo?operation=PUT", HazelcastConstants.MAP_PREFIX);
            }
        };
    }
}
