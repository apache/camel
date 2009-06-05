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
package org.apache.camel.processor.async;

import java.util.concurrent.Future;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.WaitForTaskToComplete;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version $Revision$
 */
public class AsyncRouteNoWaitTest extends ContextTestSupport {

    private static String route = "";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        route = "";
    }

    public void testAsyncNoWaitRouteExchange() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        // send an in out to the direct endpoint using the classic API
        Exchange exchange = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOut);
                exchange.getIn().setBody("Hello");
            }
        });

        // we should run before the async processor that sets B
        route += "A";

        // as it turns into a async route later we get a Future in the IN body
        Object out = exchange.getOut().getBody();
        assertIsInstanceOf(Future.class, out);

        // cast to future
        Future future = (Future) out;

        assertMockEndpointsSatisfied();

        assertEquals("AB", route);

        // get the response from the future
        String response = context.getTypeConverter().convertTo(String.class, future);
        assertEquals("Bye World", response);
    }

    public void testAsyncNoWaitRoute() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        // send a request reply to the direct start endpoint
        Object out = template.requestBody("direct:start", "Hello");

        // we should run before the async processor that sets B
        route += "A";

        // as it turns into a async route later we get a Future as response
        assertIsInstanceOf(Future.class, out);

        // cast to future
        Future future = (Future) out;

        assertMockEndpointsSatisfied();

        assertEquals("AB", route);

        // get the response from the future
        String response = context.getTypeConverter().convertTo(String.class, future);
        assertEquals("Bye World", response);
    }

    public void testAsyncRouteNoWaitWithTypeConverted() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        // send a request reply to the direct start endpoint, but will use
        // future type converter that will wait for the response, even though the async
        // is set to not wait. As the type converter will wait for us
        String response = template.requestBody("direct:start", "Hello", String.class);

        // we should wait for the async response as we ask for the result as a String body
        route += "A";

        assertMockEndpointsSatisfied();

        assertEquals("Bye World", response);
        assertEquals("BA", route);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // we start this route async
                from("direct:start")
                            // we play a bit with the message
                        .transform(body().append(" World"))
                            // now turn the route into async from this point forward
                            // the caller will have a Future<Exchange> returned as response in OUT
                            // to be used to grap the async response when he fell like it
                            // we do not want to wait for tasks to be complete so we instruct Camel
                            // to not wait, and therefore Camel returns the Future<Exchange> handle we
                            // can use to get the result when we want
                        .threads().waitForTaskToComplete(WaitForTaskToComplete.Never)
                            // from this point forward this is the async route doing its work
                            // so we do a bit of delay to simulate heavy work that takes time
                        .to("mock:foo")
                        .delay(100)
                            // and we also work with the message so we can prepare a response
                        .process(new MyProcessor())
                            // and we use mocks for unit testing
                        .to("mock:result");
            }
        };
    }

    public static class MyProcessor implements Processor {

        public MyProcessor() {
        }

        public void process(Exchange exchange) throws Exception {
            route += "B";
            assertEquals("Hello World", exchange.getIn().getBody());
            exchange.getOut().setBody("Bye World");
        }
    }
}
