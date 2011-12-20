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
package org.apache.camel.spring.processor.onexception;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.mock.MockEndpoint;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;

/**
 * Unit test for onException with the spring DSL.
 */
public class SpringContextScopeOnExceptionTest extends ContextTestSupport {

    public void testOrderOk() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Order OK");
        result.expectedHeaderReceived("orderid", "123");

        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedMessageCount(0);

        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(0);

        Object out = template.requestBodyAndHeader("direct:start", "Order: MacBook Pro", "customerid", "444");
        assertEquals("Order OK", out);

        assertMockEndpointsSatisfied();
    }

    public void testOrderError() throws Exception {
        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedBodiesReceived("Order ERROR");
        error.expectedHeaderReceived("orderid", "failed");

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(0);

        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(0);

        Object out = template.requestBodyAndHeader("direct:start", "Order: kaboom", "customerid", "555");
        assertEquals("Order ERROR", out);

        assertMockEndpointsSatisfied();
    }

    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this, "/org/apache/camel/spring/processor/onexception/SpringContextScopeOnExceptionTest.xml");
    }
}