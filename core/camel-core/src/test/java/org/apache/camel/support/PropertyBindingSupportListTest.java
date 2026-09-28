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
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.junit.jupiter.api.Test;

import static org.apache.camel.util.CollectionHelper.mapOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
    public void testPropertiesList() {
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
    public void testPropertiesListWithGaps() {
        Foo foo = new Foo();

        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("name", "James");
        prop.put("bar.age", "33");
        prop.put("bar.{{committer}}", "true");
        prop.put("bar.gold-customer", "true");
        prop.put("bar.works[5]", "#bean:company1");
        prop.put("bar.works[9]", "#bean:company2");

        PropertyBindingSupport.build().bind(context, foo, prop);

        assertEquals("James", foo.getName());
        assertEquals(33, foo.getBar().getAge());
        assertTrue(foo.getBar().isRider());
        assertTrue(foo.getBar().isGoldCustomer());
        assertEquals(10, foo.getBar().getWorks().size());
        assertEquals(123, foo.getBar().getWorks().get(5).getId());
        assertEquals("Acme", foo.getBar().getWorks().get(5).getName());
        assertEquals(456, foo.getBar().getWorks().get(9).getId());
        assertEquals("Acme 2", foo.getBar().getWorks().get(9).getName());
    }

    @Test
    public void testPropertiesListNested() {
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
    public void testPropertiesListNestedWithType() {
        Foo foo = new Foo();

        // use CollectionHelper::mapOf to avoid insertion ordered iteration
        PropertyBindingSupport.build().bind(context, foo, mapOf(
                "bar.works[0]", "#class:" + Company.class.getName(),
                "bar.works[0].name", "first",
                "bar.works[1]", "#class:" + Company.class.getName(),
                "bar.works[1].name", "second"));

        assertEquals(2, foo.getBar().getWorks().size());
        assertEquals(0, foo.getBar().getWorks().get(0).getId());
        assertEquals("first", foo.getBar().getWorks().get(0).getName());
        assertEquals(0, foo.getBar().getWorks().get(1).getId());
        assertEquals("second", foo.getBar().getWorks().get(1).getName());
    }

    @Test
    public void testPropertiesListFirst() {
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
    public void testPropertiesListNestedMoreThanTenElements() {
        Foo foo = new Foo();

        // the keys are sorted as strings, so works[10] and works[11] are bound before works[2]
        Map<String, Object> prop = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) {
            prop.put("bar.works[" + i + "].id", String.valueOf(100 + i));
            prop.put("bar.works[" + i + "].name", "Company " + i);
        }

        PropertyBindingSupport.build().bind(context, foo, prop);

        List<Company> works = foo.getBar().getWorks();
        assertEquals(12, works.size());
        for (int i = 0; i < 12; i++) {
            assertEquals(100 + i, works.get(i).getId());
            assertEquals("Company " + i, works.get(i).getName());
        }
    }

    @Test
    public void testPropertiesListNestedWithGapsNoDeclaration() {
        Foo foo = new Foo();

        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("bar.works[1].id", "123");
        prop.put("bar.works[1].name", "Acme");

        PropertyBindingSupport.build().bind(context, foo, prop);

        List<Company> works = foo.getBar().getWorks();
        assertEquals(2, works.size());
        assertNull(works.get(0));
        assertEquals(123, works.get(1).getId());
        assertEquals("Acme", works.get(1).getName());
    }

    @Test
    public void testPropertiesListNestedSparse() {
        Foo foo = new Foo();

        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("bar.works[0].name", "Zero");
        prop.put("bar.works[5].id", "5");
        prop.put("bar.works[5].name", "Five");

        PropertyBindingSupport.build().bind(context, foo, prop);

        List<Company> works = foo.getBar().getWorks();
        assertEquals(6, works.size());
        assertEquals("Zero", works.get(0).getName());
        for (int i = 1; i < 5; i++) {
            assertNull(works.get(i));
        }
        assertEquals(5, works.get(5).getId());
        assertEquals("Five", works.get(5).getName());
    }

    @Test
    public void testPropertiesListNestedFromOne() {
        Foo foo = new Foo();

        // numbered from 1: the element at index 0 is null
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("bar.works[1].name", "One");
        prop.put("bar.works[2].name", "Two");

        PropertyBindingSupport.build().bind(context, foo, prop);

        List<Company> works = foo.getBar().getWorks();
        assertEquals(3, works.size());
        assertNull(works.get(0));
        assertEquals("One", works.get(1).getName());
        assertEquals("Two", works.get(2).getName());
    }

    @Test
    public void testPropertiesListNestedWithGapsViaConfigurer() {
        context.getRegistry().bind(Company.class.getName(), new CompanyConfigurer());

        Cluster cluster = new Cluster();

        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("servers[1].id", "1");
        prop.put("servers[1].name", "One");
        prop.put("servers[3].id", "3");
        prop.put("servers[3].name", "Three");

        // no reflection so the configurers must do all the work
        PropertyBindingSupport.build().withConfigurer(new ClusterConfigurer()).withReflection(false)
                .bind(context, cluster, prop);

        List<Company> servers = cluster.getServers();
        assertEquals(4, servers.size());
        assertNull(servers.get(0));
        assertEquals(1, servers.get(1).getId());
        assertEquals("One", servers.get(1).getName());
        assertNull(servers.get(2));
        assertEquals(3, servers.get(3).getId());
        assertEquals("Three", servers.get(3).getName());
    }

    @Test
    public void testPropertiesListLast() {
        Foo foo = new Foo();

        PropertyBindingSupport.build().bind(context, foo, mapOf(
                "bar.works[0]", "#bean:company1",
                "bar.works[1]", "#class:" + Company.class.getName(),
                "bar.names[0]", "a",
                "bar.names[1]", "b"));
        assertEquals(2, foo.getBar().getWorks().size());
        assertEquals(2, foo.getBar().getNames().size());

        // last refers to the last element of the list
        PropertyBindingSupport.build().bind(context, foo, mapOf(
                "bar.works[last].id", "789",
                "bar.works[last].name", "Last",
                "bar.names[last]", "z"));

        List<Company> works = foo.getBar().getWorks();
        assertEquals(2, works.size());
        assertEquals(123, works.get(0).getId());
        assertEquals("Acme", works.get(0).getName());
        assertEquals(789, works.get(1).getId());
        assertEquals("Last", works.get(1).getName());
        assertEquals(List.of("a", "z"), foo.getBar().getNames());
    }

    @Test
    public void testPropertiesListLastEmpty() {
        Foo foo = new Foo();

        // last on an empty list creates the first element
        PropertyBindingSupport.build().bind(context, foo, mapOf(
                "bar.works[last].id", "789",
                "bar.works[last].name", "Last",
                "bar.names[last]", "z"));

        List<Company> works = foo.getBar().getWorks();
        assertEquals(1, works.size());
        assertEquals(789, works.get(0).getId());
        assertEquals("Last", works.get(0).getName());
        assertEquals(List.of("z"), foo.getBar().getNames());
    }

    @Test
    public void testPropertiesNotList() {
        Foo foo = new Foo();

        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("name", "James");
        prop.put("bar.age", "33");
        prop.put("bar.gold-customer[]", "true");

        try {
            PropertyBindingSupport.build().bind(context, foo, prop);
            fail("Should have thrown exception");
        } catch (PropertyBindingException e) {
            assertEquals("gold-customer[]", e.getPropertyName());
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertTrue(iae.getMessage().startsWith(
                    "Cannot set property: gold-customer[] as either a Map/List/array because target bean is not a Map, List or array type"));
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
        private List<String> names;
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

        public List<String> getNames() {
            return names;
        }

        public void setNames(List<String> names) {
            this.names = names;
        }

        public boolean isGoldCustomer() {
            return goldCustomer;
        }

        public void setGoldCustomer(boolean goldCustomer) {
            this.goldCustomer = goldCustomer;
        }
    }

    public static class Cluster {
        private List<Company> servers;

        public List<Company> getServers() {
            return servers;
        }

        public void setServers(List<Company> servers) {
            this.servers = servers;
        }
    }

    private static class ClusterConfigurer implements GeneratedPropertyConfigurer, PropertyConfigurerGetter {

        @Override
        @SuppressWarnings("unchecked")
        public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            if ("servers".equals(name)) {
                ((Cluster) target).setServers((List<Company>) value);
                return true;
            }
            return false;
        }

        @Override
        public Class<?> getOptionType(String name, boolean ignoreCase) {
            return "servers".equals(name) ? List.class : null;
        }

        @Override
        public Object getOptionValue(Object target, String name, boolean ignoreCase) {
            return "servers".equals(name) ? ((Cluster) target).getServers() : null;
        }

        @Override
        public Object getCollectionValueType(Object target, String name, boolean ignoreCase) {
            return "servers".equals(name) ? Company.class : null;
        }
    }

    private static class CompanyConfigurer implements GeneratedPropertyConfigurer, PropertyConfigurerGetter {

        @Override
        public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            Company company = (Company) target;
            if ("id".equals(name)) {
                company.setId(Integer.parseInt(value.toString()));
                return true;
            } else if ("name".equals(name)) {
                company.setName(value.toString());
                return true;
            }
            return false;
        }

        @Override
        public Class<?> getOptionType(String name, boolean ignoreCase) {
            if ("id".equals(name)) {
                return int.class;
            } else if ("name".equals(name)) {
                return String.class;
            }
            return null;
        }

        @Override
        public Object getOptionValue(Object target, String name, boolean ignoreCase) {
            Company company = (Company) target;
            if ("id".equals(name)) {
                return company.getId();
            } else if ("name".equals(name)) {
                return company.getName();
            }
            return null;
        }
    }

}
