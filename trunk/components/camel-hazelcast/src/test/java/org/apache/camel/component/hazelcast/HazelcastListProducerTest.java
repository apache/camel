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

import java.util.List;

import com.hazelcast.core.HazelcastInstance;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;

import org.junit.Test;

public class HazelcastListProducerTest extends CamelTestSupport {

    private List<String> list;

    @Override
    protected void doPostSetup() throws Exception {
        HazelcastComponent component = context().getComponent("hazelcast", HazelcastComponent.class);
        HazelcastInstance hazelcastInstance = component.getHazelcastInstance();
        list = hazelcastInstance.getList("bar");
        list.clear();
    }

    @Test
    public void addValue() throws InterruptedException {
        template.sendBody("direct:add", "bar");
        assertTrue(list.contains("bar"));
    }

    @Test
    public void removeValue() throws InterruptedException {
        list.add("foo1");
        list.add("foo2");
        list.add("foo3");

        assertEquals(3, list.size());

        // specify the value to remove
        template.sendBody("direct:removevalue", "foo2");

        assertEquals(2, list.size());
        assertTrue(list.contains("foo1") && list.contains("foo3"));
    }

    @Test
    public void getValueWithIdx() {
        list.add("foo1");
        list.add("foo2");

        assertEquals(2, list.size());

        template.sendBodyAndHeader("direct:get", "test", HazelcastConstants.OBJECT_POS, 1);

        assertEquals("foo2", consumer.receiveBody("seda:out", 5000, String.class));

    }

    @Test
    public void setValueWithIdx() {
        list.add("foo1");
        list.add("foo2");

        assertEquals(2, list.size());

        template.sendBodyAndHeader("direct:set", "test", HazelcastConstants.OBJECT_POS, 1);

        assertEquals(2, list.size());
        assertEquals("test", list.get(1));

    }

    @Test
    public void removeValueWithIdx() {
        list.add("foo1");
        list.add("foo2");

        assertEquals(2, list.size());

        // do not specify the value to delete, but the index
        template.sendBodyAndHeader("direct:removevalue", null, HazelcastConstants.OBJECT_POS, 1);

        assertEquals(1, list.size());
        assertEquals("foo1", list.get(0));
    }

    @Test
    public void removeValueWithoutIdx() {
        list.add("foo1");
        list.add("foo2");

        assertEquals(2, list.size());

        // do not specify the index to delete, but the value
        template.sendBody("direct:removevalue", "foo1");

        assertEquals(1, list.size());
        assertEquals("foo2", list.get(0));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:add").setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.ADD_OPERATION)).to(String.format("hazelcast:%sbar", HazelcastConstants.LIST_PREFIX));

                from("direct:set").setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.SETVALUE_OPERATION)).to(String.format("hazelcast:%sbar", HazelcastConstants.LIST_PREFIX));

                from("direct:get").setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.GET_OPERATION)).to(String.format("hazelcast:%sbar", HazelcastConstants.LIST_PREFIX))
                        .to("seda:out");

                from("direct:removevalue").setHeader(HazelcastConstants.OPERATION, constant(HazelcastConstants.REMOVEVALUE_OPERATION)).to(
                        String.format("hazelcast:%sbar", HazelcastConstants.LIST_PREFIX));
            }
        };
    }

}

