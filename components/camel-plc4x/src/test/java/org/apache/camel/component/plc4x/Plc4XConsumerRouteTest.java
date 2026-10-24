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
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The consumer's contract at the route seam: a route reads a {@code plc4x:mock:} endpoint whose tag values are
 * programmed in {@link MockDriver}, and only what arrives at the mock endpoint is asserted.
 */
public class Plc4XConsumerRouteTest extends CamelTestSupport {

    private static final String PLC = "plc4x:mock:10.10.10.1/1/1?autoReconnect=true";
    private static final String TRIGGERED = PLC + "&trigger=TRIG&period=100&tag.temperature=TEMP&tag.pressure=PRES";
    private static final String UNTRIGGERED = PLC + "&tag.temperature=TEMP&tag.pressure=PRES";
    private static final String REPORTED = PLC + "&trigger=TRIG&period=50&tag.temperature=TEMP&tag.pressure=PRES"
                                           + "&exceptionHandler=#pollFailures";
    private static final String WITHOUT_AUTO_RECONNECT
            = "plc4x:mock:10.10.10.1/1/1?trigger=TRIG&period=50&tag.temperature=TEMP";
    private static final String RESERVED_TAG_NAME = PLC + "&trigger=TRIG&period=50&tag.TRIGGER_VAR=TEMP";
    private static final String WITHOUT_PERIOD = PLC + "&trigger=TRIG&tag.temperature=TEMP";
    private static final String DRIVER_PARAMETERS = PLC + "&trigger=TRIG&period=100&tag.temperature=TEMP"
                                                    + "&exceptionHandler=#pollFailures&exchangePattern=InOnly"
                                                    + "&remote-rack=1&request-timeout-ms=500&percent-encoded=%25DB1";

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
    public void deliversTheDataTagsOfTheReadThatSawTheEdge() throws Exception {
        MockDriver.programRead("TRIG", false, true, false, true);
        MockDriver.programRead("TEMP", 21, 22, 23, 24);
        MockDriver.programRead("PRES", 1011, 1012, 1013, 1014);
        MockEndpoint result = getMockEndpoint("mock:triggered");
        result.expectedMessageCount(2);
        result.setAssertPeriod(1000);

        context.getRouteController().startRoute("triggered");

        result.assertIsSatisfied(10000);
        assertEquals(List.of(Map.of("temperature", 22, "pressure", 1012), Map.of("temperature", 24, "pressure", 1014)),
                result.getExchanges().stream().map(exchange -> exchange.getIn().getBody()).toList());
    }

    @Test
    public void reportsEachFailedPollAndResumesWhenThePlcReturns() throws Exception {
        MockDriver.programRead("TRIG", false, true);
        MockDriver.programRead("TEMP", 21);
        MockDriver.programRead("PRES", 1013);
        MockDriver.refuseConnections();
        MockEndpoint result = getMockEndpoint("mock:reported");
        result.expectedMessageCount(1);
        result.setAssertPeriod(1000);

        context.getRouteController().startRoute("reported");
        assertTrue(context.getRouteController().getRouteStatus("reported").isStarted());
        await().atMost(10, TimeUnit.SECONDS).until(() -> pollFailures.count() >= 3);
        assertEquals(0, result.getReceivedCounter(), "nothing is delivered while the PLC refuses connections");

        MockDriver.acceptConnections();

        result.assertIsSatisfied(10000);
        assertTrue(pollFailures.causes().stream().allMatch(PlcConnectionException.class::isInstance),
                () -> "Unexpected causes: " + pollFailures.causes());
    }

    @Test
    public void pollsOnTheOneConnectionOfTheEndpoint() throws Exception {
        MockDriver.programRead("TRIG", false, false, false, false, false, true);
        MockDriver.programRead("TEMP", 21);
        MockDriver.programRead("PRES", 1013);
        MockEndpoint result = getMockEndpoint("mock:reported");
        result.expectedMessageCount(1);

        context.getRouteController().startRoute("reported");

        result.assertIsSatisfied(10000);
        assertEquals(1, MockDriver.connectionStrings().size(), "six polls use the connection set up on start");
    }

    @Test
    public void resumesOnANewConnectionWhenTheDroppedOneCannotConnectAgain() throws Exception {
        MockDriver.programRead("TRIG", false, true);
        MockDriver.programRead("TEMP", 21);
        MockDriver.programRead("PRES", 1013);
        MockEndpoint result = getMockEndpoint("mock:reported");
        result.expectedMessageCount(2);
        result.setAssertPeriod(1000);

        context.getRouteController().startRoute("reported");
        await().atMost(10, TimeUnit.SECONDS).until(() -> result.getReceivedCounter() == 1);
        MockDriver.dropConnections();
        MockDriver.programRead("TRIG", false, true);

        result.assertIsSatisfied(10000);
    }

    @Test
    public void aFailedPollForgetsTheTriggerSoAStillTrueTriggerEmitsAgain() throws Exception {
        MockDriver.programRead("TRIG", true, true, MockDriver.failedRead(), true, true);
        MockDriver.programRead("TEMP", 21);
        MockDriver.programRead("PRES", 1013);
        MockEndpoint result = getMockEndpoint("mock:reported");
        result.expectedMessageCount(2);
        result.setAssertPeriod(1000);

        context.getRouteController().startRoute("reported");

        result.assertIsSatisfied(10000);
        assertEquals(1, pollFailures.count());
    }

    @Test
    public void aTagReadWithAnErrorCodeFailsThePoll() throws Exception {
        MockDriver.programRead("TRIG", false, true, true);
        MockDriver.programRead("TEMP", 21, MockDriver.responseCode(PlcResponseCode.NOT_FOUND), 23);
        MockDriver.programRead("PRES", 1013);
        MockEndpoint result = getMockEndpoint("mock:reported");
        result.expectedMessageCount(1);
        result.setAssertPeriod(1000);

        context.getRouteController().startRoute("reported");

        result.assertIsSatisfied(10000);
        assertEquals(Map.of("temperature", 23, "pressure", 1013), result.getExchanges().get(0).getIn().getBody());
        assertEquals(1, pollFailures.count());
        assertTrue(pollFailures.causes().get(0).getMessage().contains("NOT_FOUND"),
                () -> "Unexpected cause: " + pollFailures.causes().get(0));
    }

    @Test
    public void aTriggerThatIsNotABooleanFailsThePoll() throws Exception {
        MockDriver.programRead("TRIG", 1, true);
        MockDriver.programRead("TEMP", 21);
        MockDriver.programRead("PRES", 1013);
        MockEndpoint result = getMockEndpoint("mock:reported");
        result.expectedMessageCount(1);
        result.setAssertPeriod(1000);

        context.getRouteController().startRoute("reported");

        result.assertIsSatisfied(10000);
        assertEquals(1, pollFailures.count(), () -> "Unexpected failures: " + pollFailures.causes());
        String message = pollFailures.causes().get(0).getMessage();
        assertTrue(message.contains("TRIG") && message.contains(Integer.class.getName()), message);
    }

    @Test
    public void aReadThatCompletesWhileTheRouteStopsIsNotDelivered() throws Exception {
        MockDriver.programRead("TRIG", true);
        MockDriver.programRead("TEMP", 21);
        MockDriver.programRead("PRES", 1013);
        MockDriver.holdReads();
        MockEndpoint result = getMockEndpoint("mock:reported");
        result.expectedMessageCount(0);

        context.getRouteController().startRoute("reported");
        MockDriver.awaitHeldRead();
        context.getRouteController().stopRoute("reported");
        MockDriver.releaseReads();

        // with no message expected, the mock endpoint waits the whole time before it is satisfied
        result.assertIsSatisfied(1000);
        assertEquals(0, pollFailures.count(), () -> "Unexpected failures: " + pollFailures.causes());
    }

    @Test
    public void aRefusedConnectionFailsTheRouteStartWithoutAutoReconnect() {
        MockDriver.refuseConnections();

        Exception e = assertThrows(Exception.class,
                () -> context.getRouteController().startRoute("withoutAutoReconnect"));

        assertNotNull(causeOfType(e, PlcConnectionException.class), () -> "No PlcConnectionException in " + e);
    }

    @Test
    public void aTagNamedLikeTheTriggerFailsTheRouteStart() {
        Exception e = assertThrows(Exception.class, () -> context.getRouteController().startRoute("reservedTagName"));

        Throwable cause = causeOfType(e, IllegalArgumentException.class);
        assertNotNull(cause, () -> "No IllegalArgumentException in " + e);
        assertTrue(cause.getMessage().contains(Constants.TRIGGER), cause::getMessage);
    }

    @Test
    public void aTriggerWithoutAPeriodFailsTheRouteStart() {
        Exception e = assertThrows(Exception.class, () -> context.getRouteController().startRoute("withoutPeriod"));

        Throwable cause = causeOfType(e, IllegalArgumentException.class);
        assertNotNull(cause, () -> "No IllegalArgumentException in " + e);
        assertTrue(cause.getMessage().contains("period"), cause::getMessage);
    }

    @Test
    public void handsTheDriverOnlyItsOwnConnectionParameters() throws Exception {
        MockDriver.programRead("TRIG", true);
        MockDriver.programRead("TEMP", 21);
        MockEndpoint result = getMockEndpoint("mock:driverParameters");
        result.expectedMessageCount(1);

        context.getRouteController().startRoute("driverParameters");

        result.assertIsSatisfied(10000);
        assertEquals(Set.of("mock:10.10.10.1/1/1?percent-encoded=%25DB1&remote-rack=1&request-timeout-ms=500"),
                Set.copyOf(MockDriver.connectionStrings()));
        String uri = context.getEndpoint(DRIVER_PARAMETERS, Plc4XEndpoint.class).getUri();
        assertTrue(uri.contains("trigger=TRIG") && uri.contains("autoReconnect=true"),
                () -> "The endpoint no longer reports its configured uri: " + uri);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(TRIGGERED).routeId("triggered").autoStartup(false).to("mock:triggered");
                from(UNTRIGGERED).routeId("untriggered").autoStartup(false).to("mock:untriggered");
                from(REPORTED).routeId("reported").autoStartup(false).to("mock:reported");
                from(WITHOUT_AUTO_RECONNECT).routeId("withoutAutoReconnect").autoStartup(false)
                        .to("mock:withoutAutoReconnect");
                from(RESERVED_TAG_NAME).routeId("reservedTagName").autoStartup(false).to("mock:reservedTagName");
                from(WITHOUT_PERIOD).routeId("withoutPeriod").autoStartup(false).to("mock:withoutPeriod");
                from(DRIVER_PARAMETERS).routeId("driverParameters").autoStartup(false).to("mock:driverParameters");
            }
        };
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("pollFailures", pollFailures);
    }

    private static Throwable causeOfType(Throwable exception, Class<? extends Throwable> type) {
        Throwable cause = exception;
        while (cause != null && !type.isInstance(cause)) {
            cause = cause.getCause();
        }
        return cause;
    }

    /**
     * Collects the failures the consumer reports to its exception handler.
     */
    private static final class PollFailures implements ExceptionHandler {

        private final List<Throwable> causes = new CopyOnWriteArrayList<>();

        @Override
        public void handleException(Throwable exception) {
            causes.add(exception);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            causes.add(exception);
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            causes.add(exception);
        }

        int count() {
            return causes.size();
        }

        List<Throwable> causes() {
            return causes;
        }
    }

}
