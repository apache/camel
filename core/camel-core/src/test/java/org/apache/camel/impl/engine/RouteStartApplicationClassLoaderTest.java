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
package org.apache.camel.impl.engine;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.StartupListener;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.timer.TimerConsumer;
import org.apache.camel.component.timer.TimerEndpoint;
import org.apache.camel.spi.SupervisingRouteController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A consumer creates its threads when it is started, and they inherit the thread context class loader of the thread
 * starting the route. Libraries use it to look up their services, so the application class loader must be in place
 * however a route is started, not only during the lifecycle callbacks of CamelContext.
 */
public class RouteStartApplicationClassLoaderTest extends ContextTestSupport {

    private static final String TIMER_URI = "timer:tccl?delay=1&repeatCount=1";

    private final ClassLoader applicationClassLoader = new ClassLoader(getClass().getClassLoader()) {
    };
    private final AtomicReference<ClassLoader> consumerThreadClassLoader = new AtomicReference<>();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void routeStartedByCamelContext() throws Exception {
        context.setApplicationContextClassLoader(applicationClassLoader);
        context.addRoutes(routeFrom(TIMER_URI, true));

        startAndAssertConsumerThreadClassLoader(applicationClassLoader);
    }

    @Test
    public void supervisedRoute() throws Exception {
        context.setApplicationContextClassLoader(applicationClassLoader);
        context.getRouteController().supervising();
        context.addRoutes(routeFrom(TIMER_URI, true));

        startAndAssertConsumerThreadClassLoader(applicationClassLoader);
    }

    @Test
    public void supervisedRouteWithInitialDelay() throws Exception {
        context.setApplicationContextClassLoader(applicationClassLoader);
        context.getRouteController().supervising().setInitialDelay(10);
        context.addRoutes(routeFrom(TIMER_URI, true));

        startAndAssertConsumerThreadClassLoader(applicationClassLoader);
    }

    @Test
    public void routeExcludedFromSupervision() throws Exception {
        context.setApplicationContextClassLoader(applicationClassLoader);
        context.getRouteController().supervising().setExcludeRoutes("tccl");
        context.addRoutes(routeFrom(TIMER_URI, true));

        startAndAssertConsumerThreadClassLoader(applicationClassLoader);
    }

    @Test
    public void supervisedRouteRestartedAfterFailedStart() throws Exception {
        context.setApplicationContextClassLoader(applicationClassLoader);
        SupervisingRouteController src = context.getRouteController().supervising();
        src.setBackOffDelay(10);
        src.setBackOffMaxAttempts(3);
        context.addRoutes(routeFrom(failsOnFirstStart(), true));

        startAndAssertConsumerThreadClassLoader(applicationClassLoader);
    }

    @Test
    public void routeStartedManuallyAfterCamelContextStart() throws Exception {
        context.setApplicationContextClassLoader(applicationClassLoader);
        context.addRoutes(routeFrom(TIMER_URI, false));
        context.start();
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        ClassLoader caller = Thread.currentThread().getContextClassLoader();

        context.getRouteController().startRoute("tccl");

        MockEndpoint.assertIsSatisfied(context);
        assertSame(applicationClassLoader, consumerThreadClassLoader.get());
        assertSame(caller, Thread.currentThread().getContextClassLoader());
    }

    @Test
    public void routeWithoutApplicationClassLoaderKeepsTheClassLoaderOfTheCaller() throws Exception {
        context.getRouteController().supervising();
        context.addRoutes(routeFrom(TIMER_URI, true));

        startAndAssertConsumerThreadClassLoader(Thread.currentThread().getContextClassLoader());
    }

    @Test
    public void startupListenerWhenCamelContextIsFullyStarted() throws Exception {
        context.setApplicationContextClassLoader(applicationClassLoader);
        AtomicReference<ClassLoader> listenerClassLoader = new AtomicReference<>();
        context.addStartupListener(new StartupListener() {
            @Override
            public void onCamelContextStarted(CamelContext camelContext, boolean alreadyStarted) {
                // noop
            }

            @Override
            public void onCamelContextFullyStarted(CamelContext camelContext, boolean alreadyStarted) {
                listenerClassLoader.set(Thread.currentThread().getContextClassLoader());
            }
        });
        ClassLoader caller = Thread.currentThread().getContextClassLoader();

        context.start();

        assertSame(applicationClassLoader, listenerClassLoader.get());
        assertSame(caller, Thread.currentThread().getContextClassLoader());
    }

    private void startAndAssertConsumerThreadClassLoader(ClassLoader expected) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        ClassLoader caller = Thread.currentThread().getContextClassLoader();

        context.start();

        MockEndpoint.assertIsSatisfied(context);
        assertSame(expected, consumerThreadClassLoader.get());
        assertSame(caller, Thread.currentThread().getContextClassLoader());
    }

    private RouteBuilder routeFrom(String uri, boolean autoStartup) {
        return routeFrom(context.getEndpoint(uri), autoStartup);
    }

    private RouteBuilder routeFrom(Endpoint endpoint, boolean autoStartup) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(endpoint).routeId("tccl").autoStartup(autoStartup)
                        .process(e -> consumerThreadClassLoader.set(Thread.currentThread().getContextClassLoader()))
                        .to("mock:result");
            }
        };
    }

    private Endpoint failsOnFirstStart() {
        AtomicBoolean failed = new AtomicBoolean();
        TimerEndpoint endpoint = new TimerEndpoint("timer:tccl", context.getComponent("timer"), "tccl") {
            @Override
            public Consumer createConsumer(Processor processor) throws Exception {
                Consumer consumer = new TimerConsumer(this, processor) {
                    @Override
                    protected void doStart() throws Exception {
                        if (failed.compareAndSet(false, true)) {
                            throw new IllegalStateException("Cannot start");
                        }
                        super.doStart();
                    }
                };
                configureConsumer(consumer);
                return consumer;
            }
        };
        endpoint.setDelay(1);
        endpoint.setRepeatCount(1);
        return endpoint;
    }
}
