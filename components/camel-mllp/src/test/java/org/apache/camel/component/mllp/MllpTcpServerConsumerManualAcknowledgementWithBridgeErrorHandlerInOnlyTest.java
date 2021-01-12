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
import org.apache.camel.ExchangePattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

public class MllpTcpServerConsumerManualAcknowledgementWithBridgeErrorHandlerInOnlyTest
        extends TcpServerConsumerAcknowledgementTestSupport {

    @Override
    protected boolean isBridgeErrorHandler() {
        return true;
    }

    @Override
    protected boolean isAutoAck() {
        return false;
    }

    @Override
    protected ExchangePattern exchangePattern() {
        return ExchangePattern.InOnly;
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

    @Test
    public void testUnparsableMessage() throws Exception {
        final String testMessage = "MSH" + TEST_MESSAGE;

        result.expectedBodiesReceived(testMessage);
        complete.expectedMessageCount(1);

        unparsableMessage(testMessage);

        assertNull(result.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT),
                "Should not have the exception in the exchange property");
        assertNull(complete.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT),
                "Should not have the exception in the exchange property");
    }

    @Test
    public void testMessageWithEmptySegment() throws Exception {
        final String testMessage = TEST_MESSAGE.replace("\rPID|", "\r\rPID|");

        result.expectedBodiesReceived(testMessage);
        complete.expectedMessageCount(1);

        unparsableMessage(testMessage);

        assertNull(result.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT),
                "Should not have the exception in the exchange property");
        assertNull(complete.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT),
                "Should not have the exception in the exchange property");
    }

    @Test
    public void testMessageWithEmbeddedNewlines() throws Exception {
        final String testMessage = TEST_MESSAGE.replace("\rPID|", "\r\n\rPID|\n");

        result.expectedBodiesReceived(testMessage);
        complete.expectedMessageCount(1);

        unparsableMessage(testMessage);

        assertNull(result.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT),
                "Should not have the exception in the exchange property");
        assertNull(complete.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT),
                "Should not have the exception in the exchange property");
    }
}
