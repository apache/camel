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
import org.apache.camel.Message;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MllpTcpServerConsumerPopulateHl7DataHeadersTest extends CamelTestSupport {

    private MllpTcpServerConsumer consumer;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected void doPreSetup() throws Exception {
        MllpComponent mllpComponent = createCamelContext().getComponent("mllp", MllpComponent.class);
        MllpEndpoint endpoint = (MllpEndpoint) mllpComponent.createEndpoint("mllp://localhost:0");
        consumer = new MllpTcpServerConsumer(endpoint, exchange -> {
        });
    }

    @Test
    void testMessageStartingWithSegmentDelimiter() {
        // Malformed payload: leading segment delimiter (\r) before an otherwise valid MSH segment.
        // byte[0] = 0x0D (SEGMENT_DELIMITER), byte[3] = 'H' (not 0x0D), so fieldSeparator = 'H'.
        // At i=0 the else-if branch would access hl7MessageBytes[i-1] = hl7MessageBytes[-1].
        byte[] malformedMessage
                = "\rMSH|^~\\&|ADT|EPIC|JCAPS|CC|20160902123950|RISTECH|ADT^A08|00001|D|2.3\r".getBytes();

        Exchange exchange = new DefaultExchange(context);
        Message message = exchange.getIn();

        assertDoesNotThrow(
                () -> consumer.populateHl7DataHeaders(exchange, message, malformedMessage),
                "populateHl7DataHeaders should handle a leading segment delimiter gracefully");

        assertNull(message.getHeader(MllpConstants.MLLP_SENDING_APPLICATION),
                "No HL7 headers should be set for a malformed message");
        assertNull(message.getHeader(MllpConstants.MLLP_MESSAGE_TYPE),
                "No HL7 headers should be set for a malformed message");
    }

    @Test
    void testFieldSeparatorEqualsSegmentDelimiter() {
        // When byte[3] is also 0x0D, fieldSeparator == SEGMENT_DELIMITER.
        // Every 0x0D byte matches the first if-branch (fieldSeparator check),
        // so the else-if (SEGMENT_DELIMITER) is never entered and endOfMSH stays -1.
        byte[] malformedMessage = "XX\r\rYYYYYYYY".getBytes();

        Exchange exchange = new DefaultExchange(context);
        Message message = exchange.getIn();

        assertDoesNotThrow(
                () -> consumer.populateHl7DataHeaders(exchange, message, malformedMessage),
                "populateHl7DataHeaders should handle fieldSeparator == SEGMENT_DELIMITER gracefully");

        assertNull(message.getHeader(MllpConstants.MLLP_SENDING_APPLICATION),
                "No HL7 headers should be set when MSH end is not found");
        assertNull(message.getHeader(MllpConstants.MLLP_MESSAGE_TYPE),
                "No HL7 headers should be set when MSH end is not found");
    }
}
