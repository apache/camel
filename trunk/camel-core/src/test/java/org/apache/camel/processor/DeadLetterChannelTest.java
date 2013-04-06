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
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class DeadLetterChannelTest extends ContextTestSupport {
    protected Endpoint startEndpoint;
    protected MockEndpoint deadEndpoint;
    protected MockEndpoint successEndpoint;
    protected int failUntilAttempt = 2;
    protected String body = "<hello>world!</hello>";

    public void testFirstFewAttemptsFail() throws Exception {
        successEndpoint.expectedBodiesReceived(body);
        successEndpoint.message(0).header(Exchange.REDELIVERED).isEqualTo(true);
        successEndpoint.message(0).header(Exchange.REDELIVERY_COUNTER).isEqualTo(1);
        successEndpoint.message(0).header(Exchange.REDELIVERY_MAX_COUNTER).isEqualTo(2);

        deadEndpoint.expectedMessageCount(0);

        sendBody("direct:start", body);

        assertMockEndpointsSatisfied();
    }

    public void testLotsOfAttemptsFail() throws Exception {
        failUntilAttempt = 5;

        deadEndpoint.expectedBodiesReceived(body);
        // no traces of redelivery as the dead letter channel will handle the exception when moving the DLQ
        deadEndpoint.message(0).header(Exchange.REDELIVERED).isNull();
        deadEndpoint.message(0).header(Exchange.REDELIVERY_COUNTER).isNull();
        deadEndpoint.message(0).header(Exchange.REDELIVERY_MAX_COUNTER).isNull();
        successEndpoint.expectedMessageCount(0);

        sendBody("direct:start", body);

        assertMockEndpointsSatisfied();

        Throwable t = deadEndpoint.getExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        assertNotNull("Should have been a cause property", t);
        assertTrue(t instanceof RuntimeException);
        assertEquals("Failed to process due to attempt: 3 being less than: 5", t.getMessage());

        // must be InOnly
        Exchange dead = deadEndpoint.getReceivedExchanges().get(0);
        assertEquals(ExchangePattern.InOnly, dead.getPattern());
    }

    public void testLotsOfAttemptsFailInOut() throws Exception {
        failUntilAttempt = 5;

        deadEndpoint.expectedBodiesReceived(body);
        // no traces of redelivery as the dead letter channel will handle the exception when moving the DLQ
        deadEndpoint.message(0).header(Exchange.REDELIVERED).isNull();
        deadEndpoint.message(0).header(Exchange.REDELIVERY_COUNTER).isNull();
        deadEndpoint.message(0).header(Exchange.REDELIVERY_MAX_COUNTER).isNull();
        successEndpoint.expectedMessageCount(0);

        template.requestBody("direct:start", body);

        assertMockEndpointsSatisfied();

        Throwable t = deadEndpoint.getExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        assertNotNull("Should have been a cause property", t);
        assertTrue(t instanceof RuntimeException);
        assertEquals("Failed to process due to attempt: 3 being less than: 5", t.getMessage());

        // must be InOnly
        Exchange dead = deadEndpoint.getReceivedExchanges().get(0);
        assertEquals(ExchangePattern.InOnly, dead.getPattern());
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        deadEndpoint = getMockEndpoint("mock:failed");
        successEndpoint = getMockEndpoint("mock:success");
    }

    protected RouteBuilder createRouteBuilder() {
        final Processor processor = new Processor() {
            public void process(Exchange exchange) {
                Integer counter = exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER, Integer.class);
                int attempt = (counter == null) ? 1 : counter + 1;
                if (attempt < failUntilAttempt) {
                    throw new RuntimeException("Failed to process due to attempt: " + attempt
                                               + " being less than: " + failUntilAttempt);
                }
            }
        };

        return new RouteBuilder() {
            public void configure() {
                from("direct:start").errorHandler(
                    deadLetterChannel("mock:failed").maximumRedeliveries(2)
                        .redeliveryDelay(50)
                        .loggingLevel(LoggingLevel.DEBUG)

                ).process(processor).to("mock:success");
            }
        };
    }

}
