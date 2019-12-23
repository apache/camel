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
package org.apache.camel.component.timer;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Unit test for fired time exchange property
 */
public class TimerRepeatCountTest extends ContextTestSupport {

    @Test
    public void testRepeatCount() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);
        mock.setAssertPeriod(100);
        mock.message(0).header(Exchange.TIMER_COUNTER).isEqualTo(1);
        mock.message(1).header(Exchange.TIMER_COUNTER).isEqualTo(2);
        mock.message(2).header(Exchange.TIMER_COUNTER).isEqualTo(3);

        // we should only get 3 messages as we have a repeat count limit at 3

        context.getRouteController().startAllRoutes();

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("timer://hello?delay=0&repeatCount=3&period=10").noAutoStartup().to("mock:result");
            }
        };
    }
}
