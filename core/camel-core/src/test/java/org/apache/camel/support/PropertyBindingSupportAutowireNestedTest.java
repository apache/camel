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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.junit.Test;

/**
 * Unit test for PropertyBindingSupport
 */
public class PropertyBindingSupportAutowireNestedTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        Company work = new Company();
        work.setId(456);
        work.setName("Acme");
        context.getRegistry().bind("myWork", work);

        return context;
    }

    @Test
    public void testAutowireProperties() throws Exception {
        Foo foo = new Foo();

        PropertyBindingSupport.build().bind(context, foo, "name", "James");
        PropertyBindingSupport.build().bind(context, foo, "bar.age", "33");
        PropertyBindingSupport.build().bind(context, foo, "bar.rider", "true");
        PropertyBindingSupport.build().bind(context, foo, "bar.gold-customer", "true");
        PropertyBindingSupport.autowireSingletonPropertiesFromRegistry(context, foo, false, false, null);

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        // should not be auto wired
        assertNull(foo.getBar().getWork());
    }

    @Test
    public void testAutowirePropertiesNested() throws Exception {
        Foo foo = new Foo();

        PropertyBindingSupport.build().bind(context, foo, "name", "James");
        PropertyBindingSupport.build().bind(context, foo, "bar.age", "33");
        PropertyBindingSupport.build().bind(context, foo, "bar.rider", "true");
        PropertyBindingSupport.build().bind(context, foo, "bar.gold-customer", "true");
        PropertyBindingSupport.autowireSingletonPropertiesFromRegistry(context, foo, false, true, null);

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        // should be auto wired
        assertNotNull(foo.getBar().getWork());
        assertEquals(456, foo.getBar().getWork().getId());
        assertEquals("Acme", foo.getBar().getWork().getName());
    }

    public static class Foo {
        private String name;
        private Bar bar = new Bar(this);

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Bar getBar() {
            return bar;
        }

        public void setBar(Bar bar) {
            this.bar = bar;
        }
    }

    public static class Bar {
        private Foo parent;
        private int age;
        private boolean rider;
        private Company work;
        private boolean goldCustomer;

        public Bar(Foo parent) {
            this.parent = parent;
        }

        public Foo getParent() {
            // circular dependency foo -> bar && bar -> foo
            return parent;
        }

        public void setParent(Foo parent) {
            this.parent = parent;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public boolean isRider() {
            return rider;
        }

        public void setRider(boolean rider) {
            this.rider = rider;
        }

        public Company getWork() {
            return work;
        }

        public void setWork(Company work) {
            this.work = work;
        }

        public boolean isGoldCustomer() {
            return goldCustomer;
        }

        public void setGoldCustomer(boolean goldCustomer) {
            this.goldCustomer = goldCustomer;
        }
    }

}
