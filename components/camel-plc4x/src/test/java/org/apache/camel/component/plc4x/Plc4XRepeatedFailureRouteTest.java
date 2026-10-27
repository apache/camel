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
package org.apache.camel.component.plc4x;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.exceptions.PlcException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a route reports a PLC that keeps failing its requests: the triggered consumer logs the first failed poll of a run
 * at WARN and the end of the run at INFO, instead of a WARN with a stack trace on every poll, while
 * {@code bridgeErrorHandler} still gets every failure. Without {@code autoReconnect}, a lost connection shuts the
 * endpoint down once, and the triggered consumer stops polling until the route is restarted.
 */
public class Plc4XRepeatedFailureRouteTest extends CamelTestSupport {

    private static final String PLC = "plc4x:mock:10.10.10.1/1/1?trigger=TRIG&period=50&tag.temperature=TEMP";
    private static final String BRIDGED = PLC + "&bridgeErrorHandler=true";
    private static final String REPORTED = PLC + "&exceptionHandler=#pollFailures";
    private static final String WRITE = "plc4x:mock:10.10.10.2/1/1";
    private static final String SHUTDOWN = "Connection lost and auto-reconnect is turned off";
    private static final String POLLING_STOPS = "polling stops until the route is restarted";

    private final PollFailures pollFailures = new PollFailures();

    @BeforeEach
    void resetMockDriver() {
        MockDriver.reset();
    }

    @AfterAll
    static void leaveMockDriverUnprogrammed() {
        MockDriver.reset();
    }

    @Test
    public void logsARunOfFailedPollsOnceAtWarnAndItsEndOnceAtInfo() throws Exception {
        Object failed = MockDriver.failedRead();
        // two runs of failed polls, each followed by successful polls, and a rising edge after them
        MockDriver.programRead("TRIG", failed, failed, failed, false, false, false, failed, failed, false, false, true);
        MockDriver.programRead("TEMP", 21);
        MockEndpoint result = getMockEndpoint("mock:logged");
        result.expectedMessageCount(1);

        try (LogCapture log = new LogCapture(Plc4XTriggeredConsumer.class)) {
            context.getRouteController().startRoute("logged");
            result.assertIsSatisfied(10000);

            List<LogEvent> events = log.events();
            assertEquals(List.of(Level.WARN, Level.DEBUG, Level.DEBUG, Level.INFO, Level.WARN, Level.DEBUG, Level.INFO),
                    events.stream().map(LogEvent::getLevel).toList(), () -> "Unexpected log: " + messages(events));
            assertEquals(5, log.eventsContaining("Failed polling endpoint").size(),
                    () -> "Unexpected log: " + messages(events));
            assertNotNull(events.get(0).getThrown(), "the first failed poll of a run is logged with its stack trace");
            assertNotNull(events.get(4).getThrown(), "the first failed poll of a run is logged with its stack trace");
            assertNull(events.get(1).getThrown(), "a following failed poll of a run is logged without its stack trace");
            // the error counter of the consumer still counts each failed poll of a run
            String firstEnd = events.get(3).getMessage().getFormattedMessage();
            assertTrue(firstEnd.endsWith("after 3 failed polls"), firstEnd);
            String secondEnd = events.get(6).getMessage().getFormattedMessage();
            assertTrue(secondEnd.endsWith("after 2 failed polls"), secondEnd);
        }
    }

    @Test
    public void bridgeErrorHandlerRoutesEveryFailedPollToTheErrorHandlerOfTheRoute() throws Exception {
        Object failed = MockDriver.failedRead();
        MockDriver.programRead("TRIG", failed, failed, failed, false);
        MockDriver.programRead("TEMP", 21);
        MockEndpoint failures = getMockEndpoint("mock:failures");
        failures.expectedMessageCount(3);
        failures.setAssertPeriod(500);

        context.getRouteController().startRoute("bridged");

        failures.assertIsSatisfied(10000);
        assertTrue(failures.getExchanges().stream()
                .allMatch(exchange -> exchange.getProperty(Exchange.EXCEPTION_CAUGHT) instanceof PlcException),
                () -> "Unexpected failures: " + failures.getExchanges());
    }

    @Test
    public void aLostConnectionWithoutAutoReconnectStopsPollingUntilTheRouteIsRestarted() throws Exception {
        MockDriver.programRead("TRIG", false);
        MockDriver.programRead("TEMP", 21);
        MockEndpoint result = getMockEndpoint("mock:reported");

        try (LogCapture log = new LogCapture(Plc4XEndpoint.class)) {
            context.getRouteController().startRoute("reported");
            Plc4XTriggeredConsumer consumer = (Plc4XTriggeredConsumer) context.getRoute("reported").getConsumer();
            await().atMost(10, TimeUnit.SECONDS).until(() -> consumer.getSuccessCounter() >= 2);

            MockDriver.dropConnections();

            // the poll that finds the connection lost fails, and no poll follows it
            await().atMost(10, TimeUnit.SECONDS).until(() -> !pollFailures.failures().isEmpty());
            await().during(500, TimeUnit.MILLISECONDS).atMost(1, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertEquals(1, pollFailures.failures().size(), "the failed polls"));
            assertEquals(1, log.eventsContaining(SHUTDOWN).size(), "the endpoint is shut down once");
            assertEquals(1, consumer.getErrorCounter(), "the failed poll keeps the health check of the consumer down");
            PollFailures.Failure failure = pollFailures.failures().get(0);
            assertInstanceOf(PlcConnectionException.class, failure.cause());
            assertFalse(failure.interrupted(), "the failure is reported on a thread that is not interrupted");

            // the restarted route sets up a new connection and polls again
            MockDriver.programRead("TRIG", false, true);
            result.expectedMessageCount(1);
            context.getRouteController().stopRoute("reported");
            context.getRouteController().startRoute("reported");
            result.assertIsSatisfied(10000);

            // and a loss of the new connection shuts the endpoint down again
            MockDriver.dropConnections();
            await().atMost(10, TimeUnit.SECONDS).until(() -> log.eventsContaining(SHUTDOWN).size() == 2);
            await().atMost(10, TimeUnit.SECONDS).until(() -> pollFailures.failures().size() == 2);
        }
    }

    @Test
    public void logsTheFailedPollThatStopsPollingAtWarnAfterAFailedRead() throws Exception {
        MockDriver.programRead("TRIG", false, MockDriver.failedRead());
        MockDriver.programRead("TEMP", 21);

        try (LogCapture log = new LogCapture(Plc4XTriggeredConsumer.class)) {
            context.getRouteController().startRoute("logged");
            Plc4XTriggeredConsumer consumer = (Plc4XTriggeredConsumer) context.getRoute("logged").getConsumer();
            // a read fails as the PLC goes away, and a later poll finds the connection lost within the same run
            await().atMost(10, TimeUnit.SECONDS).until(() -> consumer.getErrorCounter() >= 1);

            MockDriver.dropConnections();

            await().atMost(10, TimeUnit.SECONDS).until(() -> !log.eventsContaining(POLLING_STOPS).isEmpty());
            assertEquals(List.of(Level.WARN),
                    log.eventsContaining(POLLING_STOPS).stream().map(LogEvent::getLevel).toList(),
                    () -> "Unexpected log: " + messages(log.events()));
        }
    }

    @Test
    public void aProducerReportsALostConnectionWithoutAutoReconnectOnce() throws Exception {
        Map<String, Map<String, Object>> tags = Map.of("temperature", Map.of("%DB1:DINT", 21));
        template.sendBody(WRITE, tags);

        try (LogCapture log = new LogCapture(Plc4XEndpoint.class)) {
            MockDriver.dropConnections();

            for (int i = 0; i < 3; i++) {
                Exchange exchange = template.send(WRITE, e -> e.getIn().setBody(tags));
                assertInstanceOf(PlcConnectionException.class, exchange.getException(), "each write fails");
            }
            assertEquals(1, log.eventsContaining(SHUTDOWN).size(), "the endpoint is shut down once");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(PLC).routeId("logged").autoStartup(false).to("mock:logged");
                from(BRIDGED).routeId("bridged").autoStartup(false)
                        .onException(Exception.class).handled(true).to("mock:failures").end()
                        .to("mock:bridged");
                from(REPORTED).routeId("reported").autoStartup(false).to("mock:reported");
            }
        };
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("pollFailures", pollFailures);
    }

    private static List<String> messages(List<LogEvent> events) {
        return events.stream().map(event -> event.getLevel() + " " + event.getMessage().getFormattedMessage()).toList();
    }
}
