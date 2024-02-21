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

import java.util.Timer;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TimerEndpointTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testTimerEndpoint() throws Exception {
        final TimerEndpoint te = new TimerEndpoint();
        te.setCamelContext(context);
        te.setTimer(new Timer(true));
        te.setEndpointUriIfNotSpecified("timer://foo");
        te.setPeriod(10);
        te.setDelay(10);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(te).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTimerEndpointYetAgain() throws Exception {
        final TimerEndpoint te = new TimerEndpoint("timer://foo", context.getComponent("timer"), "foo");
        te.setTimer(new Timer(true));
        te.setPeriod(10);
        te.setDelay(10);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(te).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTimerEndpointNoProducer() {
        Endpoint te = context.getEndpoint("timer://foo");

        Assertions.assertThrows(RuntimeCamelException.class, te::createProducer, "Should have thrown an exception");
    }

}
