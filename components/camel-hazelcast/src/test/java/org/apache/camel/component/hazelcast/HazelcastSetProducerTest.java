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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.hazelcast.collection.ISet;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HazelcastSetProducerTest extends CamelTestSupport {

    private static ISet<String> set;
    private static HazelcastInstance hazelcastInstance;

    @BeforeAll
    public static void beforeAll() {
        Config config = new Config();
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        set = hazelcastInstance.getSet("bar");
    }

    @AfterAll
    public static void afterEach() {
        if (hazelcastInstance != null) {
            hazelcastInstance.shutdown();
        }
    }

    @BeforeEach
    public void beforeEach() {
        set.clear();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        HazelcastCamelTestHelper.registerHazelcastComponents(context, hazelcastInstance);
        return context;
    }

    @Test
    public void testWithInvalidOperation() {
        assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:addInvalid", "bar"));
    }

    @Test
    public void addValue() throws InterruptedException {
        template.sendBody("direct:add", "one");
        assertTrue(set.contains("one"));
    }

    @Test
    public void addValueWithOperationNumber() throws InterruptedException {
        template.sendBody("direct:addWithOperationNumber", "two");
        assertTrue(set.contains("two"));
    }

    @Test
    public void addValueWithOperationName() throws InterruptedException {
        template.sendBody("direct:addWithOperationName", "three");
        assertTrue(set.contains("three"));
    }

    @Test
    public void removeValue() throws InterruptedException {
        set.add("foo2");
        template.sendBody("direct:removeValue", "foo2");
        assertFalse(set.contains("foo2"));
    }

    @Test
    public void clearList() {
        template.sendBody("direct:clear", "");
        assertTrue(set.isEmpty());
    }

    @Test
    public void addAll() throws InterruptedException {
        List<String> t = new ArrayList<>();
        t.add("test2");
        t.add("test3");
        template.sendBody("direct:addAll", t);
        assertTrue(set.containsAll(t));
    }

    @Test
    public void removeAll() throws InterruptedException {
        List<String> t = new ArrayList<>();
        t.add("test1");
        t.add("test2");
        template.sendBody("direct:removeAll", t);
        assertFalse(set.containsAll(t));
    }

    @Test
    public void retainAll() throws InterruptedException {
        List<String> t = new ArrayList<>();
        t.add("test3");
        t.add("test4");
        template.sendBody("direct:retainAll", t);
        assertFalse(set.containsAll(t));
    }

    @Test
    public void getAll() throws InterruptedException {
        Set<String> t = new HashSet<>();
        t.add("test1");
        set.addAll(t);
        template.sendBody("direct:getAll", "test");
        assertEquals(t, consumer.receiveBody("seda:out", 5000, Set.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:addInvalid").setHeader(HazelcastConstants.OPERATION, constant("bogus"))
                        .toF("hazelcast-%sbar", HazelcastConstants.SET_PREFIX);

                from("direct:add").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.ADD))
                        .toF("hazelcast-%sbar", HazelcastConstants.SET_PREFIX);

                from("direct:removeValue").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.REMOVE_VALUE))
                        .toF("hazelcast-%sbar", HazelcastConstants.SET_PREFIX);

                from("direct:clear").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.CLEAR))
                        .toF("hazelcast-%sbar", HazelcastConstants.SET_PREFIX);

                from("direct:addAll").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.ADD_ALL))
                        .toF("hazelcast-%sbar", HazelcastConstants.SET_PREFIX);

                from("direct:removeAll").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.REMOVE_ALL))
                        .toF("hazelcast-%sbar", HazelcastConstants.SET_PREFIX);

                from("direct:retainAll").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.RETAIN_ALL))
                        .toF("hazelcast-%sbar", HazelcastConstants.SET_PREFIX);

                from("direct:addWithOperationNumber").toF("hazelcast-%sbar?operation=%s", HazelcastConstants.SET_PREFIX,
                        HazelcastOperation.ADD);
                from("direct:addWithOperationName").toF("hazelcast-%sbar?operation=ADD", HazelcastConstants.SET_PREFIX);

                from("direct:getAll").setHeader(HazelcastConstants.OPERATION, constant(HazelcastOperation.GET_ALL))
                        .toF("hazelcast-%sbar", HazelcastConstants.SET_PREFIX).to("seda:out");
            }
        };
    }

}
