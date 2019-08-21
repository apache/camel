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
package org.apache.camel.processor.onexception;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.stream.InputStreamCache;
import org.junit.Test;

public class OnExceptionUseOriginalMessageTest extends ContextTestSupport {

    private static final String HELLO_WORLD = "Hello World";

    private static final String TEST_STRING = "<firstName>James</firstName>";

    @Test
    public void testOnExceptionError() throws Exception {
        getMockEndpoint("mock:middle").expectedBodiesReceived(HELLO_WORLD);
        getMockEndpoint("mock:middle").message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isInstanceOf(IllegalArgumentException.class);
        getMockEndpoint("mock:end").expectedBodiesReceived(HELLO_WORLD);
        getMockEndpoint("mock:end").message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isInstanceOf(IllegalArgumentException.class);

        template.sendBody("direct:a", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOnExceptionStreamReset() throws Exception {

        getMockEndpoint("mock:middle").expectedMessageCount(1);
        getMockEndpoint("mock:middle").message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isInstanceOf(IllegalArgumentException.class);
        getMockEndpoint("mock:end").expectedMessageCount(1);
        getMockEndpoint("mock:end").message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isInstanceOf(IllegalArgumentException.class);

        InputStreamCache cache = new InputStreamCache(TEST_STRING.getBytes());

        template.sendBody("direct:a", cache);

        assertMockEndpointsSatisfied();

        // To make sure we can read something from the InputStream
        String result = getMockEndpoint("mock:end").getExchanges().get(0).getIn().getBody(String.class);
        assertTrue(result.contains("<firstName>James</firstName>"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                onException(IllegalArgumentException.class).useOriginalMessage().handled(true).to("seda:test");

                from("direct:a").setExchangePattern(ExchangePattern.InOut).process(new MyProcessor());

                from("seda:test").to("mock:middle").to("mock:end");

            }
        };
    }

    public static class MyProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            String request = exchange.getIn().getBody(String.class);
            if (!request.equals(HELLO_WORLD)) {
                exchange.getIn().setBody(HELLO_WORLD);
            }
            // set the out message
            exchange.getMessage().setBody("Error body");
            throw new IllegalArgumentException("Get a wrong message");
        }
    }
}
