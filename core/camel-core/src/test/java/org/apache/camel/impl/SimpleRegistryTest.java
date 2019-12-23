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
package org.apache.camel.impl;

import java.util.Map;

import org.apache.camel.support.SimpleRegistry;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SimpleRegistryTest extends Assert {

    private SimpleRegistry registry;

    @Before
    public void setUp() throws Exception {
        registry = new SimpleRegistry();
        registry.bind("a", "b");
        registry.bind("c", 1);
    }

    @Test
    public void testLookupByName() {
        assertEquals("b", registry.lookupByName("a"));
    }

    @Test
    public void testLookupByWrongName() {
        assertNull(registry.lookupByName("x"));
    }

    @Test
    public void testLookupByNameAndType() {
        assertEquals("b", registry.lookupByNameAndType("a", String.class));
    }

    @Test
    public void testLookupByNameAndWrongType() {
        Object answer = registry.lookupByNameAndType("a", Float.class);
        assertNull(answer);
    }

    @Test
    public void testLookupByType() {
        Map<?, ?> map = registry.findByTypeWithName(String.class);
        assertEquals(1, map.size());
        assertEquals("b", map.get("a"));
        map = registry.findByTypeWithName(Object.class);
        assertEquals(2, map.size());
        assertEquals("b", map.get("a"));
        assertEquals(1, map.get("c"));
    }

    @Test
    public void testLookupByWrongType() {
        Map<?, ?> map = registry.findByTypeWithName(Float.class);
        assertEquals(0, map.size());
    }

    @Test
    public void testBindDual() {
        String foo = "foo";
        // bind a 2nd c but its a different type
        registry.bind("c", foo);
        assertEquals(2, registry.size());
        // should return the original entry if no specific type given
        assertSame(1, registry.lookupByName("c"));
        assertSame(1, registry.lookupByNameAndType("c", Integer.class));
        // should return the string type
        assertSame("foo", registry.lookupByNameAndType("c", String.class));

        Map<String, Integer> map = registry.findByTypeWithName(Integer.class);
        assertEquals(1, map.size());
        assertEquals(Integer.valueOf(1), map.get("c"));

        Map<String, String> map2 = registry.findByTypeWithName(String.class);
        assertEquals(2, map2.size());
        assertEquals("foo", map2.get("c"));
        assertEquals("b", map2.get("a"));
    }

}
