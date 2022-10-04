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
package org.apache.camel.component.jackson;

import java.util.stream.Stream;

import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JacksonMarshalNamingStrategyTest extends CamelTestSupport {

    @ParameterizedTest
    @MethodSource("namingStrategies")
    public void testNamingStrategy(String namingStrategy, String expectedJson) throws Exception {
        PojoNamingStrategy pojoNamingStrategy = new PojoNamingStrategy();
        pojoNamingStrategy.setFieldOne("test");
        pojoNamingStrategy.setFieldTwo("supertest");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        context.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() throws Exception {
                JacksonDataFormat format = new JacksonDataFormat();
                format.setNamingStrategy(namingStrategy);
                from("direct:in").marshal(format).to("mock:result");
            }
        });

        Object marshalled = template.requestBody("direct:in", pojoNamingStrategy);
        String marshalledAsString = context.getTypeConverter().convertTo(String.class, marshalled);
        assertEquals(expectedJson, marshalledAsString);

        mock.assertIsSatisfied();
    }

    private static Stream<Arguments> namingStrategies() {
        return Stream.of(
                Arguments.of("LOWER_DOT_CASE", "{\"field.one\":\"test\",\"field.two\":\"supertest\"}"),
                Arguments.of("SNAKE_CASE", "{\"field_one\":\"test\",\"field_two\":\"supertest\"}"),
                Arguments.of("LOWER_CAMEL_CASE", "{\"fieldOne\":\"test\",\"fieldTwo\":\"supertest\"}"),
                Arguments.of("LOWER_CASE", "{\"fieldone\":\"test\",\"fieldtwo\":\"supertest\"}"),
                Arguments.of("KEBAB_CASE", "{\"field-one\":\"test\",\"field-two\":\"supertest\"}"),
                Arguments.of("UPPER_CAMEL_CASE", "{\"FieldOne\":\"test\",\"FieldTwo\":\"supertest\"}"));
    }
}
