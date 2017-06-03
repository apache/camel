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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.validation.PredicateValidationException;

/**
 * @version 
 */
public class ValidateSimpleTest extends ContextTestSupport {

    protected Endpoint startEndpoint;
    protected MockEndpoint resultEndpoint;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        startEndpoint = resolveMandatoryEndpoint("direct:start", Endpoint.class);
        resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
    }

    public void testSendMatchingMessage() throws Exception {
        resultEndpoint.expectedMessageCount(1);

        template.sendBody(startEndpoint, "Hello Camel");

        assertMockEndpointsSatisfied();
    }

    public void testSendNotMatchingMessage() throws Exception {
        resultEndpoint.expectedMessageCount(0);

        try {
            template.sendBody(startEndpoint, "Bye World");
            fail("CamelExecutionException expected");
        } catch (CamelExecutionException e) {
            // expected
            assertIsInstanceOf(PredicateValidationException.class, e.getCause());
            String s = "Validation failed for Predicate[Simple: ${body} contains 'Camel'].";
            assertTrue(e.getCause().getMessage().startsWith(s));
        }

        assertMockEndpointsSatisfied();
    }


    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .validate().simple("${body} contains 'Camel'")
                    .to("mock:result");
            }
        };
    }
}