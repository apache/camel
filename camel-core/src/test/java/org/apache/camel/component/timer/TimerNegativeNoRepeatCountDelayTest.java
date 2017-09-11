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
package org.apache.camel.component.timer;

import java.util.Iterator;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class TimerNegativeNoRepeatCountDelayTest extends ContextTestSupport {

    public void testNegativeDelay() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        context.stopRoute("routeTest");

        List<Exchange> exchanges = mock.getExchanges();
        Iterator<Exchange> iter = exchanges.iterator();
        
        while (iter.hasNext()) {
            Exchange exchange = iter.next();
            assertEquals("negativeDelay", exchange.getProperty(Exchange.TIMER_NAME));
            assertNotNull(exchange.getProperty(Exchange.TIMER_FIRED_TIME));
            assertNotNull(exchange.getIn().getHeader("firedTime"));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer://negativeDelay?delay=-1&repeatCount=10").routeId("routeTest").to("mock:result");
            }
        };
    }
}
