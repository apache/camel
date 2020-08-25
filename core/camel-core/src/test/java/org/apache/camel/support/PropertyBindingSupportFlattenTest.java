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
import org.apache.camel.spi.PropertiesComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for PropertyBindingSupport
 */
public class PropertyBindingSupportFlattenTest extends ContextTestSupport {

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

        // Map.of requires JDK9 onwards and we are still compatible with Java 8
        Map<String, Object> work = new HashMap<>();
        work.put("id", "123");
        work.put("name", "{{companyName}}");

        Map<String, Object> bar = new HashMap<>();
        bar.put("age", "33");
        bar.put("{{committer}}", "true");
        bar.put("gold-customer", "true");
        bar.put("work", work);

        Map<String, Object> map = new HashMap<>();
        map.put("name", "James");
        map.put("bar", bar);

        PropertyBindingSupport.bindWithFlattenProperties(context, foo, map);

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(123, foo.getBar().getWork().getId());
        assertEquals("Acme", foo.getBar().getWork().getName());

        assertTrue(map.isEmpty(), "Should bind all properties");
    }

    @Test
    public void testProperty() throws Exception {
        PropertiesComponent pc = context.getPropertiesComponent();
        Properties prop = new Properties();
        prop.setProperty("customerName", "James");
        prop.setProperty("customerAge", "33");
        prop.setProperty("workKey", "customerWork");
        prop.setProperty("customerWork", "Acme");
        pc.setInitialProperties(prop);

        Foo foo = new Foo();

        // Map.of requires JDK9 onwards and we are still compatible with Java 8
        Map<String, Object> work = new HashMap<>();
        work.put("id", "456");
        work.put("name", "#property:{{workKey}}");

        Map<String, Object> bar = new HashMap<>();
        bar.put("age", "#property:customerAge");
        bar.put("rider", "true");
        bar.put("gold-customer", "true");
        bar.put("work", work);

        Map<String, Object> map = new HashMap<>();
        map.put("name", "#property:customerName");
        map.put("bar", bar);

        PropertyBindingSupport.bindWithFlattenProperties(context, foo, map);

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(456, foo.getBar().getWork().getId());
        assertEquals("Acme", foo.getBar().getWork().getName());

        try {
            PropertyBindingSupport.build().bind(context, foo, "name", "#property:unknown");
            fail("Should have thrown exception");
        } catch (PropertyBindingException e) {
            assertEquals("name", e.getPropertyName());
            assertEquals("#property:unknown", e.getValue());
            assertEquals("Property with key unknown not found by properties component", e.getCause().getMessage());
        }
    }

    @Test
    public void testWithFluentBuilder() throws Exception {
        Foo foo = new Foo();

        // Map.of requires JDK9 onwards and we are still compatible with Java 8
        Map<String, Object> work = new HashMap<>();
        work.put("name", "{{companyName}}");

        Map<String, Object> bar = new HashMap<>();
        bar.put("age", "33");
        bar.put("rider", "true");
        bar.put("gold-customer", "true");
        bar.put("work", work);

        Map<String, Object> prop = new HashMap<>();
        prop.put("bar", bar);

        PropertyBindingSupport.build().withCamelContext(context).withTarget(foo).withProperty("name", "James")
                .withProperty("bar.work.id", "123")
                .withFlattenProperties(true)
                // and add the rest
                .withProperties(prop).bind();

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(123, foo.getBar().getWork().getId());
        assertEquals("Acme", foo.getBar().getWork().getName());

        assertTrue(prop.isEmpty(), "Should bind all properties");
    }

    @Test
    public void testPropertiesNoReflection() throws Exception {
        Foo foo = new Foo();

        // Map.of requires JDK9 onwards and we are still compatible with Java 8
        Map<String, Object> bar = new HashMap<>();
        bar.put("AGE", "33");

        Map<String, Object> prop = new HashMap<>();
        prop.put("bar", bar);
        prop.put("name", "James");

        PropertyBindingSupport.build().withFlattenProperties(true).withReflection(false).bind(context, foo, prop);

        assertNull(foo.getName());
        assertEquals(0, foo.getBar().getAge());

        // should not bind any properties as reflection is off
        assertEquals(2, prop.size());
    }

    @Test
    public void testPropertiesIgnoreCase() throws Exception {
        Foo foo = new Foo();

        // Map.of requires JDK9 onwards and we are still compatible with Java 8
        Map<String, Object> work = new HashMap<>();
        work.put("naME", "{{companyName}}");
        work.put("ID", "123");

        Map<String, Object> bar = new HashMap<>();
        bar.put("AGE", "33");
        bar.put("{{committer}}", "true");
        bar.put("gOLd-Customer", "true");
        bar.put("WoRk", work);

        Map<String, Object> prop = new HashMap<>();
        prop.put("bar", bar);
        prop.put("name", "James");

        PropertyBindingSupport.build().withFlattenProperties(true).withIgnoreCase(true).bind(context, foo, prop);

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(123, foo.getBar().getWork().getId());
        assertEquals("Acme", foo.getBar().getWork().getName());

        assertTrue(prop.isEmpty(), "Should bind all properties");
    }

    @Test
    public void testPropertiesDash() throws Exception {
        Foo foo = new Foo();

        // Map.of requires JDK9 onwards and we are still compatible with Java 8
        Map<String, Object> work = new HashMap<>();
        work.put("name", "{{companyName}}");
        work.put("id", "123");

        Map<String, Object> bar = new HashMap<>();
        bar.put("age", "33");
        bar.put("{{committer}}", "true");
        bar.put("gold-customer", "true");
        bar.put("work", work);

        Map<String, Object> prop = new HashMap<>();
        prop.put("bar", bar);
        prop.put("name", "James");

        PropertyBindingSupport.build().withFlattenProperties(true).bind(context, foo, prop);

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(123, foo.getBar().getWork().getId());
        assertEquals("Acme", foo.getBar().getWork().getName());

        assertTrue(prop.isEmpty(), "Should bind all properties");
    }

    @Test
    public void testBindPropertiesWithOptionPrefix() throws Exception {
        Foo foo = new Foo();

        // Map.of requires JDK9 onwards and we are still compatible with Java 8
        Map<String, Object> work = new HashMap<>();
        work.put("name", "{{companyName}}");
        work.put("id", "123");

        Map<String, Object> bar = new HashMap<>();
        bar.put("age", "33");
        bar.put("{{committer}}", "true");
        bar.put("gold-customer", "true");
        bar.put("work", work);

        Map<String, Object> prop = new HashMap<>();
        prop.put("bar", bar);
        prop.put("name", "James");

        Map<String, Object> other = new HashMap<>();
        other.put("something", "test");

        Map<String, Object> root = new HashMap<>();
        root.put("prefix", prop);
        root.put("other", other);

        Map<String, Object> my = new HashMap<>();
        my.put("my", root);

        PropertyBindingSupport.build().withFlattenProperties(true).withOptionPrefix("my.prefix.").bind(context, foo, my);

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(123, foo.getBar().getWork().getId());
        assertEquals("Acme", foo.getBar().getWork().getName());
        assertEquals("test", other.get("something"));
        assertEquals(1, other.size());
    }

    @Test
    public void testPropertiesOptionalKey() throws Exception {
        Foo foo = new Foo();

        // Map.of requires JDK9 onwards and we are still compatible with Java 8
        Map<String, Object> adr = new HashMap<>();
        adr.put("?addresss", "Some street");
        adr.put("?zip", "1234");

        Map<String, Object> work = new HashMap<>();
        work.put("?naME", "{{companyName}}");
        work.put("?ID", "123");
        work.put("address", adr);

        Map<String, Object> bar = new HashMap<>();
        bar.put("AGE", "33");
        bar.put("{{committer}}", "true");
        bar.put("gOLd-Customer", "true");
        bar.put("?silver-Customer", "true");
        bar.put("WoRk", work);

        Map<String, Object> prop = new HashMap<>();
        prop.put("bar", bar);
        prop.put("name", "James");

        PropertyBindingSupport.build().withFlattenProperties(true).withIgnoreCase(true).bind(context, foo, prop);

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(123, foo.getBar().getWork().getId());
        assertEquals("Acme", foo.getBar().getWork().getName());

        assertFalse(prop.isEmpty(), "Should NOT bind all properties");
        assertTrue(bar.containsKey("?silver-Customer"));
        assertTrue(adr.containsKey("?addresss"));
        assertTrue(adr.containsKey("?zip"));
    }

    public static class Foo {
        private String name;
        private Bar bar = new Bar();
        private Animal animal;

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

        public Animal getAnimal() {
            return animal;
        }

        public void setAnimal(Animal animal) {
            this.animal = animal;
        }
    }

    public static class Bar {
        private int age;
        private boolean rider;
        private Company work; // has no default value but Camel can automatic
                             // create one if there is a setter
        private boolean goldCustomer;

        public int getAge() {
            return age;
        }

        public boolean isRider() {
            return rider;
        }

        public Company getWork() {
            return work;
        }

        public boolean isGoldCustomer() {
            return goldCustomer;
        }

        // this has no setter but only builders
        // and mix the builders with both styles (with as prefix and no prefix
        // at all)

        public Bar withAge(int age) {
            this.age = age;
            return this;
        }

        public Bar withRider(boolean rider) {
            this.rider = rider;
            return this;
        }

        public Bar work(Company work) {
            this.work = work;
            return this;
        }

        public Bar goldCustomer(boolean goldCustomer) {
            this.goldCustomer = goldCustomer;
            return this;
        }
    }

}
