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
package org.apache.camel.processor.intercept;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.interceptor.TraceEventMessage;
import org.apache.camel.processor.interceptor.Tracer;

/**
 * @version 
 */
public class InterceptSimpleRouteTraceTest extends ContextTestSupport {

    public void testIntercept() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        getMockEndpoint("mock:intercepted").expectedMessageCount(3);
        getMockEndpoint("mock:trace").expectedMessageCount(6);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        TraceEventMessage msg1 = getMockEndpoint("mock:trace").getReceivedExchanges().get(0).getIn().getBody(TraceEventMessage.class);
        TraceEventMessage msg2 = getMockEndpoint("mock:trace").getReceivedExchanges().get(1).getIn().getBody(TraceEventMessage.class);
        TraceEventMessage msg3 = getMockEndpoint("mock:trace").getReceivedExchanges().get(2).getIn().getBody(TraceEventMessage.class);
        TraceEventMessage msg4 = getMockEndpoint("mock:trace").getReceivedExchanges().get(3).getIn().getBody(TraceEventMessage.class);
        TraceEventMessage msg5 = getMockEndpoint("mock:trace").getReceivedExchanges().get(4).getIn().getBody(TraceEventMessage.class);
        TraceEventMessage msg6 = getMockEndpoint("mock:trace").getReceivedExchanges().get(5).getIn().getBody(TraceEventMessage.class);

        assertEquals("direct://start", msg1.getFromEndpointUri());
        assertEquals("mock://intercepted", msg1.getToNode());

        assertEquals("mock://intercepted", msg2.getPreviousNode());
        assertEquals("mock://foo", msg2.getToNode());

        assertEquals("mock://foo", msg3.getPreviousNode());
        assertEquals("mock://intercepted", msg3.getToNode());

        assertEquals("mock://intercepted", msg4.getPreviousNode());
        assertEquals("mock://bar", msg4.getToNode());

        assertEquals("mock://bar", msg5.getPreviousNode());
        assertEquals("mock://intercepted", msg5.getToNode());

        assertEquals("mock://intercepted", msg6.getPreviousNode());
        assertEquals("mock://result", msg6.getToNode());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Tracer tracer = new Tracer();
                tracer.setDestinationUri("mock:trace");
                context.addInterceptStrategy(tracer);

                intercept().to("mock:intercepted");

                from("direct:start")
                    .to("mock:foo").to("mock:bar").to("mock:result");
            }
        };
    }
}