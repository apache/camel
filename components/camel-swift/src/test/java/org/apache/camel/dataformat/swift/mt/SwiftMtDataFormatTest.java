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
package org.apache.camel.dataformat.swift.mt;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prowidesoftware.swift.model.mt.mt1xx.MT103;
import com.prowidesoftware.swift.model.mt.mt5xx.MT515;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.dataformat.SwiftMtDataFormat;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The unit test for {@link org.apache.camel.dataformat.swift.mt.SwiftMtDataFormat} testing the Java DSL.
 */
class SwiftMtDataFormatTest extends CamelTestSupport {

    @ParameterizedTest
    @ValueSource(strings = { "", "dsl" })
    void testUnmarshal(String mode) throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint(String.format("mock:unmarshal%s", mode));
        mockEndpoint.expectedMessageCount(1);

        Object result
                = template.requestBody(String.format("direct:unmarshal%s", mode),
                        Files.readAllBytes(Paths.get("src/test/resources/mt/message1.txt")));
        assertNotNull(result);
        assertInstanceOf(MT515.class, result);
        mockEndpoint.assertIsSatisfied();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "dsl" })
    void testMarshal(String mode) throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint(String.format("mock:marshal%s", mode));
        mockEndpoint.expectedMessageCount(1);

        MT103 message = MT103.parse(Files.readString(Paths.get("src/test/resources/mt/message2.txt")));

        Object result
                = template.requestBody(String.format("direct:marshal%s", mode), message);
        assertNotNull(result);
        assertInstanceOf(InputStream.class, result);
        MT103 actual = MT103.parse((InputStream) result);
        assertEquals(message.message(), actual.message());
        mockEndpoint.assertIsSatisfied();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "dsl" })
    void testMarshalJson(String mode) throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint(String.format("mock:marshalJson%s", mode));
        mockEndpoint.expectedMessageCount(1);

        MT103 message = MT103.parse(Files.readString(Paths.get("src/test/resources/mt/message2.txt")));

        Object result
                = template.requestBody(String.format("direct:marshalJson%s", mode), message);
        assertNotNull(result);
        assertInstanceOf(InputStream.class, result);

        ObjectMapper mapper = new ObjectMapper();
        assertEquals(mapper.readTree(Files.readString(Paths.get("src/test/resources/mt/message2.json"))),
                mapper.readTree((InputStream) result));
        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:unmarshal").unmarshal(new SwiftMtDataFormat()).to("mock:unmarshal");
                from("direct:unmarshaldsl").unmarshal().swiftMt().to("mock:unmarshaldsl");
                from("direct:marshal").marshal(new SwiftMtDataFormat()).to("mock:marshal");
                from("direct:marshaldsl").marshal().swiftMt().to("mock:marshaldsl");
                from("direct:marshalJson").marshal(new SwiftMtDataFormat("true")).to("mock:marshalJson");
                from("direct:marshalJsondsl").marshal().swiftMt(true).to("mock:marshalJsondsl");
            }
        };
    }
}
