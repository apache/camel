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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.test.mllp.Hl7TestMessageGenerator;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.instanceOf;

public abstract class TcpServerConsumerEndOfDataAndValidationTestSupport extends CamelTestSupport {
    static final int CONNECT_TIMEOUT = 500;
    static final int RECEIVE_TIMEOUT = 1000;
    static final int READ_TIMEOUT = 500;

    @Rule
    public MllpClientResource mllpClient = new MllpClientResource();

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
        context.setName(this.getClass().getSimpleName());

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        mllpClient.setMllpHost("localhost");
        mllpClient.setMllpPort(AvailablePortFinder.getNextAvailable());

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                String routeId = "mllp-test-receiver-route";

                onException(MllpInvalidMessageException.class)
                    .to(invalid);

                onCompletion().onFailureOnly()
                    .to(failed);

                fromF("mllp://%s:%d?autoAck=true&connectTimeout=%d&receiveTimeout=%d&readTimeout=%d&validatePayload=%b&requireEndOfData=%b",
                    mllpClient.getMllpHost(), mllpClient.getMllpPort(), CONNECT_TIMEOUT, RECEIVE_TIMEOUT, READ_TIMEOUT, validatePayload(), requireEndOfData())
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
        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);

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

        assertTrue("First two normal exchanges did not complete", notify1.matches(RECEIVE_TIMEOUT, TimeUnit.MILLISECONDS));

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

        assertTrue("Remaining exchanges did not complete", notify2.matches(RECEIVE_TIMEOUT, TimeUnit.MILLISECONDS));

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);

        assertTrue("Should be acknowledgment for message 1", acknowledgement1.contains("MSA|AA|00001"));
        assertTrue("Should be acknowledgment for message 2", acknowledgement2.contains("MSA|AA|00002"));
        assertTrue("Should be acknowledgment for message 4", acknowledgement4.contains("MSA|AA|00004"));
        assertTrue("Should be acknowledgment for message 5", acknowledgement5.contains("MSA|AA|00005"));
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

        assertTrue("One exchange should have completed", oneDone.matches(RECEIVE_TIMEOUT, TimeUnit.MILLISECONDS));

        mllpClient.setSendEndOfBlock(false);
        mllpClient.setSendEndOfData(false);

        mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage());

        assertTrue("Two exchanges should have completed", twoDone.matches(RECEIVE_TIMEOUT, TimeUnit.MILLISECONDS));
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
            String acknowledgement = mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage(10002));
            assertEquals("If the send doesn't throw an exception, the acknowledgement should be empty", "", acknowledgement);
        } catch (MllpJUnitResourceException expected) {
            assertThat("If the send throws an exception, the cause should be a SocketException", expected.getCause(), instanceOf(SocketException.class));
        }

        mllpClient.disconnect();
        mllpClient.connect();

        log.info("Sending third message");
        mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage(10003));
    }

    @Test
    public abstract void testInvalidMessage() throws Exception;

    protected void runInvalidMessage() throws Exception {
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
                assertTrue("Exchange with invalid payload should have completed", invalidMessageDone.matches(5, TimeUnit.SECONDS));
                mllpClient.disconnect();
                mllpClient.connect();
            } else {
                String acknowledgement = mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage(i));
                assertNotNull("The acknowledgement returned should not be null", acknowledgement);
                assertNotEquals("An acknowledgement should be received for a valid HL7 message", 0, acknowledgement.length());
            }
        }
    }

    @Test
    public abstract void testMessageContainingEmbeddedStartOfBlock() throws Exception;

    protected void runMessageContainingEmbeddedStartOfBlock() throws Exception {
        setExpectedCounts();

        NotifyBuilder done = new NotifyBuilder(context()).whenDone(1).create();

        mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage().replaceFirst("PID", "PID" + MllpProtocolConstants.START_OF_BLOCK));

        assertTrue("Exchange should have completed", done.matches(15, TimeUnit.SECONDS));
    }


    @Test
    public abstract void testNthMessageContainingEmbeddedStartOfBlock() throws Exception;

    protected void runNthMessageContainingEmbeddedStartOfBlock() throws Exception {
        int messageCount = 10;

        expectedCompleteCount = messageCount - expectedInvalidCount;

        setExpectedCounts();

        for (int i = 0; i < messageCount; ++i) {
            String message = (i == (messageCount / 2))
                ? Hl7TestMessageGenerator.generateMessage(i + 1).replaceFirst("PID", "PID" + MllpProtocolConstants.START_OF_BLOCK)
                : Hl7TestMessageGenerator.generateMessage(i + 1);

            log.debug("Sending message {}", Hl7Util.convertToPrintFriendlyString(message));

            mllpClient.sendMessageAndWaitForAcknowledgement(message);
        }
    }

    @Test
    public abstract void testMessageContainingEmbeddedEndOfBlock() throws Exception;

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
                assertTrue("Exchange containing invalid message should have completed", invalidMessageDone.matches(5, TimeUnit.SECONDS));
                // The component may reset the connection in this case, so reconnect if needed
                /*
                // TODO: Figure out why this isn't working
                try {
                    mllpClient.checkConnection();
                } catch (MllpJUnitResourceException checkConnectionEx) {
                    mllpClient.disconnect();
                    mllpClient.connect();
                }
                */
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
                assertTrue("Invalid message should have completed", done.matches(15, TimeUnit.SECONDS));
                mllpClient.disconnect();
                mllpClient.connect();
            } else {
                String acknowledgement = mllpClient.sendMessageAndWaitForAcknowledgement(message);
                assertNotNull("The acknowledgement returned should not be null", acknowledgement);
                assertNotEquals("An acknowledgement should be received for a valid HL7 message", 0, acknowledgement.length());
            }
        }

        assertTrue("Exchanges should have completed", done.matches(15, TimeUnit.SECONDS));
    }

    @Test
    public void testInitialMessageWithoutEndOfDataByte() throws Exception {
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

