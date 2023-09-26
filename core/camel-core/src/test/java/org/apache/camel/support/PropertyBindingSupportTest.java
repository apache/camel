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
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.PropertyBindingException;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.PropertiesComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

        assertTrue(prop.isEmpty(), "Should bind all properties");
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

        PropertyBindingSupport.build().bind(context, foo, "name", "#property:customerName");
        PropertyBindingSupport.build().bind(context, foo, "bar.age", "#property:customerAge");
        PropertyBindingSupport.build().bind(context, foo, "bar.gold-customer", "true");
        PropertyBindingSupport.build().bind(context, foo, "bar.rider", "true");
        PropertyBindingSupport.build().bind(context, foo, "bar.work.id", "456");
        PropertyBindingSupport.build().bind(context, foo, "bar.work.name", "#property:{{workKey}}");

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

        Map<String, Object> prop = new HashMap<>();
        prop.put("bar.age", "33");
        prop.put("bar.{{committer}}", "true");
        prop.put("bar.gold-customer", "true");
        prop.put("bar.work.name", "{{companyName}}");

        PropertyBindingSupport.build().withCamelContext(context).withTarget(foo).withProperty("name", "James")
                .withProperty("bar.work.id", "123")
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

        Map<String, Object> prop = new HashMap<>();
        prop.put("name", "James");
        prop.put("bar.AGE", "33");

        PropertyBindingSupport.build().withReflection(false).bind(context, foo, prop);

        assertNull(foo.getName());
        assertEquals(0, foo.getBar().getAge());

        // should not bind any properties as reflection is off
        assertEquals(2, prop.size());
    }

    @Test
    public void testPropertiesIgnoreCase() throws Exception {
        Foo foo = new Foo();

        Map<String, Object> prop = new HashMap<>();
        prop.put("name", "James");
        prop.put("bar.AGE", "33");
        prop.put("BAR.{{committer}}", "true");
        prop.put("bar.gOLd-Customer", "true");
        prop.put("bAr.work.ID", "123");
        prop.put("bar.WORk.naME", "{{companyName}}");

        PropertyBindingSupport.build().withIgnoreCase(true).bind(context, foo, prop);

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

        Map<String, Object> prop = new HashMap<>();
        prop.put("name", "James");
        prop.put("bar.age", "33");
        prop.put("bar.{{committer}}", "true");
        prop.put("bar.gold-customer", "true");
        prop.put("bar.work.id", "123");
        prop.put("bar.work.name", "{{companyName}}");

        PropertyBindingSupport.build().bind(context, foo, prop);

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

        Map<String, Object> prop = new HashMap<>();
        prop.put("my.prefix.name", "James");
        prop.put("my.prefix.bar.age", "33");
        prop.put("my.prefix.bar.{{committer}}", "true");
        prop.put("my.prefix.bar.gold-customer", "true");
        prop.put("my.prefix.bar.work.id", "123");
        prop.put("my.prefix.bar.work.name", "{{companyName}}");
        prop.put("my.other.prefix.something", "test");

        PropertyBindingSupport.build().withOptionPrefix("my.prefix.").bind(context, foo, prop);

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(123, foo.getBar().getWork().getId());
        assertEquals("Acme", foo.getBar().getWork().getName());
        assertTrue(prop.containsKey("my.other.prefix.something"));
        assertEquals(1, prop.size());
    }

    @Test
    public void testBindPropertiesWithOptionPrefixIgnoreCase() throws Exception {
        Foo foo = new Foo();

        Map<String, Object> prop = new HashMap<>();
        prop.put("my.prefix.name", "James");
        prop.put("my.PREFIX.bar.AGE", "33");
        prop.put("my.prefix.bar.{{committer}}", "true");
        prop.put("My.prefix.bar.Gold-custoMER", "true");
        prop.put("mY.prefix.bar.work.ID", "123");
        prop.put("my.prEFIx.bar.Work.Name", "{{companyName}}");
        prop.put("my.other.prefix.something", "test");

        PropertyBindingSupport.build().withOptionPrefix("my.prefix.").withIgnoreCase(true).bind(context, foo, prop);

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(123, foo.getBar().getWork().getId());
        assertEquals("Acme", foo.getBar().getWork().getName());
        assertTrue(prop.containsKey("my.other.prefix.something"));
        assertEquals(1, prop.size());
    }

    @Test
    public void testNested() throws Exception {
        Foo foo = new Foo();

        PropertyBindingSupport.build().bind(context, foo, "name", "James");
        PropertyBindingSupport.build().bind(context, foo, "bar.age", "33");
        PropertyBindingSupport.build().bind(context, foo, "bar.{{committer}}", "true");
        PropertyBindingSupport.build().bind(context, foo, "bar.gold-customer", "true");
        PropertyBindingSupport.build().bind(context, foo, "bar.work.id", "123");
        PropertyBindingSupport.build().bind(context, foo, "bar.work.name", "{{companyName}}");

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

        PropertyBindingSupport.build().bind(context, foo, "name", "James");
        PropertyBindingSupport.build().bind(context, foo, "bar.age", "33");
        PropertyBindingSupport.build().bind(context, foo, "bar.gold-customer", "true");
        PropertyBindingSupport.build().bind(context, foo, "bar.rider", "true");
        PropertyBindingSupport.build().bind(context, foo, "bar.work", "#bean:myWork");

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

        PropertyBindingSupport.build().bind(context, foo, "name", "James");
        PropertyBindingSupport.build().bind(context, foo, "bar.age", "33");
        PropertyBindingSupport.build().bind(context, foo, "bar.gold-customer", "true");
        PropertyBindingSupport.build().bind(context, foo, "bar.rider", "true");
        PropertyBindingSupport.build().bind(context, foo, "bar.work", "#bean:myWork");

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

        PropertyBindingSupport.build().bind(context, foo, "name", "James");
        PropertyBindingSupport.build().bind(context, foo, "bar.age", "33");
        PropertyBindingSupport.build().bind(context, foo, "bar.{{committer}}", "true");
        PropertyBindingSupport.build().bind(context, foo, "bar.gold-customer", "true");
        PropertyBindingSupport.build().bind(context, foo, "bar.work", "#type:org.apache.camel.support.Company");

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

        PropertyBindingSupport.build().bind(context, foo, "name", "James");
        PropertyBindingSupport.build().bind(context, foo, "bar.age", "33");
        PropertyBindingSupport.build().bind(context, foo, "bar.{{committer}}", "true");
        PropertyBindingSupport.build().bind(context, foo, "bar.gold-customer", "true");
        PropertyBindingSupport.build().bind(context, foo, "bar.work", "#class:org.apache.camel.support.Company");

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        // a new class was created so its empty
        assertEquals(0, foo.getBar().getWork().getId());
        assertNull(foo.getBar().getWork().getName());
    }

    @Test
    public void testAutowired() throws Exception {
        Foo foo = new Foo();

        PropertyBindingSupport.build().bind(context, foo, "name", "James");
        PropertyBindingSupport.build().bind(context, foo, "bar.age", "33");
        PropertyBindingSupport.build().bind(context, foo, "bar.{{committer}}", "true");
        PropertyBindingSupport.build().bind(context, foo, "bar.gold-customer", "true");
        PropertyBindingSupport.build().bind(context, foo, "bar.work", "#autowired");

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

        PropertyBindingSupport.build().withMandatory(true).bind(context, foo, "name", "James");

        boolean bound = PropertyBindingSupport.build().bind(context, foo, "bar.myAge", "33");
        assertFalse(bound);

        try {
            PropertyBindingSupport.build().withMandatory(true).bind(context, foo, "bar.myAge", "33");
            fail("Should have thrown exception");
        } catch (PropertyBindingException e) {
            assertEquals("myAge", e.getPropertyName());
            assertSame(foo.getBar(), e.getTarget());
        }
    }

    @Test
    public void testDoesNotExistClass() throws Exception {
        Foo foo = new Foo();

        PropertyBindingSupport.build().bind(context, foo, "name", "James");
        try {
            PropertyBindingSupport.build().bind(context, foo, "bar.work", "#class:org.apache.camel.support.DoesNotExist");
            fail("Should throw exception");
        } catch (PropertyBindingException e) {
            assertIsInstanceOf(ClassNotFoundException.class, e.getCause());
        }
    }

    @Test
    public void testNullInjectorClass() throws Exception {
        Foo foo = new Foo();

        context.setInjector(new Injector() {
            @Override
            public <T> T newInstance(Class<T> type) {
                return null;
            }

            @Override
            public <T> T newInstance(Class<T> type, String factoryMethod) {
                return null;
            }

            @Override
            public <T> T newInstance(Class<T> type, Class<?> factoryClass, String factoryMethod) {
                return null;
            }

            @Override
            public <T> T newInstance(Class<T> type, boolean postProcessBean) {
                return null;
            }

            @Override
            public boolean supportsAutoWiring() {
                return false;
            }
        });

        PropertyBindingSupport.build().bind(context, foo, "name", "James");
        try {
            PropertyBindingSupport.build().bind(context, foo, "bar.work", "#class:org.apache.camel.support.Company");
            fail("Should throw exception");
        } catch (PropertyBindingException e) {
            assertIsInstanceOf(IllegalStateException.class, e.getCause());
        }
    }

    @Test
    public void testNestedClassConstructorParameterOneParameter() throws Exception {
        Foo foo = new Foo();

        PropertyBindingSupport.build().bind(context, foo, "name", "James");
        PropertyBindingSupport.build().bind(context, foo, "animal", "#class:org.apache.camel.support.Animal('Tony Tiger')");
        PropertyBindingSupport.build().bind(context, foo, "animal.dangerous", "true");

        assertEquals("James", foo.getName());
        assertEquals("Tony Tiger", foo.getAnimal().getName());
        assertTrue(foo.getAnimal().isDangerous());
    }

    @Test
    public void testNestedClassConstructorParameterPlaceholder() throws Exception {
        Foo foo = new Foo();

        PropertyBindingSupport.build().bind(context, foo, "name", "James");
        PropertyBindingSupport.build().bind(context, foo, "animal",
                "#class:org.apache.camel.support.Animal('{{companyName}}', false)");

        assertEquals("James", foo.getName());
        assertEquals("Acme", foo.getAnimal().getName());
        assertFalse(foo.getAnimal().isDangerous());
    }

    @Test
    public void testNestedClassConstructorParameterTwoParameter() throws Exception {
        Foo foo = new Foo();

        PropertyBindingSupport.build().bind(context, foo, "name", "James");
        PropertyBindingSupport.build().bind(context, foo, "animal",
                "#class:org.apache.camel.support.Animal('Donald Duck', false)");

        assertEquals("James", foo.getName());
        assertEquals("Donald Duck", foo.getAnimal().getName());
        assertFalse(foo.getAnimal().isDangerous());
    }

    @Test
    public void testNestedClassConstructorParameterMandatoryBean() throws Exception {
        Foo foo = new Foo();

        PropertyBindingSupport.build().bind(context, foo, "name", "James");
        try {
            PropertyBindingSupport.build().bind(context, foo, "animal",
                    "#class:org.apache.camel.support.Animal('#bean:myName', false)");
            fail("Should have thrown exception");
        } catch (PropertyBindingException e) {
            NoSuchBeanException nsb = assertIsInstanceOf(NoSuchBeanException.class, e.getCause());
            assertEquals("myName", nsb.getName());
        }

        // add bean and try again
        context.getRegistry().bind("myName", "Acme");
        PropertyBindingSupport.build().bind(context, foo, "animal",
                "#class:org.apache.camel.support.Animal('#bean:myName', false)");

        assertEquals("James", foo.getName());
        assertEquals("Acme", foo.getAnimal().getName());
        assertFalse(foo.getAnimal().isDangerous());
    }

    @Test
    public void testNestedClassFactoryParameterOneParameter() throws Exception {
        Foo foo = new Foo();

        PropertyBindingSupport.build().bind(context, foo, "name", "James");
        PropertyBindingSupport.build().bind(context, foo, "animal",
                "#class:org.apache.camel.support.AnimalFactory#createAnimal('Tiger')");

        assertEquals("James", foo.getName());
        assertEquals("Tiger", foo.getAnimal().getName());
        assertTrue(foo.getAnimal().isDangerous());
    }

    @Test
    public void testNestedClassFactoryParameterTwoParameter() throws Exception {
        Foo foo = new Foo();

        PropertyBindingSupport.build().bind(context, foo, "name", "James");
        PropertyBindingSupport.build().bind(context, foo, "animal",
                "#class:org.apache.camel.support.AnimalFactory#createAnimal('Donald Duck', false)");

        assertEquals("James", foo.getName());
        assertEquals("Donald Duck", foo.getAnimal().getName());
        assertFalse(foo.getAnimal().isDangerous());
    }

    @Test
    public void testNestedClassFactoryParameterPlaceholder() throws Exception {
        Foo foo = new Foo();

        PropertyBindingSupport.build().bind(context, foo, "name", "James");
        PropertyBindingSupport.build().bind(context, foo, "animal",
                "#class:org.apache.camel.support.AnimalFactory#createAnimal('{{companyName}}', false)");

        assertEquals("James", foo.getName());
        assertEquals("Acme", foo.getAnimal().getName());
        assertFalse(foo.getAnimal().isDangerous());
    }

    @Test
    public void testPropertiesOptionalKey() throws Exception {
        Foo foo = new Foo();

        Map<String, Object> prop = new HashMap<>();
        prop.put("name", "James");
        prop.put("?bar.AGE", "33");
        prop.put("BAR.{{committer}}", "true");
        prop.put("bar.gOLd-Customer", "true");
        prop.put("?bar.silver-Customer", "true");
        prop.put("?bAr.work.ID", "123");
        prop.put("?bar.WORk.naME", "{{companyName}}");
        prop.put("?bar.work.addresss", "Some street");
        prop.put("?bar.work.addresss.zip", "1234");

        PropertyBindingSupport.build().withIgnoreCase(true).bind(context, foo, prop);

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(123, foo.getBar().getWork().getId());
        assertEquals("Acme", foo.getBar().getWork().getName());

        assertFalse(prop.isEmpty(), "Should NOT bind all properties");
        assertEquals(3, prop.size());
        assertTrue(prop.containsKey("?bar.silver-Customer"));
        assertTrue(prop.containsKey("?bar.work.addresss"));
        assertTrue(prop.containsKey("?bar.work.addresss.zip"));
    }

    @Test
    public void testPropertiesOptionalKeyMandatory() throws Exception {
        Foo foo = new Foo();

        Map<String, Object> prop = new HashMap<>();
        prop.put("name", "James");
        prop.put("bar.AGE", "33");
        prop.put("BAR.{{committer}}", "true");
        prop.put("bar.gOLd-Customer", "true");
        prop.put("?bar.silver-Customer", "true");
        prop.put("?bAr.work.ID", "123");
        prop.put("?bar.WORk.naME", "{{companyName}}");
        prop.put("?bar.work.addresss", "Some street");
        prop.put("?bar.work.addresss.zip", "1234");

        PropertyBindingSupport.build().withIgnoreCase(true).withMandatory(true).bind(context, foo, prop);

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(123, foo.getBar().getWork().getId());
        assertEquals("Acme", foo.getBar().getWork().getName());

        assertFalse(prop.isEmpty(), "Should NOT bind all properties");
        assertEquals(3, prop.size());
        assertTrue(prop.containsKey("?bar.silver-Customer"));
        assertTrue(prop.containsKey("?bar.work.addresss"));
        assertTrue(prop.containsKey("?bar.work.addresss.zip"));

        // should not fail as we marked the option as optional
        prop.put("?bar.unknown", "123");
        PropertyBindingSupport.build().withIgnoreCase(true).withMandatory(true).bind(context, foo, prop);
        prop.remove("?bar.unknown");

        // should fail as its mandatory
        prop.put("bar.unknown", "123");
        try {
            PropertyBindingSupport.build().withIgnoreCase(true).withMandatory(true).bind(context, foo, prop);
            fail("Should fail");
        } catch (PropertyBindingException e) {
            assertEquals("unknown", e.getPropertyName());
        }
    }

    @Test
    public void testConvert() throws Exception {
        Foo foo = new Foo();

        Map<String, Object> prop = new HashMap<>();
        prop.put("name", "James");
        prop.put("bar.age", "#valueAs(Integer):33");
        prop.put("bar.rider", "#valueAs(boolean):true");
        prop.put("bar.gold-customer", "#valueAs(boolean):true");
        prop.put("bar.work.id", "#valueAs(int):123");
        prop.put("bar.work.name", "{{companyName}}");

        PropertyBindingSupport.bindProperties(context, foo, prop);

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(123, foo.getBar().getWork().getId());
        assertEquals("Acme", foo.getBar().getWork().getName());

        assertTrue(prop.isEmpty(), "Should bind all properties");
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
