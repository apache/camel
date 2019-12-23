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

import org.apache.camel.CamelExchangeException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Unit test for useOriginalBody option on DeadLetterChannel
 */
public class OnExceptionUseOriginalBodyTest extends ContextTestSupport {

    @Test
    public void testUseOriginalBody() throws Exception {
        MockEndpoint dead = getMockEndpoint("mock:a");
        dead.expectedBodiesReceived("Hello");

        template.sendBody("direct:a", "Hello");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDoNotUseOriginalBody() throws Exception {
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedBodiesReceived("Hello World");

        template.sendBody("direct:b", "Hello");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // will not use original exchange
                errorHandler(deadLetterChannel("mock:dead").disableRedelivery().logStackTrace(false).redeliveryDelay(0));

                onException(IllegalArgumentException.class).maximumRedeliveries(2).useOriginalMessage().handled(true).to("mock:a");

                from("direct:a").setBody(body().append(" World")).process(new MyThrowProcessor(false));

                from("direct:b").setBody(body().append(" World")).process(new MyThrowProcessor(true));
            }
        };
    }

    public static class MyThrowProcessor implements Processor {

        private boolean camelException;

        public MyThrowProcessor() {
        }

        public MyThrowProcessor(boolean camelException) {
            this.camelException = camelException;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            assertEquals("Hello World", exchange.getIn().getBody(String.class));
            if (camelException) {
                throw new CamelExchangeException("I cannot do it", exchange);
            } else {
                throw new IllegalArgumentException("Forced");
            }
        }

        public boolean isCamelException() {
            return camelException;
        }

        public void setCamelException(boolean camelException) {
            this.camelException = camelException;
        }
    }

}
