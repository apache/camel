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

import java.util.Collection;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.MultiMap;

import org.apache.camel.test.junit4.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class HazelcastMultimapProducerForSpringTest extends CamelSpringTestSupport {

    private MultiMap<String, Object> map;

    @Override
    public void setUp() throws Exception {
        this.map = Hazelcast.getMultiMap("foo");
        this.map.clear();

        super.setUp();
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("/META-INF/spring/test-camel-context-multimap.xml");
    }

    @Test
    public void testPut() throws InterruptedException {
        template.sendBodyAndHeader("direct:put", "my-foo", HazelcastConstants.OBJECT_ID, "4711");
        template.sendBodyAndHeader("direct:put", "my-bar", HazelcastConstants.OBJECT_ID, "4711");

        assertTrue(map.containsKey("4711"));
        Collection<Object> values = map.get("4711");

        assertTrue(values.contains("my-foo"));
        assertTrue(values.contains("my-bar"));
    }

    @Test
    public void testRemoveValue() {
        map.put("4711", "my-foo");
        map.put("4711", "my-bar");

        assertEquals(2, map.get("4711").size());

        template.sendBodyAndHeader("direct:removevalue", "my-foo", HazelcastConstants.OBJECT_ID, "4711");

        assertEquals(1, map.get("4711").size());
        assertTrue(map.get("4711").contains("my-bar"));
    }

    @Test
    public void testGet() {
        map.put("4711", "my-foo");

        template.sendBodyAndHeader("direct:get", null, HazelcastConstants.OBJECT_ID, "4711");
        Collection<Object> body = consumer.receiveBody("seda:out", 5000, Collection.class);

        assertTrue(body.contains("my-foo"));
    }

    @Test
    public void testDelete() {
        map.put("4711", "my-foo");
        assertEquals(1, map.size());

        template.sendBodyAndHeader("direct:delete", null, HazelcastConstants.OBJECT_ID, "4711");
        assertEquals(0, map.size());
    }

}
