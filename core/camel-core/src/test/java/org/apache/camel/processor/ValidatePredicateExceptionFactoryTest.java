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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.PredicateExceptionFactory;
import org.apache.camel.support.processor.PredicateValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class ValidatePredicateExceptionFactoryTest extends ContextTestSupport {

    protected Endpoint startEndpoint;
    protected MockEndpoint resultEndpoint;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        startEndpoint = resolveMandatoryEndpoint("direct:start", Endpoint.class);
        resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
    }

    @Test
    public void testSendMatchingMessage() throws Exception {
        resultEndpoint.expectedMessageCount(1);

        template.sendBody(startEndpoint, "Hello Camel");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendNotMatchingMessage() throws Exception {
        resultEndpoint.expectedMessageCount(0);

        try {
            template.sendBody(startEndpoint, "Bye World");
            fail("CamelExecutionException expected");
        } catch (CamelExecutionException e) {
            // expected
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            String s = "Dude was here myValidate";
            assertStringContains(e.getCause().getMessage(), s);
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSkip() throws Exception {
        resultEndpoint.expectedMessageCount(0);

        try {
            template.sendBody(startEndpoint, "Skip World");
            fail("CamelExecutionException expected");
        } catch (CamelExecutionException e) {
            // expected normal exception
            assertIsInstanceOf(PredicateValidationException.class, e.getCause());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .validate().simple("${body} contains 'Camel'").predicateExceptionFactory(new MyExceptionFactory())
                        .id("myValidate")
                        .to("mock:result");
            }
        };
    }

    private class MyExceptionFactory implements PredicateExceptionFactory {

        @Override
        public Exception newPredicateException(Exchange exchange, Predicate predicate, String id) {
            if (exchange.getMessage().getBody(String.class).startsWith("Skip")) {
                return null;
            }
            throw new IllegalArgumentException("Dude was here " + id);
        }
    }
}
