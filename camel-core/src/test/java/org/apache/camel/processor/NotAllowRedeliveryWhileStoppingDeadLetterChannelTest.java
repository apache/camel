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

import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.StopWatch;

/**
 * @version 
 */
public class NotAllowRedeliveryWhileStoppingDeadLetterChannelTest extends ContextTestSupport {

    public void testRedelivery() throws Exception {
        StopWatch watch = new StopWatch();

        MockEndpoint before = getMockEndpoint("mock:foo");
        before.expectedMessageCount(1);

        template.sendBody("seda:start", "Hello World");

        assertMockEndpointsSatisfied();

        Thread.sleep(500);

        context.stopRoute("foo");

        // we should reject the task and stop quickly
        assertTrue("Should stop quickly: " + watch.taken(), watch.taken() < 5000);

        // should go to DLC
        Exchange dead = getMockEndpoint("mock:dead").getExchanges().get(0);
        assertNotNull(dead);

        Throwable cause = dead.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        assertNotNull(cause);
        assertIsInstanceOf(RejectedExecutionException.class, cause);
        assertEquals("Redelivery not allowed while stopping", cause.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead")
                    .maximumRedeliveries(5).redeliveryDelay(5000).allowRedeliveryWhileStopping(false));

                from("seda:start").routeId("foo")
                    .to("mock:foo")
                    .throwException(new IllegalArgumentException("Forced"));
            }
        };
    }
}
