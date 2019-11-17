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

import org.apache.camel.Exchange;
import org.junit.Test;

public class MllpTcpServerConsumerManualAcknowledgementWithoutBridgeErrorHandlerTest extends TcpServerConsumerAcknowledgementTestSupport {
    @Override
    protected boolean isBridgeErrorHandler() {
        return false;
    }

    @Override
    protected boolean isAutoAck() {
        return false;
    }

    @Test
    public void testReceiveSingleMessage() throws Exception {
        result.expectedBodiesReceived(TEST_MESSAGE);

        complete.expectedBodiesReceived(TEST_MESSAGE);

        receiveSingleMessage();

        Exchange completeExchange = complete.getReceivedExchanges().get(0);

        assertNull(completeExchange.getIn().getHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT));
        assertNull(completeExchange.getIn().getHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING));
    }

    public void testAcknowledgementDeliveryFailure() throws Exception {
        result.expectedBodiesReceived(TEST_MESSAGE);

        failure.expectedBodiesReceived(TEST_MESSAGE);
        failure.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, "AA");
        failure.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT, EXPECTED_ACKNOWLEDGEMENT);
        failure.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, EXPECTED_ACKNOWLEDGEMENT);

        acknowledgementDeliveryFailure();

        Exchange failureExchange = failure.getExchanges().get(0);
        Object failureException = failureExchange.getProperty(MllpConstants.MLLP_ACKNOWLEDGEMENT_EXCEPTION);
        assertNotNull("OnFailureOnly exchange should have a " + MllpConstants.MLLP_ACKNOWLEDGEMENT_EXCEPTION + " property", failureException);
        assertIsInstanceOf(Exception.class, failureException);
    }

    @Test
    public void testUnparsableMessage() throws Exception {
        final String testMessage = "MSH" + TEST_MESSAGE;

        result.expectedBodiesReceived(testMessage);
        complete.expectedMessageCount(1);

        unparsableMessage(testMessage);

        assertNull("Should not have the exception in the exchange property", result.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT));
        assertNull("Should not have the exception in the exchange property", complete.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT));
    }

    @Test
    public void testMessageWithEmptySegment() throws Exception {
        final String testMessage = TEST_MESSAGE.replace("\rPID|", "\r\rPID|");

        result.expectedBodiesReceived(testMessage);
        complete.expectedMessageCount(1);

        unparsableMessage(testMessage);

        assertNull("Should not have the exception in the exchange property", result.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT));
        assertNull("Should not have the exception in the exchange property", complete.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT));
    }


    @Test
    public void testMessageWithEmbeddedNewlines() throws Exception {
        final String testMessage = TEST_MESSAGE.replace("\rPID|", "\r\n\rPID|\n");

        result.expectedBodiesReceived(testMessage);
        complete.expectedMessageCount(1);

        unparsableMessage(testMessage);

        assertNull("Should not have the exception in the exchange property", result.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT));
        assertNull("Should not have the exception in the exchange property", complete.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT));
    }
}

