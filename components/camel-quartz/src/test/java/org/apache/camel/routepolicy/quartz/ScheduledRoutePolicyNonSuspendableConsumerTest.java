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
package org.apache.camel.routepolicy.quartz;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.Suspendable;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.quartz.QuartzComponent;
import org.apache.camel.impl.engine.DefaultShutdownStrategy;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.service.ServiceHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A consumer that is not {@link Suspendable} is stopped when a scheduled route policy suspends its route. The scheduled
 * resume (or start) must start it again, as {@link org.apache.camel.support.RoutePolicySupport#resumeOrStartConsumer}
 * does.
 */
class ScheduledRoutePolicyNonSuspendableConsumerTest extends NoBuilderTest {

    @Test
    void scheduledResumeStartsConsumerThatWasStoppedBySuspend() throws Exception {
        context.addEndpoint("listener:in", new ListenerEndpoint());
        context.getComponent("quartz", QuartzComponent.class)
                .setPropertiesFile("org/apache/camel/routepolicy/quartz/myquartz.properties");
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                SimpleScheduledRoutePolicy policy = new SimpleScheduledRoutePolicy();
                long now = System.currentTimeMillis();
                policy.setRouteSuspendDate(new Date(now + 500));
                policy.setRouteSuspendRepeatCount(0);
                policy.setRouteResumeDate(new Date(now + 2000));
                policy.setRouteResumeRepeatCount(0);

                from("listener:in").routeId("test").routePolicy(policy).to("mock:result");
            }
        });
        context.start();

        Consumer consumer = context.getRoute("test").getConsumer();

        // the suspend stops the consumer, as it cannot be suspended
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(ServiceHelper.isStopped(consumer), "consumer should be stopped"));
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("test"));

        // the resume must start it again
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(ServiceHelper.isStarted(consumer), "consumer should be started again"));
    }

    @Test
    void scheduledStartStartsConsumerThatWasStoppedBySuspend() throws Exception {
        context.addEndpoint("listener:in", new ListenerEndpoint());
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                CronScheduledRoutePolicy policy = new CronScheduledRoutePolicy();
                // never fires by itself, the actions are run below as the scheduler would
                policy.setRouteSuspendTime("0 0 0 1 1 ? 2099");
                policy.setRouteStartTime("0 0 0 1 1 ? 2099");

                from("listener:in").routeId("test").routePolicy(policy).to("mock:result");
            }
        });
        context.start();

        Route route = context.getRoute("test");
        ScheduledRoutePolicy policy = scheduledRoutePolicy(route);
        Consumer consumer = route.getConsumer();

        policy.onJobExecute(ScheduledRoutePolicyConstants.Action.SUSPEND, route);
        assertTrue(ServiceHelper.isStopped(consumer), "consumer should be stopped");
        assertFalse(ServiceHelper.isSuspended(consumer));

        policy.onJobExecute(ScheduledRoutePolicyConstants.Action.START, route);
        assertTrue(ServiceHelper.isStarted(consumer), "consumer should be started again");
    }

    @Test
    void scheduledSuspendDoesNotRecordAConsumerThatWasAlreadyStopped() throws Exception {
        Route route = startRouteWithActionsRunByTheTest();
        ScheduledRoutePolicy policy = scheduledRoutePolicy(route);
        Consumer consumer = route.getConsumer();

        // something else stopped the consumer (such as a throttling policy), and the route is still started
        ServiceHelper.stopService(consumer);
        policy.onJobExecute(ScheduledRoutePolicyConstants.Action.SUSPEND, route);

        // the consumer was not stopped by the scheduled suspend, so the scheduled resume or start leaves it stopped
        policy.onJobExecute(ScheduledRoutePolicyConstants.Action.RESUME, route);
        assertTrue(ServiceHelper.isStopped(consumer), "the scheduled resume started a consumer it did not stop");
        policy.onJobExecute(ScheduledRoutePolicyConstants.Action.START, route);
        assertTrue(ServiceHelper.isStopped(consumer), "the scheduled start started a consumer it did not stop");
    }

    @Test
    void routeSuspendedAndResumedAfterTheScheduledSuspendKeepsNoRecordOfIt() throws Exception {
        Route route = startRouteWithActionsRunByTheTest();
        ScheduledRoutePolicy policy = scheduledRoutePolicy(route);
        Consumer consumer = route.getConsumer();

        policy.onJobExecute(ScheduledRoutePolicyConstants.Action.SUSPEND, route);
        assertTrue(ServiceHelper.isStopped(consumer), "consumer should be stopped");

        // the route controller suspends and resumes the route: its consumer is not Suspendable, so the route does not
        // support suspension, and the route controller stops and starts the route instead
        context.getRouteController().suspendRoute("test");
        context.getRouteController().resumeRoute("test");
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("test"));
        route = context.getRoute("test");
        Consumer started = route.getConsumer();
        assertTrue(ServiceHelper.isStarted(started), "consumer should be started by the route controller");

        // something else stops the consumer: the scheduled suspend before the resume of the route no longer counts
        ServiceHelper.stopService(started);
        policy.onJobExecute(ScheduledRoutePolicyConstants.Action.RESUME, route);
        assertTrue(ServiceHelper.isStopped(started), "the scheduled resume started a consumer it did not stop");
    }

    /**
     * Starts a route with a non-Suspendable consumer and a scheduled route policy whose triggers never fire: the test
     * runs the actions as the scheduler would.
     */
    private Route startRouteWithActionsRunByTheTest() throws Exception {
        context.addEndpoint("listener:in", new ListenerEndpoint());
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                CronScheduledRoutePolicy policy = new CronScheduledRoutePolicy();
                policy.setRouteSuspendTime("0 0 0 1 1 ? 2099");
                policy.setRouteResumeTime("0 0 0 1 1 ? 2099");
                policy.setRouteStartTime("0 0 0 1 1 ? 2099");

                from("listener:in").routeId("test").routePolicy(policy).to("mock:result");
            }
        });
        context.start();
        return context.getRoute("test");
    }

    @Test
    void scheduledResumeOrStartDoesNotStartTheConsumerWhileCamelStops() throws Exception {
        List<String> startedDuringShutdown = runScheduledResumeAndStartWhileStopping(false);

        assertEquals(List.of(), startedDuringShutdown, "the consumer was started again while Camel stopped");
    }

    @Test
    void scheduledResumeOrStartDoesNotStartTheConsumerWhileTheRouteStops() throws Exception {
        // the route status stays Started while the shutdown strategy waits for the in-flight exchanges
        List<String> startedDuringShutdown = runScheduledResumeAndStartWhileStopping(true);

        assertEquals(List.of(), startedDuringShutdown, "the consumer was started again while the route stopped");
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("test"));
    }

    /**
     * Stops the route, or Camel, and runs the scheduled resume and start after the shutdown strategy stopped the
     * consumer, as a trigger that fires during the shutdown would.
     *
     * @return the actions after which the consumer was started
     */
    private List<String> runScheduledResumeAndStartWhileStopping(boolean routeOnly) throws Exception {
        context.addEndpoint("listener:in", new ListenerEndpoint());
        List<String> startedDuringShutdown = new CopyOnWriteArrayList<>();
        // the shutdown stops the consumer of the started route first (it is not Suspendable), and stops the route
        // later: run the scheduled actions in between, as a trigger that fires during the shutdown would
        context.setShutdownStrategy(new DefaultShutdownStrategy(context) {
            @Override
            protected void shutdownNow(String routeId, Consumer consumer) {
                super.shutdownNow(routeId, consumer);
                Route route = context.getRoute(routeId);
                for (ScheduledRoutePolicyConstants.Action action : List.of(
                        ScheduledRoutePolicyConstants.Action.RESUME, ScheduledRoutePolicyConstants.Action.START)) {
                    try {
                        scheduledRoutePolicy(route).onJobExecute(action, route);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    if (ServiceHelper.isStarted(consumer)) {
                        startedDuringShutdown.add(action.name());
                    }
                }
            }
        });
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                CronScheduledRoutePolicy policy = new CronScheduledRoutePolicy();
                // never fires by itself, the actions are run by the shutdown strategy above
                policy.setRouteResumeTime("0 0 0 1 1 ? 2099");
                policy.setRouteStartTime("0 0 0 1 1 ? 2099");

                from("listener:in").routeId("test").routePolicy(policy).to("mock:result");
            }
        });
        context.start();
        Consumer consumer = context.getRoute("test").getConsumer();
        assertTrue(ServiceHelper.isStarted(consumer));

        if (routeOnly) {
            context.getRouteController().stopRoute("test");
        } else {
            context.stop();
        }

        assertTrue(ServiceHelper.isStopped(consumer), "consumer should be stopped");
        return startedDuringShutdown;
    }

    private static ScheduledRoutePolicy scheduledRoutePolicy(Route route) {
        for (RoutePolicy policy : route.getRoutePolicyList()) {
            if (policy instanceof ScheduledRoutePolicy srp) {
                return srp;
            }
        }
        throw new IllegalStateException("No scheduled route policy on route " + route.getId());
    }

    /**
     * An endpoint whose consumer is not {@link Suspendable}, like the consumers of the netty, mina, mllp or paho
     * components.
     */
    private static final class ListenerEndpoint extends DefaultEndpoint {

        ListenerEndpoint() {
            super("listener:in", null);
        }

        @Override
        public Producer createProducer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            Consumer answer = new DefaultConsumer(this, processor) {
            };
            configureConsumer(answer);
            return answer;
        }
    }
}
