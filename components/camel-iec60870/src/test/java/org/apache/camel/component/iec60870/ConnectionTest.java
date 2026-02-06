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
package org.apache.camel.component.iec60870;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.AssertionClause;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.eclipse.neoscada.protocol.iec60870.asdu.types.Value;
import org.eclipse.neoscada.protocol.iec60870.client.AutoConnectClient.State;
import org.eclipse.neoscada.protocol.iec60870.server.data.model.WriteModel.Request;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConnectionTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionTest.class);

    private static final String DIRECT_SEND_S_1 = "direct:sendServer1";

    private static final String DIRECT_SEND_C_1 = "direct:sendClient1";

    private static final String DIRECT_INTERROGATION = "direct:interrogation";

    private static final String DIRECT_READ = "direct:read";

    private static final String DIRECT_STATUS = "direct:status";

    private static final String MOCK_CLIENT_1 = "mock:testClient1";

    private static final String MOCK_CLIENT_2 = "mock:testClient2";

    private static final String MOCK_SERVER_1 = "mock:testServer1";

    @Produce(DIRECT_SEND_S_1)
    protected ProducerTemplate producerServer1;

    @Produce(DIRECT_SEND_C_1)
    protected ProducerTemplate producerClient1;

    @Produce(DIRECT_INTERROGATION)
    protected ProducerTemplate producerInterrogation;

    @Produce(DIRECT_READ)
    protected ProducerTemplate producerRead;

    @Produce(DIRECT_STATUS)
    protected ProducerTemplate producerStatus;

    @EndpointInject(MOCK_CLIENT_1)
    protected MockEndpoint testClient1Endpoint;

    @EndpointInject(MOCK_CLIENT_2)
    protected MockEndpoint testClient2Endpoint;

    @EndpointInject(MOCK_SERVER_1)
    protected MockEndpoint testServer1Endpoint;

    private int testPort;

    @Override
    protected RoutesBuilder createRouteBuilder() {

        testPort = AvailablePortFinder.getNextAvailable();

        return new RouteBuilder() {
            @Override
            public void configure() {
                from(DIRECT_SEND_S_1).toF("iec60870-server:localhost:%s/00-00-00-00-01", testPort);
                fromF("iec60870-client:localhost:%s/00-00-00-00-01", testPort).to(MOCK_CLIENT_1);
                fromF("iec60870-client:localhost:%s/00-00-00-00-02", testPort).to(MOCK_CLIENT_2);

                from(DIRECT_SEND_C_1).toF("iec60870-client:localhost:%s/00-00-00-01-01", testPort);
                fromF("iec60870-server:localhost:%s/00-00-00-01-01", testPort).to(MOCK_SERVER_1);

                // Route for interrogation command
                from(DIRECT_INTERROGATION).toF("iec60870-client:localhost:%s/00-00-00-00-01", testPort);

                // Route for read command
                from(DIRECT_READ).toF("iec60870-client:localhost:%s/00-00-00-00-01", testPort);

                // Route for status command - gets connection state without sending protocol commands
                from(DIRECT_STATUS)
                        .setHeader(Constants.IEC60870_COMMAND_TYPE, constant(Constants.COMMAND_TYPE_STATUS))
                        .toF("iec60870-client:localhost:%s/00-00-00-00-01", testPort);
            }
        };
    }

    @Test
    public void testFloat1() throws InterruptedException {
        this.producerServer1.sendBody(1.23f);

        // expect - count

        this.testClient1Endpoint.setExpectedCount(1);
        this.testClient2Endpoint.setExpectedCount(0);

        // expect

        expectValue(testClient1Endpoint.message(0), assertGoodValue(1.23f));

        // assert

        MockEndpoint.assertIsSatisfied(context, 1_000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testBoolean1() throws InterruptedException {
        this.producerServer1.sendBody(true);

        // expect - count

        this.testClient1Endpoint.setExpectedCount(1);
        this.testClient2Endpoint.setExpectedCount(0);

        // expect

        expectValue(testClient1Endpoint.message(0), assertGoodValue(true));

        // assert

        MockEndpoint.assertIsSatisfied(context, 1_000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testCommand1() throws InterruptedException {
        this.producerClient1.sendBody(true);

        // expect - count

        this.testServer1Endpoint.setExpectedCount(1);

        // expect

        expectRequest(testServer1Endpoint.message(0), expectRequest(true));

        // assert

        MockEndpoint.assertIsSatisfied(context, 2_000, TimeUnit.MILLISECONDS);
        LOG.debug("Received body: {}", testServer1Endpoint.getExchanges().get(0).getIn().getBody());

    }

    private <T> void expectValue(AssertionClause message, Consumer<Value<?>> consumer) {
        message.predicate(exchange -> {
            final Value<?> body = exchange.getIn().getBody(Value.class);
            consumer.accept(body);
            return true;
        });
    }

    private <T> void expectRequest(AssertionClause message, Consumer<Request<?>> consumer) {
        message.predicate(exchange -> {
            final Request<?> body = exchange.getIn().getBody(Request.class);
            consumer.accept(body);
            return true;
        });
    }

    public static Consumer<Value<?>> assertGoodValue(final Object expectedValue) {
        return value -> {
            assertNotNull(value);
            assertEquals(expectedValue, value.getValue());
            assertTrue(value.getQualityInformation().isValid());
            assertTrue(value.getQualityInformation().isTopical());
            assertFalse(value.getQualityInformation().isBlocked());
            assertFalse(value.getQualityInformation().isSubstituted());
        };
    }

    private Consumer<Request<?>> expectRequest(final Object expectedValue) {
        return value -> {
            assertNotNull(value);
            assertEquals(expectedValue, value.getValue());
        };
    }

    /**
     * Test that connection state header is included in consumer messages. Verifies the CamelIec60870ConnectionState
     * header is set to CONNECTED when receiving values.
     */
    @Test
    public void testConnectionStateHeader() throws InterruptedException {
        this.producerServer1.sendBody(42.0f);

        this.testClient1Endpoint.setExpectedCount(1);

        // Verify connection state header is present and shows CONNECTED
        testClient1Endpoint.message(0).predicate(exchange -> {
            State connectionState = exchange.getIn().getHeader(Constants.IEC60870_CONNECTION_STATE, State.class);
            assertNotNull(connectionState, "Connection state header should be present");
            assertEquals(State.CONNECTED, connectionState, "Connection should be CONNECTED");
            return true;
        });

        MockEndpoint.assertIsSatisfied(context, 1_000, TimeUnit.MILLISECONDS);
        LOG.debug("Connection state: {}", testClient1Endpoint.getExchanges().get(0).getIn()
                .getHeader(Constants.IEC60870_CONNECTION_STATE));
    }

    /**
     * Test that connection uptime header is included in consumer messages. Verifies the CamelIec60870ConnectionUptime
     * header shows positive uptime when connected.
     */
    @Test
    public void testConnectionUptimeHeader() throws InterruptedException {
        this.producerServer1.sendBody(99.9f);

        this.testClient1Endpoint.setExpectedCount(1);

        // Verify connection uptime header is present and positive
        testClient1Endpoint.message(0).predicate(exchange -> {
            Long uptime = exchange.getIn().getHeader(Constants.IEC60870_CONNECTION_UPTIME, Long.class);
            assertNotNull(uptime, "Connection uptime header should be present");
            assertTrue(uptime >= 0, "Connection uptime should be non-negative");
            LOG.debug("Connection uptime: {} ms", uptime);
            return true;
        });

        MockEndpoint.assertIsSatisfied(context, 1_000, TimeUnit.MILLISECONDS);
    }

    /**
     * Test that individual quality flag headers are included in consumer messages. Verifies the quality flags (Blocked,
     * Substituted, NotTopical, Valid) are exposed as separate headers.
     */
    @Test
    public void testQualityFlagHeaders() throws InterruptedException {
        this.producerServer1.sendBody(123.45f);

        this.testClient1Endpoint.setExpectedCount(1);

        // Verify individual quality flag headers are present
        testClient1Endpoint.message(0).predicate(exchange -> {
            Boolean blocked = exchange.getIn().getHeader(Constants.IEC60870_QUALITY_BLOCKED, Boolean.class);
            Boolean substituted = exchange.getIn().getHeader(Constants.IEC60870_QUALITY_SUBSTITUTED, Boolean.class);
            Boolean notTopical = exchange.getIn().getHeader(Constants.IEC60870_QUALITY_NOT_TOPICAL, Boolean.class);
            Boolean valid = exchange.getIn().getHeader(Constants.IEC60870_QUALITY_VALID, Boolean.class);

            assertNotNull(blocked, "Quality blocked header should be present");
            assertNotNull(substituted, "Quality substituted header should be present");
            assertNotNull(notTopical, "Quality not topical header should be present");
            assertNotNull(valid, "Quality valid header should be present");

            // Good quality means: not blocked, not substituted, topical, valid
            assertFalse(blocked, "Value should not be blocked");
            assertFalse(substituted, "Value should not be substituted");
            assertFalse(notTopical, "Value should be topical");
            assertTrue(valid, "Value should be valid");

            return true;
        });

        MockEndpoint.assertIsSatisfied(context, 1_000, TimeUnit.MILLISECONDS);
    }

    /**
     * Test sending an interrogation command via the producer. Verifies that the CamelIec60870CommandType=interrogation
     * header triggers an interrogation request.
     */
    @Test
    public void testInterrogationCommand() throws InterruptedException {
        // First, send a value to the server so there's data to interrogate
        this.producerServer1.sendBody(77.7f);

        // Wait for initial value
        this.testClient1Endpoint.setExpectedCount(1);
        MockEndpoint.assertIsSatisfied(context, 1_000, TimeUnit.MILLISECONDS);
        this.testClient1Endpoint.reset();

        // Now send an interrogation command - this should trigger the server to resend all values
        // Using global interrogation (QOI = 20)
        this.producerInterrogation.sendBodyAndHeader(
                null,
                Constants.IEC60870_COMMAND_TYPE,
                Constants.COMMAND_TYPE_INTERROGATION);

        // The interrogation should cause the server to resend data
        // We expect to receive the value again
        this.testClient1Endpoint.setMinimumExpectedMessageCount(1);
        MockEndpoint.assertIsSatisfied(context, 2_000, TimeUnit.MILLISECONDS);

        LOG.debug("Received {} messages after interrogation",
                testClient1Endpoint.getReceivedCounter());
    }

    /**
     * Test sending a group interrogation command with specific QOI. Verifies that the CamelIec60870Qoi header can
     * specify group interrogation (QOI 21-36).
     */
    @Test
    public void testGroupInterrogationCommand() throws InterruptedException {
        // First, send a value to the server
        this.producerServer1.sendBody(88.8f);

        // Wait for initial value
        this.testClient1Endpoint.setExpectedCount(1);
        MockEndpoint.assertIsSatisfied(context, 1_000, TimeUnit.MILLISECONDS);
        this.testClient1Endpoint.reset();

        // Send group 1 interrogation (QOI = 21)
        Exchange exchange = producerInterrogation.send(e -> {
            e.getIn().setHeader(Constants.IEC60870_COMMAND_TYPE, Constants.COMMAND_TYPE_INTERROGATION);
            e.getIn().setHeader(Constants.IEC60870_QOI, (short) 21); // Group 1
        });

        // Command should execute without error
        assertFalse(exchange.isFailed(), "Interrogation command should not fail");

        LOG.debug("Group interrogation command sent successfully");
    }

    /**
     * Test sending a read command via the producer. Verifies that the CamelIec60870CommandType=read header triggers a
     * read request.
     */
    @Test
    public void testReadCommand() throws InterruptedException {
        // First, send a value to the server so there's data to read
        this.producerServer1.sendBody(55.5f);

        // Wait for initial value
        this.testClient1Endpoint.setExpectedCount(1);
        MockEndpoint.assertIsSatisfied(context, 1_000, TimeUnit.MILLISECONDS);
        this.testClient1Endpoint.reset();

        // Send a read command for the specific address
        Exchange exchange = producerRead.send(e -> {
            e.getIn().setHeader(Constants.IEC60870_COMMAND_TYPE, Constants.COMMAND_TYPE_READ);
        });

        // Command should execute without error
        assertFalse(exchange.isFailed(), "Read command should not fail");

        LOG.debug("Read command sent successfully");
    }

    /**
     * Test getting connection status via producer using the 'status' command type. This demonstrates how to get
     * connection state on-demand without sending any IEC 60870 protocol command.
     *
     */
    @Test
    public void testStatusCommand() throws InterruptedException {
        // Send to the status route - the route sets the command type header
        Exchange exchange = producerStatus.send(e -> {
            // No body or headers needed - the route sets command type to 'status'
        });

        // Command should execute without error
        assertFalse(exchange.isFailed(), "Status command should not fail");

        // Verify connection state headers are returned by the producer
        State connectionState = exchange.getMessage().getHeader(Constants.IEC60870_CONNECTION_STATE, State.class);
        Long uptime = exchange.getMessage().getHeader(Constants.IEC60870_CONNECTION_UPTIME, Long.class);

        assertNotNull(connectionState, "Connection state header should be set");
        assertNotNull(uptime, "Connection uptime header should be set");
        assertEquals(State.CONNECTED, connectionState, "Connection should be CONNECTED");
        assertTrue(uptime >= 0, "Uptime should be non-negative");

        LOG.debug("Status command: state={}, uptime={} ms", connectionState, uptime);
    }

}
