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

import java.io.IOException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test for try .. catch with handled false.
 */
public class TryProcessorHandledTest extends ContextTestSupport {

    public void testOk() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:error").expectedMessageCount(0);

        sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testIllegalStateException() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        template.sendBody("direct:start", "Damn State");

        assertMockEndpointsSatisfied();
    }

    public void testIOException() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:io").expectedMessageCount(1);

        try {
            template.sendBody("direct:start", "Damn IO");
            fail("Should have thrown a RuntimeCamelException");
        } catch (RuntimeCamelException e) {
            assertIsInstanceOf(IOException.class, e.getCause());
            assertEquals("Damn IO", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @SuppressWarnings("deprecation")
            public void configure() {
                // START SNIPPET: e1
                from("direct:start")
                    // here is our try where we try processing the exchange in the route below if it fails
                    // we can catch it below, just like regular try .. catch .. finally in Java
                    .doTry()
                        .process(new ProcessorFail())
                        .to("mock:result")
                    // catch IOExcption that we do not want to handle, eg the caller should get the error back
                    .doCatch(IOException.class)
                        // mark this as NOT handled, eg the caller will also get the exception
                        .handled(false)
                        .to("mock:io")
                    .doCatch(Exception.class)
                        // and catch all other exceptions
                        // they are handled by default (ie handled = true)
                        .to("mock:error")
                    // here the try block ends
                    .end();
                // END SNIPPET: e1
            }
        };
    }

    public static class ProcessorFail implements Processor {
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