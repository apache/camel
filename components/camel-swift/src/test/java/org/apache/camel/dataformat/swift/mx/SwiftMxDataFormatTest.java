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
package org.apache.camel.dataformat.swift.mx;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prowidesoftware.swift.model.mx.MxCamt04800103;
import com.prowidesoftware.swift.model.mx.MxPacs00800107;
import com.prowidesoftware.swift.model.mx.MxReadConfiguration;
import com.prowidesoftware.swift.model.mx.MxWriteConfiguration;
import com.prowidesoftware.swift.model.mx.sys.MxXsys01100102;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.dataformat.SwiftMxDataFormat;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The unit test for {@link org.apache.camel.dataformat.swift.mx.SwiftMxDataFormat} testing the Java DSL.
 */
class SwiftMxDataFormatTest extends CamelTestSupport {

    @ParameterizedTest
    @ValueSource(strings = { "", "dsl" })
    void testUnmarshal(String mode) throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint(String.format("mock:unmarshal%s", mode));
        mockEndpoint.expectedMessageCount(1);

        Object result
                = template.requestBody(String.format("direct:unmarshal%s", mode),
                        Files.readAllBytes(Paths.get("src/test/resources/mx/message1.xml")));
        assertNotNull(result);
        assertInstanceOf(MxCamt04800103.class, result);
        mockEndpoint.assertIsSatisfied();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "dsl" })
    void testUnmarshalFull(String mode) throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint(String.format("mock:unmarshalFull%s", mode));
        mockEndpoint.expectedMessageCount(1);

        Object result
                = template.requestBody(String.format("direct:unmarshalFull%s", mode),
                        Files.readAllBytes(Paths.get("src/test/resources/mx/message3.xml")));
        assertNotNull(result);
        assertInstanceOf(MxXsys01100102.class, result);
        mockEndpoint.assertIsSatisfied();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "dsl" })
    void testMarshal(String mode) throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint(String.format("mock:marshal%s", mode));
        mockEndpoint.expectedMessageCount(1);

        MxPacs00800107 message = MxPacs00800107.parse(Files.readString(Paths.get("src/test/resources/mx/message2.xml")));
        Object result
                = template.requestBody(String.format("direct:marshal%s", mode), message);
        assertNotNull(result);
        assertInstanceOf(InputStream.class, result);
        MxPacs00800107 actual = MxPacs00800107.parse(IOUtils.toString((InputStream) result, StandardCharsets.UTF_8));
        assertEquals(message.message(), actual.message());
        mockEndpoint.assertIsSatisfied();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "dsl" })
    void testMarshalJson(String mode) throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint(String.format("mock:marshalJson%s", mode));
        mockEndpoint.expectedMessageCount(1);

        MxPacs00800107 message = MxPacs00800107.parse(Files.readString(Paths.get("src/test/resources/mx/message2.xml")));
        Object result
                = template.requestBody(String.format("direct:marshalJson%s", mode), message);
        assertNotNull(result);
        assertInstanceOf(InputStream.class, result);

        ObjectMapper mapper = new ObjectMapper();
        assertEquals(mapper.readTree(Files.readString(Paths.get("src/test/resources/mx/message2.json"))),
                mapper.readTree((InputStream) result));
        mockEndpoint.assertIsSatisfied();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "dsl" })
    void testMarshalFull(String mode) throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint(String.format("mock:marshalFull%s", mode));
        mockEndpoint.expectedMessageCount(1);

        MxPacs00800107 message = MxPacs00800107.parse(Files.readString(Paths.get("src/test/resources/mx/message2.xml")));
        Object result
                = template.requestBody(String.format("direct:marshalFull%s", mode), message);
        assertNotNull(result);
        assertInstanceOf(InputStream.class, result);

        BufferedReader reader = new BufferedReader(new InputStreamReader((InputStream) result, StandardCharsets.UTF_8));
        String line = reader.readLine();
        assertFalse(line.contains("<?xml"), String.format("Should not start with the xml header, the first line was %s", line));
        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:unmarshal").unmarshal(new SwiftMxDataFormat()).to("mock:unmarshal");
                from("direct:unmarshaldsl").unmarshal().swiftMx().to("mock:unmarshaldsl");
                MxReadConfiguration readConfig = new MxReadConfiguration();
                from("direct:unmarshalFull")
                        .unmarshal(new SwiftMxDataFormat(false, "urn:swift:xsd:xsys.011.001.02", readConfig))
                        .to("mock:unmarshalFull");
                from("direct:unmarshalFulldsl").unmarshal()
                        .swiftMx(false, "urn:swift:xsd:xsys.011.001.02", readConfig).to("mock:unmarshalFulldsl");
                from("direct:marshal").marshal(new SwiftMxDataFormat()).to("mock:marshal");
                from("direct:marshaldsl").marshal().swiftMx().to("mock:marshaldsl");
                MxWriteConfiguration writeConfiguration = new MxWriteConfiguration();
                writeConfiguration.includeXMLDeclaration = false;
                from("direct:marshalFull").marshal(new SwiftMxDataFormat(writeConfiguration, null, null))
                        .to("mock:marshalFull");
                from("direct:marshalFulldsl").marshal().swiftMx(writeConfiguration, null, null).to("mock:marshalFulldsl");
                from("direct:marshalJson").marshal(new SwiftMxDataFormat(true)).to("mock:marshalJson");
                from("direct:marshalJsondsl").marshal().swiftMx(true).to("mock:marshalJsondsl");
            }
        };
    }
}
