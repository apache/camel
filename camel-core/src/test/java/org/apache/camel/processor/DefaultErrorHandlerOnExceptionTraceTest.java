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
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.processor.interceptor.TraceEventMessage;
import org.apache.camel.processor.interceptor.Tracer;

/**
 * Default error handler test with trace
 *
 * @version 
 */
public class DefaultErrorHandlerOnExceptionTraceTest extends ContextTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myProcessor", new MyProcessor());
        return jndi;
    }

    public void testOk() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:trace").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        TraceEventMessage msg1 = getMockEndpoint("mock:trace").getReceivedExchanges().get(0).getIn().getBody(TraceEventMessage.class);
        TraceEventMessage msg2 = getMockEndpoint("mock:trace").getReceivedExchanges().get(1).getIn().getBody(TraceEventMessage.class);

        assertEquals("direct://start", msg1.getFromEndpointUri());
        assertEquals("ref:myProcessor", msg1.getToNode());

        assertEquals("ref:myProcessor", msg2.getPreviousNode());
        assertEquals("mock://result", msg2.getToNode());
    }

    public void testWithError() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:boom");
        mock.expectedMessageCount(1);
        getMockEndpoint("mock:trace").expectedMessageCount(4);

        template.sendBody("direct:start", "Kabom");

        assertMockEndpointsSatisfied();

        TraceEventMessage msg1 = getMockEndpoint("mock:trace").getReceivedExchanges().get(0).getIn().getBody(TraceEventMessage.class);
        TraceEventMessage msg2 = getMockEndpoint("mock:trace").getReceivedExchanges().get(1).getIn().getBody(TraceEventMessage.class);
        TraceEventMessage msg3 = getMockEndpoint("mock:trace").getReceivedExchanges().get(2).getIn().getBody(TraceEventMessage.class);
        TraceEventMessage msg4 = getMockEndpoint("mock:trace").getReceivedExchanges().get(3).getIn().getBody(TraceEventMessage.class);

        assertEquals("direct://start", msg1.getFromEndpointUri());
        assertEquals("ref:myProcessor", msg1.getToNode());

        assertEquals("ref:myProcessor", msg2.getPreviousNode());
        assertEquals("OnException[IllegalArgumentException]", msg2.getToNode());

        assertEquals("OnException[IllegalArgumentException]", msg3.getPreviousNode());
        assertEquals("log://boom", msg3.getToNode());

        assertEquals("log://boom", msg4.getPreviousNode());
        assertEquals("mock://boom", msg4.getToNode());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Tracer tracer = new Tracer();
                tracer.setDestinationUri("mock:trace");
                context.addInterceptStrategy(tracer);

                onException(IllegalArgumentException.class).handled(true).to("log:boom").to("mock:boom");

                from("direct:start").process("myProcessor").to("mock:result");
            }
        };
    }

    public static class MyProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            String body = exchange.getIn().getBody(String.class);
            if ("Kabom".equals(body)) {
                throw new IllegalArgumentException("Boom");
            }
            exchange.getIn().setBody("Bye World");
        }
    }
}