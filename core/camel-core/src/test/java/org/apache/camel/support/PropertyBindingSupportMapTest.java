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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.PropertyBindingException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for PropertyBindingSupport
 */
public class PropertyBindingSupportMapTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        Company work1 = new Company();
        work1.setId(123);
        work1.setName("Acme");
        context.getRegistry().bind("company1", work1);
        Company work2 = new Company();
        work2.setId(456);
        work2.setName("Acme 2");
        context.getRegistry().bind("company2", work2);

        Properties placeholders = new Properties();
        placeholders.put("companyName", "Acme");
        placeholders.put("committer", "rider");
        context.getPropertiesComponent().setInitialProperties(placeholders);

        return context;
    }

    @Test
    public void testPropertiesMap() throws Exception {
        Foo foo = new Foo();

        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("name", "James");
        prop.put("bar.age", "33");
        prop.put("bar.{{committer}}", "true");
        prop.put("bar.gold-customer", "true");
        prop.put("bar.works[acme]", "#bean:company1");
        prop.put("bar.works[burger]", "#bean:company2");

        PropertyBindingSupport.build().bind(context, foo, prop);

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(2, foo.getBar().getWorks().size());
        assertEquals(123, foo.getBar().getWorks().get("acme").getId());
        assertEquals("Acme", foo.getBar().getWorks().get("acme").getName());
        assertEquals(456, foo.getBar().getWorks().get("burger").getId());
        assertEquals("Acme 2", foo.getBar().getWorks().get("burger").getName());
    }

    @Test
    public void testPropertiesMapNested() throws Exception {
        Foo foo = new Foo();

        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("name", "James");
        prop.put("bar.age", "33");
        prop.put("bar.{{committer}}", "true");
        prop.put("bar.gold-customer", "true");
        prop.put("bar.works[acme]", "#bean:company1");
        prop.put("bar.works[acme].id", "666");
        prop.put("bar.works[burger]", "#bean:company2");
        prop.put("bar.works[burger].name", "I changed this");

        PropertyBindingSupport.build().bind(context, foo, prop);

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(2, foo.getBar().getWorks().size());
        assertEquals(666, foo.getBar().getWorks().get("acme").getId());
        assertEquals("Acme", foo.getBar().getWorks().get("acme").getName());
        assertEquals(456, foo.getBar().getWorks().get("burger").getId());
        assertEquals("I changed this", foo.getBar().getWorks().get("burger").getName());
    }

    @Test
    public void testPropertiesMapFirst() throws Exception {
        Bar bar = new Bar();

        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("works[acme]", "#bean:company1");
        prop.put("works[acme].id", "666");
        prop.put("works[burger]", "#bean:company2");
        prop.put("works[burger].name", "I changed this");

        PropertyBindingSupport.build().bind(context, bar, prop);

        assertEquals(2, bar.getWorks().size());
        assertEquals(666, bar.getWorks().get("acme").getId());
        assertEquals("Acme", bar.getWorks().get("acme").getName());
        assertEquals(456, bar.getWorks().get("burger").getId());
        assertEquals("I changed this", bar.getWorks().get("burger").getName());
    }

    @Test
    public void testPropertiesNotMap() throws Exception {
        Foo foo = new Foo();

        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("name", "James");
        prop.put("bar.age", "33");
        prop.put("bar.gold-customer[foo]", "true");

        try {
            PropertyBindingSupport.build().bind(context, foo, prop);
            fail("Should have thrown exception");
        } catch (PropertyBindingException e) {
            assertEquals("gold-customer[foo]", e.getPropertyName());
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertTrue(iae.getMessage().startsWith(
                    "Cannot set property: gold-customer[foo] as either a Map/List/array because target bean is not a Map, List or array type"));
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
        private Map<String, Company> works; // should auto-create this via the
                                           // setter
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

        public Map<String, Company> getWorks() {
            return works;
        }

        public void setWorks(Map<String, Company> works) {
            this.works = works;
        }

        public boolean isGoldCustomer() {
            return goldCustomer;
        }

        public void setGoldCustomer(boolean goldCustomer) {
            this.goldCustomer = goldCustomer;
        }
    }

}
