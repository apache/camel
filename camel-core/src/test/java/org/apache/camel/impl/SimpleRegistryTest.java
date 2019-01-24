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
package org.apache.camel.impl;
import java.util.Map;

import org.apache.camel.NoSuchBeanException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SimpleRegistryTest extends Assert {

    private SimpleRegistry registry;
    
    @Before
    public void setUp() throws Exception {
        registry = new SimpleRegistry();
        registry.put("a", "b");
        registry.put("c", 1);
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
        try {
            registry.lookupByNameAndType("a", Float.class);
            fail();
        } catch (NoSuchBeanException e) {
            // expected
            assertEquals("a", e.getName());
            assertTrue(e.getMessage().endsWith("of type: java.lang.String expected type was: class java.lang.Float"));
        }
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

}
