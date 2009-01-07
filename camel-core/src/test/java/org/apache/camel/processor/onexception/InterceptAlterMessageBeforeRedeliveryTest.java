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
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for testing possibility to modify exchange before redelivering
 */
public class InterceptAlterMessageBeforeRedeliveryTest extends ContextTestSupport {

    static int counter;

    public void testInterceptAlterMessageBeforeRedelivery() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World123");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testInterceptAlterMessageWithHeadersBeforeRedelivery() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World123");
        mock.expectedHeaderReceived("foo", "123");

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "123");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        counter = 0;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // to execute unit test much faster we dont use delay between redeliveries
                errorHandler(deadLetterChannel("mock:error").delay(0L));

                // START SNIPPET: e1
                // we configure an interceptor that is triggered when the redelivery flag
                // has been set on an exchange
                intercept().when(header("org.apache.camel.Redelivered").isNotNull()).
                        process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                // the message is being redelivered so we can alter it

                                // we just append the redelivery counter to the body
                                // you can of course do all kind of stuff instead
                                String body = exchange.getIn().getBody(String.class);
                                int count = exchange.getIn().getHeader("org.apache.camel.RedeliveryCounter", Integer.class);

                                exchange.getIn().setBody(body + count);
                            }
                        });
                // END SNIPPET: e1


                from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        // force some error so Camel will do redelivery
                        if (++counter <= 3) {
                            throw new MyTechnicalException("Forced by unit test");
                        }
                    }
                }).to("mock:result");

            }
        };
    }


}