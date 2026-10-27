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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.plc4x.java.api.exceptions.PlcException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a route reports a PLC that keeps failing its requests: the triggered consumer logs the first failed poll of a run
 * at WARN and the end of the run at INFO, instead of a WARN with a stack trace on every poll, while
 * {@code bridgeErrorHandler} still gets every failure.
 */
public class Plc4XRepeatedFailureRouteTest extends CamelTestSupport {

    private static final String PLC = "plc4x:mock:10.10.10.1/1/1?trigger=TRIG&period=50&tag.temperature=TEMP";
    private static final String BRIDGED = PLC + "&bridgeErrorHandler=true";

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

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(PLC).routeId("logged").autoStartup(false).to("mock:logged");
                from(BRIDGED).routeId("bridged").autoStartup(false)
                        .onException(Exception.class).handled(true).to("mock:failures").end()
                        .to("mock:bridged");
            }
        };
    }

    private static List<String> messages(List<LogEvent> events) {
        return events.stream().map(event -> event.getLevel() + " " + event.getMessage().getFormattedMessage()).toList();
    }
}
