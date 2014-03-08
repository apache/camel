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
package org.apache.camel.spring.processor;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;

public class SpringWireTapUsingFireAndForgetCopyTest extends ContextTestSupport {

    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this, "org/apache/camel/spring/processor/SpringWireTapUsingFireAndForgetCopyTest.xml");
    }

    public void testFireAndForgetUsingExpression() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("World");

        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();

        // should be different exchange instances
        Exchange e1 = result.getReceivedExchanges().get(0);
        Exchange e2 = foo.getReceivedExchanges().get(0);
        assertNotSame("Should not be same Exchange", e1, e2);

        // should have same from endpoint
        assertEquals("direct://start", e1.getFromEndpoint().getEndpointUri());
        assertEquals("direct://start", e2.getFromEndpoint().getEndpointUri());
    }

    public void testFireAndForgetUsingProcessor() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("World");

        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.expectedBodiesReceived("Bye World");
        foo.expectedHeaderReceived("foo", "bar");

        template.sendBody("direct:start2", "World");

        assertMockEndpointsSatisfied();

        // should be different exchange instances
        Exchange e1 = result.getReceivedExchanges().get(0);
        Exchange e2 = foo.getReceivedExchanges().get(0);
        assertNotSame("Should not be same Exchange", e1, e2);

        // should have same from endpoint
        assertEquals("direct://start2", e1.getFromEndpoint().getEndpointUri());
        assertEquals("direct://start2", e2.getFromEndpoint().getEndpointUri());
    }

    // START SNIPPET: e1
    public static class MyProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            String body = exchange.getIn().getBody(String.class);
            // here we prepare the new exchange by setting the payload on the exchange
            // on the IN message.
            exchange.getIn().setBody("Bye " + body);
            exchange.getIn().setHeader("foo", "bar");
        }
    }
    // END SNIPPET: e1

}