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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;

/**
 *
 */
public class TryFinallyCaughtExceptionTest extends ContextTestSupport {

    public void testTryFinallyCaughtException() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        MockEndpoint error = getMockEndpoint("mock:b");
        error.expectedMessageCount(1);

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();

        Exception e = error.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertNotNull(e);
        assertEquals("Forced", e.getMessage());

        String to = error.getReceivedExchanges().get(0).getProperty(Exchange.FAILURE_ENDPOINT, String.class);
        assertEquals("bean://myBean?method=doSomething", to);
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myBean", this);
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .doTry()
                        .to("mock:a")
                        .to("bean:myBean?method=doSomething")
                    .doFinally()
                        .to("mock:b")
                    .end()
                    .to("mock:result");
            }
        };
    }

    public void doSomething(String body) throws Exception {
        throw new IllegalArgumentException("Forced");
    }
}
