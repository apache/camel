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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class AOPAfterFinallyTest extends ContextTestSupport {

    @Test
    public void testAOPAfterFinally() throws Exception {
        getMockEndpoint("mock:after").message(0).body().isEqualTo("Bye World");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAOPAfterFinallyWithException() throws Exception {
        getMockEndpoint("mock:after").message(0).body().isEqualTo("Kaboom the World");

        try {
            template.requestBody("direct:start", "Kaboom", String.class);
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Damn", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @SuppressWarnings("deprecation")
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .aop().afterFinally("mock:after")
                    .choice()
                        .when(body().isEqualTo("Hello World"))
                            .transform(constant("Bye World"))
                        .otherwise()
                            .transform(constant("Kaboom the World"))
                            .throwException(new IllegalArgumentException("Damn"))
                        .end()
                    .to("mock:result");
            }
        };
    }
}
