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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.NoSuchBeanException;
import org.apache.camel.language.simple.SimpleLanguage;
import org.apache.camel.spi.Language;
import org.apache.camel.support.jndi.JndiTest;
import org.junit.Assert;
import org.junit.Test;

public class JndiRegistryTest extends Assert {

    @Test
    public void testLookupByType() throws Exception {
        JndiRegistry jndi = new JndiRegistry(JndiTest.createInitialContext());
        jndi.bind("foo", new SimpleLanguage());
        jndi.bind("bar", "Hello bar");

        assertEquals("Hello bar", jndi.lookupByName("bar"));
        assertEquals("Hello bar", jndi.lookupByName("bar"));
        assertEquals("Hello bar", jndi.lookupByNameAndType("bar", String.class));
        assertNull(jndi.lookupByName("unknown"));
        assertNull(jndi.lookupByName("unknown"));

        try {
            assertNull(jndi.lookupByNameAndType("bar", Language.class));
            fail("Should throw exception");
        } catch (NoSuchBeanException e) {
            // expected
        }

        assertNotNull(jndi.lookupByNameAndType("foo", Language.class));
        assertNotNull(jndi.lookupByNameAndType("foo", SimpleLanguage.class));
        assertSame(jndi.lookupByNameAndType("foo", Language.class), jndi.lookupByNameAndType("foo", SimpleLanguage.class));

        Map<String, ?> set = jndi.findByTypeWithName(Language.class);
        assertNotNull(set);
        assertEquals(1, set.size());

        String key = set.keySet().iterator().next();
        assertEquals("foo", key);
        assertSame(jndi.lookupByName("foo"), set.values().iterator().next());
    }

    @Test
    public void testStandalone() throws Exception {
        JndiRegistry jndi = new JndiRegistry(true);
        jndi.bind("bar", "Hello bar");
        assertEquals("Hello bar", jndi.lookupByName("bar"));
    }

    @Test
    public void testCamelContextFactory() throws Exception {
        Map<Object, Object> env = new HashMap<>();
        env.put("java.naming.factory.initial", "org.apache.camel.support.jndi.CamelInitialContextFactory");

        JndiRegistry jndi = new JndiRegistry(env);
        jndi.bind("bar", "Hello bar");
        assertEquals("Hello bar", jndi.lookupByName("bar"));
    }

}
