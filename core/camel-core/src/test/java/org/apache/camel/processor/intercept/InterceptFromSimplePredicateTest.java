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
package org.apache.camel.processor.intercept;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * Testing http://camel.apache.org/dsl.html
 */
public class InterceptFromSimplePredicateTest extends ContextTestSupport {

    @Test
    public void testNoIntercept() throws Exception {
        getMockEndpoint("mock:intercepted").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testIntercepted() throws Exception {
        getMockEndpoint("mock:intercepted").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedBodiesReceived("This is a test body");

        template.sendBodyAndHeader("direct:start", "Hello World", "usertype", "test");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                interceptFrom().when(header("usertype").isEqualTo("test")).process(new MyTestServiceProcessor()).to("mock:intercepted");

                // and here is our route
                from("direct:start").to("seda:bar").to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

    private static class MyTestServiceProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getIn().setBody("This is a test body");
        }
    }
}
