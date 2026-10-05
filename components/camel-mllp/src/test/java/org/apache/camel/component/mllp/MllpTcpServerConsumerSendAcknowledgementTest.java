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

import java.net.Socket;
import java.nio.charset.Charset;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.mllp.internal.MllpSocketBuffer;
import org.apache.camel.component.mllp.internal.TcpSocketConsumerRunnable;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MllpTcpServerConsumerSendAcknowledgementTest {

    private CamelContext context;
    private MllpTcpServerConsumer consumer;
    private MllpEndpoint endpoint;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.start();
        MllpComponent mllpComponent = context.getComponent("mllp", MllpComponent.class);
        endpoint = (MllpEndpoint) mllpComponent.createEndpoint("mllp://localhost:0");
        consumer = new MllpTcpServerConsumer(endpoint, exchange -> {
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        if (consumer != null) {
            consumer.stop();
        }
        if (endpoint != null) {
            endpoint.stop();
        }
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void testAcknowledgementTypeExtractedFromMsaSegment() {
        // HL7 message where the \r segment delimiter is at position 62, well past position 13.
        // MSA|AA|00001 follows after the \r.
        // With the bug (SEGMENT_DELIMITER == i), only position 13 is checked for \r,
        // so MSA is never found and MLLP_ACKNOWLEDGEMENT_TYPE stays null.
        byte[] originalMessage
                = "MSH|^~\\&|JCAPS|CC|ADT|EPIC|20160902123950||ACK^A08|00001|D|2.3\rMSA|AA|00001\r".getBytes();
        byte[] acknowledgementBytes = "MSH|^~\\&|ACK\rMSA|AA|00001\r".getBytes();

        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(MllpConstants.MLLP_ACKNOWLEDGEMENT, acknowledgementBytes);

        TcpSocketConsumerRunnable mockRunnable = createMockRunnable(acknowledgementBytes);

        consumer.sendAcknowledgement(originalMessage, exchange, mockRunnable);

        assertEquals("AA", exchange.getMessage().getHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE),
                "MLLP_ACKNOWLEDGEMENT_TYPE should be extracted from the MSA segment");
    }

    @Test
    void testAcknowledgementTypeExtractedWhenDelimiterAtPosition13() {
        // MSH segment is exactly 13 bytes so the \r falls at byte offset 13 (== 0x0D).
        // This is the edge case where the original bug accidentally matched because
        // the loop index equalled the SEGMENT_DELIMITER constant.
        byte[] originalMessage = "MSH|^~\\&|A|B|\rMSA|AE|00002\r".getBytes();
        byte[] acknowledgementBytes = "MSH|^~\\&|ACK\rMSA|AE|00002\r".getBytes();

        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(MllpConstants.MLLP_ACKNOWLEDGEMENT, acknowledgementBytes);

        TcpSocketConsumerRunnable mockRunnable = createMockRunnable(acknowledgementBytes);

        consumer.sendAcknowledgement(originalMessage, exchange, mockRunnable);

        assertEquals("AE", exchange.getMessage().getHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE),
                "MLLP_ACKNOWLEDGEMENT_TYPE should be extracted when segment delimiter is at position 13");
    }

    private TcpSocketConsumerRunnable createMockRunnable(byte[] acknowledgementBytes) {
        TcpSocketConsumerRunnable mockRunnable = mock(TcpSocketConsumerRunnable.class);
        Socket mockSocket = mock(Socket.class);
        MllpSocketBuffer mockBuffer = mock(MllpSocketBuffer.class);
        when(mockRunnable.getSocket()).thenReturn(mockSocket);
        when(mockRunnable.getMllpBuffer()).thenReturn(mockBuffer);
        when(mockBuffer.hasCompleteEnvelope()).thenReturn(true);
        when(mockBuffer.toMllpPayload()).thenReturn(acknowledgementBytes);
        when(mockBuffer.toHl7String(any(Charset.class))).thenReturn("");
        return mockRunnable;
    }
}
