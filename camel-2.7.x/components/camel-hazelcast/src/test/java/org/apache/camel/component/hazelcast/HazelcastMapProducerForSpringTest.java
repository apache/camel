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

import java.io.Serializable;
import java.util.Collection;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.IMap;

import org.apache.camel.test.junit4.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class HazelcastMapProducerForSpringTest extends CamelSpringTestSupport implements Serializable {

    private IMap<String, Object> map;

    @Override
    public void setUp() throws Exception {
        this.map = Hazelcast.getMap("foo");
        this.map.clear();

        super.setUp();
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("/META-INF/spring/test-camel-context-map.xml");
    }

    @Test
    public void testPut() throws InterruptedException {
        template.sendBodyAndHeader("direct:put", "my-foo", HazelcastConstants.OBJECT_ID, "4711");

        assertTrue(map.containsKey("4711"));
        assertEquals("my-foo", map.get("4711"));
    }

    @Test
    public void testUpdate() {
        template.sendBodyAndHeader("direct:put", "my-foo", HazelcastConstants.OBJECT_ID, "4711");

        assertTrue(map.containsKey("4711"));
        assertEquals("my-foo", map.get("4711"));

        template.sendBodyAndHeader("direct:update", "my-fooo", HazelcastConstants.OBJECT_ID, "4711");
        assertEquals("my-fooo", map.get("4711"));
    }

    @Test
    public void testGet() {
        map.put("4711", "my-foo");

        template.sendBodyAndHeader("direct:get", null, HazelcastConstants.OBJECT_ID, "4711");
        String body = consumer.receiveBody("seda:out", 5000, String.class);

        assertEquals("my-foo", body);
    }

    @Test
    public void testDelete() {
        map.put("4711", "my-foo");
        assertEquals(1, map.size());

        template.sendBodyAndHeader("direct:delete", null, HazelcastConstants.OBJECT_ID, "4711");
        assertEquals(0, map.size());
    }

    @Test
    public void testQuery() {
        map.put("1", new Dummy("alpha", 1000));
        map.put("2", new Dummy("beta", 2000));
        map.put("3", new Dummy("gamma", 3000));

        String q1 = "bar > 1000";
        String q2 = "foo LIKE alp%";

        template.sendBodyAndHeader("direct:query", null, HazelcastConstants.QUERY, q1);
        Collection<Dummy> b1 = consumer.receiveBody("seda:out", 5000, Collection.class);

        assertNotNull(b1);
        assertEquals(2, b1.size());

        template.sendBodyAndHeader("direct:query", null, HazelcastConstants.QUERY, q2);
        Collection<Dummy> b2 = consumer.receiveBody("seda:out", 5000, Collection.class);

        assertNotNull(b2);
        assertEquals(1, b2.size());
    }

    public class Dummy implements Serializable {

        private static final long serialVersionUID = 3688457704655925278L;

        private String foo;
        private int bar;
        
        public Dummy(String foo, int bar) {
            this.foo = foo;
            this.bar = bar;
        }

        public String getFoo() {
            return foo;
        }

        public void setFoo(String foo) {
            this.foo = foo;
        }

        public int getBar() {
            return bar;
        }

        public void setBar(int bar) {
            this.bar = bar;
        }

    }

}
