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
package org.apache.camel.impl.event;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventNotifierExchangeSentParallelTest extends EventNotifierExchangeSentTest {

    @Override
    @Test
    public void testExchangeSentRecipient() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:foo", "Hello World", "foo", "direct:cool,direct:start");

        // wait for the message to be fully done using oneExchangeDone
        assertMockEndpointsSatisfied();
        assertTrue(oneExchangeDone.matchesWaitTime());

        // stop Camel to let all the events complete
        context.stop();

        assertTrue(events.size() >= 11, "Should be 11 or more, was: " + events.size());

        // we run parallel so just assert we got 6 sending and 6 sent events
        int sent = 0;
        int sending = 0;
        for (CamelEvent event : events) {
            if (event instanceof ExchangeSendingEvent) {
                sending++;
            } else {
                sent++;
            }
        }

        assertTrue(sending >= 5, "There should be 5 or more, was " + sending);
        assertTrue(sent >= 5, "There should be 5 or more, was " + sent);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("log:foo").to("direct:bar").to("mock:result");

                from("direct:bar").delay(500);

                from("direct:foo").recipientList(header("foo")).parallelProcessing();

                from("direct:cool").delay(1000);

                from("direct:tap").wireTap("log:foo").to("mock:result");
            }
        };
    }

}
