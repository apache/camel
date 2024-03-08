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

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpServerResource;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.mllp.Hl7TestMessageGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class TcpClientProducerEndOfDataAndValidationTestSupport extends CamelTestSupport {
    static final int RECEIVE_TIMEOUT = 1000;
    static final int READ_TIMEOUT = 500;

    static final String TEST_MESSAGE
            = "MSH|^~\\&|REQUESTING|ICE|INHOUSE|RTH00|20161206193919||ORM^O01|00001|D|2.3|||||||" + '\r'
              + "PID|1||ICE999999^^^ICE^ICE||Testpatient^Testy^^^Mr||19740401|M|||123 Barrel Drive^^^^SW18 4RT|||||2||||||||||||||"
              + '\r'
              + "NTE|1||Free text for entering clinical details|" + '\r'
              + "PV1|1||^^^^^^^^Admin Location|||||||||||||||NHS|" + '\r'
              + "ORC|NW|213||175|REQ||||20080808093202|ahsl^^Administrator||G999999^TestDoctor^GPtests^^^^^^NAT|^^^^^^^^Admin Location | 819600|200808080932||RTH00||ahsl^^Administrator||"
              + '\r'
              + "OBR|1|213||CCOR^Serum Cortisol ^ JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||"
              + '\r'
              + "OBR|2|213||GCU^Serum Copper ^ JRH06 |||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||"
              + '\r'
              + "OBR|3|213||THYG^Serum Thyroglobulin ^JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||"
              + '\r'
              + '\n';

    static final String EXPECTED_AA = "MSH|^~\\&|INHOUSE|RTH00|REQUESTING|ICE|20161206193919||ACK^O01|00001|D|2.3|||||||" + '\r'
                                      + "MSA|AA|00001|" + '\r'
                                      + '\n';

    static final String EXPECTED_AR = "MSH|^~\\&|INHOUSE|RTH00|REQUESTING|ICE|20161206193919||ACK^O01|00001|D|2.3|||||||" + '\r'
                                      + "MSA|AR|00001|" + '\r'
                                      + '\n';

    static final String EXPECTED_AE = "MSH|^~\\&|INHOUSE|RTH00|REQUESTING|ICE|20161206193919||ACK^O01|00001|D|2.3|||||||" + '\r'
                                      + "MSA|AE|00001|" + '\r'
                                      + '\n';

    @RegisterExtension
    public MllpServerResource mllpServer = new MllpServerResource("localhost", AvailablePortFinder.getNextAvailable());

    @EndpointInject("direct://source")
    protected ProducerTemplate source;

    @EndpointInject("mock://aa-ack")
    protected MockEndpoint aa;
    @EndpointInject("mock://ae-nack")
    protected MockEndpoint ae;
    @EndpointInject("mock://ar-nack")
    protected MockEndpoint ar;

    @EndpointInject("mock://invalid-ack")
    protected MockEndpoint invalid;

    @EndpointInject("mock://ack-receive-error")
    protected MockEndpoint ackReceiveError;

    @EndpointInject("mock://ack-timeout-error")
    protected MockEndpoint ackTimeoutError;

    @EndpointInject("mock://failed")
    protected MockEndpoint failed;

    protected int expectedAACount;
    protected int expectedAECount;
    protected int expectedARCount;

    protected int expectedInvalidCount;

    protected int expectedReceiveErrorCount;
    protected int expectedTimeoutCount;

    protected int expectedFailedCount;

    abstract boolean requireEndOfData();

    abstract boolean validatePayload();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();

        context.setUseMDCLogging(true);
        context.getCamelContextExtension().setName(this.getClass().getSimpleName());

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            String routeId = "mllp-sender";

            public void configure() {
                onException(MllpApplicationRejectAcknowledgementException.class)
                        .handled(true)
                        .to(ar)
                        .log(LoggingLevel.ERROR, routeId, "AR Acknowledgement");

                onException(MllpApplicationErrorAcknowledgementException.class)
                        .handled(true)
                        .to(ae)
                        .log(LoggingLevel.ERROR, routeId, "AE Acknowledgement");

                onException(MllpAcknowledgementReceiveException.class)
                        .handled(true)
                        .to(ackReceiveError)
                        .log(LoggingLevel.ERROR, routeId, "Acknowledgement Receive failed");

                onException(MllpAcknowledgementTimeoutException.class)
                        .handled(true)
                        .to(ackTimeoutError)
                        .log(LoggingLevel.ERROR, routeId, "Acknowledgement Receive timeout");

                onException(MllpInvalidAcknowledgementException.class)
                        .handled(true)
                        .to(invalid)
                        .log(LoggingLevel.ERROR, routeId, "Invalid Acknowledgement");

                onCompletion()
                        .onFailureOnly()
                        .to(failed)
                        .log(LoggingLevel.DEBUG, routeId, "Exchange failed");

                from(source.getDefaultEndpoint()).routeId(routeId)
                        .log(LoggingLevel.INFO, routeId, "Sending Message")
                        .toF("mllp://%s:%d?receiveTimeout=%d&readTimeout=%d&validatePayload=%b&requireEndOfData=%b",
                                mllpServer.getListenHost(), mllpServer.getListenPort(),
                                RECEIVE_TIMEOUT, READ_TIMEOUT, validatePayload(), requireEndOfData())
                        .log(LoggingLevel.INFO, routeId, "Received Acknowledgement")
                        .to(aa);
            }
        };
    }

    protected void setExpectedCounts() {
        aa.expectedMessageCount(expectedAACount);
        ae.expectedMessageCount(expectedAECount);
        ar.expectedMessageCount(expectedARCount);

        ackReceiveError.expectedMessageCount(expectedReceiveErrorCount);
        ackTimeoutError.expectedMessageCount(expectedTimeoutCount);

        invalid.expectedMessageCount(expectedInvalidCount);
        failed.expectedMessageCount(expectedFailedCount);
    }

    @Override
    public void tearDown() throws Exception {
        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);

        super.tearDown();
    }

    @Test
    public void testSendSingleMessageWithEndOfDataByte() {
        aa.expectedMessageCount(1);

        source.sendBody(Hl7TestMessageGenerator.generateMessage());
    }

    @Test
    public void testSendMultipleMessagesWithEndOfDataByte() throws Exception {
        expectedAACount = 5;

        setExpectedCounts();

        NotifyBuilder[] complete = new NotifyBuilder[expectedAACount];
        for (int i = 0; i < expectedAACount; ++i) {
            complete[i] = new NotifyBuilder(context).whenDone(i + 1).create();
        }

        for (int i = 0; i < expectedAACount; ++i) {
            source.sendBody(Hl7TestMessageGenerator.generateMessage(i + 1));
            assertTrue(complete[i].matches(1, TimeUnit.SECONDS), "Message " + i + " not completed");
        }
    }

    @Test
    public abstract void testSendSingleMessageWithoutEndOfData();

    protected void runSendSingleMessageWithoutEndOfData() throws Exception {
        setExpectedCounts();

        mllpServer.setExcludeEndOfDataModulus(1);

        source.sendBody(Hl7TestMessageGenerator.generateMessage());
    }

    @Test
    public abstract void testSendMultipleMessagesWithoutEndOfDataByte();

    protected void runSendMultipleMessagesWithoutEndOfDataByte() {
        NotifyBuilder[] complete = new NotifyBuilder[expectedAACount];
        for (int i = 0; i < expectedAACount; ++i) {
            complete[i] = new NotifyBuilder(context).whenDone(i + 1).create();
        }

        mllpServer.setExcludeEndOfDataModulus(1);

        for (int i = 0; i < expectedAACount; ++i) {
            source.sendBody(Hl7TestMessageGenerator.generateMessage(i + 1));
            assertTrue(complete[i].matches(1, TimeUnit.SECONDS), "Message " + i + " not completed");
        }
    }

    protected void runSendMultipleMessagesWithoutEndOfDataByte(MockEndpoint expectedEndpoint) throws Exception {
        int messageCount = 3;

        setExpectedCounts();

        expectedEndpoint.expectedMessageCount(messageCount);

        NotifyBuilder[] complete = new NotifyBuilder[messageCount];
        for (int i = 0; i < messageCount; ++i) {
            complete[i] = new NotifyBuilder(context).whenDone(i + 1).create();
        }

        mllpServer.setExcludeEndOfDataModulus(1);

        for (int i = 0; i < messageCount; ++i) {
            source.sendBody(Hl7TestMessageGenerator.generateMessage(i + 1));
            assertTrue(complete[i].matches(1, TimeUnit.SECONDS), "Message " + i + " not completed");
        }
    }

    @Test
    public void testNoResponseOnFirstMessage() throws Exception {
        int sendMessageCount = 5;

        expectedAACount = sendMessageCount - 1;
        expectedTimeoutCount = 1;

        setExpectedCounts();

        NotifyBuilder[] complete = new NotifyBuilder[sendMessageCount];
        for (int i = 0; i < sendMessageCount; ++i) {
            complete[i] = new NotifyBuilder(context).whenDone(i + 1).create();
        }

        mllpServer.disableResponse();

        source.sendBody(Hl7TestMessageGenerator.generateMessage(1));
        assertTrue(complete[0].matches(1, TimeUnit.SECONDS), "Message 0 not completed");

        mllpServer.enableResponse();

        for (int i = 1; i < sendMessageCount; ++i) {
            source.sendBody(Hl7TestMessageGenerator.generateMessage(i + 1));
            assertTrue(complete[i].matches(1, TimeUnit.SECONDS), "Message " + i + " not completed");
        }
    }

    @Test
    public void testNoResponseOnNthMessage() throws Exception {
        int sendMessageCount = 3;

        expectedAACount = sendMessageCount - 1;
        expectedTimeoutCount = 1;

        setExpectedCounts();

        NotifyBuilder[] complete = new NotifyBuilder[sendMessageCount];
        for (int i = 0; i < sendMessageCount; ++i) {
            complete[i] = new NotifyBuilder(context).whenDone(i + 1).create();
        }

        mllpServer.disableResponse(sendMessageCount);

        for (int i = 0; i < sendMessageCount; ++i) {
            source.sendBody(Hl7TestMessageGenerator.generateMessage(i + 1));
            assertTrue(complete[i].matches(1, TimeUnit.SECONDS), "Message " + i + " not completed");
        }
    }

    @Test
    public void testApplicationAcceptAcknowledgement() throws Exception {
        setExpectedCounts();

        aa.expectedBodiesReceived(TEST_MESSAGE);
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, "AA");
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT, EXPECTED_AA.getBytes());
        aa.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, EXPECTED_AA);

        source.sendBody(TEST_MESSAGE);
    }

    @Test
    public void testApplicationRejectAcknowledgement() throws Exception {
        setExpectedCounts();

        ar.expectedBodiesReceived(TEST_MESSAGE);
        ar.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, "AR");
        ar.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT, EXPECTED_AR.getBytes());
        ar.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, EXPECTED_AR);

        mllpServer.setSendApplicationRejectAcknowledgementModulus(1);

        source.sendBody(TEST_MESSAGE);
    }

    @Test
    public void testApplicationErrorAcknowledgement() throws Exception {
        setExpectedCounts();

        ae.expectedBodiesReceived(TEST_MESSAGE);
        ae.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, "AE");
        ae.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT, EXPECTED_AE.getBytes());
        ae.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, EXPECTED_AE);

        mllpServer.setSendApplicationErrorAcknowledgementModulus(1);

        source.sendBody(TEST_MESSAGE);
    }

    @Test
    public abstract void testEmptyAcknowledgement();

    protected void runEmptyAcknowledgement(MockEndpoint expectedEndpoint) throws Exception {
        setExpectedCounts();

        expectedEndpoint.expectedMessageCount(1);

        mllpServer.setExcludeAcknowledgementModulus(1);

        source.sendBody(TEST_MESSAGE);
    }

    @Test
    public abstract void testInvalidAcknowledgement();

    protected void runInvalidAcknowledgement(MockEndpoint expectedEndpoint) throws Exception {
        final String badAcknowledgement = "A VERY BAD ACKNOWLEDGEMENT";

        setExpectedCounts();

        expectedEndpoint.expectedMessageCount(1);
        expectedEndpoint.expectedBodiesReceived(TEST_MESSAGE);
        expectedEndpoint.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, badAcknowledgement.getBytes());
        expectedEndpoint.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, badAcknowledgement);

        mllpServer.setAcknowledgementString(badAcknowledgement);

        source.sendBody(TEST_MESSAGE);
    }

    @Test
    public abstract void testMissingEndOfDataByte();

    /**
     * NOTE: Set expectation variables BEFORE calling this method.
     *
     * @throws Exception
     */
    protected void runMissingEndOfDataByte() throws Exception {
        int sendMessageCount = 3;

        setExpectedCounts();

        NotifyBuilder[] complete = new NotifyBuilder[sendMessageCount];
        for (int i = 0; i < sendMessageCount; ++i) {
            complete[i] = new NotifyBuilder(context).whenDone(i + 1).create();
        }

        mllpServer.setExcludeEndOfDataModulus(sendMessageCount);

        for (int i = 0; i < sendMessageCount; ++i) {
            source.sendBody(Hl7TestMessageGenerator.generateMessage(i + 1));
            assertTrue(complete[i].matches(1, TimeUnit.SECONDS), "Message " + i + " not completed");
        }
    }

    @Test
    public void testAcknowledgementReceiveTimeout() throws Exception {
        setExpectedCounts();

        ackTimeoutError.expectedMessageCount(1);

        mllpServer.disableResponse(1);

        source.sendBody(Hl7TestMessageGenerator.generateMessage());
    }

    @Test
    public void testAcknowledgementReadTimeout() throws Exception {
        setExpectedCounts();

        ackTimeoutError.expectedMessageCount(1);

        mllpServer.setDelayDuringAcknowledgement(15000);

        source.sendBody(Hl7TestMessageGenerator.generateMessage());
    }

    @Test
    public void testMissingEndOfBlockByte() throws Exception {
        int sendMessageCount = 3;

        expectedAACount = sendMessageCount - 1;
        expectedTimeoutCount = 1;

        setExpectedCounts();

        NotifyBuilder[] complete = new NotifyBuilder[sendMessageCount];
        for (int i = 0; i < sendMessageCount; ++i) {
            complete[i] = new NotifyBuilder(context).whenDone(i + 1).create();
        }

        mllpServer.setExcludeEndOfBlockModulus(sendMessageCount);

        for (int i = 0; i < sendMessageCount; ++i) {
            source.sendBody(Hl7TestMessageGenerator.generateMessage(i + 1));
            assertTrue(complete[i].matches(1, TimeUnit.SECONDS), "Message " + i + " not completed");
        }
    }

    @Test
    public abstract void testSendMultipleMessagesWithoutSomeEndOfDataByte();

    protected void runSendMultipleMessagesWithoutSomeEndOfDataByte() throws Exception {
        setExpectedCounts();

        int messageCount = expectedAACount + expectedTimeoutCount;

        NotifyBuilder[] complete = new NotifyBuilder[messageCount];
        for (int i = 0; i < messageCount; ++i) {
            complete[i] = new NotifyBuilder(context).whenDone(i + 1).create();
        }

        mllpServer.setExcludeEndOfDataModulus(messageCount - 1);

        for (int i = 0; i < messageCount; ++i) {
            source.sendBody(Hl7TestMessageGenerator.generateMessage(i + 1));
            assertTrue(complete[i].matches(1, TimeUnit.SECONDS), "Message " + i + " not completed");
        }
    }

    @Test
    public abstract void testInvalidAcknowledgementContainingEmbeddedStartOfBlock();

    /**
     * NOTE: Set expectation variables BEFORE calling this method.
     *
     * @throws Exception
     */
    public void runInvalidAcknowledgementContainingEmbeddedStartOfBlock() throws Exception {
        final String badAcknowledgement = EXPECTED_AA.replaceFirst("RISTECH", "RISTECH" + MllpProtocolConstants.START_OF_BLOCK);

        setExpectedCounts();

        mllpServer.setAcknowledgementString(badAcknowledgement);

        source.sendBody(TEST_MESSAGE);
    }

    @Test
    public abstract void testInvalidAcknowledgementContainingEmbeddedEndOfBlockByte();

    /**
     * NOTE: Set expectation variables BEFORE calling this method.
     *
     */
    protected void runInvalidAcknowledgementContainingEmbeddedEndOfBlockByte() {
        final String badAcknowledgement = EXPECTED_AA.replaceFirst("RISTECH", "RISTECH" + MllpProtocolConstants.END_OF_BLOCK);

        mllpServer.setAcknowledgementString(badAcknowledgement);

        source.sendBody(TEST_MESSAGE);
    }

}
