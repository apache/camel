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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The consumer's contract at the route seam: a route reads a {@code plc4x:mock:} endpoint whose tag values are
 * programmed in {@link MockDriver}, and only what arrives at the mock endpoint is asserted.
 */
public class Plc4XConsumerRouteTest extends CamelTestSupport {

    private static final String PLC = "plc4x:mock:10.10.10.1/1/1?autoReconnect=true";
    private static final String TRIGGERED = PLC + "&trigger=TRIG&period=100&tag.temperature=TEMP&tag.pressure=PRES";
    private static final String UNTRIGGERED = PLC + "&tag.temperature=TEMP&tag.pressure=PRES";
    private static final String REFUSED = PLC + "&trigger=TRIG&period=50&tag.temperature=TEMP&tag.pressure=PRES";

    @BeforeEach
    void resetMockDriver() {
        MockDriver.reset();
    }

    @AfterAll
    static void leaveMockDriverUnprogrammed() {
        MockDriver.reset();
    }

    @Test
    public void emitsOncePerRisingEdgeOfTheTrigger() throws Exception {
        MockDriver.programRead("TRIG", false, false, true, true, false, true);
        MockDriver.programRead("TEMP", 21);
        MockDriver.programRead("PRES", 1013);
        MockEndpoint result = getMockEndpoint("mock:triggered");
        result.expectedMessageCount(2);
        result.setAssertPeriod(1000);

        context.getRouteController().startRoute("triggered");

        result.assertIsSatisfied(10000);
    }

    @Test
    public void deliversTheDataTagsWithoutTheTrigger() throws Exception {
        MockDriver.programRead("TRIG", false, true);
        MockDriver.programRead("TEMP", 21);
        MockDriver.programRead("PRES", 1013);
        MockEndpoint result = getMockEndpoint("mock:triggered");
        result.expectedMessageCount(1);

        context.getRouteController().startRoute("triggered");

        result.assertIsSatisfied(10000);
        assertEquals(Map.of("temperature", 21, "pressure", 1013), result.getExchanges().get(0).getIn().getBody(),
                "the body holds the data tags, not the trigger");
    }

    @Test
    public void emitsOnceForATriggerThatIsAlreadyTrueOnTheFirstRead() throws Exception {
        MockDriver.programRead("TRIG", true, true, false);
        MockDriver.programRead("TEMP", 21);
        MockDriver.programRead("PRES", 1013);
        MockEndpoint result = getMockEndpoint("mock:triggered");
        result.expectedMessageCount(1);
        result.setAssertPeriod(1000);

        context.getRouteController().startRoute("triggered");

        result.assertIsSatisfied(10000);
    }

    @Test
    public void stopsReadingWhenTheRouteStopsAndResumesWithoutDuplicatesAfterARestart() throws Exception {
        MockDriver.programRead("TRIG", false, true);
        MockDriver.programRead("TEMP", 21);
        MockDriver.programRead("PRES", 1013);
        MockEndpoint result = getMockEndpoint("mock:triggered");
        result.expectedMessageCount(3);
        result.setAssertPeriod(1000);

        context.getRouteController().startRoute("triggered");
        await().atMost(10, TimeUnit.SECONDS).until(() -> result.getReceivedCounter() == 1);
        context.getRouteController().stopRoute("triggered");

        MockDriver.programRead("TRIG", false, true, false, true, false);
        await().during(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).until(() -> result.getReceivedCounter() == 1);

        context.getRouteController().startRoute("triggered");

        result.assertIsSatisfied(10000);
    }

    @Test
    public void readsTheTagsOnceWithoutATrigger() throws Exception {
        MockDriver.programRead("TEMP", 21);
        MockDriver.programRead("PRES", 1013);
        MockEndpoint result = getMockEndpoint("mock:untriggered");
        result.expectedMessageCount(1);
        result.setAssertPeriod(1000);

        context.getRouteController().startRoute("untriggered");

        result.assertIsSatisfied(10000);
        assertEquals(Map.of("temperature", 21, "pressure", 1013), result.getExchanges().get(0).getIn().getBody());
    }

    @Test
    public void aRefusedConnectionNeitherFailsTheRouteStartNorFloodsTheLog() throws Exception {
        MockDriver.programRead("TRIG", false, true);
        MockDriver.programRead("TEMP", 21);
        MockDriver.programRead("PRES", 1013);
        MockDriver.refuseConnections();
        MockEndpoint result = getMockEndpoint("mock:refused");
        result.expectedMessageCount(0);
        result.setAssertPeriod(1000);
        WarningCapture warnings = new WarningCapture();

        try {
            warnings.attach();
            context.getRouteController().startRoute("refused");
            assertTrue(context.getRouteController().getRouteStatus("refused").isStarted());
            // the connection set up on start and at least one connection for reading the trigger were refused
            await().atMost(10, TimeUnit.SECONDS).until(() -> MockDriver.refusedConnections() >= 2);
            result.assertIsSatisfied();
        } finally {
            warnings.detach();
        }

        // more than 40 periods of 50 ms have elapsed; a warning per period would be a flood
        assertTrue(warnings.messages().size() <= 5, () -> "Too many warnings: " + warnings.messages());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(TRIGGERED).routeId("triggered").autoStartup(false).to("mock:triggered");
                from(UNTRIGGERED).routeId("untriggered").autoStartup(false).to("mock:untriggered");
                from(REFUSED).routeId("refused").autoStartup(false).to("mock:refused");
            }
        };
    }

    /**
     * Captures WARN and ERROR events of the component and PLC4X loggers.
     */
    private static final class WarningCapture extends AbstractAppender {

        private final List<String> messages = new CopyOnWriteArrayList<>();

        WarningCapture() {
            super("plc4x-warnings", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            String logger = event.getLoggerName();
            if (event.getLevel().isMoreSpecificThan(Level.WARN)
                    && (logger.startsWith("org.apache.camel.component.plc4x") || logger.startsWith("org.apache.plc4x"))) {
                messages.add(event.getLevel() + " " + logger + " " + event.getMessage().getFormattedMessage());
            }
        }

        void attach() {
            start();
            LoggerContext loggerContext = LoggerContext.getContext(false);
            loggerContext.getConfiguration().getRootLogger().addAppender(this, Level.WARN, null);
            loggerContext.updateLoggers();
        }

        void detach() {
            LoggerContext loggerContext = LoggerContext.getContext(false);
            loggerContext.getConfiguration().getRootLogger().removeAppender(getName());
            loggerContext.updateLoggers();
            stop();
        }

        List<String> messages() {
            return messages;
        }
    }
}
