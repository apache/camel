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
package org.apache.camel.component.scheduler;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SchedulerNoPolledMessagesTest extends ContextTestSupport {

    @Test
    public void testSchedulerNoPolledMessages() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(3);

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);

        // Verify backoff by checking timestamps of the first 3 received
        // messages directly.  We do NOT use the arrives().afterPrevious()
        // API here because it has a subtle race: afterPrevious() actually
        // compares with the NEXT message's timestamp when one has already
        // arrived, so a 4th message arriving ~100 ms after the 3rd can
        // violate the lower bound intended for the backoff gap (msg 1→2).
        List<Exchange> received = mock.getReceivedExchanges();
        long t0 = received.get(0).getProperty(Exchange.RECEIVED_TIMESTAMP, Date.class).getTime();
        long t1 = received.get(1).getProperty(Exchange.RECEIVED_TIMESTAMP, Date.class).getTime();
        long t2 = received.get(2).getProperty(Exchange.RECEIVED_TIMESTAMP, Date.class).getTime();

        // The first 2 messages fire quickly (~100 ms scheduler interval)
        long gap01 = t1 - t0;
        assertTrue(gap01 <= 2000,
                "Gap between message 0 and 1 should be <= 2000 ms, was " + gap01 + " ms");

        // After 2 idle polls the backoff kicks in
        // (backoffMultiplier=10 × delay=100 ms ≈ 1000 ms)
        long gap12 = t2 - t1;
        assertTrue(gap12 >= 200,
                "Backoff should cause gap >= 200 ms between message 1 and 2, was " + gap12 + " ms");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("scheduler://foo?delay=100&backoffMultiplier=10&backoffIdleThreshold=2&poolSize=2")
                        .log("Fired scheduler").process(new Processor() {
                            @Override
                            public void process(Exchange exchange) {
                                // force no messages to be polled which should affect
                                // the scheduler to think its idle
                                exchange.setProperty(Exchange.SCHEDULER_POLLED_MESSAGES, false);
                            }
                        }).to("mock:result");
            }
        };
    }

}
