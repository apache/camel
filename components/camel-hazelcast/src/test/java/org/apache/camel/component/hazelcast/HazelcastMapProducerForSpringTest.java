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
package org.apache.camel.component.hazelcast;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.query.SqlPredicate;

import org.apache.camel.component.hazelcast.testutil.Dummy;
import org.junit.After;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class HazelcastMapProducerForSpringTest extends HazelcastCamelSpringTestSupport {

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

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("/META-INF/spring/test-camel-context-map.xml");
    }

    @Test
    public void testPut() throws InterruptedException {
        template.sendBodyAndHeader("direct:put", "my-foo", HazelcastConstants.OBJECT_ID, 4711L);
        verify(map).put(4711L, "my-foo");
    }

    @Test
    public void testUpdate() {
        template.sendBodyAndHeader("direct:update", "my-fooo", HazelcastConstants.OBJECT_ID, 4711L);
        verify(map).lock(4711L);
        verify(map).replace(4711L, "my-fooo");
        verify(map).unlock(4711L);
    }

    @Test
    public void testGet() {
        when(map.get(4711L)).thenReturn("my-foo");
        template.sendBodyAndHeader("direct:get", null, HazelcastConstants.OBJECT_ID, 4711L);
        verify(map).get(4711L);
        String body = consumer.receiveBody("seda:out", 5000, String.class);

        assertEquals("my-foo", body);
    }

    @Test
    public void testDelete() {
        template.sendBodyAndHeader("direct:delete", null, HazelcastConstants.OBJECT_ID, 4711L);
        verify(map).remove(4711L);
    }

    @Test
    public void testQuery() {
        String sql = "bar > 1000";

        when(map.values(any(SqlPredicate.class))).thenReturn(Arrays.<Object>asList(new Dummy("beta", 2000), new Dummy("gamma", 3000)));
        template.sendBodyAndHeader("direct:query", null, HazelcastConstants.QUERY, sql);
        verify(map).values(any(SqlPredicate.class));

        Collection<?> b1 = consumer.receiveBody("seda:out", 5000, Collection.class);

        assertNotNull(b1);
        assertEquals(2, b1.size());
    }
    
    @Test
    public void testPutIfAbsent() throws InterruptedException {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(HazelcastConstants.OBJECT_ID, "4711");
        template.sendBodyAndHeaders("direct:putIfAbsent", "replaced", headers);
        verify(map).putIfAbsent("4711", "replaced");
    }
    
    @Test
    public void testPutIfAbsentWithTtl() throws InterruptedException {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(HazelcastConstants.OBJECT_ID, "4711");
        headers.put(HazelcastConstants.TTL_VALUE, new Long(1));
        headers.put(HazelcastConstants.TTL_UNIT, TimeUnit.MINUTES);
        template.sendBodyAndHeaders("direct:putIfAbsent", "replaced", headers);
        verify(map).putIfAbsent("4711", "replaced", new Long(1), TimeUnit.MINUTES);
    }
    
    @Test
    public void testGetAllEmptySet() {
        Set<Object> l = new HashSet<Object>();
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
        Set<Object> l = new HashSet<Object>();
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
    public void testClear() throws InterruptedException {
        template.sendBody("direct:clear", "test");
        verify(map).clear();
    }
    
    @Test
    public void testEvict() throws InterruptedException {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(HazelcastConstants.OBJECT_ID, "4711");
        template.sendBodyAndHeaders("direct:evict", "", headers);
        verify(map).evict("4711");
    }
    
    @Test
    public void testEvictAll() throws InterruptedException {
        Map<String, Object> headers = new HashMap<String, Object>();
        template.sendBodyAndHeaders("direct:evictAll", "", headers);
        verify(map).evictAll();
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
}
