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
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;

/**
 * @version 
 */
public class MockAsBeanTest extends ContextTestSupport {

    // create foo bean as a mock endpoint
    @SuppressWarnings("deprecation")
    private MockEndpoint foo = new MockEndpoint("mock:foo");

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("foo", foo);
        return jndi;
    }

    // START SNIPPET: e1
    public void testMockAsBeanWithWhenAnyExchangeReceived() throws Exception {
        // we should expect to receive the transformed message
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        // the foo bean is a MockEndpoint which we use in this test to transform
        // the message
        foo.whenAnyExchangeReceived(new Processor() {
            public void process(Exchange exchange) throws Exception {
                String in = exchange.getIn().getBody(String.class);
                exchange.getIn().setBody("Bye " + in);
            }
        });

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }
    // END SNIPPET: e1    
    
    @Override
    // START SNIPPET: e2
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    // send to foo bean
                    .bean("foo")
                    // and then to result mock
                    .to("mock:result");
            }
        };
    }
    // END SNIPPET: e2
    
   // START SNIPPET: e3
    public void testMockAsBeanWithReplyBody() throws Exception {
        // we should expect to receive the transformed message
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
        
        foo.returnReplyBody(ExpressionBuilder.simpleExpression("Bye ${body}"));

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }
    // END SNIPPET: e3
    
   // START SNIPPET: e4
    public void testMockAsBeanWithReplyHeader() throws Exception {
        // we should expect to receive the transformed message
        getMockEndpoint("mock:result").expectedHeaderReceived("myHeader", "Bye World");
        
        foo.returnReplyHeader("myHeader", ExpressionBuilder.simpleExpression("Bye ${body}"));

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }
    // END SNIPPET: e4

}
