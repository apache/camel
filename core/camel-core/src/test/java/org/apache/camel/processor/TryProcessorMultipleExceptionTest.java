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
package org.apache.camel.processor;

import java.io.IOException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * Unit test for try .. handle with multiple exceptions.
 */
public class TryProcessorMultipleExceptionTest extends ContextTestSupport {

    @Test
    public void testIOException() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:catch").expectedMessageCount(1);
        getMockEndpoint("mock:finally").expectedMessageCount(1);

        sendBody("direct:start", "Damn IO");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testIllegalStateException() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:catch").expectedMessageCount(1);
        getMockEndpoint("mock:finally").expectedMessageCount(1);

        sendBody("direct:start", "Damn State");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOk() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:catch").expectedMessageCount(0);
        getMockEndpoint("mock:finally").expectedMessageCount(1);

        sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @SuppressWarnings("unchecked")
            public void configure() {
                // START SNIPPET: e1
                from("direct:start").doTry().process(new ProcessorFail()).to("mock:result").doCatch(IOException.class, IllegalStateException.class).to("mock:catch").doFinally()
                    .to("mock:finally").end();
                // END SNIPPET: e1
            }
        };
    }

    public static class ProcessorFail implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            String body = exchange.getIn().getBody(String.class);
            if ("Damn IO".equals(body)) {
                throw new IOException("Damn IO");
            } else if ("Damn State".equals(body)) {
                throw new IllegalStateException("Damn State");
            }
        }
    }

}
