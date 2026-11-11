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
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

/**
 * The first fire of a timer with {@code delay=0} must not be dropped when its route is (re)started at runtime on an
 * already started CamelContext.
 * <p/>
 * The test uses a custom {@link Timer} that runs the task synchronously as soon as it is scheduled, which is when a
 * real timer may also fire with {@code delay=0}: while the consumer is still starting. The custom timer does not
 * schedule the task for real, so a dropped first fire cannot be masked by a later fire.
 */
public class TimerFirstFireAfterRouteRestartTest extends ContextTestSupport {

    @Test
    public void testFirstFireNotDroppedOnRouteRestart() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");

        // the timer fires once when the route is started with the CamelContext
        mock.expectedMessageCount(1);
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);

        mock.reset();

        context.getRouteController().stopRoute("t");

        // restarting the route at runtime must fire the timer once immediately again
        mock.expectedMessageCount(1);
        context.getRouteController().startRoute("t");

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    @Test
    public void testFirstFireNotDroppedOnRouteAddedAtRuntime() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:added");

        // adding (and thereby starting) a route at runtime on an already started
        // CamelContext must fire the timer once immediately
        mock.expectedMessageCount(1);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:u?delay=0&period=3600000&timer=#eager").routeId("u").to("mock:added");
            }
        });

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = super.createCamelRegistry();
        registry.bind("eager", new EagerTimer());
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:t?delay=0&period=3600000&timer=#eager").routeId("t").to("mock:result");
            }
        };
    }

    /**
     * A timer that runs the task synchronously when it is scheduled, and does not schedule it for real.
     */
    private static final class EagerTimer extends Timer {

        EagerTimer() {
            super("eager", true);
        }

        @Override
        public void schedule(TimerTask task, long delay, long period) {
            task.run();
        }

        @Override
        public void scheduleAtFixedRate(TimerTask task, long delay, long period) {
            task.run();
        }
    }
}
