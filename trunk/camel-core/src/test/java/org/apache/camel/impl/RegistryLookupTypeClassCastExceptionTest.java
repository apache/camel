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

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.NoSuchBeanException;

/**
 *
 */
public class RegistryLookupTypeClassCastExceptionTest extends TestCase {

    public void testLookupOk() throws Exception {
        SimpleRegistry simple = new SimpleRegistry();

        MyClass my = new MyClass();
        simple.put("my", my);

        assertEquals(my, simple.lookupByName("my"));
        assertEquals(my, simple.lookupByNameAndType("my", MyClass.class));

        assertNull(simple.lookupByName("foo"));
        assertNull(simple.lookupByNameAndType("foo", MyClass.class));
    }

    public void testCamelContextLookupOk() throws Exception {
        SimpleRegistry simple = new SimpleRegistry();
        CamelContext context = new DefaultCamelContext(simple);

        MyClass my = new MyClass();
        simple.put("my", my);

        assertEquals(my, context.getRegistry().lookupByName("my"));
        assertEquals(my, context.getRegistry().lookupByNameAndType("my", MyClass.class));

        assertNull(context.getRegistry().lookupByName("foo"));
        assertNull(context.getRegistry().lookupByNameAndType("foo", MyClass.class));
    }

    public void testLookupClassCast() throws Exception {
        SimpleRegistry simple = new SimpleRegistry();

        MyClass my = new MyClass();
        simple.put("my", my);

        try {
            simple.lookupByNameAndType("my", String.class);
            fail("Should have thrown exception");
        } catch (NoSuchBeanException e) {
            assertEquals("my", e.getName());
            assertTrue(e.getMessage().endsWith("expected type was: class java.lang.String"));
        }
    }

    public void testCamelContextLookupClassCast() throws Exception {
        SimpleRegistry simple = new SimpleRegistry();
        CamelContext context = new DefaultCamelContext(simple);

        MyClass my = new MyClass();
        simple.put("my", my);

        try {
            context.getRegistry().lookupByNameAndType("my", String.class);
            fail("Should have thrown exception");
        } catch (NoSuchBeanException e) {
            assertEquals("my", e.getName());
            assertTrue(e.getMessage().endsWith("expected type was: class java.lang.String"));
        }
    }

    public static class MyClass {
        // just a test class
    }

}
