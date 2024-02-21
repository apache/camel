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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.TypeConversionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

public class EnumConverterTest extends ContextTestSupport {

    @Test
    public void testMandatoryConvertEnum() throws Exception {
        LoggingLevel level = context.getTypeConverter().mandatoryConvertTo(LoggingLevel.class, "DEBUG");
        assertEquals(LoggingLevel.DEBUG, level);
    }

    @Test
    public void testMandatoryConvertWithExchangeEnum() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        LoggingLevel level = context.getTypeConverter().mandatoryConvertTo(LoggingLevel.class, exchange, "WARN");
        assertEquals(LoggingLevel.WARN, level);
    }

    @Test
    public void testCaseInsensitive() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        LoggingLevel level = context.getTypeConverter().mandatoryConvertTo(LoggingLevel.class, exchange, "Warn");
        assertEquals(LoggingLevel.WARN, level);

        level = context.getTypeConverter().mandatoryConvertTo(LoggingLevel.class, exchange, "warn");
        assertEquals(LoggingLevel.WARN, level);

        level = context.getTypeConverter().mandatoryConvertTo(LoggingLevel.class, exchange, "wARn");
        assertEquals(LoggingLevel.WARN, level);

        level = context.getTypeConverter().mandatoryConvertTo(LoggingLevel.class, exchange, "inFO");
        assertEquals(LoggingLevel.INFO, level);
    }

    @Test
    public void testMandatoryConvertFailed() throws Exception {
        try {
            LoggingLevel level = context.getTypeConverter().convertTo(LoggingLevel.class, "XXX");
            fail("Should have thrown an exception");
        } catch (TypeConversionException e) {
            // expected
        }

        try {
            context.getTypeConverter().mandatoryConvertTo(LoggingLevel.class, "XXX");
            fail("Should have thrown an exception");
        } catch (TypeConversionException e) {
            // expected
        }
    }

    @Test
    public void testCamelCash() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        MyEnum level = context.getTypeConverter().mandatoryConvertTo(MyEnum.class, exchange, "GET_USERS");
        assertEquals(MyEnum.GET_USERS, level);

        level = context.getTypeConverter().mandatoryConvertTo(MyEnum.class, exchange, "getUsers");
        assertEquals(MyEnum.GET_USERS, level);

        level = context.getTypeConverter().mandatoryConvertTo(MyEnum.class, exchange, "getUsersByTopic");
        assertEquals(MyEnum.GET_USERS_BY_TOPIC, level);

        level = context.getTypeConverter().mandatoryConvertTo(MyEnum.class, exchange, "GetUsersByTopic");
        assertEquals(MyEnum.GET_USERS_BY_TOPIC, level);

        level = context.getTypeConverter().mandatoryConvertTo(MyEnum.class, exchange, "get-users-by-topic");
        assertEquals(MyEnum.GET_USERS_BY_TOPIC, level);

        level = context.getTypeConverter().mandatoryConvertTo(MyEnum.class, exchange, "Get-Users-By-Topic");
        assertEquals(MyEnum.GET_USERS_BY_TOPIC, level);
    }

    @Test
    public void testConvertEnumWithToStringOverride() throws Exception {
        Exchange exchange = new DefaultExchange(context);

        MyTypeEnum type = context.getTypeConverter().mandatoryConvertTo(MyTypeEnum.class, exchange, "TYPE1");
        assertSame(MyTypeEnum.TYPE1, type);

        type = context.getTypeConverter().mandatoryConvertTo(MyTypeEnum.class, exchange, "TYPE2");
        assertSame(MyTypeEnum.TYPE2, type);

        type = context.getTypeConverter().mandatoryConvertTo(MyTypeEnum.class, exchange, "TYPE3");
        assertSame(MyTypeEnum.TYPE3, type);
    }

    @Test
    public void testEnumWithToStringOverrideRouteSimple() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .choice()
                        .when(simple("${header.type} == ${type:org.apache.camel.converter.MyTypeEnum.TYPE1}")).to("mock:type1")
                        .when(simple("${header.type} == ${type:org.apache.camel.converter.MyTypeEnum.TYPE2}")).to("mock:type2")
                        .when(simple("${header.type} == ${type:org.apache.camel.converter.MyTypeEnum.TYPE3}")).to("mock:type3")
                        .otherwise().to("mock:other");
            }
        });
        context.start();

        getMockEndpoint("mock:type1").expectedBodiesReceived("C");
        getMockEndpoint("mock:type2").expectedBodiesReceived("A");
        getMockEndpoint("mock:type3").expectedBodiesReceived("B");

        template.sendBodyAndHeader("direct:start", "A", "type", MyTypeEnum.TYPE2);
        template.sendBodyAndHeader("direct:start", "B", "type", MyTypeEnum.TYPE3);
        template.sendBodyAndHeader("direct:start", "C", "type", MyTypeEnum.TYPE1);

        assertMockEndpointsSatisfied();

        context.stop();
    }

    public enum MyEnum {
        GET_USERS,
        GET_USERS_BY_TOPIC
    }

}
