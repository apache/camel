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
package org.apache.camel.component.camelevent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With backpressurePolicy=Fail, an event that does not fit in the full queue is reported to the exception handler of
 * the consumer: an exception thrown to Camel, which fired the event, would only be logged and ignored.
 */
class EventBackpressureFailTest extends CamelTestSupport {

    private final List<Throwable> handled = new CopyOnWriteArrayList<>();
    private final CountDownLatch processing = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @Test
    void anEventThatDoesNotFitIsReportedToTheExceptionHandler() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:events");
        mock.expectedMessageCount(2);

        // the only consumer thread takes the first event and waits in the route
        template.sendBody("direct:source", "one");
        assertTrue(processing.await(20, TimeUnit.SECONDS), "the first event was not processed");
        // the second one fills the queue of size 1, and the third one does not fit
        template.sendBody("direct:source", "two");
        template.sendBody("direct:source", "three");

        await().atMost(20, TimeUnit.SECONDS).until(() -> !handled.isEmpty());
        release.countDown();

        mock.assertIsSatisfied();
        assertEquals(1, handled.size(), "handled: " + handled);
        IllegalStateException e = assertInstanceOf(IllegalStateException.class, handled.get(0));
        assertTrue(e.getMessage().contains("Event queue full"), e.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        context.getRegistry().bind("recorder", new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                handled.add(exception);
            }

            @Override
            public void handleException(String message, Throwable exception) {
                handled.add(exception);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                handled.add(exception);
            }
        });
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("event:ExchangeCompleted?async=true&asyncPoolSize=1&asyncQueueSize=1&backpressurePolicy=Fail"
                     + "&include=source&exceptionHandler=#recorder")
                        .routeId("events")
                        .process(e -> {
                            processing.countDown();
                            release.await(20, TimeUnit.SECONDS);
                        })
                        .to("mock:events");

                from("direct:source").routeId("source").log("${body}");
            }
        };
    }
}
