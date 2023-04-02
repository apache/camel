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
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.LoggingLevel;
import org.apache.camel.PropertyBindingException;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for PropertyBindingSupport
 */
public class PropertyBindingSupportConfigurerTest extends ContextTestSupport {

    private final MyConfigurer myConfigurer = new MyConfigurer();

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
        BeanIntrospection bi = PluginHelper.getBeanIntrospection(context);
        bi.setExtendedStatistics(true);
        bi.setLoggingLevel(LoggingLevel.WARN);

        Bar bar = new Bar();

        Map<String, Object> prop = new HashMap<>();
        prop.put("age", "33");
        prop.put("{{committer}}", "true");
        prop.put("gold-customer", "true");

        myConfigurer.reset();
        PropertyBindingSupport.build().withConfigurer(myConfigurer).withIgnoreCase(true).bind(context, bar, prop);
        assertEquals(3, myConfigurer.getCounter());

        assertEquals(33, bar.getAge());
        assertTrue(bar.isRider());
        assertTrue(bar.isGoldCustomer());
        assertNull(bar.getWork());

        assertTrue(prop.isEmpty(), "Should bind all properties");

        // should not use reflection
        assertEquals(0, bi.getInvokedCounter());
    }

    @Test
    public void testPropertiesNested() throws Exception {
        BeanIntrospection bi = PluginHelper.getBeanIntrospection(context);
        bi.setExtendedStatistics(true);
        bi.setLoggingLevel(LoggingLevel.WARN);

        Bar bar = new Bar();

        Map<String, Object> prop = new HashMap<>();
        prop.put("age", "33");
        prop.put("{{committer}}", "true");
        prop.put("gold-customer", "true");
        prop.put("work.id", "123");
        prop.put("work.name", "{{companyName}}");

        myConfigurer.reset();
        PropertyBindingSupport.build().withConfigurer(myConfigurer).withIgnoreCase(true).bind(context, bar, prop);
        assertEquals(6, myConfigurer.getCounter());

        assertEquals(33, bar.getAge());
        assertTrue(bar.isRider());
        assertTrue(bar.isGoldCustomer());
        assertEquals(123, bar.getWork().getId());
        assertEquals("Acme", bar.getWork().getName());

        assertTrue(prop.isEmpty(), "Should bind all properties");

        // will use reflection for configuring Work as we do not have a configurer for it
        assertTrue(bi.getInvokedCounter() > 0);
    }

    @Test
    public void testAutowired() throws Exception {
        Bar bar = new Bar();

        Map<String, Object> prop = new HashMap<>();
        prop.put("age", "33");
        prop.put("{{committer}}", "true");
        prop.put("gold-customer", "true");
        prop.put("work", "#autowired");

        myConfigurer.reset();
        PropertyBindingSupport.build().withConfigurer(myConfigurer).withIgnoreCase(true).bind(context, bar, prop);
        // there should be 4 as autowried is also used
        assertEquals(3 + 1, myConfigurer.getCounter());

        assertEquals(33, bar.getAge());
        assertTrue(bar.isRider());
        assertTrue(bar.isGoldCustomer());
        assertEquals(456, bar.getWork().getId());
        assertEquals("Acme", bar.getWork().getName());

        assertTrue(prop.isEmpty(), "Should bind all properties");
    }

    @Test
    public void testPropertiesOptionalKey() throws Exception {
        Bar bar = new Bar();

        Map<String, Object> prop = new HashMap<>();
        prop.put("?AGE", "33");
        prop.put("{{committer}}", "true");
        prop.put("gOLd-Customer", "true");
        prop.put("?silver-Customer", "true");
        prop.put("?work.ID", "123");
        prop.put("?WORk.naME", "{{companyName}}");
        prop.put("?work.addresss", "Some street");
        prop.put("?work.addresss.zip", "1234");

        myConfigurer.reset();
        PropertyBindingSupport.build().withConfigurer(myConfigurer).withIgnoreCase(true).bind(context, bar, prop);
        assertEquals(7, myConfigurer.getCounter());

        assertEquals(33, bar.getAge());
        assertTrue(bar.isRider());
        assertTrue(bar.isGoldCustomer());
        assertEquals(123, bar.getWork().getId());
        assertEquals("Acme", bar.getWork().getName());

        assertFalse(prop.isEmpty(), "Should NOT bind all properties");
        assertEquals(3, prop.size());
        assertTrue(prop.containsKey("?silver-Customer"));
        assertTrue(prop.containsKey("?work.addresss"));
        assertTrue(prop.containsKey("?work.addresss.zip"));
    }

    @Test
    public void testPropertiesOptionalKeyMandatory() throws Exception {
        Bar bar = new Bar();

        Map<String, Object> prop = new HashMap<>();
        prop.put("?AGE", "33");
        prop.put("{{committer}}", "true");
        prop.put("gOLd-Customer", "true");
        prop.put("?silver-Customer", "true");
        prop.put("?work.ID", "123");
        prop.put("?WORk.naME", "{{companyName}}");
        prop.put("?work.addresss", "Some street");
        prop.put("?work.addresss.zip", "1234");

        myConfigurer.reset();
        PropertyBindingSupport.build().withConfigurer(myConfigurer).withIgnoreCase(true).withMandatory(true).bind(context, bar,
                prop);
        assertEquals(7, myConfigurer.getCounter());

        assertEquals(33, bar.getAge());
        assertTrue(bar.isRider());
        assertTrue(bar.isGoldCustomer());
        assertEquals(123, bar.getWork().getId());
        assertEquals("Acme", bar.getWork().getName());

        assertFalse(prop.isEmpty(), "Should NOT bind all properties");
        assertEquals(3, prop.size());
        assertTrue(prop.containsKey("?silver-Customer"));
        assertTrue(prop.containsKey("?work.addresss"));
        assertTrue(prop.containsKey("?work.addresss.zip"));

        // should not fail as we marked the option as optional
        prop.put("?unknown", "123");
        PropertyBindingSupport.build().withConfigurer(myConfigurer).withIgnoreCase(true).withMandatory(true).bind(context, bar,
                prop);
        prop.remove("?unknown");

        // should fail as its mandatory
        prop.put("unknown", "123");
        try {
            PropertyBindingSupport.build().withConfigurer(myConfigurer).withIgnoreCase(true).withMandatory(true).bind(context,
                    bar, prop);
            fail("Should fail");
        } catch (PropertyBindingException e) {
            assertEquals("unknown", e.getPropertyName());
        }
    }

    @Test
    public void testPropertiesNoReflection() throws Exception {
        BeanIntrospection bi = PluginHelper.getBeanIntrospection(context);
        bi.setExtendedStatistics(true);
        bi.setLoggingLevel(LoggingLevel.WARN);

        Bar bar = new Bar();

        Map<String, Object> prop = new HashMap<>();
        prop.put("age", "33");
        prop.put("{{committer}}", "true");
        prop.put("gold-customer", "true");
        prop.put("work.id", "123");
        prop.put("work.name", "{{companyName}}");

        myConfigurer.reset();
        PropertyBindingSupport.build().withReflection(false).withConfigurer(myConfigurer).withIgnoreCase(true).bind(context,
                bar, prop);
        assertEquals(6, myConfigurer.getCounter());

        assertEquals(33, bar.getAge());
        assertTrue(bar.isRider());
        assertTrue(bar.isGoldCustomer());
        assertEquals(0, bar.getWork().getId());
        assertNull(bar.getWork().getName());

        assertEquals(2, prop.size());
        assertEquals("123", prop.get("work.id"));
        assertEquals("{{companyName}}", prop.get("work.name"));

        // reflection is turned off
        assertEquals(0, bi.getInvokedCounter());
    }

    @Test
    public void testPropertiesDash() throws Exception {
        PropertyBindingSupportTest.Foo foo = new PropertyBindingSupportTest.Foo();

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

    private static class MyConfigurer implements GeneratedPropertyConfigurer, PropertyConfigurerGetter {

        private int counter;

        @Override
        public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            if (ignoreCase) {
                name = name.toLowerCase(Locale.ENGLISH);
            }
            if (target instanceof Bar) {
                Bar bar = (Bar) target;
                if ("age".equals(name)) {
                    bar.withAge(Integer.parseInt(value.toString()));
                    counter++;
                    return true;
                } else if ("rider".equals(name)) {
                    bar.withRider(Boolean.parseBoolean(value.toString()));
                    counter++;
                    return true;
                } else if ("work".equals(name)) {
                    bar.work((Company) value);
                    counter++;
                    return true;
                } else if ("goldCustomer".equals(name) || "goldcustomer".equals(name)) {
                    bar.goldCustomer(Boolean.parseBoolean(value.toString()));
                    counter++;
                    return true;
                }
            }
            return false;
        }

        public int getCounter() {
            return counter;
        }

        public void reset() {
            counter = 0;
        }

        @Override
        public Class<?> getOptionType(String name, boolean ignoreCase) {
            if ("age".equals(name)) {
                return int.class;
            } else if ("rider".equals(name)) {
                return boolean.class;
            } else if ("work".equals(name)) {
                return Company.class;
            } else if ("goldCustomer".equals(name) || "goldcustomer".equals(name)) {
                return boolean.class;
            }
            return null;
        }

        @Override
        public Object getOptionValue(Object target, String name, boolean ignoreCase) {
            if (ignoreCase) {
                name = name.toLowerCase(Locale.ENGLISH);
            }
            if (target instanceof Bar) {
                Bar bar = (Bar) target;
                if ("age".equals(name)) {
                    counter++;
                    return bar.getAge();
                } else if ("rider".equals(name)) {
                    counter++;
                    return bar.isRider();
                } else if ("work".equals(name)) {
                    counter++;
                    return bar.getWork();
                } else if ("goldCustomer".equals(name) || "goldcustomer".equals(name)) {
                    counter++;
                    return bar.isGoldCustomer();
                }
            }
            return null;
        }
    }

}
