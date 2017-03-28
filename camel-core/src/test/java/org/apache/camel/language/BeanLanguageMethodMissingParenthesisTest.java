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
package org.apache.camel.language;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

public class BeanLanguageMethodMissingParenthesisTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testFooCorrect() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .filter(method(BeanLanguageMethodMissingParenthesisTest.class, "couldThisBeFoo(${body}, ${header.foo})"))
                        .to("mock:foo")
                    .end()
                    .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "yes");
        assertMockEndpointsSatisfied();

        resetMocks();

        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:foo").expectedMessageCount(0);
        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "no");
        assertMockEndpointsSatisfied();
    }

    public void testFooMissingParenthesis() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .filter(method(BeanLanguageMethodMissingParenthesisTest.class, "couldThisBeFoo(${body}, ${header.foo}"))
                        .to("mock:foo")
                    .end()
                    .to("mock:result");
            }
        });
        context.start();

        try {
            template.sendBodyAndHeader("direct:start", "Hello World", "foo", "yes");
            fail("Should throw exception");
        } catch (CamelExecutionException e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause().getCause());
            assertEquals("Method should end with parenthesis, was couldThisBeFoo(${body}, ${header.foo}", iae.getMessage());
        }
    }

    public void testFooInvalidName() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .filter(method(BeanLanguageMethodMissingParenthesisTest.class, "--couldThisBeFoo(${body}, ${header.foo})"))
                        .to("mock:foo")
                    .end()
                    .to("mock:result");
            }
        });
        context.start();

        try {
            template.sendBodyAndHeader("direct:start", "Hello World", "foo", "yes");
            fail("Should throw exception");
        } catch (CamelExecutionException e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause().getCause());
            assertEquals("Method name must start with a valid java identifier at position: 0 in method: --couldThisBeFoo(${body}, ${header.foo})", iae.getMessage());
        }
    }

    public boolean couldThisBeFoo(String body, String header) {
        return "yes".equals(header);
    }
}
