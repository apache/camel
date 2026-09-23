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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.support.TypeConverterSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CoreTypeConverterRegistryTest extends ContextTestSupport {

    @Test
    public void testConvertWrapperToOtherPrimitive() throws Exception {
        TypeConverter tc = context.getTypeConverter();

        assertInstanceOf(Integer.class, tc.convertTo(int.class, 5L));
        assertInstanceOf(Long.class, tc.convertTo(long.class, 5));
        assertInstanceOf(Double.class, tc.convertTo(double.class, 5));
        assertInstanceOf(Integer.class, tc.mandatoryConvertTo(int.class, 5L));
        assertInstanceOf(Long.class, tc.mandatoryConvertTo(long.class, 7));
        assertInstanceOf(Double.class, tc.tryConvertTo(double.class, 7));
        assertInstanceOf(Integer.class, tc.tryConvertTo(int.class, 7L));

        int i = tc.convertTo(int.class, 5L);
        assertEquals(5, i);
        long l = tc.convertTo(long.class, 5);
        assertEquals(5L, l);

        // same wrapper type is returned as-is
        assertInstanceOf(Integer.class, tc.convertTo(int.class, 5));
        assertInstanceOf(Long.class, tc.convertTo(long.class, 5L));
    }

    @Test
    public void testTryConvertToPrimitiveBoolean() {
        TypeConverter tc = context.getTypeConverter();

        assertNull(tc.tryConvertTo(boolean.class, "abc"));
        assertNull(tc.tryConvertTo(boolean.class, new Object()));
        assertEquals(Boolean.TRUE, tc.tryConvertTo(boolean.class, "true"));
    }

    @Test
    public void testBeanWithIntParameterAndLongBody() {
        assertEquals("int:5", template.requestBody("direct:int", 5L));
        assertEquals("long:5", template.requestBody("direct:long", 5));
    }

    @Test
    public void testFallbackStillTriedAfterMiss() {
        context.getTypeConverterRegistry().addFallbackTypeConverter(new FooFallback(), false);
        TypeConverter tc = context.getTypeConverter();

        // the fallback cannot convert this value, which is recorded as a miss
        assertNull(tc.convertTo(Foo.class, "bar"));
        // but it can convert this value of the same type
        assertEquals("b", tc.convertTo(Foo.class, "foo:b").value);
        assertNull(tc.convertTo(Foo.class, "baz"));
        assertEquals("c", tc.tryConvertTo(Foo.class, "foo:c").value);
    }

    @Test
    public void testFallbackAddedAfterMiss() {
        TypeConverter tc = context.getTypeConverter();

        assertNull(tc.convertTo(Foo.class, "foo:x"));
        context.getTypeConverterRegistry().addFallbackTypeConverter(new FooFallback(), false);
        assertEquals("x", tc.convertTo(Foo.class, "foo:x").value);
    }

    @Test
    public void testConverterAddedAfterMiss() {
        TypeConverter tc = context.getTypeConverter();

        assertNull(tc.convertTo(Foo.class, new Sub()));
        context.getTypeConverterRegistry().addTypeConverter(Foo.class, Base.class, new TypeConverterSupport() {
            @Override
            public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
                return type.cast(new Foo("base"));
            }
        });
        // converter for the super class is used for the sub class that previously missed
        assertEquals("base", tc.convertTo(Foo.class, new Sub()).value);
    }

    @Test
    public void testMissOnSuperClassDoesNotAffectSubClass() {
        context.getTypeConverterRegistry().addFallbackTypeConverter(new TypeConverterSupport() {
            @Override
            public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
                return value instanceof Sub && type == Foo.class ? type.cast(new Foo("sub")) : null;
            }
        }, false);
        TypeConverter tc = context.getTypeConverter();

        assertNull(tc.convertTo(Foo.class, new Base()));
        assertEquals("sub", tc.convertTo(Foo.class, new Sub()).value);
    }

    @Test
    public void testLookupDoesNotReturnMiss() {
        TypeConverterRegistry registry = context.getTypeConverterRegistry();

        assertNull(context.getTypeConverter().convertTo(Foo.class, new Base()));
        assertNull(registry.lookup(Foo.class, Base.class));
        assertNull(registry.lookup(Foo.class, Sub.class));
        assertTrue(registry.lookup(Foo.class).isEmpty());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:int").bean(MyNumberBean.class, "intArg");
                from("direct:long").bean(MyNumberBean.class, "longArg");
            }
        };
    }

    public static class MyNumberBean {
        public String intArg(int x) {
            return "int:" + x;
        }

        public String longArg(long x) {
            return "long:" + x;
        }
    }

    public static class Foo {
        private final String value;

        public Foo(String value) {
            this.value = value;
        }
    }

    public static class Base {
    }

    public static class Sub extends Base {
    }

    /**
     * Fallback that depends on the value: it can only convert strings that start with foo:
     */
    private static class FooFallback extends TypeConverterSupport {
        @Override
        public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
            if (type == Foo.class && value instanceof String s && s.startsWith("foo:")) {
                return type.cast(new Foo(s.substring(4)));
            }
            return null;
        }
    }
}
