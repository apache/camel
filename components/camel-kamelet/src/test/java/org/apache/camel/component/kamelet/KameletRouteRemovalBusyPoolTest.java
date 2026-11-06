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
package org.apache.camel.component.kamelet;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The route a Kamelet endpoint created while the context runs is removed when the endpoint is shut down, also while a
 * thread pool of that route stays busy.
 */
class KameletRouteRemovalBusyPoolTest extends CamelTestSupport {

    private final AtomicBoolean holdSource = new AtomicBoolean();
    private final CountDownLatch sourceBusy = new CountDownLatch(1);
    private final CountDownLatch kameletRouteStopping = new CountDownLatch(1);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event instanceof CamelEvent.RouteStoppingEvent stopping && stopping.getRoute().isCreatedByKamelet()) {
                    kameletRouteStopping.countDown();
                }
            }
        });
        return context;
    }

    @Test
    void routeOfBusySourceKameletIsRemovedWithTheRouteThatConsumedFromIt() throws Exception {
        // far beyond the wait below: the route must be removed without waiting for its busy thread pool
        context.getShutdownStrategy().setTimeout(60);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("kamelet:busy-source").routeId("consumer")
                        .to("mock:consumer");
            }
        });
        getMockEndpoint("mock:consumer").expectedMinimumMessageCount(1);
        getMockEndpoint("mock:consumer").assertIsSatisfied();

        // the thread pool of the Kamelet's route stays busy with its own messages: no calling route runs on it
        holdSource.set(true);
        assertThat(sourceBusy.await(10, TimeUnit.SECONDS)).isTrue();

        context.getRouteController().stopRoute("consumer");
        assertThat(context.removeRoute("consumer")).isTrue();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(kameletRoutes()).isZero());
    }

    @Test
    void routeOfDiscardedEndpointIsRemovedAfterTheShutdownTimeoutWhileItsPoolIsBusy() throws Exception {
        context.getShutdownStrategy().setTimeout(2);
        getMockEndpoint("mock:slow").expectedMessageCount(1);

        // the calling route continues on a thread of the Kamelet's route, and stays there until that route stops
        Future<Object> sent = template.asyncRequestBodyAndHeader("direct:recipientList", "body", "id", "a");

        // the removal waits for the pool at most the shutdown timeout (2 seconds here), not until the caller leaves it
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(kameletRoutes()).isZero());
        sent.get(20, TimeUnit.SECONDS);
        getMockEndpoint("mock:slow").assertIsSatisfied();
    }

    private long kameletRoutes() {
        return context.getRoutes().stream().filter(Route::isCreatedByKamelet).count();
    }

    private void holdUntilTheKameletRouteStops(Exchange exchange) throws Exception {
        if (holdSource.get()) {
            sourceBusy.countDown();
            ServiceSupport consumer = (ServiceSupport) exchange.getContext().getRoute(exchange.getFromRouteId()).getConsumer();
            // removing the route stops its consumer (the timer) first, then waits for the messages in flight
            await().atMost(30, TimeUnit.SECONDS)
                    .until(() -> consumer.isSuspendingOrSuspended() || consumer.isStoppingOrStopped());
            exchange.setRouteStop(true);
        }
    }

    private void awaitKameletRouteStopping() {
        try {
            kameletRouteStopping.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // stopping the Kamelet's route shuts its thread pool down, which can interrupt this thread once released
            Thread.currentThread().interrupt();
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("busy-source")
                        .from("timer:tick?period=50")
                        .threads(1)
                        .process(e -> holdUntilTheKameletRouteStops(e))
                        .to("kamelet:sink");

                routeTemplate("pooled")
                        .templateParameter("value")
                        .from("kamelet:source")
                        .threads(2)
                        .setBody().simple("{{value}}-${body}");

                from("direct:recipientList").routeId("recipientListCaller")
                        .recipientList(simple("kamelet:pooled?value=${header.id}")).cacheSize(-1)
                        .process(e -> awaitKameletRouteStopping())
                        .to("mock:slow");
            }
        };
    }
}
