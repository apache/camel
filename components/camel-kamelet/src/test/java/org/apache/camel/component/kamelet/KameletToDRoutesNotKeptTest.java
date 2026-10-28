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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A Kamelet called with toD and cacheSize=-1 (the documented setting for a dynamic endpoint that is different for every
 * message) gets a new endpoint for every call, which creates a route from the template. The endpoint is discarded after
 * the call, so its route must be removed as well, otherwise every call keeps one more route.
 */
class KameletToDRoutesNotKeptTest extends CamelTestSupport {

    private static final int MESSAGES = 50;

    @Test
    void routesOfDiscardedKameletEndpointsAreRemoved() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(MESSAGES);
        int endpoints = context.getEndpointRegistry().size();

        for (int i = 0; i < MESSAGES; i++) {
            template.sendBodyAndHeader("direct:prototype", "body", "id", "id" + i);
        }
        mock.assertIsSatisfied();
        for (int i = 0; i < MESSAGES; i++) {
            assertThat(mock.getReceivedExchanges().get(i).getMessage().getBody()).isEqualTo("id" + i + "-body");
        }

        // the routes are removed on a thread of the component, once the exchange is done, and their endpoints
        // (kamelet:source) with them: removeRoute takes the route out of getRoutes() before it removes its endpoints
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(kameletRoutes()).isZero();
            assertThat(context.getEndpointRegistry().values()).hasSize(endpoints);
        });
    }

    @Test
    void sameValueEveryTimeDoesNotKeepRoutes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(MESSAGES);

        for (int i = 0; i < MESSAGES; i++) {
            template.sendBodyAndHeader("direct:prototype", "body" + i, "id", "same");
        }
        mock.assertIsSatisfied();
        for (int i = 0; i < MESSAGES; i++) {
            assertThat(mock.getReceivedExchanges().get(i).getMessage().getBody()).isEqualTo("same-body" + i);
        }

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(kameletRoutes()).isZero());
    }

    @Test
    void cachedKameletEndpointsKeepTheirRoutes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a-1", "b-2", "a-3", "b-4");

        String[] ids = { "a", "b", "a", "b" };
        for (int i = 0; i < ids.length; i++) {
            template.sendBodyAndHeader("direct:cached", String.valueOf(i + 1), "id", ids[i]);
        }
        mock.assertIsSatisfied();

        // the default toD keeps the endpoints and so their routes for the next messages
        assertThat(kameletRoutes()).isEqualTo(2);
    }

    @Test
    void templateWithThreadsDoesNotInterruptTheCallingRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:threads");
        mock.expectedMessageCount(MESSAGES);

        for (int i = 0; i < MESSAGES; i++) {
            template.sendBodyAndHeader("direct:threads", "body", "id", "id" + i);
        }
        mock.assertIsSatisfied();
        for (int i = 0; i < MESSAGES; i++) {
            Exchange exchange = mock.getReceivedExchanges().get(i);
            assertThat(exchange.getMessage().getBody()).isEqualTo("id" + i + "-body");
            // the calling route continues on a thread of the pool of the Kamelet's route
            assertThat(exchange.getMessage().getHeader("thread", String.class)).contains("Threads");
            assertThat(exchange.getMessage().getHeader("interrupted", Boolean.class)).isFalse();
            // the Kamelet's route is removed only after the exchange is done
            assertThat(exchange.getMessage().getHeader("kameletRouteKept", Boolean.class)).isTrue();
        }

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(kameletRoutes()).isZero());
    }

    @Test
    void kameletRouteIsKeptWhileTheCallingRouteRunsOnItsThread() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:slow");
        mock.expectedBodiesReceived("x-body");

        template.sendBodyAndHeader("direct:slow", "body", "id", "x");
        mock.assertIsSatisfied();

        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertThat(exchange.getMessage().getHeader("thread", String.class)).contains("Threads");
        assertThat(exchange.getMessage().getHeader("kameletRouteKept", Boolean.class)).isTrue();
        assertThat(exchange.getMessage().getHeader("interrupted", Boolean.class)).isFalse();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(kameletRoutes()).isZero());
    }

    @Test
    void kameletRouteIsKeptWhileTheCallingRouteRunsOnItsThreadAfterARecipientList() throws Exception {
        // recipientList with cacheSize=-1 shuts the endpoint down when it releases the producer, after the call has
        // completed: the exchange of the calling route is not known there
        assertKameletRouteIsKeptWhileTheCallingRouteRunsOnItsThread("direct:recipientList");
    }

    @Test
    void kameletRouteIsKeptWhileTheCallingRouteRunsOnItsThreadAfterARoutingSlip() throws Exception {
        // routingSlip with cacheSize=-1 shuts the endpoint down in its callback, with a copy of the exchange that has
        // no unit of work
        assertKameletRouteIsKeptWhileTheCallingRouteRunsOnItsThread("direct:routingSlip");
    }

    private void assertKameletRouteIsKeptWhileTheCallingRouteRunsOnItsThread(String caller) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:slow");
        mock.expectedBodiesReceived("x-body");

        template.sendBodyAndHeader(caller, "body", "id", "x");
        mock.assertIsSatisfied();

        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertThat(exchange.getMessage().getHeader("thread", String.class)).contains("Threads");
        assertThat(exchange.getMessage().getHeader("kameletRouteKept", Boolean.class)).isTrue();
        assertThat(exchange.getMessage().getHeader("interrupted", Boolean.class)).isFalse();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(kameletRoutes()).isZero());
    }

    @Test
    void sendsFromSeveralThreadsAtOnce() throws Exception {
        int threads = 8;
        int perThread = 25;
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(threads * perThread);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Object>> replies = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                for (int i = 0; i < perThread; i++) {
                    String id = "t" + t + "-" + i;
                    replies.add(executor.submit(() -> template.requestBodyAndHeader("direct:prototype", "body", "id", id)));
                }
            }
            List<Object> bodies = new ArrayList<>();
            for (Future<Object> reply : replies) {
                bodies.add(reply.get(30, TimeUnit.SECONDS));
            }
            List<String> expected = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                for (int i = 0; i < perThread; i++) {
                    expected.add("t" + t + "-" + i + "-body");
                }
            }
            assertThat(bodies).containsExactlyElementsOf(expected);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        mock.assertIsSatisfied();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(kameletRoutes()).isZero());
    }

    @Test
    void routeIdGivenInTheUriIsLeftAlone() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(MESSAGES);

        for (int i = 0; i < MESSAGES; i++) {
            template.sendBodyAndHeader("direct:myRoute", "body", "id", "id" + i);
        }
        mock.assertIsSatisfied();
        for (int i = 0; i < MESSAGES; i++) {
            assertThat(mock.getReceivedExchanges().get(i).getMessage().getBody()).isEqualTo("id" + i + "-body");
        }

        // every endpoint replaces the route of the previous one, as they share the route id: the last one is kept
        assertThat(context.getRoutes()).filteredOn(Route::isCreatedByKamelet).extracting(Route::getRouteId)
                .containsExactly("myRoute");
    }

    @Test
    void routeOfKameletIsRemovedWithTheRouteAddedAtRuntimeThatConsumesFromIt() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("kamelet:source-of?name=a").routeId("consumer")
                        .to("mock:consumer");
            }
        });
        assertThat(kameletRoutes()).isEqualTo(1);

        MockEndpoint mock = getMockEndpoint("mock:consumer");
        mock.expectedBodiesReceived("from a");
        template.sendBody("direct:source-a", "from a");
        mock.assertIsSatisfied();

        // removing the route shuts down its input endpoint, which removes the route it created from the template
        context.getRouteController().stopRoute("consumer");
        assertThat(context.removeRoute("consumer")).isTrue();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(kameletRoutes()).isZero());
    }

    private long kameletRoutes() {
        return context.getRoutes().stream().filter(Route::isCreatedByKamelet).count();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("prefix")
                        .templateParameter("value")
                        .from("kamelet:source")
                        .setBody().simple("{{value}}-${body}");

                routeTemplate("pooled")
                        .templateParameter("value")
                        .from("kamelet:source")
                        .threads(2)
                        .setHeader("kameletRouteId").simple("${routeId}")
                        .setBody().simple("{{value}}-${body}");

                routeTemplate("source-of")
                        .templateParameter("name")
                        .from("direct:source-{{name}}")
                        .to("kamelet:sink");

                from("direct:prototype").routeId("prototype")
                        .toD("kamelet:prefix?value=${header.id}", -1)
                        .to("mock:result");

                from("direct:cached").routeId("cached")
                        .toD("kamelet:prefix?value=${header.id}")
                        .to("mock:result");

                from("direct:myRoute").routeId("myRouteCaller")
                        .toD("kamelet:prefix/myRoute?value=${header.id}", -1)
                        .to("mock:result");

                from("direct:threads").routeId("threadsCaller")
                        .toD("kamelet:pooled?value=${header.id}", -1)
                        .process(e -> {
                            e.getMessage().setHeader("thread", Thread.currentThread().getName());
                            e.getMessage().setHeader("interrupted", Thread.currentThread().isInterrupted());
                            String id = e.getMessage().getHeader("kameletRouteId", String.class);
                            e.getMessage().setHeader("kameletRouteKept", context.getRoute(id) != null);
                        })
                        .to("mock:threads");

                from("direct:slow").routeId("slowCaller")
                        .toD("kamelet:pooled?value=${header.id}", -1)
                        .process(e -> recordWhetherTheKameletRouteIsKept(e))
                        .to("mock:slow");

                from("direct:recipientList").routeId("recipientListCaller")
                        .recipientList(simple("kamelet:pooled?value=${header.id}")).cacheSize(-1)
                        .process(e -> recordWhetherTheKameletRouteIsKept(e))
                        .to("mock:slow");

                from("direct:routingSlip").routeId("routingSlipCaller")
                        .routingSlip(simple("kamelet:pooled?value=${header.id}")).cacheSize(-1)
                        .process(e -> recordWhetherTheKameletRouteIsKept(e))
                        .to("mock:slow");
            }
        };
    }

    private void recordWhetherTheKameletRouteIsKept(Exchange e) {
        e.getMessage().setHeader("thread", Thread.currentThread().getName());
        String id = e.getMessage().getHeader("kameletRouteId", String.class);
        boolean kept;
        try {
            // the rest of the calling route runs on a thread of the Kamelet's route, which must not be stopped
            // (shutting down its thread pool) before the calling route has left that thread
            await().during(500, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS)
                    .until(() -> context.getRoute(id) != null);
            kept = true;
        } catch (ConditionTimeoutException ex) {
            kept = false;
        }
        e.getMessage().setHeader("kameletRouteKept", kept);
        e.getMessage().setHeader("interrupted", Thread.currentThread().isInterrupted());
    }
}
