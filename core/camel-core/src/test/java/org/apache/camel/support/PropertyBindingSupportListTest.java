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
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.PropertyBindingException;
import org.junit.Test;

/**
 * Unit test for PropertyBindingSupport
 */
public class PropertyBindingSupportListTest extends ContextTestSupport {

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
    public void testPropertiesList() throws Exception {
        Foo foo = new Foo();

        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("name", "James");
        prop.put("bar.age", "33");
        prop.put("bar.{{committer}}", "true");
        prop.put("bar.gold-customer", "true");
        prop.put("bar.works[0]", "#bean:company1");
        prop.put("bar.works[1]", "#bean:company2");

        PropertyBindingSupport.build().bind(context, foo, prop);

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(2, foo.getBar().getWorks().size());
        assertEquals(123, foo.getBar().getWorks().get(0).getId());
        assertEquals("Acme", foo.getBar().getWorks().get(0).getName());
        assertEquals(456, foo.getBar().getWorks().get(1).getId());
        assertEquals("Acme 2", foo.getBar().getWorks().get(1).getName());
    }

    @Test
    public void testPropertiesListNested() throws Exception {
        Foo foo = new Foo();

        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("name", "James");
        prop.put("bar.age", "33");
        prop.put("bar.{{committer}}", "true");
        prop.put("bar.gold-customer", "true");
        prop.put("bar.works[0]", "#bean:company1");
        prop.put("bar.works[0].id", "666");
        prop.put("bar.works[1]", "#bean:company2");
        prop.put("bar.works[1].name", "I changed this");

        PropertyBindingSupport.build().bind(context, foo, prop);

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(2, foo.getBar().getWorks().size());
        assertEquals(666, foo.getBar().getWorks().get(0).getId());
        assertEquals("Acme", foo.getBar().getWorks().get(0).getName());
        assertEquals(456, foo.getBar().getWorks().get(1).getId());
        assertEquals("I changed this", foo.getBar().getWorks().get(1).getName());
    }

    @Test
    public void testPropertiesListFirst() throws Exception {
        Bar bar = new Bar();

        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("works[0]", "#bean:company1");
        prop.put("works[0].id", "666");
        prop.put("works[1]", "#bean:company2");
        prop.put("works[1].name", "I changed this");

        PropertyBindingSupport.build().bind(context, bar, prop);

        assertEquals(2, bar.getWorks().size());
        assertEquals(666, bar.getWorks().get(0).getId());
        assertEquals("Acme", bar.getWorks().get(0).getName());
        assertEquals(456, bar.getWorks().get(1).getId());
        assertEquals("I changed this", bar.getWorks().get(1).getName());
    }

    @Test
    public void testPropertiesNotList() throws Exception {
        Foo foo = new Foo();

        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("name", "James");
        prop.put("bar.age", "33");
        prop.put("bar.gold-customer[]", "true");

        try {
            PropertyBindingSupport.build().bind(context, foo, prop);
            fail("Should have thrown exception");
        } catch (PropertyBindingException e) {
            assertEquals("bar.gold-customer[]", e.getPropertyName());
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertTrue(iae.getMessage().startsWith("Cannot set property: gold-customer[] as either a Map/List because target bean is not a Map or List type"));
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
        private List<Company> works; // should auto-create this via the setter
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

        public List<Company> getWorks() {
            return works;
        }

        public void setWorks(List<Company> works) {
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
