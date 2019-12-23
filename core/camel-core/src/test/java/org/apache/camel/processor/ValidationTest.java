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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class ValidationTest extends ContextTestSupport {
    protected Processor validator = new MyValidator();
    protected MockEndpoint validEndpoint;
    protected MockEndpoint invalidEndpoint;

    @Test
    public void testValidMessage() throws Exception {
        validEndpoint.expectedMessageCount(1);
        invalidEndpoint.expectedMessageCount(0);

        Object result = template.requestBodyAndHeader("direct:start", "<valid/>", "foo", "bar");
        assertEquals("validResult", result);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInvalidMessage() throws Exception {
        validEndpoint.expectedMessageCount(0);
        invalidEndpoint.expectedMessageCount(1);

        try {
            template.sendBodyAndHeader("direct:start", "<invalid/>", "foo", "notMatchedHeaderValue");
        } catch (RuntimeCamelException e) {
            // the expected empty catch block here is not intended for this
            // class itself but the subclasses
            // e.g. ValidationWithErrorInHandleAndFinallyBlockTest where
            // noErrorHandler() is being installed.
            // this's also why there's no fail("Should have thrown an
            // exception") call here right after
            // template.sendBodyAndHeader() call as RuntimeCamelException will
            // be not thrown by *all* subclasses
            // but only by some of them.
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testinvalidThenValidMessage() throws Exception {
        validEndpoint.expectedMessageCount(2);
        invalidEndpoint.expectedMessageCount(1);

        try {
            template.sendBodyAndHeader("direct:start", "<invalid/>", "foo", "notMatchedHeaderValue");
        } catch (RuntimeCamelException e) {
            // the same as above
        }

        Object result = template.requestBodyAndHeader("direct:start", "<valid/>", "foo", "bar");
        assertEquals("validResult", result);

        result = template.requestBodyAndHeader("direct:start", "<valid/>", "foo", "bar");
        assertEquals("validResult", result);

        assertMockEndpointsSatisfied();
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        validEndpoint = resolveMandatoryEndpoint("mock:valid", MockEndpoint.class);
        invalidEndpoint = resolveMandatoryEndpoint("mock:invalid", MockEndpoint.class);

        validEndpoint.whenAnyExchangeReceived(e -> e.getMessage().setBody("validResult"));
        invalidEndpoint.whenAnyExchangeReceived(e -> e.getMessage().setBody("invalidResult"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").doTry().process(validator).to("mock:valid").doCatch(ValidationException.class).to("mock:invalid");
            }
        };
    }
}
