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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for testing possibility to modify exchange before redelivering specific
 * per on exception
 */
public class DeadLetterChannelOnExceptionOnRedeliveryTest extends ContextTestSupport {

    static int counter;

    public void testGlobalOnRedelivery() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World3");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testRouteSpecificOnRedelivery() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.message(0).header("Timeout").isEqualTo(5000);

        template.sendBody("direct:io", "Hello World");

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
                // START SNIPPET: e1
                // when we redeliver caused by an IOException we want to do some special
                // code before the redeliver attempt
                onException(IOException.class)
                        // try to redeliver at most 3 times
                        .maximumRedeliveries(3)
                        // setting delay to zero is just to make unit testing faster
                        .redeliveryDelay(0)
                        .onRedelivery(new MyIORedeliverProcessor());
                // END SNIPPET: e1

                // START SNIPPET: e2
                // we configure our Dead Letter Channel to invoke
                // MyRedeliveryProcessor before a redelivery is
                // attempted. This allows us to alter the message before
                errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(5)
                        .onRedelivery(new MyRedeliverProcessor())
                        // setting delay to zero is just to make unit testing faster
                        .redeliveryDelay(0L));
                // END SNIPPET: e2


                from("direct:start").process(new ThrowExceptionProcessor()).to("mock:result");

                from("direct:io").process(new ThrowIOExceptionProcessor()).to("mock:result");
            }
        };
    }

    // START SNIPPET: e3
    // This is our processor that is executed before every redelivery attempt
    // here we can do what we want in the java code, such as altering the message
    public static class MyRedeliverProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            // the message is being redelivered so we can alter it

            // we just append the redelivery counter to the body
            // you can of course do all kind of stuff instead
            String body = exchange.getIn().getBody(String.class);
            int count = exchange.getIn().getHeader("CamelRedeliveryCounter", Integer.class);

            exchange.getIn().setBody(body + count);
        }
    }
    // END SNIPPET: e3

    // START SNIPPET: e4
    // This is our processor that is executed before IOException redeliver attempt
    // here we can do what we want in the java code, such as altering the message

    public static class MyIORedeliverProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            // just for show and tell, here we set a special header to instruct
            // the receive a given timeout value
            exchange.getIn().setHeader("Timeout", 5000);
        }
    }
    // END SNIPPET: e4

    public static class ThrowExceptionProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            // force some error so Camel will do redelivery
            if (++counter <= 3) {
                throw new IllegalArgumentException("Forced by unit test");
            }

        }
    }

    public static class ThrowIOExceptionProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            // force some error so Camel will do redelivery
            if (++counter <= 3) {
                throw new IOException("Cannot connect");
            }

        }
    }


}
