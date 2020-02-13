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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class RedeliveryPolicyOnExceptionWhileRedeliveringIssueTest extends ContextTestSupport {

    private class FirstException extends Exception {

        private static final long serialVersionUID = 1L;
    }

    private class SecondException extends Exception {

        private static final long serialVersionUID = 1L;
    }

    private class ExceptionThrowingProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            String camelRedeliveryCounter = exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER, String.class);
            int redeliveries = camelRedeliveryCounter == null ? 0 : Integer.valueOf(camelRedeliveryCounter);
            switch (redeliveries) {
                case 0:
                    throw new FirstException();
                case 1:
                    throw new SecondException();
                default:
                    break; // no-op
            }
        }
    }

    @Test
    public void testMessageShouldGoToError() throws Exception {
        String msg = "payload";

        getMockEndpoint("mock:destination").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:error").expectedBodiesReceived(msg);

        template.sendBody("direct:source", msg);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:source").onException(FirstException.class).redeliveryDelay(0).maximumRedeliveries(-1).handled(true).end().onException(SecondException.class)
                        .handled(true).to("mock:error").end().process(new ExceptionThrowingProcessor()).to("mock:destination");
            }
        };
    }
}
