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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultMessage;
import org.junit.Test;

/**
 * @version 
 */
public class SetBodyProcessorTest extends ContextTestSupport {

    @Test
    public void testSetBody() throws Exception {
        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.expectedBodiesReceived("World");
        foo.expectedHeaderReceived("foo", 123);
        foo.message(0).predicate(new Predicate() {
            public boolean matches(Exchange exchange) {
                // this time we should have the specialized message
                return exchange.getIn() instanceof MyMessage;
            }
        });

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Bye World");
        result.expectedHeaderReceived("foo", 123);
        result.message(0).predicate(new Predicate() {
            public boolean matches(Exchange exchange) {
                // this time we should have lost the specialized message
                return !(exchange.getIn() instanceof MyMessage);
            }
        });

        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                MyMessage my = new MyMessage(exchange.getContext());
                my.setBody("World");
                my.setHeader("foo", 123);
                exchange.setIn(my);
            }
        });

        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testSetBodyWithHeader() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:test");
        result.expectedBodiesReceived("bbb");
        result.expectedHeaderReceived("text", "aab");
        template.sendBodyAndHeader("direct:start2", "aab", "text", "aab");
        
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("mock:foo")
                    .setBody(simple("Bye ${body}"))
                    .to("mock:result");
                
                from("direct:start2")
                    .setBody(simple("header.text.replace('a','b')")).to("mock:test");
            }
        };
    }

    private static class MyMessage extends DefaultMessage {

        public MyMessage(CamelContext camelContext) {
            super(camelContext);
        }

        @Override
        public MyMessage newInstance() {
            return new MyMessage(getCamelContext());
        }
    }
}
