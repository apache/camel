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
package org.apache.camel.support;

import java.util.Iterator;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.FooBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultRegistryTest {

    private final SimpleRegistry br = new SimpleRegistry();
    private final DefaultRegistry registry = new DefaultRegistry(br);
    private final Company myCompany = new Company();
    private final FooBar myFooBar = new FooBar();

    @BeforeEach
    protected void setUp() throws Exception {
        br.bind("myCompany", myCompany);
        registry.bind("myFooBar", myFooBar);
    }

    @Test
    public void testLookupByName() throws Exception {
        assertNull(registry.lookupByName("foo"));
        assertSame(myCompany, registry.lookupByName("myCompany"));
        assertSame(myFooBar, registry.lookupByName("myFooBar"));
    }

    @Test
    public void testLookupByNameAndType() throws Exception {
        assertNull(registry.lookupByNameAndType("foo", Object.class));
        assertSame(myCompany, registry.lookupByNameAndType("myCompany", Company.class));
        assertSame(myFooBar, registry.lookupByNameAndType("myFooBar", FooBar.class));
        assertSame(myCompany, registry.lookupByNameAndType("myCompany", Object.class));
        assertSame(myFooBar, registry.lookupByNameAndType("myFooBar", Object.class));

        assertNull(registry.lookupByNameAndType("myCompany", FooBar.class));
        assertNull(registry.lookupByNameAndType("myFooBar", Company.class));
    }

    @Test
    public void testFindByType() throws Exception {
        assertEquals(0, registry.findByType(DefaultRegistry.class).size());

        assertEquals(1, registry.findByType(Company.class).size());
        assertEquals(myCompany, registry.findByType(Company.class).iterator().next());
        assertEquals(1, registry.findByType(FooBar.class).size());
        assertEquals(myFooBar, registry.findByType(FooBar.class).iterator().next());

        assertEquals(2, registry.findByType(Object.class).size());
        Iterator it = registry.findByType(Object.class).iterator();
        assertSame(myCompany, it.next());
        assertSame(myFooBar, it.next());
    }

    @Test
    public void testFindByTypeWithName() throws Exception {
        assertEquals(0, registry.findByTypeWithName(DefaultRegistry.class).size());

        assertEquals(1, registry.findByTypeWithName(Company.class).size());
        assertEquals(myCompany, registry.findByTypeWithName(Company.class).values().iterator().next());
        assertEquals(1, registry.findByTypeWithName(FooBar.class).size());
        assertEquals(myFooBar, registry.findByTypeWithName(FooBar.class).values().iterator().next());

        assertEquals(2, registry.findByTypeWithName(Object.class).size());
        Iterator it = registry.findByTypeWithName(Object.class).keySet().iterator();
        assertEquals("myCompany", it.next());
        assertEquals("myFooBar", it.next());
    }

    @Test
    public void testBindCamelContextAwareInject() throws Exception {
        CamelContext context = new DefaultCamelContext();
        registry.setCamelContext(context);

        MyBean my = new MyBean("Tiger");
        registry.bind("tiger", my);

        MyBean lookup = (MyBean) registry.lookupByName("tiger");
        assertSame(my, lookup);

        assertNotNull(lookup.getCamelContext());
        assertSame(context, lookup.getCamelContext());
    }

    private class MyBean implements CamelContextAware {

        private CamelContext camelContext;

        private String name;

        public MyBean(String name) {
            this.name = name;
        }

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        public String getName() {
            return name;
        }
    }

}
