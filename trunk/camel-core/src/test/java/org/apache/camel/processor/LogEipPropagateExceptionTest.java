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

/**
 * Testing CAMEL-4388
 */
public class LogEipPropagateExceptionTest extends ContextTestSupport {

    public void testFailure() throws Exception {
        getMockEndpoint("mock:handleFailure").whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                throw new RuntimeException("TEST EXCEPTION");
            }
        });

        getMockEndpoint("mock:exceptionFailure").expectedMessageCount(1);

        sendBody("direct:startFailure", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testSuccess() throws Exception {
        getMockEndpoint("mock:handleSuccess").whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                throw new RuntimeException("TEST EXCEPTION");
            }
        });

        getMockEndpoint("mock:exceptionSuccess").expectedMessageCount(1);

        sendBody("direct:startSuccess", "Hello World");
        assertMockEndpointsSatisfied();

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:startFailure")
                    .onException(Throwable.class)
                        .to("mock:exceptionFailure")
                        .end()
                    .to("direct:handleFailure")
                    .to("mock:resultFailure");

                from("direct:handleFailure")
                    .errorHandler(noErrorHandler())
                    .log("FAULTY LOG")
                    .to("mock:handleFailure");

                from("direct:startSuccess")
                    .onException(Throwable.class)
                        .to("mock:exceptionSuccess")
                        .end()
                    .to("direct:handleSuccess")
                    .to("mock:resultSuccess");

                from("direct:handleSuccess")
                    .errorHandler(noErrorHandler())
                    .to("mock:handleSuccess");
            }
        };
    }

}
