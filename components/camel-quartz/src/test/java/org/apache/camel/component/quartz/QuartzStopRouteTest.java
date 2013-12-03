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
package org.apache.camel.component.quartz;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class QuartzStopRouteTest extends BaseQuartzTest {

    @Test
    public void testQuartzStop() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        assertMockEndpointsSatisfied();

        context.stopRoute("foo");

        int size = mock.getReceivedCounter();

        resetMocks();

        mock.expectedMessageCount(0);
        mock.assertIsSatisfied(3000);

        assertEquals("Should not schedule when stopped", size, size);

        resetMocks();
        mock.expectedMinimumMessageCount(1);

        context.startRoute("foo");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1
                // triggers every second at precise 00,01,02,03..59
                // notice we must use + as space when configured using URI parameter
                from("quartz://myGroup/myTimerName?cron=0/1+*+*+*+*+?")
                        .routeId("foo")
                        .to("log:result", "mock:result");
                // END SNIPPET: e1
            }
        };
    }
}