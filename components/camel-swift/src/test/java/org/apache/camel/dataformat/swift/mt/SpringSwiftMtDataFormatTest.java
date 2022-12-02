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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The unit test for {@link SwiftMtDataFormat} testing the XML DSL.
 */
class SpringSwiftMtDataFormatTest extends CamelSpringTestSupport {

    @Test
    void testUnmarshal() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:unmarshal");
        mockEndpoint.expectedMessageCount(1);

        Object result
                = template.requestBody("direct:unmarshal", Files.readAllBytes(Paths.get("src/test/resources/mt/message1.txt")));
        assertNotNull(result);
        assertInstanceOf(MT515.class, result);
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    void testMarshal() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:marshal");
        mockEndpoint.expectedMessageCount(1);

        MT103 message = MT103.parse(Files.readString(Paths.get("src/test/resources/mt/message2.txt")));

        Object result = template.requestBody("direct:marshal", message);
        assertNotNull(result);
        assertInstanceOf(InputStream.class, result);
        MT103 actual = MT103.parse((InputStream) result);
        assertEquals(message.message(), actual.message());
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    void testMarshalJson() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:marshalJson");
        mockEndpoint.expectedMessageCount(1);

        MT103 message = MT103.parse(Files.readString(Paths.get("src/test/resources/mt/message2.txt")));

        Object result = template.requestBody("direct:marshalJson", message);
        assertNotNull(result);
        assertInstanceOf(InputStream.class, result);

        ObjectMapper mapper = new ObjectMapper();
        assertEquals(mapper.readTree(Files.readString(Paths.get("src/test/resources/mt/message2.json"))),
                mapper.readTree((InputStream) result));
        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("routes/SpringSwiftMtDataFormatTest.xml");
    }
}
