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
package org.apache.camel.coap;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.eclipse.californium.core.CoapResource;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A route with an {@code observe=true} consumer that is stopped must stop observing the resource, so that it routes
 * each notification once when it is started again.
 */
class CoAPObserveRestartTest extends CoAPTestSupport {

    // the errors the observe consumer reports (instead of logging them as WARN)
    private final List<Throwable> observerErrors = new CopyOnWriteArrayList<>();

    @BindToRegistry("observerErrors")
    private final ExceptionHandler observerErrorHandler = new ExceptionHandler() {
        @Override
        public void handleException(Throwable exception) {
            observerErrors.add(exception);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            observerErrors.add(exception);
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            observerErrors.add(exception);
        }
    };

    @Produce("direct:notify")
    protected ProducerTemplate notify;

    @Test
    void stoppedObserverCancelsItsObservation() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:observed");
        mock.expectedBodiesReceived("Hello 0");
        MockEndpoint.assertIsSatisfied(context);
        await().atMost(10, TimeUnit.SECONDS).until(() -> observerCount() == 1);

        context.getRouteController().stopRoute("observer");

        // the server must no longer have the stopped consumer as an observer
        await().atMost(10, TimeUnit.SECONDS).until(() -> observerCount() == 0);

        mock.reset();
        mock.expectedBodiesReceived("Hello 0", "Hello 1");
        context.getRouteController().startRoute("observer");
        await().atMost(10, TimeUnit.SECONDS).until(() -> mock.getReceivedCounter() >= 1);
        assertEquals(1, observerCount(), "observers of the resource after the route was started again");

        // one observer, so the notification is routed once
        notify.sendBody(null);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void restartedObserverRoutesEachNotificationOnce() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:observed");
        mock.expectedBodiesReceived("Hello 0");
        MockEndpoint.assertIsSatisfied(context);

        context.getRouteController().stopRoute("observer");
        context.getRouteController().startRoute("observer");
        // the new consumer observes the resource and gets its current state
        await().atMost(10, TimeUnit.SECONDS).until(() -> mock.getReceivedCounter() >= 2);

        mock.reset();
        mock.expectedBodiesReceived("Hello 1");
        mock.setAssertPeriod(1000);
        notify.sendBody(null);
        // routed once, not once more by the consumer that was stopped
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void quickRestartDoesNotRouteTheResponseToTheCancel() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:observed");
        mock.expectedBodiesReceived("Hello 0");
        MockEndpoint.assertIsSatisfied(context);

        mock.reset();
        // only the current state that the new observation gets, not the response to the cancel of the old one
        mock.expectedBodiesReceived("Hello 0");
        mock.setAssertPeriod(1000);
        context.getRouteController().stopRoute("observer");
        context.getRouteController().startRoute("observer");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void quickSuspendResumeDoesNotRouteTheResponseToTheCancel() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:observed");
        mock.expectedBodiesReceived("Hello 0");
        MockEndpoint.assertIsSatisfied(context);

        mock.reset();
        mock.expectedBodiesReceived("Hello 0");
        mock.setAssertPeriod(1000);
        context.getRouteController().suspendRoute("observer");
        context.getRouteController().resumeRoute("observer");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void stoppedRouteGetsNoResponseToTheCancel() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:observed");
        mock.expectedBodiesReceived("Hello 0");
        MockEndpoint.assertIsSatisfied(context);

        context.getRouteController().stopRoute("observer");
        await().atMost(10, TimeUnit.SECONDS).until(() -> observerCount() == 0);

        // nothing is handed to the stopped route (it would fail with a RouteStoppingException, logged as a WARN)
        await().during(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(List.of(), observerErrors, "errors of the stopped observer"));
        assertEquals(1, mock.getReceivedCounter(), "exchanges routed by the observer");
    }

    private int observerCount() throws Exception {
        CoAPComponent component = context.getComponent("coap", CoAPComponent.class);
        CoapResource resource = (CoapResource) component.servers.get(PORT.getPort()).getRoot().getChild("TestResource");
        return resource.getObserverCount();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                AtomicInteger i = new AtomicInteger();

                fromF("coap://localhost:%d/TestResource?observable=true", PORT.getPort())
                        .process(exchange -> exchange.getMessage().setBody("Hello " + i.get()));

                from("direct:notify")
                        .process(exchange -> i.incrementAndGet())
                        .toF("coap://localhost:%d/TestResource?notify=true", PORT.getPort());

                fromF("coap://localhost:%d/TestResource?observe=true&exceptionHandler=#observerErrors", PORT.getPort())
                        .routeId("observer")
                        .to("mock:observed");
            }
        };
    }
}
