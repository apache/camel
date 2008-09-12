/**
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
import org.apache.camel.processor.validation.SchemaValidationException;
import org.apache.camel.processor.validation.ValidatingProcessor;

/**
 * Unit test of ValidatingProcessor.
 */
public class ValidatingProcessorTest extends ContextTestSupport {

    private ValidatingProcessor validating;

    @Override
    protected void setUp() throws Exception {
        validating = new ValidatingProcessor();
        validating.setSchemaFile(new File("src/test/resources/org/apache/camel/processor/ValidatingProcessor.xsd").getAbsoluteFile());

        super.setUp();
    }

    public void testValidMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:valid");
        mock.expectedMessageCount(1);

        String xml = "<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>"
            + "<user xmlns=\"http://foo.com/bar\">"
            + "  <id>1</id>"
            + "  <username>davsclaus</username>"
            + "</user>";

        template.sendBody("direct:start", xml);

        assertMockEndpointsSatisfied();
    }

    public void testInvalidMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:invalid");
        mock.expectedMessageCount(1);

        String xml = "<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>"
            + "<user xmlns=\"http://foo.com/bar\">"
            + "  <username>someone</username>"
            + "</user>";

        try {
            template.sendBody("direct:start", xml);
            fail("Should have thrown a RuntimeCamelException");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof SchemaValidationException);
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                exception(ValidationException.class).to("mock:invalid");

                from("direct:start").
                    process(validating).
                    to("mock:valid");
            }
        };
    }
}