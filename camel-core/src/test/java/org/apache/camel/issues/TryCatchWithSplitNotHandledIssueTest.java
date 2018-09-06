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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

/**
 * @version 
 */
public class TryCatchWithSplitNotHandledIssueTest extends ContextTestSupport {

    @Test
    public void testSplitWithErrorIsNotHandled() throws Exception {
        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedBodiesReceived("James");
        error.message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isNotNull();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hi Claus", "Hi Willem");

        try {
            template.sendBody("direct:start", "Claus@James@Willem");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            assertEquals("This is a dummy error James!", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }


    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("error", new GenerateError());
        return jndi;
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @SuppressWarnings("deprecation")
            public void configure() {
                from("direct:start")
                    .split(body().tokenize("@"))
                    .doTry()
                        .to("bean:error")
                        .to("mock:result")
                    .doCatch(Exception.class).handled(false)
                        .to("mock:error")
                    .end();
            }

        };
    }

    public static class GenerateError {

        public String dummyException(String payload) throws Exception {
            if (payload.equals("James")) {
                throw new Exception("This is a dummy error James!");
            }
            return "Hi " + payload;
        }

    }

}