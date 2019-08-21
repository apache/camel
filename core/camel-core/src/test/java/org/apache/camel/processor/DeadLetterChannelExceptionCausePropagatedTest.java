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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class DeadLetterChannelExceptionCausePropagatedTest extends ContextTestSupport {
    protected static final RuntimeException RUNTIME_EXCEPTION = new RuntimeException("Expected exception.");
    protected String body = "<hello>world!</hello>";

    @Test
    public void testFirstFewAttemptsFail() throws Exception {
        MockEndpoint failedEndpoint = getMockEndpoint("mock:failed");
        MockEndpoint successEndpoint = getMockEndpoint("mock:success");

        failedEndpoint.expectedBodiesReceived(body);
        failedEndpoint.message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isEqualTo(RUNTIME_EXCEPTION);
        failedEndpoint.expectedMessageCount(1);

        successEndpoint.expectedMessageCount(0);

        sendBody("direct:start", body);

        assertMockEndpointsSatisfied();
        assertNull(failedEndpoint.getExchanges().get(0).getException());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(3));

                onException(RuntimeException.class).handled(true).to("mock:failed");

                from("direct:start").process(e -> {
                    throw RUNTIME_EXCEPTION;
                }).to("mock:success");
            }
        };
    }

}
