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
package org.apache.camel.util;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OrderedPropertiesTest {

    @Test
    public void testOrdered() {
        Properties prop = new OrderedProperties();
        prop.setProperty("c", "CCC");
        prop.setProperty("d", "DDD");
        prop.setProperty("e", "EEE");
        prop.setProperty("b", "BBB");
        prop.setProperty("a", "AAA");

        assertEquals(5, prop.size());

        Iterator<Object> it = prop.keySet().iterator();
        assertEquals("c", it.next());
        assertEquals("d", it.next());
        assertEquals("e", it.next());
        assertEquals("b", it.next());
        assertEquals("a", it.next());

        it = prop.values().iterator();
        assertEquals("CCC", it.next());
        assertEquals("DDD", it.next());
        assertEquals("EEE", it.next());
        assertEquals("BBB", it.next());
        assertEquals("AAA", it.next());
    }

    @Test
    public void testOrderedLoad() throws Exception {
        Properties prop = new OrderedProperties();
        prop.load(OrderedPropertiesTest.class.getResourceAsStream("/application.properties"));

        assertEquals(4, prop.size());

        Iterator<Object> it = prop.keySet().iterator();
        assertEquals("hello", it.next());
        assertEquals("camel.component.seda.concurrent-consumers", it.next());
        assertEquals("camel.component.seda.queueSize", it.next());
        assertEquals("camel.component.direct.timeout", it.next());

        // should be ordered values
        it = prop.values().iterator();
        assertEquals("World", it.next());
        assertEquals("2", it.next());
        assertEquals("500", it.next());
        assertEquals("1234", it.next());
    }

    @Test
    public void testContainsKey() {
        Properties prop = new OrderedProperties();
        prop.setProperty("foo", "bar");

        assertTrue(prop.containsKey("foo"));
        assertFalse(prop.containsKey("bar"));
    }

    @Test
    public void testContainsValue() {
        Properties prop = new OrderedProperties();
        prop.setProperty("foo", "bar");

        assertTrue(prop.containsValue("bar"));
        assertFalse(prop.containsValue("foo"));
    }

    @Test
    public void testContainsLegacy() {
        Properties prop = new OrderedProperties();
        prop.setProperty("foo", "bar");

        assertTrue(prop.contains("bar"));
        assertFalse(prop.contains("foo"));
    }

    @Test
    public void testContainsKeyAfterRemove() {
        Properties prop = new OrderedProperties();
        prop.setProperty("foo", "bar");

        assertTrue(prop.containsKey("foo"));

        prop.remove("foo");

        assertFalse(prop.containsKey("foo"));
    }

    @Test
    public void testMapMethods() {
        OrderedLocationProperties prop = new OrderedLocationProperties();
        prop.put("app.properties", "b", "1");
        prop.put("app.properties", "a", "2");

        // forEach iterates the properties (in order)
        List<Object> keys = new ArrayList<>();
        prop.forEach((k, v) -> keys.add(k));
        assertEquals(List.of("b", "a"), keys);

        assertEquals("1", prop.getOrDefault("b", "x"));
        assertEquals("x", prop.getOrDefault("c", "x"));

        assertEquals("1", prop.putIfAbsent("b", "3"));
        assertNull(prop.putIfAbsent("c", "3"));
        assertEquals("3", prop.get("c"));

        assertEquals("33", prop.compute("c", (k, v) -> v + "3"));
        assertEquals("4", prop.computeIfAbsent("d", k -> "4"));
        assertEquals("44", prop.merge("d", "4", (a, b) -> a + "" + b));
        assertEquals("2", prop.replace("a", "22"));
        assertTrue(prop.replace("a", "22", "222"));
        assertEquals("222", prop.get("a"));
        assertTrue(prop.remove("d", "44"));
        assertFalse(prop.containsKey("d"));

        List<Object> values = new ArrayList<>();
        Enumeration<Object> e = prop.elements();
        while (e.hasMoreElements()) {
            values.add(e.nextElement());
        }
        assertEquals(List.of("1", "222", "33"), values);

        OrderedLocationProperties other = new OrderedLocationProperties();
        other.put("b", "1");
        assertNotEquals(prop, other);
        assertEquals(prop, prop);
    }

}
