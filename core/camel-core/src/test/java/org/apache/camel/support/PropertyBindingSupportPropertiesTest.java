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

import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for PropertyBindingSupport
 */
public class PropertyBindingSupportPropertiesTest extends ContextTestSupport {
    @Test
    public void testProperties() {
        Bar bar = new Bar();

        PropertyBindingSupport.build()
                .withCamelContext(context)
                .withReflection(true)
                .withTarget(bar)
                .withProperty("works[acme]", "company1")
                .withProperty("works[burger]", "company2")
                .bind();

        assertEquals("company1", bar.getWorks().getProperty("acme"));
        assertEquals("company2", bar.getWorks().getProperty("burger"));
    }

    @Test
    public void testPropertiesWithConfigurer() {
        Bar bar = new Bar();

        PropertyBindingSupport.build()
                .withCamelContext(context)
                .withReflection(false)
                .withConfigurer(new BarConfigurer())
                .withTarget(bar)
                .withProperty("works[acme]", "company1")
                .withProperty("works[burger]", "company2")
                .bind();

        assertEquals("company1", bar.getWorks().getProperty("acme"));
        assertEquals("company2", bar.getWorks().getProperty("burger"));
    }

    @Test
    public void testPropertiesMap() {
        BarWithMap bar = new BarWithMap();

        PropertyBindingSupport.build()
                .withCamelContext(context)
                .withReflection(true)
                .withTarget(bar)
                .withProperty("works[acme].name", "company1")
                .withProperty("works[burger].name", "company2")
                .bind();

        assertEquals("company1", bar.getWorks().get("acme").getProperty("name"));
        assertEquals("company2", bar.getWorks().get("burger").getProperty("name"));
    }

    @Test
    public void testPropertiesMapWithConfigurer() {
        BarWithMap bar = new BarWithMap();

        PropertyBindingSupport.build()
                .withCamelContext(context)
                .withReflection(false)
                .withConfigurer(new BarWithMapConfigurer())
                .withTarget(bar)
                .withProperty("works[acme].name", "company1")
                .withProperty("works[burger].name", "company2")
                .bind();

        assertEquals("company1", bar.getWorks().get("acme").getProperty("name"));
        assertEquals("company2", bar.getWorks().get("burger").getProperty("name"));
    }

    public static class Bar {
        private Properties works;

        public Properties getWorks() {
            return works;
        }

        public void setWorks(Properties works) {
            this.works = works;
        }
    }

    public static class BarWithMap {
        private Map<String, Properties> works;

        public Map<String, Properties> getWorks() {
            return works;
        }

        public void setWorks(Map<String, Properties> works) {
            this.works = works;
        }
    }

    private static class BarConfigurer implements PropertyConfigurer, PropertyConfigurerGetter {
        @Override
        public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            if (ignoreCase) {
                name = name.toLowerCase(Locale.ENGLISH);
            }
            if (target instanceof PropertyBindingSupportPropertiesTest.Bar) {
                PropertyBindingSupportPropertiesTest.Bar bar = (PropertyBindingSupportPropertiesTest.Bar) target;
                if ("works".equals(name)) {
                    bar.setWorks((Properties) value);
                    return true;
                }
            }
            return false;
        }

        @Override
        public Class<?> getOptionType(String name, boolean ignoreCase) {
            if ("works".equals(name)) {
                return Properties.class;
            }

            return null;
        }

        @Override
        public Object getOptionValue(Object target, String name, boolean ignoreCase) {
            if (ignoreCase) {
                name = name.toLowerCase(Locale.ENGLISH);
            }
            if (target instanceof PropertyBindingSupportPropertiesTest.Bar) {
                PropertyBindingSupportPropertiesTest.Bar bar = (PropertyBindingSupportPropertiesTest.Bar) target;
                if ("works".equals(name)) {
                    return bar.getWorks();
                }
            }
            return null;
        }
    }

    private static class BarWithMapConfigurer implements PropertyConfigurer, PropertyConfigurerGetter {
        @Override
        public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            if (ignoreCase) {
                name = name.toLowerCase(Locale.ENGLISH);
            }
            if (target instanceof PropertyBindingSupportPropertiesTest.BarWithMap) {
                PropertyBindingSupportPropertiesTest.BarWithMap bar = (PropertyBindingSupportPropertiesTest.BarWithMap) target;
                if ("works".equals(name)) {
                    bar.setWorks((Map) value);
                    return true;
                }
            }
            return false;
        }

        @Override
        public Class<?> getOptionType(String name, boolean ignoreCase) {
            if ("works".equals(name)) {
                return Map.class;
            }

            return null;
        }

        @Override
        public Object getOptionValue(Object target, String name, boolean ignoreCase) {
            if (ignoreCase) {
                name = name.toLowerCase(Locale.ENGLISH);
            }
            if (target instanceof PropertyBindingSupportPropertiesTest.BarWithMap) {
                PropertyBindingSupportPropertiesTest.BarWithMap bar = (PropertyBindingSupportPropertiesTest.BarWithMap) target;
                if ("works".equals(name)) {
                    return bar.getWorks();
                }
            }
            return null;
        }

        @Override
        public Object getCollectionValueType(Object target, String name, boolean ignoreCase) {
            if ("works".equals(name)) {
                return Properties.class;
            }

            return null;
        }
    }

}
