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
package org.apache.camel.processor.interceptor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class DefaultTraceEventMessageCausedByExceptionTest extends ContextTestSupport {

    public void testCausedByException() throws Exception {
        getMockEndpoint("mock:handled").expectedMessageCount(1);

        MockEndpoint traced = getMockEndpoint("mock:traced");
        traced.expectedMessageCount(4);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        DefaultTraceEventMessage em1 = traced.getReceivedExchanges().get(0).getIn().getBody(DefaultTraceEventMessage.class);
        DefaultTraceEventMessage em2 = traced.getReceivedExchanges().get(1).getIn().getBody(DefaultTraceEventMessage.class);
        DefaultTraceEventMessage em3 = traced.getReceivedExchanges().get(2).getIn().getBody(DefaultTraceEventMessage.class);
        DefaultTraceEventMessage em4 = traced.getReceivedExchanges().get(3).getIn().getBody(DefaultTraceEventMessage.class);

        assertNotNull(em1);
        assertNotNull(em2);
        assertNotNull(em3);
        assertNotNull(em4);

        assertNull(em1.getCausedByException());
        assertNull(em2.getCausedByException());
        assertEquals("java.lang.IllegalArgumentException: Forced", em3.getCausedByException());
        assertEquals("java.lang.IllegalArgumentException: Forced", em4.getCausedByException());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Tracer tracer = Tracer.createTracer(context);
                tracer.setDestinationUri("mock:traced");
                context.addInterceptStrategy(tracer);

                onException(Exception.class)
                    .handled(true)
                    .to("mock:handled");

                from("direct:start").to("mock:foo").throwException(new IllegalArgumentException("Forced"));
            }
        };
    }
}
