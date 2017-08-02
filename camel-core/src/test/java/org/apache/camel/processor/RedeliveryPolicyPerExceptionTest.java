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

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class RedeliveryPolicyPerExceptionTest extends ContextTestSupport {
    protected MockEndpoint a;
    protected MockEndpoint b;

    public void testUsingCustomExceptionHandlerAndOneRedelivery() throws Exception {
        a.expectedMessageCount(1);

        sendBody("direct:start", "a");

        MockEndpoint.assertIsSatisfied(a, b);

        List<Exchange> list = a.getReceivedExchanges();
        assertTrue("List should not be empty!", !list.isEmpty());
        Exchange exchange = list.get(0);
        Message in = exchange.getIn();
        log.info("Found message with headers: " + in.getHeaders());

        assertMessageHeader(in, Exchange.REDELIVERY_COUNTER, 2);
        assertMessageHeader(in, Exchange.REDELIVERY_MAX_COUNTER, 2);
        assertMessageHeader(in, Exchange.REDELIVERED, true);
    }

    public void testUsingCustomExceptionHandlerWithNoRedeliveries() throws Exception {
        b.expectedMessageCount(1);

        sendBody("direct:start", "b");

        MockEndpoint.assertIsSatisfied(a, b);

        List<Exchange> list = b.getReceivedExchanges();
        assertTrue("List should not be empty!", !list.isEmpty());
        Exchange exchange = list.get(0);
        Message in = exchange.getIn();
        log.info("Found message with headers: " + in.getHeaders());

        assertMessageHeader(in, Exchange.REDELIVERY_COUNTER, 0);
        assertMessageHeader(in, Exchange.REDELIVERY_MAX_COUNTER, null);
        assertMessageHeader(in, Exchange.REDELIVERED, false);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        a = resolveMandatoryEndpoint("mock:a", MockEndpoint.class);
        b = resolveMandatoryEndpoint("mock:b", MockEndpoint.class);
    }

    protected RouteBuilder createRouteBuilder() {

        final Processor processor = new Processor() {
            public void process(Exchange exchange) {
                if ("b".equals(exchange.getIn().getBody())) {
                    throw new NullPointerException("MyCustomException");
                } else {
                    throw new IllegalArgumentException("MyCustomException");
                }
            }
        };

        return new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(3));

                onException(IllegalArgumentException.class).redeliveryDelay(0).maximumRedeliveries(2).to("mock:a");
                onException(NullPointerException.class).to("mock:b");

                from("direct:start").process(processor);
            }
        };
    }
}