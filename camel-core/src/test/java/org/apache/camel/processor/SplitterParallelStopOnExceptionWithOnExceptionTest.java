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

/**
 * @version 
 */
public class SplitterParallelStopOnExceptionWithOnExceptionTest extends ContextTestSupport {

    public void testSplitStopOnExceptionOk() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedBodiesReceivedInAnyOrder("Hello World", "Bye World");
        getMockEndpoint("mock:handled").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello World,Bye World");

        assertMockEndpointsSatisfied();
    }

    public void testSplitStopOnExceptionStop1() throws Exception {
        // we run parallel so the tasks could haven been processed so we get 0 or more messages
        getMockEndpoint("mock:split").expectedMinimumMessageCount(0);
        getMockEndpoint("mock:handled").expectedMessageCount(1);

        String out = template.requestBody("direct:start", "Kaboom,Hello World,Bye World", String.class);
        assertEquals("Damn Forced", out);

        assertMockEndpointsSatisfied();
    }

    public void testSplitStopOnExceptionStop2() throws Exception {
        // we run parallel so the tasks could haven been processed so we get 0 or more messages
        getMockEndpoint("mock:split").expectedMinimumMessageCount(0);
        getMockEndpoint("mock:handled").expectedMessageCount(1);

        String out = template.requestBody("direct:start", "Hello World,Kaboom,Bye World", String.class);
        assertEquals("Damn Forced", out);

        assertMockEndpointsSatisfied();
    }

    public void testSplitStopOnExceptionStop3() throws Exception {
        // we run parallel so the tasks could haven been processed so we get 0 or more messages
        getMockEndpoint("mock:split").expectedMinimumMessageCount(0);
        getMockEndpoint("mock:handled").expectedMessageCount(1);

        String out = template.requestBody("direct:start", "Hello World,Bye World,Kaboom", String.class);
        assertEquals("Damn Forced", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class)
                    .handled(true)
                    .to("mock:handled")
                    .transform(simple("Damn ${exception.message}"));

                from("direct:start")
                    .split(body().tokenize(",")).stopOnException().parallelProcessing()
                        .process(new MyProcessor())
                        .to("mock:split");
            }
        };
    }

    public static class MyProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            String body = exchange.getIn().getBody(String.class);
            if ("Kaboom".equals(body)) {
                throw new IllegalArgumentException("Forced");
            }
        }
    }
}
