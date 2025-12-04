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

package org.apache.camel.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.TypeConverter;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.ObjectHelper;
import org.junit.jupiter.api.Test;

public class TypeCoerceCompareTest extends ContextTestSupport {

    @Test
    public void testCompareStringString() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, "40", "7") > 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, "40", "7.5") > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, "40", "40"));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, "7", "40") < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, "7.5", "40") < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, "7.5", "7.5"));
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, "7.5", "7.5"));
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, "7.0", "7"));
    }

    @Test
    public void testCompareStringInteger() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, "40", 7) > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, "40", 40));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, "7", 40) < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, "7.5", 40) < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, "7.0", 7));
    }

    @Test
    public void testCompareStringLong() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, "40", 7L) > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, "40", 40L));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, "7", 40L) < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, "7.5", 40L) < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, "7.0", 7L));
    }

    @Test
    public void testCompareStringDouble() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, "40", 7d) > 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, "40", 7.5d) > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, "40", 40d));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, "7", 40d) < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, "7.5", 40d) < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, "7.5", 7.5d));
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, "7.5", 7.5d));
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, "7.0", 7d));
    }

    @Test
    public void testCompareStringFloat() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, "40", 7f) > 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, "40", 7.5f) > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, "40", 40f));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, "7", 40f) < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, "7.5", 40f) < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, "7.5", 7.5f));
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, "7.5", 7.5f));
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, "7.0", 7f));
    }

    @Test
    public void testCompareIntegerString() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 40, "7") > 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 40, "7.5") > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 40, "40"));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 7, "40") < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 7, "7.5") < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7, "7"));
    }

    @Test
    public void testCompareLongString() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 40L, "7") > 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 40L, "7.5") > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 40L, "40"));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 7L, "40") < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 7L, "7.5") < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7L, "7"));
    }

    @Test
    public void testCompareDoubleString() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 40d, "7") > 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 40.5d, "7.5") > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 40d, "40"));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 7d, "40.5") < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 7.5d, "40") < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7.5d, "7.5"));
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7.5d, "7.5"));
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7.0d, "7"));
    }

    @Test
    public void testCompareFloatString() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 40f, "7") > 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 40.5f, "7.5") > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 40f, "40"));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 7f, "40.5") < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 7.5f, "40") < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7.5f, "7.5"));
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7.5f, "7.5"));
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7.0f, "7"));
    }

    @Test
    public void testCompareIntegerInteger() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 40, 7) > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 40, 40));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 7, 40) < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 8, 40) < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7, 7));
    }

    @Test
    public void testCompareLongLong() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 40L, 7L) > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 40L, 40L));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 7L, 40L) < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 8L, 40L) < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7L, 7L));
    }

    @Test
    public void testCompareIntegerLong() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 40, 7L) > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 40, 40L));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 7, 40L) < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 8, 40L) < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7, 7L));
    }

    @Test
    public void testCompareLongInteger() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 40L, 7) > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 40L, 40));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 7L, 40) < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 8L, 40) < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7L, 7));
    }

    @Test
    public void testCompareDoubleInteger() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 40.5d, 7) > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 40d, 40));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 7.5d, 40) < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 8d, 40) < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7d, 7));
    }

    @Test
    public void testCompareIntegerDouble() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 40, 7.5d) > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 40, 40.0d));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 7, 40d) < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 8, 40d) < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7, 7d));
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7, 7.0d));
    }

    @Test
    public void testCompareDoubleLong() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 40.5d, 7L) > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 40d, 40L));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 7.5d, 40L) < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 8d, 40L) < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7d, 7L));
    }

    @Test
    public void testCompareLongDouble() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 40L, 7.5d) > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 40L, 40d));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 7L, 40.5d) < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 8L, 40d) < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7L, 7d));
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7L, 7.0d));
    }

    @Test
    public void testCompareFloatInteger() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 40.5f, 7) > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 40f, 40));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 7.5f, 40) < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 8f, 40) < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7f, 7));
    }

    @Test
    public void testCompareIntegerFloat() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 40, 7.5f) > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 40, 40f));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 7, 40.0f) < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 8, 40f) < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7, 7.0f));
    }

    @Test
    public void testCompareFloatLong() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 40.5f, 7L) > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 40f, 40L));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 7.5f, 40L) < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 8f, 40L) < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7f, 7L));
    }

    @Test
    public void testCompareLongFloat() {
        TypeConverter tc = context.getTypeConverter();
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 40L, 7.5f) > 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 40L, 40.0f));
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 7L, 40f) < 0);
        assertTrue(ObjectHelper.typeCoerceCompare(tc, 8L, 40f) < 0);
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7L, 7f));
        assertEquals(0, ObjectHelper.typeCoerceCompare(tc, 7L, 7.0f));
    }

    @Test
    public void testPredicate() throws Exception {
        getMockEndpoint("mock:match").expectedBodiesReceived("40", "8", "7.5", 41f, 8f, 7.5f);
        getMockEndpoint("mock:other").expectedBodiesReceived("6", "1", 5f, 2f);

        // string
        template.sendBody("direct:start", "40");
        template.sendBody("direct:start", "8");
        template.sendBody("direct:start", "7.5");
        template.sendBody("direct:start", "6");
        template.sendBody("direct:start", "1");

        // float
        template.sendBody("direct:start", 41f);
        template.sendBody("direct:start", 8f);
        template.sendBody("direct:start", 7.5f);
        template.sendBody("direct:start", 5f);
        template.sendBody("direct:start", 2f);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPredicate2() throws Exception {
        getMockEndpoint("mock:match2").expectedBodiesReceived("40", "8", "7.0", 41f, 8f, 7.0f);
        getMockEndpoint("mock:other2").expectedBodiesReceived("6", "1", 5f, 2f);

        // string
        template.sendBody("direct:start2", "40");
        template.sendBody("direct:start2", "8");
        template.sendBody("direct:start2", "7.0");
        template.sendBody("direct:start2", "6");
        template.sendBody("direct:start2", "1");

        // float
        template.sendBody("direct:start2", 41f);
        template.sendBody("direct:start2", 8f);
        template.sendBody("direct:start2", 7.0f);
        template.sendBody("direct:start2", 5f);
        template.sendBody("direct:start2", 2f);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .setProperty("left", simple("${body}"))
                        .setProperty("right", simple("7.5"))
                        .choice()
                        .when(simple("${exchangeProperty.left} >= ${exchangeProperty.right}"))
                        .to("mock:match")
                        .otherwise()
                        .to("mock:other")
                        .end();

                from("direct:start2")
                        .setProperty("left", simple("${body}"))
                        .setProperty("right", simple("7"))
                        .choice()
                        .when(simple("${exchangeProperty.left} >= ${exchangeProperty.right}"))
                        .to("mock:match2")
                        .otherwise()
                        .to("mock:other2")
                        .end();
            }
        };
    }
}
