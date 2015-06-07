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

/**
 * @version 
 */
public class TryCatchWithSplitIssueTest extends ContextTestSupport {

    public void testSplitWithErrorIsHandled() throws Exception {
        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedBodiesReceived("James");
        error.message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isNotNull();
        error.message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).method("getMessage").isEqualTo("This is a dummy error James!");

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hi Claus", "Hi Willem");

        template.sendBody("direct:start", "Claus@James@Willem");

        assertMockEndpointsSatisfied();
    }

    public void testSplitOnlyWithErrorIsHandled() throws Exception {
        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedBodiesReceived("James");
        error.message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isNotNull();
        error.message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).method("getMessage").isEqualTo("This is a dummy error James!");

        template.sendBody("direct:start", "James");

        assertMockEndpointsSatisfied();
    }


    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("error", new GenerateError());
        return jndi;
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                context.setTracing(true);

                from("direct:start")
                    .split(body().tokenize("@"))
                    .doTry()
                        .to("bean:error")
                        .to("mock:result")
                    .doCatch(Exception.class)
                        .to("mock:error")
                    .doFinally()
                        .to("mock:foo")
                        .to("mock:bar")
                    .end();
            }

        };
    }

    public static class GenerateError {

        public String dummyException(String payload) throws Exception {
            if (payload.equals("James")) {
                throw new IllegalArgumentException("This is a dummy error James!");
            }
            return "Hi " + payload;
        }

    }

}
