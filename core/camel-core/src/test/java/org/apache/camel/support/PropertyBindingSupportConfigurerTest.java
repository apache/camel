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
import org.apache.camel.PropertyBindingException;
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.junit.Test;

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
        Bar bar = new Bar();

        Map<String, Object> prop = new HashMap<>();
        prop.put("age", "33");
        prop.put("{{committer}}", "true");
        prop.put("gold-customer", "true");
        prop.put("work.id", "123");
        prop.put("work.name", "{{companyName}}");

        myConfigurer.reset();
        PropertyBindingSupport.build().withConfigurer(myConfigurer).withIgnoreCase(true).bind(context, bar, prop);
        assertEquals(3, myConfigurer.getCounter());

        assertEquals(33, bar.getAge());
        assertTrue(bar.isRider());
        assertTrue(bar.isGoldCustomer());
        assertEquals(123, bar.getWork().getId());
        assertEquals("Acme", bar.getWork().getName());

        assertTrue("Should bind all properties", prop.isEmpty());
    }

    @Test
    public void testPropertiesOptionallKey() throws Exception {
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
        assertEquals(3, myConfigurer.getCounter());

        assertEquals(33, bar.getAge());
        assertTrue(bar.isRider());
        assertTrue(bar.isGoldCustomer());
        assertEquals(123, bar.getWork().getId());
        assertEquals("Acme", bar.getWork().getName());

        assertFalse("Should NOT bind all properties", prop.isEmpty());
        assertEquals(3, prop.size());
        assertTrue(prop.containsKey("?silver-Customer"));
        assertTrue(prop.containsKey("?work.addresss"));
        assertTrue(prop.containsKey("?work.addresss.zip"));
    }

    @Test
    public void testPropertiesOptionallKeyMandatory() throws Exception {
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
        PropertyBindingSupport.build().withConfigurer(myConfigurer).withIgnoreCase(true).withMandatory(true).bind(context, bar, prop);
        assertEquals(3, myConfigurer.getCounter());

        assertEquals(33, bar.getAge());
        assertTrue(bar.isRider());
        assertTrue(bar.isGoldCustomer());
        assertEquals(123, bar.getWork().getId());
        assertEquals("Acme", bar.getWork().getName());

        assertFalse("Should NOT bind all properties", prop.isEmpty());
        assertEquals(3, prop.size());
        assertTrue(prop.containsKey("?silver-Customer"));
        assertTrue(prop.containsKey("?work.addresss"));
        assertTrue(prop.containsKey("?work.addresss.zip"));

        // should not fail as we marked the option as optional
        prop.put("?unknown", "123");
        PropertyBindingSupport.build().withConfigurer(myConfigurer).withIgnoreCase(true).withMandatory(true).bind(context, bar, prop);
        prop.remove("?unknown");

        // should fail as its mandatory
        prop.put("unknown", "123");
        try {
            PropertyBindingSupport.build().withConfigurer(myConfigurer).withIgnoreCase(true).withMandatory(true).bind(context, bar, prop);
            fail("Should fail");
        } catch (PropertyBindingException e) {
            assertEquals("unknown", e.getPropertyName());
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

    private class MyConfigurer implements GeneratedPropertyConfigurer {

        private int counter;

        @Override
        public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            name = name.toLowerCase(Locale.ENGLISH);
            name = name.replaceAll("-", "");
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
                } else if ("goldcustomer".equals(name)) {
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
    }

}
