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
package org.apache.camel.management;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ManagedRouteWithOnExceptionTest extends ManagementTestSupport {

    @Override
    protected void setUp() throws Exception {
        System.setProperty(JmxSystemPropertyKeys.CREATE_CONNECTOR, "true");
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        System.clearProperty(JmxSystemPropertyKeys.CREATE_CONNECTOR);
        super.tearDown();
    }

    public void testShouldBeInstrumentedOk() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testShouldBeInstrumentedKabom() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Kabom");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    public void testShouldBeInstrumentedOkAndKabom() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");
        try {
            template.sendBody("direct:start", "Kabom");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .onException(Exception.class)
                        .to("mock:error")
                    .end()
                    .delay(100)
                    .choice()
                        .when(body().isEqualTo("Kabom")).throwException(new IllegalArgumentException("Kabom"))
                        .otherwise().to("mock:result")
                    .end();
            }
        };
    }
}
