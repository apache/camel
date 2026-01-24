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
package org.apache.camel.processor;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.processor.validation.NoXmlBodyValidationException;
import org.apache.camel.support.processor.validation.SchemaValidationException;
import org.apache.camel.support.processor.validation.ValidatingProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test of ValidatingProcessor.
 */
public class ValidatingProcessorNotUseSharedSchemaTest extends ContextTestSupport {

    protected ValidatingProcessor validating;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        validating = new ValidatingProcessor();
        validating.setSchemaFile(new File("src/test/resources/org/apache/camel/processor/ValidatingProcessor.xsd"));
        validating.setUseSharedSchema(false);

        super.setUp();
    }

    @Test
    public void testValidMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:valid");
        mock.expectedMessageCount(1);

        String xml = "<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>" + "<user xmlns=\"http://foo.com/bar\">" + "  <id>1</id>"
                     + "  <username>davsclaus</username>" + "</user>";

        template.sendBody("direct:start", xml);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testValidMessageTwice() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:valid");
        mock.expectedMessageCount(2);

        String xml = "<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>" + "<user xmlns=\"http://foo.com/bar\">" + "  <id>1</id>"
                     + "  <username>davsclaus</username>" + "</user>";

        template.sendBody("direct:start", xml);
        template.sendBody("direct:start", xml);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInvalidMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:invalid");
        mock.expectedMessageCount(1);

        String xml = "<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>" + "<user xmlns=\"http://foo.com/bar\">"
                     + "  <username>someone</username>" + "</user>";

        RuntimeCamelException e = assertThrows(RuntimeCamelException.class,
                () -> template.sendBody("direct:start", xml),
                "Should have thrown a RuntimeCamelException");
        boolean b = e.getCause() instanceof SchemaValidationException;
        assertTrue(b);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNoXMLBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:invalid");
        mock.expectedMessageCount(1);

        RuntimeCamelException e = assertThrows(RuntimeCamelException.class,
                () -> template.sendBody("direct:start", null),
                "Should have thrown a RuntimeCamelException");
        assertIsInstanceOf(NoXmlBodyValidationException.class, e.getCause());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testValidatingOptions() throws Exception {
        assertNotNull(validating.getErrorHandler());
        assertNotNull(validating.getSchema());
        assertNotNull(validating.getSchemaFactory());
        assertNotNull(validating.getSchemaFile());
        assertNotNull(validating.getSchemaLanguage());

        assertNull(validating.getSchemaUrl());

        assertThrows(IllegalArgumentException.class,
                () -> validating.getSchemaSource(),
                "Should have thrown an exception");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(3));

                onException(ValidationException.class).to("mock:invalid");

                from("direct:start").process(validating).to("mock:valid");
            }
        };
    }

}
