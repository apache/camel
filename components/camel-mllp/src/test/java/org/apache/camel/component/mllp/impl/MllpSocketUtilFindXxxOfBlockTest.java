/**
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
package org.apache.camel.component.mllp.impl;

import org.apache.camel.test.util.PayloadBuilder;
import org.junit.Test;

import static org.apache.camel.component.mllp.MllpEndpoint.END_OF_BLOCK;
import static org.apache.camel.component.mllp.MllpEndpoint.END_OF_DATA;
import static org.apache.camel.component.mllp.MllpEndpoint.START_OF_BLOCK;
import static org.junit.Assert.assertEquals;

public class MllpSocketUtilFindXxxOfBlockTest {
    static final String HL7_PAYLOAD_STRING =
            "MSH|^~\\&|JCAPS|CC|ADT|EPIC|20161206193919|RISTECH|ACK^A08|00001|D|2.3^^|||||||" + '\r'
                    + "MSA|AA|00001|" + '\r'
                    + '\n';

    @Test
    public void testFindStartOfBlockWithDummyPayload() throws Exception {
        byte[] payload = PayloadBuilder.build(START_OF_BLOCK, "Dummy non-hl7 payload", END_OF_BLOCK, END_OF_DATA);

        int actual = MllpSocketUtil.findStartOfBlock(payload);

        assertEquals(0, actual);
    }

    @Test
    public void testFindStartOfBlockWithHl7Payload() throws Exception {
        byte[] payload = PayloadBuilder.build(START_OF_BLOCK, HL7_PAYLOAD_STRING, END_OF_BLOCK, END_OF_DATA);

        int actual = MllpSocketUtil.findStartOfBlock(payload);

        assertEquals(0, actual);
    }

    @Test
    public void testFindStartOfBlockWithNullPayload() throws Exception {
        int actual = MllpSocketUtil.findStartOfBlock(null, 12345);

        assertEquals(-1, actual);
    }

    @Test
    public void testFindStartOfBlockWithOnlyStartOfBlock() throws Exception {
        byte[] payload = PayloadBuilder.build(START_OF_BLOCK);

        int actual = MllpSocketUtil.findStartOfBlock(payload);

        assertEquals(0, actual);
    }

    @Test
    public void testFindStartOfBlockWithStartOfBlockAfterLength() throws Exception {
        byte[] payload = PayloadBuilder.build(HL7_PAYLOAD_STRING, END_OF_BLOCK, END_OF_DATA, START_OF_BLOCK);

        int actual = MllpSocketUtil.findStartOfBlock(payload, payload.length - 1);

        assertEquals(-1, actual);
    }

    @Test
    public void testFindStartOfBlockWithMissingStartOfBlock() throws Exception {
        byte[] payload = PayloadBuilder.build(HL7_PAYLOAD_STRING, END_OF_BLOCK, END_OF_DATA);

        int actual = MllpSocketUtil.findStartOfBlock(payload);

        assertEquals(-1, actual);
    }

    @Test
    public void testFindStartOfBlockWithLengthLargerThanArraySize() throws Exception {
        byte[] payload = PayloadBuilder.build(START_OF_BLOCK, HL7_PAYLOAD_STRING, END_OF_BLOCK, END_OF_DATA);

        int actual = MllpSocketUtil.findStartOfBlock(payload, payload.length + 1);

        assertEquals(0, actual);
    }

    @Test
    public void testFindStartOfBlockWithLengthSmallerThanArraySize() throws Exception {
        byte[] payload = PayloadBuilder.build(START_OF_BLOCK, HL7_PAYLOAD_STRING, END_OF_BLOCK, END_OF_DATA);

        int actual = MllpSocketUtil.findStartOfBlock(payload, payload.length - 2);

        assertEquals(0, actual);
    }

    @Test
    public void testFindEndOfMessageWithDummyPayload() throws Exception {
        final byte[] dummyPayload = "Dummy non-hl7 payload".getBytes();
        byte[] payload = PayloadBuilder.build(dummyPayload, END_OF_BLOCK, END_OF_DATA);

        int expected = dummyPayload.length;
        int actual = MllpSocketUtil.findEndOfMessage(payload);

        assertEquals(expected, actual);
    }

    @Test
    public void testFindEndOfMessageWithDummyPayloadAndStartOfBlock() throws Exception {
        final byte[] dummyPayload = "Dummy non-hl7 payload".getBytes();
        byte[] payload = PayloadBuilder.build(START_OF_BLOCK, dummyPayload, END_OF_BLOCK, END_OF_DATA);

        int expected = dummyPayload.length + 1;
        int actual = MllpSocketUtil.findEndOfMessage(payload);

        assertEquals(expected, actual);
    }

    @Test
    public void testFindEndOfMessageWithHl7Payload() throws Exception {
        byte[] payload = PayloadBuilder.build(HL7_PAYLOAD_STRING, END_OF_BLOCK, END_OF_DATA);

        int expected = HL7_PAYLOAD_STRING.length();
        int actual = MllpSocketUtil.findEndOfMessage(payload);

        assertEquals(expected, actual);
    }

    @Test
    public void testFindEndOfMessageWithHl7PayloadAndStartOfBlock() throws Exception {
        byte[] payload = PayloadBuilder.build(START_OF_BLOCK, HL7_PAYLOAD_STRING, END_OF_BLOCK, END_OF_DATA);

        int expected = HL7_PAYLOAD_STRING.length() + 1;
        int actual = MllpSocketUtil.findEndOfMessage(payload);

        assertEquals(expected, actual);
    }

    @Test
    public void testFindEndOfMessageWithNullPayload() throws Exception {
        assertEquals(-1, MllpSocketUtil.findEndOfMessage(null, 12345));
    }

    @Test
    public void testFindEndOfMessagekWithOnlyEndOfBlock() throws Exception {
        byte[] payload = PayloadBuilder.build(END_OF_BLOCK);

        int actual = MllpSocketUtil.findEndOfMessage(payload);

        assertEquals(-1, actual);
    }

    @Test
    public void testFindEndOfMessagekWithOnlyEndOfData() throws Exception {
        byte[] payload = PayloadBuilder.build(END_OF_DATA);

        int actual = MllpSocketUtil.findEndOfMessage(payload);

        assertEquals(-1, actual);
    }

    @Test
    public void testFindEndOfMessagekWithOnlyEndOfBlockAndEndOfData() throws Exception {
        byte[] payload = PayloadBuilder.build(END_OF_BLOCK, END_OF_DATA);

        int actual = MllpSocketUtil.findEndOfMessage(payload);

        assertEquals(0, actual);
    }

    @Test
    public void testFindEndOfMessageWithEndOfBlockAfterLength() throws Exception {
        byte[] payload = PayloadBuilder.build(HL7_PAYLOAD_STRING, END_OF_BLOCK, END_OF_DATA);

        int actual = MllpSocketUtil.findEndOfMessage(payload, payload.length - 2);

        assertEquals(-1, actual);
    }

    @Test
    public void testFindEndOfMessageWithMissingEndOfBlock() throws Exception {
        byte[] payload = PayloadBuilder.build(HL7_PAYLOAD_STRING, END_OF_DATA);

        int actual = MllpSocketUtil.findEndOfMessage(payload);

        assertEquals(-1, actual);
    }

    @Test
    public void testFindEndOfMessageWithEndOfBlockButMissingEndOfData() throws Exception {
        byte[] payload = PayloadBuilder.build(HL7_PAYLOAD_STRING, END_OF_BLOCK);

        int actual = MllpSocketUtil.findEndOfMessage(payload);

        assertEquals(-1, actual);
    }

    @Test
    public void testFindEndOfMessageWithStartOfBlockButMissingEndOfBlock() throws Exception {
        byte[] payload = PayloadBuilder.build(START_OF_BLOCK, HL7_PAYLOAD_STRING, END_OF_DATA);

        int actual = MllpSocketUtil.findEndOfMessage(payload);

        assertEquals(-1, actual);
    }

    @Test
    public void testFindEndOfMessageWithStartOfBlockAndEndOfBlockButMissingEndOfData() throws Exception {
        byte[] payload = PayloadBuilder.build(START_OF_BLOCK, HL7_PAYLOAD_STRING, END_OF_BLOCK);

        int actual = MllpSocketUtil.findEndOfMessage(payload);

        assertEquals(-1, actual);
    }

    @Test
    public void testFindEndOfMessageWithLengthLargerThanArraySize() throws Exception {
        byte[] payload = PayloadBuilder.build(START_OF_BLOCK, HL7_PAYLOAD_STRING, END_OF_BLOCK, END_OF_DATA);

        int expected = HL7_PAYLOAD_STRING.length() + 1;
        int actual = MllpSocketUtil.findEndOfMessage(payload, payload.length + 1);

        assertEquals(expected, actual);
    }

    @Test
    public void testFindEndOfMessageWithLengthSmallerThanArraySize() throws Exception {
        byte[] payload = PayloadBuilder.build(START_OF_BLOCK, HL7_PAYLOAD_STRING, END_OF_BLOCK, END_OF_DATA);

        int actual = MllpSocketUtil.findEndOfMessage(payload, payload.length - 1);

        assertEquals(-1, actual);
    }

}