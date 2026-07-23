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

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that decrementRedeliveryCounter correctly uses the internal field when the failure handler receives the
 * exchange after removeHeaders("*") was called in a child route.
 */
public class RedeliverToSubRouteRemoveHeadersDecrementTest extends ContextTestSupport {

    private static final AtomicInteger failureCounter = new AtomicInteger(-1);

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        failureCounter.set(-1);
    }

    @Test
    public void testDecrementCounterWithRemoveHeaders() throws Exception {
        getMockEndpoint("mock:failure").expectedMessageCount(1);

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        try {
            template.sendBody("direct:start", "Hello World");
        } catch (Exception e) {
            // expected: handled(false) propagates the exception
        }

        assertTrue(notify.matches(10, TimeUnit.SECONDS), "Exchange did not complete in time");

        assertMockEndpointsSatisfied();

        // With maximumRedeliveries(3), redeliveryCounter reaches 4 (initial + 3 redeliveries),
        // then decrementRedeliveryCounter brings it to 3. Without the fix, removeHeaders("*")
        // in the child route would cause the header to be null and the counter would be set to 0.
        assertEquals(3, failureCounter.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(IOException.class)
                        .maximumRedeliveries(3)
                        .redeliveryDelay(0)
                        .handled(false)
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) {
                                Integer counter = exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER, Integer.class);
                                if (counter != null) {
                                    failureCounter.set(counter);
                                }
                            }
                        })
                        .to("mock:failure");

                from("direct:start")
                        .to("direct:sub");

                from("direct:sub")
                        .errorHandler(noErrorHandler())
                        .removeHeaders("*")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                throw new IOException("Forced");
                            }
                        });
            }
        };
    }
}
