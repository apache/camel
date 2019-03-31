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

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultRegistry;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class RegistryLookupTypeClassCastExceptionTest extends Assert {

    @Test
    public void testLookupOk() throws Exception {
        Registry registry = new DefaultRegistry();

        MyClass my = new MyClass();
        registry.bind("my", my);

        assertEquals(my, registry.lookupByName("my"));
        assertEquals(my, registry.lookupByNameAndType("my", MyClass.class));

        assertNull(registry.lookupByName("foo"));
        assertNull(registry.lookupByNameAndType("foo", MyClass.class));
    }

    @Test
    public void testCamelContextLookupOk() throws Exception {
        CamelContext context = new DefaultCamelContext();

        MyClass my = new MyClass();
        context.getRegistry().bind("my", my);

        assertEquals(my, context.getRegistry().lookupByName("my"));
        assertEquals(my, context.getRegistry().lookupByNameAndType("my", MyClass.class));

        assertNull(context.getRegistry().lookupByName("foo"));
        assertNull(context.getRegistry().lookupByNameAndType("foo", MyClass.class));
    }

    @Test
    public void testCamelContextLookupClassCast() throws Exception {
        CamelContext context = new DefaultCamelContext();

        MyClass my = new MyClass();
        context.getRegistry().bind("my", my);

        Object answer = context.getRegistry().lookupByNameAndType("my", String.class);
        assertNull(answer);
        answer = context.getRegistry().lookupByNameAndType("my", MyClass.class);
        assertNotNull(answer);
    }

    public static class MyClass {
        // just a test class
    }

}
