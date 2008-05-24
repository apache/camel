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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.Builder;
import org.apache.camel.builder.ProcessorBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class ValidationTest extends ContextTestSupport {
    protected Processor validator = new MyValidator();
    protected MockEndpoint validEndpoint;
    protected MockEndpoint invalidEndpoint;

    public void testValidMessage() throws Exception {
        validEndpoint.expectedMessageCount(1);
        invalidEndpoint.expectedMessageCount(0);

        Object result = template.sendBodyAndHeader("direct:start", "<valid/>", "foo", "bar");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint);
        assertEquals("validResult", result);
    }

    public void testInvalidMessage() throws Exception {
        invalidEndpoint.expectedMessageCount(1);
        validEndpoint.expectedMessageCount(0);

        Object result = template.sendBodyAndHeader("direct:start", "<invalid/>", "foo", "notMatchedHeaderValue");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint);
        assertEquals("invalidResult", result);
    }

    public void testinvalidThenValidMessage() throws Exception {
        validEndpoint.expectedMessageCount(2);
        invalidEndpoint.expectedMessageCount(1);

        Object result;
        
        result = template.sendBodyAndHeader("direct:start", "<invalid/>", "foo",  "notMatchedHeaderValue");
        assertEquals("invalidResult", result);
        result = template.sendBodyAndHeader("direct:start", "<valid/>", "foo",   "bar");
        assertEquals("validResult", result);
        result = template.sendBodyAndHeader("direct:start", "<valid/>", "foo",   "bar");
        assertEquals("validResult", result);

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        validEndpoint = resolveMandatoryEndpoint("mock:valid", MockEndpoint.class);
        invalidEndpoint = resolveMandatoryEndpoint("mock:invalid", MockEndpoint.class);
        
        validEndpoint.whenAnyExchangeReceived(ProcessorBuilder.setOutBody(Builder.constant("validResult")));
        invalidEndpoint.whenAnyExchangeReceived(ProcessorBuilder.setOutBody(Builder.constant("invalidResult")));
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").
                        tryBlock().
                        process(validator).
                        to("mock:valid").
                        handle(ValidationException.class).to("mock:invalid");
            }
        };
    }
}
