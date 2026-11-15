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
package org.apache.camel.impl.converter;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Converter;
import org.apache.camel.TypeConverters;
import org.apache.camel.spi.TypeConverterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TypeConvertersTest extends ContextTestSupport {

    private final MyConverters converters = new MyConverters();

    @Test
    public void testAddInstanceWithoutDefaultConstructor() {
        context.getTypeConverterRegistry().addTypeConverters(new PrefixConverters("x-"));

        Target target = context.getTypeConverter().convertTo(Target.class, new Source("a"));
        assertNotNull(target);
        assertEquals("x-a", target.value());
    }

    @Test
    public void testAddConfiguredInstance() {
        ConfigurableConverters configurable = new ConfigurableConverters();
        configurable.setPrefix("x-");

        context.getTypeConverterRegistry().addTypeConverters(configurable);

        Target target = context.getTypeConverter().convertTo(Target.class, new Source("a"));
        assertNotNull(target);
        assertEquals("x-a", target.value());

        FallbackTarget fallback = context.getTypeConverter().convertTo(FallbackTarget.class, new Source("b"));
        assertNotNull(fallback);
        assertEquals("x-b", fallback.value());

        assertSame(context, configurable.getCamelContext());
    }

    @Test
    public void testAdd() {
        int before = context.getTypeConverterRegistry().size();

        context.getTypeConverterRegistry().addTypeConverters(converters);

        int after = context.getTypeConverterRegistry().size();
        int delta = after - before;
        assertEquals(2, delta, "There should be 2 more type converters");

        Country country = context.getTypeConverter().convertTo(Country.class, "en");
        assertNotNull(country);
        assertEquals("England", country.getName());

        String iso = context.getTypeConverter().convertTo(String.class, country);
        assertNotNull(iso);
        assertEquals("en", iso);
    }

    @Test
    public void testStringToPrimitiveTypes() throws Exception {
        assertEquals(Short.parseShort("1"), context.getTypeConverter().mandatoryConvertTo(short.class, "1"));
        assertEquals(Integer.parseInt("1"), context.getTypeConverter().mandatoryConvertTo(int.class, "1"));
        assertEquals(Long.parseLong("1"), context.getTypeConverter().mandatoryConvertTo(long.class, "1"));

        assertEquals(Float.parseFloat("1.1"), context.getTypeConverter().mandatoryConvertTo(float.class, "1.1"));
        assertEquals(Double.parseDouble("1.1"), context.getTypeConverter().mandatoryConvertTo(double.class, "1.1"));

        assertEquals('a', context.getTypeConverter().mandatoryConvertTo(char.class, "a"));
        assertEquals(Boolean.parseBoolean("true"), context.getTypeConverter().mandatoryConvertTo(boolean.class, "true"));
    }

    public record Source(String value) {
    }

    public record Target(String value) {
    }

    public record FallbackTarget(String value) {
    }

    public static class PrefixConverters implements TypeConverters {
        private final String prefix;

        public PrefixConverters(String prefix) {
            this.prefix = prefix;
        }

        @Converter
        public Target toTarget(Source source) {
            return new Target(prefix + source.value());
        }
    }

    public static class ConfigurableConverters implements TypeConverters, CamelContextAware {
        private CamelContext camelContext;
        private String prefix = "default-";

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        @Converter
        public Target toTarget(Source source) {
            return new Target(prefix + source.value());
        }

        @Converter(fallback = true)
        public Object toFallbackTarget(Class<?> type, Object value, TypeConverterRegistry registry) {
            if (type == FallbackTarget.class && value instanceof Source source) {
                return new FallbackTarget(prefix + source.value());
            }
            return null;
        }
    }
}
