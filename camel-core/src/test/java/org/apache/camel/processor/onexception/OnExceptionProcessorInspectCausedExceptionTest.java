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
package org.apache.camel.processor.onexception;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test for using a processor to peek the caused exception
 */
public class OnExceptionProcessorInspectCausedExceptionTest extends ContextTestSupport {

    public void testInspectExceptionByProcessor() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:myerror").expectedMessageCount(1);

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should throw exception");
        } catch (Exception e) {
            // ok
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(3));

                // START SNIPPET: e1
                // here we register exception cause for MyFunctionException
                // when this exception occur we want it to be processed by our processor
                onException(MyFunctionalException.class).process(new MyFunctionFailureHandler()).stop();
                // END SNIPPET: e1

                from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        throw new MyFunctionalException("Sorry you cannot do this");
                    }
                });
            }
        };
    }

    // START SNIPPET: e2
    public static class MyFunctionFailureHandler implements Processor {

        public void process(Exchange exchange) throws Exception {
            // the caused by exception is stored in a property on the exchange
            Throwable caused = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
            assertNotNull(caused);
            // here you can do what you want, but Camel regard this exception as handled, and
            // this processor as a failurehandler, so it wont do redeliveries. So this is the
            // end of this route. But if we want to route it somewhere we can just get a
            // producer template and send it.

            // send it to our mock endpoint
            exchange.getContext().createProducerTemplate().send("mock:myerror", exchange);
        }
    }
    // END SNIPPET: e2
}