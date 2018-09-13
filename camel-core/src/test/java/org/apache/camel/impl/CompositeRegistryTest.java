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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class CompositeRegistryTest {
    private CompositeRegistry registry;
    @Before
    public void createContext() throws Exception {
        SimpleRegistry sr1 = new SimpleRegistry();
        sr1.put("name", new Integer(12));
        SimpleRegistry sr2 = new SimpleRegistry();
        sr2.put("name", "12");
        registry = new CompositeRegistry();
        registry.addRegistry(sr2);
        registry.addRegistry(sr1);
    }
    
    @Test
    public void testGetNameAndType() throws Exception {
        Object result = registry.lookupByNameAndType("name", String.class);
        assertNotNull(result);
        assertEquals("Get a wrong result", result, "12");
        
        result = registry.lookup("test", Integer.class);
        assertNull(result);
    }

}
