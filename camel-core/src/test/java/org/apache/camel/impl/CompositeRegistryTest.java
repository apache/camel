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
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CompositeRegistryTest {
    private CompositeRegistry registry;
    @Before
    public void createContext() throws Exception {
        SimpleRegistry sr1 = new SimpleRegistry();
        sr1.put("name", new Integer(12));
        SimpleRegistry sr2 = new SimpleRegistry();
        sr2.put("name", "12");
        CompositeRegistry compositeRegistry = new CompositeRegistry();
        compositeRegistry.addRegistry(compositeRegistry); // Try to break the search with a loop
        compositeRegistry.addRegistry(sr2);
        compositeRegistry.addRegistry(sr1);
        registry = new CompositeRegistry();
        registry.addRegistry(compositeRegistry); // Let's test nested registries
    }

    @Test
    public void testGetRegistry() {
        assertNotNull(registry.getRegistry(Map.class));
        assertNotNull(registry.getRegistry(SimpleRegistry.class));
    }
    
    @Test
    public void testGetNameAndType() throws Exception {
        Object result = registry.lookupByNameAndType("name", String.class);
        assertNotNull(result);
        assertEquals("Got wrong result", result, "12");
        
        result = registry.lookup("test", Integer.class);
        assertNull(result);
    }

    @Test
    public void testLookupByName() {
        Object result = registry.lookupByName("name");
        assertNotNull(result);
        assertEquals("Got wrong result", result, "12");
    }

    @Test
    public void testFindByTypeWithName() {
        Map<String, Integer> result = registry.findByTypeWithName(Integer.class);
        assertTrue(result.containsKey("name"));
        assertEquals(result.get("name"), Integer.valueOf(12));
    }

    @Test
    public void testFindByType() {
        Set<Integer> result = registry.findByType(Integer.class);
        assertNotNull(result);
        assertTrue(result.contains(Integer.valueOf(12)));
    }
}
