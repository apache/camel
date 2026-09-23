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
import org.apache.camel.TypeConverter;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
