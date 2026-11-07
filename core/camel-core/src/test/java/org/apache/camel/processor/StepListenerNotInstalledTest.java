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

import java.util.List;

import org.apache.camel.Channel;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.StepListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With no {@link StepListener} installed when the routes are created, no channel has the wrapper of the listeners: the
 * error handler of each step (or the channel of a step without one) calls the processor of the step itself, as before
 * the listeners existed.
 */
class StepListenerNotInstalledTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start")
                        .setHeader("greeting", constant("Hello"))
                        .process(e -> e.getMessage().setBody("Bye"))
                        .doTry()
                            .to("mock:try")
                        .doCatch(IllegalArgumentException.class)
                            .to("mock:caught")
                        .doFinally()
                            .to("mock:finally")
                        .end()
                        .split(body())
                            .to("mock:split")
                        .end()
                        .multicast()
                            .to("mock:mc1")
                            .to("mock:mc2")
                        .end()
                        .to("mock:result");

                from("direct:noErrorHandler").routeId("noErrorHandler")
                        .errorHandler(noErrorHandler())
                        .to("mock:noEh1")
                        .to("mock:noEh2");

                from("direct:retry").routeId("retry")
                        .errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0))
                        .to("mock:retried");
            }
        };
    }

    @Test
    void testNoWrapperWithoutListener() throws Exception {
        assertTrue(context.getCamelContextExtension().getStepListeners().isEmpty());
        int steps = 0;
        for (Route route : context.getRoutes()) {
            List<Channel> channels = StepListenerRecorder.channels(route);
            for (Channel channel : channels) {
                // the error handler (or the channel) calls the processor of the step, not a wrapper
                assertSame(channel.getNextProcessor(), StepListenerRecorder.callOf(channel),
                        route.getRouteId() + " " + channel);
                steps++;
            }
        }
        // the steps of all the routes, also the nested ones
        assertEquals(17, steps);

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello");
        assertMockEndpointsSatisfied();
    }
}
