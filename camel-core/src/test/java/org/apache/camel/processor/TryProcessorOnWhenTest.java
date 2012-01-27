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

import org.apache.camel.CamelExchangeException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test for try .. handle with onWhen.
 */
public class TryProcessorOnWhenTest extends ContextTestSupport {

    public void testIOException() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:catch").expectedMessageCount(1);
        getMockEndpoint("mock:catchCamel").expectedMessageCount(0);
        getMockEndpoint("mock:finally").expectedMessageCount(1);

        template.sendBody("direct:start", "Damn IO");

        assertMockEndpointsSatisfied();
    }

    public void testIllegalStateException() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:catch").expectedMessageCount(1);
        getMockEndpoint("mock:catchCamel").expectedMessageCount(0);
        getMockEndpoint("mock:finally").expectedMessageCount(1);

        template.sendBody("direct:start", "Damn State");

        assertMockEndpointsSatisfied();
    }

    public void testCamelException() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:catch").expectedMessageCount(0);
        getMockEndpoint("mock:catchCamel").expectedMessageCount(1);
        getMockEndpoint("mock:finally").expectedMessageCount(1);

        template.sendBody("direct:start", "Camel");

        assertMockEndpointsSatisfied();
    }

    public void testOtherBug() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:catch").expectedMessageCount(0);
        getMockEndpoint("mock:catchCamel").expectedMessageCount(0);
        getMockEndpoint("mock:finally").expectedMessageCount(1);

        try {
            template.sendBody("direct:start", "Other Bug");
            fail("Should have thrown a RuntimeCamelException");
        } catch (RuntimeCamelException e) {
            assertIsInstanceOf(IllegalStateException.class, e.getCause());
            assertEquals("Other Bug", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    public void testOk() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:catch").expectedMessageCount(0);
        getMockEndpoint("mock:finally").expectedMessageCount(1);

        sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1
                from("direct:start")
                    // here is our try where we try processing the exchange in the route below if it fails
                    // we can catch it below, just like regular try .. catch .. finally in Java
                    .doTry()
                        .process(new ProcessorFail())
                        .to("mock:result")
                    // here we catch the following 2 exceptions but only if
                    // the onWhen predicate matches, eg if the exception messsage
                    // conatins the string word Damn
                    .doCatch(IOException.class, IllegalStateException.class)
                        .onWhen(exceptionMessage().contains("Damn"))
                        .to("mock:catch")
                    // another catch for CamelExchangeException that does not have any onWhen predicate
                    .doCatch(CamelExchangeException.class)
                        .to("mock:catchCamel")
                    // and the finally that is always processed
                    .doFinally()
                        .to("mock:finally")
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
            } else if ("Other Bug".equals(body)) {
                throw new IllegalStateException("Other Bug");
            } else if ("Camel".equals(body)) {
                throw new CamelExchangeException("Sorry old Camel", exchange);
            }
        }
    }

}