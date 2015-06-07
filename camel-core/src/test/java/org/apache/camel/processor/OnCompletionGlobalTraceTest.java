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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.processor.interceptor.TraceEventMessage;
import org.apache.camel.processor.interceptor.Tracer;

/**
 * @version 
 */
public class OnCompletionGlobalTraceTest extends ContextTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myProcessor", new MyProcessor());
        return jndi;
    }

    public void testSynchronizeComplete() throws Exception {
        getMockEndpoint("mock:sync").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:sync").expectedPropertyReceived(Exchange.ON_COMPLETION, true);
        getMockEndpoint("mock:trace").expectedMessageCount(4);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        TraceEventMessage msg1 = getMockEndpoint("mock:trace").getReceivedExchanges().get(0).getIn().getBody(TraceEventMessage.class);
        TraceEventMessage msg2 = getMockEndpoint("mock:trace").getReceivedExchanges().get(1).getIn().getBody(TraceEventMessage.class);
        TraceEventMessage msg3 = getMockEndpoint("mock:trace").getReceivedExchanges().get(2).getIn().getBody(TraceEventMessage.class);
        TraceEventMessage msg4 = getMockEndpoint("mock:trace").getReceivedExchanges().get(3).getIn().getBody(TraceEventMessage.class);

        assertEquals("direct://start", msg1.getFromEndpointUri());
        assertEquals("ref:myProcessor", msg1.getToNode());

        assertEquals("ref:myProcessor", msg2.getPreviousNode());
        assertEquals("mock://result", msg2.getToNode());

        assertTrue(msg3.getPreviousNode().startsWith("OnCompletion"));
        assertEquals("log://global", msg3.getToNode());

        assertEquals("log://global", msg4.getPreviousNode());
        assertEquals("mock://sync", msg4.getToNode());
    }

    public void testSynchronizeFailure() throws Exception {
        getMockEndpoint("mock:sync").expectedMessageCount(1);
        getMockEndpoint("mock:sync").expectedPropertyReceived(Exchange.ON_COMPLETION, true);
        getMockEndpoint("mock:trace").expectedMessageCount(3);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Kabom");
            fail("Should throw exception");
        } catch (CamelExecutionException e) {
            assertEquals("Kabom", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();

        TraceEventMessage msg1 = getMockEndpoint("mock:trace").getReceivedExchanges().get(0).getIn().getBody(TraceEventMessage.class);
        TraceEventMessage msg2 = getMockEndpoint("mock:trace").getReceivedExchanges().get(1).getIn().getBody(TraceEventMessage.class);
        TraceEventMessage msg3 = getMockEndpoint("mock:trace").getReceivedExchanges().get(2).getIn().getBody(TraceEventMessage.class);

        assertEquals("direct://start", msg1.getFromEndpointUri());
        assertEquals("ref:myProcessor", msg1.getToNode());

        assertTrue(msg2.getPreviousNode().startsWith("OnCompletion"));
        assertEquals("log://global", msg2.getToNode());

        assertEquals("log://global", msg3.getPreviousNode());
        assertEquals("mock://sync", msg3.getToNode());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Tracer tracer = new Tracer();
                tracer.setDestinationUri("mock:trace");
                context.addInterceptStrategy(tracer);

                // START SNIPPET: e1
                // define a global on completion that is invoked when the exchange is complete
                onCompletion().to("log:global").to("mock:sync");

                from("direct:start")
                    .process("myProcessor")
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

    public static class MyProcessor implements Processor {

        public MyProcessor() {
        }

        public void process(Exchange exchange) throws Exception {
            if ("Kabom".equals(exchange.getIn().getBody())) {
                throw new IllegalArgumentException("Kabom");
            }
            exchange.getIn().setBody("Bye World");
        }
    }
}