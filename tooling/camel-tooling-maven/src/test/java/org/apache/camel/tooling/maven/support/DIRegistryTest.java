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
package org.apache.camel.tooling.maven.support;

import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class DIRegistryTest {

    public static final Logger LOG = LoggerFactory.getLogger(DIRegistryTest.class);

    @Test
    public void justOneBean() throws IOException {
        try (DIRegistry registry = new DIRegistry()) {
            registry.bind(MySimpleBean.class);

            MySimpleBean bean = registry.lookupByClass(MySimpleBean.class);
            assertEquals("mySimpleBean", bean.value);
            assertSame(bean, registry.lookupByClass(MySimpleBean.class));
        }
    }

    @Test
    public void beanWithAlias() throws IOException {
        try (DIRegistry registry = new DIRegistry()) {
            registry.bind(MySimpleBean.class);
            registry.alias(FilenameFilter.class, MySimpleBean.class);

            MySimpleBean bean = registry.lookupByClass(MySimpleBean.class);
            assertEquals("mySimpleBean", bean.value);
            assertSame(bean, registry.lookupByClass(MySimpleBean.class));

            MySimpleBean bean2 = (MySimpleBean) registry.lookupByName(FilenameFilter.class.getName());
            assertEquals("mySimpleBean", bean2.value);
        }
    }

    @Test
    public void namedBean() throws IOException {
        try (DIRegistry registry = new DIRegistry()) {
            registry.bind(MyNamedBean.class);

            MyNamedBean bean1 = registry.lookupByClass(MyNamedBean.class);
            MyNamedBean bean2 = (MyNamedBean) registry.lookupByName("bean1");
            assertEquals("myNamedBean", bean1.value);
            assertSame(bean1, bean2);
        }
    }

    @Test
    public void dependantBeans() throws IOException {
        try (DIRegistry registry = new DIRegistry()) {
            registry.bind(MySimpleBean.class);
            registry.bind(MyNamedBean.class);
            registry.bind(MyComplexishBean.class);

            MyComplexishBean bean = registry.lookupByClass(MyComplexishBean.class);
            assertSame(bean, registry.lookupByClass(MyComplexishBean.class),
                    "supplier should be called only once");
            assertSame(bean.getSimpleBean(), registry.lookupByClass(MySimpleBean.class));
            assertSame(bean.getNamedBean(), registry.lookupByClass(MyNamedBean.class));
        }
    }

    @Test
    public void brokenBeanFailing() throws IOException {
        try (DIRegistry registry = new DIRegistry()) {
            registry.bind(MyBrokenBean.class);

            try {
                registry.lookupByClass(MyBrokenBean.class);
                fail("Shouldn't be able to find a bean with unsatisfied constructor");
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Test
    public void luckyBrokenBean() throws IOException {
        try (DIRegistry registry = new DIRegistry()) {
            registry.bind(MyBrokenBean.class);
            registry.bind("hello", String.class, "Hello");

            MyBrokenBean bean = registry.lookupByClass(MyBrokenBean.class);
            assertEquals("Hello", bean.getValue());
        }
    }

    @Test
    public void cyclicBeans() throws IOException {
        try (DIRegistry registry = new DIRegistry()) {
            registry.bind(MyBean1.class);
            registry.bind(MyBean2.class);

            try {
                registry.lookupByClass(MyBean1.class);
                fail("Shouldn't be able to find a bean with cyclic dependencies");
            } catch (IllegalStateException ignored) {
            }
        }
    }

    @Test
    public void genericBean() throws IOException {
        try (DIRegistry registry = new DIRegistry()) {
            registry.bind(MyGenericBean.class);
            registry.bind("i1", Integer.class, 42);
            registry.bind("s1", String.class, "S1");
            registry.bind("s2", String.class, "S2");

            MyGenericBean bean = registry.lookupByClass(MyGenericBean.class);
            assertEquals(42, bean.integer);
            assertEquals(2, bean.strings.size());
            assertTrue(bean.strings.contains("S1"));
            assertTrue(bean.strings.contains("S2"));
            assertEquals(2, bean.map.size());
            assertEquals("S1", bean.map.get("s1"));
            assertEquals("S2", bean.map.get("s2"));
            assertEquals(1, bean.ints.length);
            assertEquals(42, bean.ints[0]);
        }
    }

    public static class MySimpleBean {
        public String value = "mySimpleBean";
    }

    @Named("bean1")
    public static class MyNamedBean {
        public String value = "myNamedBean";
    }

    public static class MyComplexishBean {
        private final MySimpleBean simpleBean;
        private final MyNamedBean namedBean;

        @Inject
        public MyComplexishBean(MySimpleBean sb, MyNamedBean nb) {
            simpleBean = sb;
            namedBean = nb;
        }

        public MySimpleBean getSimpleBean() {
            return simpleBean;
        }

        public MyNamedBean getNamedBean() {
            return namedBean;
        }
    }

    public static class MyGenericBean {
        public final Integer integer;
        public final Set<String> strings;
        public final Map<String, String> map;
        public final Integer[] ints;

        @Inject
        public MyGenericBean(Integer integer, Set<String> strings, Map<String, String> map, Integer[] ints) {
            this.integer = integer;
            this.strings = strings;
            this.map = map;
            this.ints = ints;
        }
    }

    public static class MyBean1 {
        @Inject
        public MyBean1(MyBean2 bean) {
        }
    }

    public static class MyBean2 {
        @Inject
        public MyBean2(MyBean1 bean) {
        }
    }

    public static class MyBrokenBean {
        private final String value;

        @Inject
        public MyBrokenBean(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

}
