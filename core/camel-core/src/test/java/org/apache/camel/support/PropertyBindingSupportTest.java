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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.PropertyBindingException;
import org.junit.Test;

/**
 * Unit test for PropertyBindingSupport
 */
public class PropertyBindingSupportTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        Company work = new Company();
        work.setId(456);
        work.setName("Acme");
        context.getRegistry().bind("myWork", work);

        Properties placeholders = new Properties();
        placeholders.put("companyName", "Acme");
        placeholders.put("committer", "rider");
        context.getPropertiesComponent().setInitialProperties(placeholders);

        return context;
    }

    @Test
    public void testProperties() throws Exception {
        Foo foo = new Foo();

        Map<String, Object> prop = new HashMap<>();
        prop.put("name", "James");
        prop.put("bar.age", "33");
        prop.put("bar.{{committer}}", "true");
        prop.put("bar.gold-customer", "true");
        prop.put("bar.work.id", "123");
        prop.put("bar.work.name", "{{companyName}}");

        PropertyBindingSupport.bindProperties(context, foo, prop);

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(123, foo.getBar().getWork().getId());
        assertEquals("Acme", foo.getBar().getWork().getName());
    }

    @Test
    public void testNested() throws Exception {
        Foo foo = new Foo();

        PropertyBindingSupport.bindProperty(context, foo, "name", "James");
        PropertyBindingSupport.bindProperty(context, foo, "bar.age", "33");
        PropertyBindingSupport.bindProperty(context, foo, "bar.{{committer}}", "true");
        PropertyBindingSupport.bindProperty(context, foo, "bar.gold-customer", "true");
        PropertyBindingSupport.bindProperty(context, foo, "bar.work.id", "123");
        PropertyBindingSupport.bindProperty(context, foo, "bar.work.name", "{{companyName}}");

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(123, foo.getBar().getWork().getId());
        assertEquals("Acme", foo.getBar().getWork().getName());
    }

    @Test
    public void testNestedReference() throws Exception {
        Foo foo = new Foo();

        PropertyBindingSupport.bindProperty(context, foo, "name", "James");
        PropertyBindingSupport.bindProperty(context, foo, "bar.age", "33");
        PropertyBindingSupport.bindProperty(context, foo, "bar.gold-customer", "true");
        PropertyBindingSupport.bindProperty(context, foo, "bar.rider", "true");
        PropertyBindingSupport.bindProperty(context, foo, "bar.work", "#myWork");

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(456, foo.getBar().getWork().getId());
        assertEquals("Acme", foo.getBar().getWork().getName());
    }

    @Test
    public void testNestedReferenceId() throws Exception {
        Foo foo = new Foo();

        PropertyBindingSupport.bindProperty(context, foo, "name", "James");
        PropertyBindingSupport.bindProperty(context, foo, "bar.age", "33");
        PropertyBindingSupport.bindProperty(context, foo, "bar.gold-customer", "true");
        PropertyBindingSupport.bindProperty(context, foo, "bar.rider", "true");
        PropertyBindingSupport.bindProperty(context, foo, "bar.work", "#id:myWork");

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(456, foo.getBar().getWork().getId());
        assertEquals("Acme", foo.getBar().getWork().getName());
    }

    @Test
    public void testNestedType() throws Exception {
        Foo foo = new Foo();

        PropertyBindingSupport.bindProperty(context, foo, "name", "James");
        PropertyBindingSupport.bindProperty(context, foo, "bar.age", "33");
        PropertyBindingSupport.bindProperty(context, foo, "bar.{{committer}}", "true");
        PropertyBindingSupport.bindProperty(context, foo, "bar.gold-customer", "true");
        PropertyBindingSupport.bindProperty(context, foo, "bar.work", "#type:org.apache.camel.support.Company");

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(456, foo.getBar().getWork().getId());
        assertEquals("Acme", foo.getBar().getWork().getName());
    }

    @Test
    public void testNestedClass() throws Exception {
        Foo foo = new Foo();

        PropertyBindingSupport.bindProperty(context, foo, "name", "James");
        PropertyBindingSupport.bindProperty(context, foo, "bar.age", "33");
        PropertyBindingSupport.bindProperty(context, foo, "bar.{{committer}}", "true");
        PropertyBindingSupport.bindProperty(context, foo, "bar.gold-customer", "true");
        PropertyBindingSupport.bindProperty(context, foo, "bar.work", "#class:org.apache.camel.support.Company");

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        // a new class was created so its empty
        assertEquals(0, foo.getBar().getWork().getId());
        assertEquals(null, foo.getBar().getWork().getName());
    }

    @Test
    public void testAutowire() throws Exception {
        Foo foo = new Foo();

        PropertyBindingSupport.bindProperty(context, foo, "name", "James");
        PropertyBindingSupport.bindProperty(context, foo, "bar.age", "33");
        PropertyBindingSupport.bindProperty(context, foo, "bar.{{committer}}", "true");
        PropertyBindingSupport.bindProperty(context, foo, "bar.gold-customer", "true");
        PropertyBindingSupport.bindProperty(context, foo, "bar.work", "#autowire");

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(456, foo.getBar().getWork().getId());
        assertEquals("Acme", foo.getBar().getWork().getName());
    }

    @Test
    public void testMandatory() throws Exception {
        Foo foo = new Foo();

        PropertyBindingSupport.bindMandatoryProperty(context, foo, "name", "James");

        boolean bound = PropertyBindingSupport.bindProperty(context, foo, "bar.myAge", "33");
        assertFalse(bound);

        try {
            PropertyBindingSupport.bindMandatoryProperty(context, foo, "bar.myAge", "33");
            fail("Should have thrown exception");
        } catch (PropertyBindingException e) {
            assertEquals("bar.myAge", e.getPropertyName());
            assertSame(foo, e.getTarget());
        }
    }

    public static class Foo {
        private String name;
        private Bar bar = new Bar();

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
        private int age;
        private boolean rider;
        private Company work; // has no default value but Camel can automatic create one if there is a setter
        private boolean goldCustomer;

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

