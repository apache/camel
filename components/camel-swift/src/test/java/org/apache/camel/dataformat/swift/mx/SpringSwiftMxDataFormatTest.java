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
import com.prowidesoftware.swift.model.mx.sys.MxXsys01100102;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The unit test for {@link SwiftMxDataFormat} testing the XML DSL.
 */
class SpringSwiftMxDataFormatTest extends CamelSpringTestSupport {

    @Test
    void testUnmarshal() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:unmarshal");
        mockEndpoint.expectedMessageCount(1);

        Object result
                = template.requestBody("direct:unmarshal",
                        Files.readAllBytes(Paths.get("src/test/resources/mx/message1.xml")));
        assertNotNull(result);
        assertInstanceOf(MxCamt04800103.class, result);
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    void testUnmarshalFull() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:unmarshalFull");
        mockEndpoint.expectedMessageCount(1);

        Object result
                = template.requestBody("direct:unmarshalFull",
                        Files.readAllBytes(Paths.get("src/test/resources/mx/message3.xml")));
        assertNotNull(result);
        assertInstanceOf(MxXsys01100102.class, result);
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    void testMarshal() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:marshal");
        mockEndpoint.expectedMessageCount(1);

        MxPacs00800107 message = MxPacs00800107.parse(Files.readString(Paths.get("src/test/resources/mx/message2.xml")));
        Object result
                = template.requestBody("direct:marshal", message);
        assertNotNull(result);
        assertInstanceOf(InputStream.class, result);
        MxPacs00800107 actual = MxPacs00800107.parse(IOUtils.toString((InputStream) result, StandardCharsets.UTF_8));
        assertEquals(message.message(), actual.message());
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    void testMarshalJson() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:marshalJson");
        mockEndpoint.expectedMessageCount(1);

        MxPacs00800107 message = MxPacs00800107.parse(Files.readString(Paths.get("src/test/resources/mx/message2.xml")));
        Object result
                = template.requestBody("direct:marshalJson", message);
        assertNotNull(result);
        assertInstanceOf(InputStream.class, result);

        ObjectMapper mapper = new ObjectMapper();
        assertEquals(mapper.readTree(Files.readString(Paths.get("src/test/resources/mx/message2.json"))),
                mapper.readTree((InputStream) result));
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    void testMarshalFull() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:marshalFull");
        mockEndpoint.expectedMessageCount(1);

        MxPacs00800107 message = MxPacs00800107.parse(Files.readString(Paths.get("src/test/resources/mx/message2.xml")));
        Object result
                = template.requestBody("direct:marshalFull", message);
        assertNotNull(result);
        assertInstanceOf(InputStream.class, result);

        BufferedReader reader = new BufferedReader(new InputStreamReader((InputStream) result, StandardCharsets.UTF_8));
        String line = reader.readLine();
        assertFalse(line.contains("<?xml"), String.format("Should not start with the xml header, the first line was %s", line));
        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("routes/SpringSwiftMxDataFormatTest.xml");
    }
}
