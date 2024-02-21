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
package org.apache.camel.component.mllp;

import java.net.SocketException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mllp.internal.Hl7Util;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.apache.camel.test.junit.rule.mllp.MllpJUnitResourceException;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.mllp.Hl7TestMessageGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.mllp.MllpExceptionTestSupport.*;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class TcpServerConsumerEndOfDataAndValidationTestSupport extends CamelTestSupport {

    static final int CONNECT_TIMEOUT = 500;
    static final int RECEIVE_TIMEOUT = 1000;
    static final int READ_TIMEOUT = 500;

    Logger log = LoggerFactory.getLogger(TcpServerConsumerEndOfDataAndValidationTestSupport.class);

    @RegisterExtension
    MllpClientResource mllpClient = new MllpClientResource();

    @EndpointInject("mock://complete")
    MockEndpoint complete;

    @EndpointInject("mock://failed")
    MockEndpoint failed;

    @EndpointInject("mock://invalid-ex")
    MockEndpoint invalid;

    int expectedCompleteCount;
    int expectedFailedCount;
    int expectedInvalidCount;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();

        context.setUseMDCLogging(true);

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        mllpClient.setMllpHost("localhost");
        mllpClient.setMllpPort(AvailablePortFinder.getNextAvailable());

        return new RouteBuilder() {
            @Override
            public void configure() {
                String routeId = "mllp-test-receiver-route";

                onException(MllpInvalidMessageException.class)
                        .to(invalid);

                onCompletion().onFailureOnly()
                        .to(failed);

                fromF("mllp://%s:%d?autoAck=true&connectTimeout=%d&receiveTimeout=%d&readTimeout=%d&validatePayload=%b&requireEndOfData=%b",
                        mllpClient.getMllpHost(), mllpClient.getMllpPort(), CONNECT_TIMEOUT, RECEIVE_TIMEOUT, READ_TIMEOUT,
                        validatePayload(), requireEndOfData())
                        .routeId(routeId)
                        .log(LoggingLevel.INFO, routeId, "Test route received message")
                        .to(complete);
            }
        };
    }

    abstract boolean validatePayload();

    abstract boolean requireEndOfData();

    protected void setExpectedCounts() {
        complete.expectedMessageCount(expectedCompleteCount);
        failed.expectedMessageCount(expectedFailedCount);
        invalid.expectedMessageCount(expectedInvalidCount);
    }

    @Override
    public void tearDown() throws Exception {
        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);

        super.tearDown();
    }

    @Test
    public void testReceiveSingleMessage() throws Exception {
        expectedCompleteCount = 1;

        setExpectedCounts();

        mllpClient.connect();

        mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage(), 10000);
    }

    @Test
    public void testReceiveSingleMessageWithDelayAfterConnection() throws Exception {
        expectedCompleteCount = 1;

        setExpectedCounts();

        mllpClient.connect();

        Thread.sleep(5000);
        mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage(), 10000);
    }

    @Test
    public void testReceiveMultipleMessages() throws Exception {
        expectedCompleteCount = 5;

        setExpectedCounts();

        mllpClient.connect();

        for (int i = 1; i <= expectedCompleteCount; ++i) {
            mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage(i));
        }
    }

    @Test
    public void testOpenMllpEnvelopeWithReset() throws Exception {
        expectedCompleteCount = 4;
        expectedInvalidCount = 1;

        setExpectedCounts();

        NotifyBuilder notify1 = new NotifyBuilder(context).whenDone(2).create();
        NotifyBuilder notify2 = new NotifyBuilder(context).whenDone(5).create();

        mllpClient.connect();
        mllpClient.setSoTimeout(10000);

        log.info("Sending TEST_MESSAGE_1");
        String acknowledgement1 = mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage(1));

        log.info("Sending TEST_MESSAGE_2");
        String acknowledgement2 = mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage(2));

        assertTrue(notify1.matches(RECEIVE_TIMEOUT, TimeUnit.MILLISECONDS), "First two normal exchanges did not complete");

        log.info("Sending TEST_MESSAGE_3");
        mllpClient.setSendEndOfBlock(false);
        mllpClient.setSendEndOfData(false);
        // Acknowledgement won't come here
        try {
            mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage(3));
        } catch (MllpJUnitResourceException resourceEx) {
            log.info("Expected exception reading response");
        }
        mllpClient.disconnect();
        Thread.sleep(1000);
        mllpClient.connect();

        log.info("Sending TEST_MESSAGE_4");
        mllpClient.setSendEndOfBlock(true);
        mllpClient.setSendEndOfData(true);
        String acknowledgement4 = mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage(4));

        log.info("Sending TEST_MESSAGE_5");
        String acknowledgement5 = mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage(5));

        assertTrue(notify2.matches(RECEIVE_TIMEOUT, TimeUnit.MILLISECONDS), "Remaining exchanges did not complete");

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);

        assertTrue(acknowledgement1.contains("MSA|AA|00001"), "Should be acknowledgment for message 1");
        assertTrue(acknowledgement2.contains("MSA|AA|00002"), "Should be acknowledgment for message 2");
        assertTrue(acknowledgement4.contains("MSA|AA|00004"), "Should be acknowledgment for message 4");
        assertTrue(acknowledgement5.contains("MSA|AA|00005"), "Should be acknowledgment for message 5");
    }

    @Test
    public void testMessageReadTimeout() throws Exception {
        expectedCompleteCount = 1;
        expectedInvalidCount = 1;

        setExpectedCounts();

        NotifyBuilder oneDone = new NotifyBuilder(context).whenDone(1).create();
        NotifyBuilder twoDone = new NotifyBuilder(context).whenDone(2).create();

        // Send one message to establish the connection and start the ConsumerClientSocketThread
        mllpClient.sendFramedData(Hl7TestMessageGenerator.generateMessage());

        assertTrue(oneDone.matches(RECEIVE_TIMEOUT, TimeUnit.MILLISECONDS), "One exchange should have completed");

        mllpClient.setSendEndOfBlock(false);
        mllpClient.setSendEndOfData(false);

        mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage());

        assertTrue(twoDone.matches(RECEIVE_TIMEOUT, TimeUnit.MILLISECONDS), "Two exchanges should have completed");
    }

    @Test
    public void testInitialMessageReadTimeout() throws Exception {
        expectedCompleteCount = 1;

        setExpectedCounts();

        mllpClient.setSendEndOfBlock(false);
        mllpClient.setSendEndOfData(false);

        log.info("Sending first message");
        mllpClient.sendFramedData(Hl7TestMessageGenerator.generateMessage(10001));

        Thread.sleep(RECEIVE_TIMEOUT * 5);

        mllpClient.setSendEndOfBlock(true);
        mllpClient.setSendEndOfData(true);

        try {
            log.info("Attempting to send second message");
            String acknowledgement
                    = mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage(10002));
            assertEquals("", acknowledgement, "If the send doesn't throw an exception, the acknowledgement should be empty");
        } catch (MllpJUnitResourceException expected) {
            assertThat("If the send throws an exception, the cause should be a SocketException", expected.getCause(),
                    instanceOf(SocketException.class));
        }

        mllpClient.disconnect();
        mllpClient.connect();

        log.info("Sending third message");
        mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage(10003));
    }

    @Test
    public abstract void testInvalidMessage() throws Exception;

    protected void runInvalidMessage() {
        setExpectedCounts();

        mllpClient.sendFramedData("INVALID PAYLOAD");
    }

    @Test
    public abstract void testNthInvalidMessage() throws Exception;

    protected void runNthInvalidMessage() throws Exception {
        int messageCount = 10;

        expectedCompleteCount = messageCount - expectedInvalidCount;

        setExpectedCounts();

        int invalidMessageNumber = messageCount / 2;

        NotifyBuilder invalidMessageDone = new NotifyBuilder(context()).whenDone(invalidMessageNumber).create();

        for (int i = 1; i <= messageCount; ++i) {
            if (i == invalidMessageNumber) {
                mllpClient.sendFramedData("INVALID PAYLOAD");
                // The component will reset the connection in this case, so we need to reconnect
                assertTrue(invalidMessageDone.matches(5, TimeUnit.SECONDS),
                        "Exchange with invalid payload should have completed");
                mllpClient.disconnect();
                mllpClient.connect();
            } else {
                String acknowledgement
                        = mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage(i));
                assertNotNull(acknowledgement, "The acknowledgement returned should not be null");
                assertNotEquals(0, acknowledgement.length(), "An acknowledgement should be received for a valid HL7 message");
            }
        }
    }

    @Test
    public abstract void testMessageContainingEmbeddedStartOfBlock() throws Exception;

    protected void runMessageContainingEmbeddedStartOfBlock() throws Exception {
        setExpectedCounts();

        NotifyBuilder done = new NotifyBuilder(context()).whenDone(1).create();

        mllpClient.sendMessageAndWaitForAcknowledgement(
                Hl7TestMessageGenerator.generateMessage().replaceFirst("PID", "PID" + MllpProtocolConstants.START_OF_BLOCK));

        assertTrue(done.matches(15, TimeUnit.SECONDS), "Exchange should have completed");
    }

    @Test
    public abstract void testNthMessageContainingEmbeddedStartOfBlock() throws Exception;

    protected void runNthMessageContainingEmbeddedStartOfBlock() throws Exception {
        int messageCount = 10;

        expectedCompleteCount = messageCount - expectedInvalidCount;

        setExpectedCounts();

        for (int i = 0; i < messageCount; ++i) {
            String message = (i == (messageCount / 2))
                    ? Hl7TestMessageGenerator.generateMessage(i + 1).replaceFirst("PID",
                            "PID" + MllpProtocolConstants.START_OF_BLOCK)
                    : Hl7TestMessageGenerator.generateMessage(i + 1);

            log.debug("Sending message {}", new Hl7Util(5120, LOG_PHI_TRUE).convertToPrintFriendlyString(message));

            mllpClient.sendMessageAndWaitForAcknowledgement(message);
        }
    }

    @Test
    public abstract void testMessageContainingEmbeddedEndOfBlock();

    @Test
    public abstract void testInvalidMessageContainingEmbeddedEndOfBlock() throws Exception;

    protected void runInvalidMessageContainingEmbeddedEndOfBlock() throws Exception {
        int messageCount = 10;

        expectedCompleteCount = messageCount - expectedInvalidCount;

        setExpectedCounts();

        int invalidMessageNumber = messageCount / 2;

        NotifyBuilder invalidMessageDone = new NotifyBuilder(context()).whenDone(invalidMessageNumber).create();

        for (int i = 1; i <= messageCount; ++i) {
            String message = Hl7TestMessageGenerator.generateMessage(i);

            if (i == invalidMessageNumber) {
                mllpClient.sendFramedData(message.replaceFirst("PID", "PID" + MllpProtocolConstants.END_OF_BLOCK));
                assertTrue(invalidMessageDone.matches(5, TimeUnit.SECONDS),
                        "Exchange containing invalid message should have completed");
                // The component may reset the connection in this case, so reconnect if needed
                mllpClient.disconnect();
                mllpClient.connect();
            } else {
                mllpClient.sendMessageAndWaitForAcknowledgement(message);
            }
        }
    }

    @Test
    public abstract void testNthMessageContainingEmbeddedEndOfBlock() throws Exception;

    protected void runNthMessageContainingEmbeddedEndOfBlock() throws Exception {
        int messageCount = 10;

        expectedCompleteCount = messageCount - expectedInvalidCount;

        setExpectedCounts();

        NotifyBuilder done = new NotifyBuilder(context()).whenDone(messageCount / 2).create();

        for (int i = 1; i <= messageCount; ++i) {
            String message = Hl7TestMessageGenerator.generateMessage(i);
            if (i == messageCount / 2) {
                mllpClient.sendFramedData(message.replaceFirst("PID", "PID" + MllpProtocolConstants.END_OF_BLOCK));
                assertTrue(done.matches(15, TimeUnit.SECONDS), "Invalid message should have completed");
                mllpClient.disconnect();
                mllpClient.connect();
            } else {
                String acknowledgement = mllpClient.sendMessageAndWaitForAcknowledgement(message);
                assertNotNull(acknowledgement, "The acknowledgement returned should not be null");
                assertNotEquals(0, acknowledgement.length(), "An acknowledgement should be received for a valid HL7 message");
            }
        }

        assertTrue(done.matches(15, TimeUnit.SECONDS), "Exchanges should have completed");
    }

    @Test
    public void testInitialMessageWithoutEndOfDataByte() {
        setExpectedCounts();

        mllpClient.setSendEndOfData(false);

        mllpClient.sendFramedData(Hl7TestMessageGenerator.generateMessage());
    }

    @Test
    public abstract void testMessageWithoutEndOfDataByte() throws Exception;

    protected void runMessageWithoutEndOfDataByte() throws Exception {
        setExpectedCounts();

        mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage());

        mllpClient.setSendEndOfData(false);

        mllpClient.sendFramedData(Hl7TestMessageGenerator.generateMessage());
    }
}
