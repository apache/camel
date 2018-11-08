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
import java.util.Arrays;
import java.util.List;

import org.apache.camel.util.jndi.JndiTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @version 
 */
public class RegistryTest extends Assert {
    protected JndiRegistry registry;

    @Test
    public void testBind() throws Exception {
        List<?> foo = Arrays.asList("a", "b", "c");

        registry.bind("foo", foo);

        List<?> list = registry.lookupByNameAndType("foo", List.class);
        assertEquals("Should be same!", foo, list);
    }

    @Test
    public void testDefaultProviderAllowsValuesToBeCreatedInThePropertiesFile() throws Exception {
        Object value = registry.lookupByName("foo");
        assertEquals("lookup of foo", "bar", value);
    }

    @Test
    public void testLookupOfUnknownName() throws Exception {
        Object value = registry.lookupByName("No such entry!");
        assertNull("Should not find anything!", value);
    }

    @Before
    public void setUp() throws Exception {

        registry = new JndiRegistry(JndiTest.createInitialContext());
    }
}
