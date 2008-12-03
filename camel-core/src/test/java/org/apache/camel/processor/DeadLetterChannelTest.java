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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.LoggingLevel;

/**
 * @version $Revision$
 */
public class DeadLetterChannelTest extends ContextTestSupport {
    protected Endpoint<Exchange> startEndpoint;
    protected MockEndpoint deadEndpoint;
    protected MockEndpoint successEndpoint;
    protected int failUntilAttempt = 2;
    protected String body = "<hello>world!</hello>";

    public void testFirstFewAttemptsFail() throws Exception {
        successEndpoint.expectedBodiesReceived(body);
        successEndpoint.message(0).header(DeadLetterChannel.REDELIVERED).isEqualTo(true);
        successEndpoint.message(0).header(DeadLetterChannel.REDELIVERY_COUNTER).isEqualTo(1);

        deadEndpoint.expectedMessageCount(0);

        sendBody("direct:start", body);

        assertMockEndpointsSatisfied();
    }

    public void testLotsOfAttemptsFail() throws Exception {
        failUntilAttempt = 5;

        deadEndpoint.expectedBodiesReceived(body);
        deadEndpoint.message(0).header(DeadLetterChannel.REDELIVERED).isEqualTo(true);
        deadEndpoint.message(0).header(DeadLetterChannel.REDELIVERY_COUNTER).isEqualTo(2);
        successEndpoint.expectedMessageCount(0);

        sendBody("direct:start", body);

        assertMockEndpointsSatisfied();

        Throwable t = deadEndpoint.getExchanges().get(0).getProperty(DeadLetterChannel.EXCEPTION_CAUSE_PROPERTY, Throwable.class);
        assertNotNull("Should have been a cause property", t);
        assertTrue(t instanceof RuntimeException);
        assertEquals("Failed to process due to attempt: 3 being less than: 5", t.getMessage());

        Throwable t2 = deadEndpoint.getExchanges().get(0).getException();
        assertEquals(t, t2);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        deadEndpoint = getMockEndpoint("mock:failed");
        successEndpoint = getMockEndpoint("mock:success");
    }

    protected RouteBuilder createRouteBuilder() {
        final Processor processor = new AsyncProcessor() {
            public void process(Exchange exchange) {
                Integer counter = exchange.getIn().getHeader(DeadLetterChannel.REDELIVERY_COUNTER,
                                                             Integer.class);
                int attempt = (counter == null) ? 1 : counter + 1;
                if (attempt < failUntilAttempt) {
                    throw new RuntimeException("Failed to process due to attempt: " + attempt
                                               + " being less than: " + failUntilAttempt);
                }
            }

            public boolean process(Exchange exchange, AsyncCallback callback) {                
                Integer counter = exchange.getIn().getHeader(DeadLetterChannel.REDELIVERY_COUNTER,
                                                             Integer.class);
                int attempt = (counter == null) ? 1 : counter + 1;
                if (attempt > 1) {
                    assertEquals("Now we should use TimerThread to call the process", Thread.currentThread().getName(), "Timer-0");
                }
                
                if (attempt < failUntilAttempt) {
                    // we can't throw the exception here , or the callback will not be invoked.
                    exchange.setException(new RuntimeException("Failed to process due to attempt: " + attempt
                                               + " being less than: " + failUntilAttempt));
                }
                callback.done(false);
                return false;
            }
        };

        return new RouteBuilder() {
            public void configure() {
                from("direct:start").errorHandler(
                    deadLetterChannel("mock:failed").maximumRedeliveries(2)

                        .initialRedeliveryDelay(1000)

                        .loggingLevel(LoggingLevel.DEBUG)

                ).process(processor).to("mock:success");
            }
        };
    }

}
