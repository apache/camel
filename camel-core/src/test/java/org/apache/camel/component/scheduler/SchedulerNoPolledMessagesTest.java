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
package org.apache.camel.component.scheduler;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class SchedulerNoPolledMessagesTest extends ContextTestSupport {

    public void testSchedulerNoPolledMessages() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(3);
        // the first 2 is fast
        mock.message(0).arrives().between(0, 500).millis().beforeNext();
        mock.message(1).arrives().between(0, 500).millis().beforeNext();
        // the last message should be slower as the backoff idle has kicked in
        mock.message(2).arrives().between(500, 1500).millis().afterPrevious();

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("scheduler://foo?delay=100&backoffMultiplier=10&backoffIdleThreshold=2")
                    .log("Fired scheduler")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            // force no messages to be polled which should affect the scheduler to think its idle
                            exchange.setProperty(Exchange.SCHEDULER_POLLED_MESSAGES, false);
                        }
                    })
                    .to("mock:result");
            }
        };
    }

}
