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
package org.apache.camel.component.mock;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class MockPredicateTest extends ContextTestSupport {

    public void testMockPredicate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:foo");
        mock.message(0).predicate().header("foo");

        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "bar");

        assertMockEndpointsSatisfied();
    }

    public void testMockPredicateAsParameter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:foo");
        mock.message(0).predicate(PredicateBuilder.isNotNull(header("foo")));

        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "bar");

        assertMockEndpointsSatisfied();
    }

    public void testOutBodyType() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:foo");
        mock.message(0).outBody(String.class).isEqualTo("Bye World");
        mock.expectedMessageCount(1);

        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
                exchange.getOut().setBody("Bye World");
            }
        });

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:foo");
            }
        };
    }
}
