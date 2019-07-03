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

import java.io.ByteArrayOutputStream;

import org.junit.Test;

import static org.apache.camel.component.mllp.MllpEndpoint.END_OF_BLOCK;
import static org.apache.camel.component.mllp.MllpEndpoint.START_OF_BLOCK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class Hl7UtilTest {
    static final String TEST_MESSAGE =
        "MSH|^~\\&|REQUESTING|ICE|INHOUSE|RTH00|20161206193919||ORM^O01|00001|D|2.3|||||||" + '\r'
            + "PID|1||ICE999999^^^ICE^ICE||Testpatient^Testy^^^Mr||19740401|M|||123 Barrel Drive^^^^SW18 4RT|||||2||||||||||||||" + '\r'
            + "NTE|1||Free text for entering clinical details|" + '\r'
            + "PV1|1||^^^^^^^^Admin Location|||||||||||||||NHS|" + '\r'
            + "ORC|NW|213||175|REQ||||20080808093202|ahsl^^Administrator||G999999^TestDoctor^GPtests^^^^^^NAT|^^^^^^^^Admin Location | 819600|200808080932||RTH00||ahsl^^Administrator||" + '\r'
            + "OBR|1|213||CCOR^Serum Cortisol ^ JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + '\r'
            + "OBR|2|213||GCU^Serum Copper ^ JRH06 |||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + '\r'
            + "OBR|3|213||THYG^Serum Thyroglobulin ^JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + '\r'
            + '\n';

    @Test
    public void testGenerateInvalidPayloadExceptionMessage() throws Exception {
        String message = Hl7Util.generateInvalidPayloadExceptionMessage(TEST_MESSAGE.getBytes());

        assertNull("Valid payload should result in a null message", message);
    }

    @Test
    public void testGenerateInvalidPayloadExceptionMessageWithLengthLargerThanArraySize() throws Exception {
        byte[] payload = TEST_MESSAGE.getBytes();
        String message = Hl7Util.generateInvalidPayloadExceptionMessage(payload, payload.length * 2);

        assertNull("Valid payload should result in a null message", message);
    }

    @Test
    public void testGenerateInvalidPayloadExceptionMessageWithLengthSmallerThanArraySize() throws Exception {
        byte[] payload = TEST_MESSAGE.getBytes();
        String message = Hl7Util.generateInvalidPayloadExceptionMessage(payload, 10);

        assertEquals("The HL7 payload terminating bytes [0x7c, 0x52] are incorrect - expected [0xd, 0xa]  {ASCII [<CR>, <LF>]}", message);
    }

    @Test
    public void testGenerateInvalidPayloadExceptionMessageWithNullPayload() throws Exception {
        assertEquals("HL7 payload is null", Hl7Util.generateInvalidPayloadExceptionMessage(null));
    }

    @Test
    public void testGenerateInvalidPayloadExceptionMessageWithInvalidStartingSegment() throws Exception {
        byte[] invalidStartingSegment = "MSA|AA|00001|\r".getBytes();
        byte[] basePayload = TEST_MESSAGE.getBytes();

        ByteArrayOutputStream payloadStream = new ByteArrayOutputStream(invalidStartingSegment.length + basePayload.length);
        payloadStream.write(invalidStartingSegment);
        payloadStream.write(basePayload.length);

        assertEquals("The first segment of the HL7 payload {MSA} is not an MSH segment", Hl7Util.generateInvalidPayloadExceptionMessage(payloadStream.toByteArray()));
    }

    @Test
    public void testGenerateInvalidPayloadExceptionMessageWithEmptyPayload() throws Exception {
        byte[] payload = new byte[0];

        assertEquals("HL7 payload is empty", Hl7Util.generateInvalidPayloadExceptionMessage(payload));
        assertEquals("HL7 payload is empty", Hl7Util.generateInvalidPayloadExceptionMessage(payload, payload.length));
    }

    @Test
    public void testGenerateInvalidPayloadExceptionMessageWithEmbeddedStartOfBlock() throws Exception {
        byte[] basePayload = TEST_MESSAGE.getBytes();

        ByteArrayOutputStream payloadStream = new ByteArrayOutputStream(basePayload.length + 1);

        int embeddedStartOfBlockIndex = basePayload.length / 2;
        payloadStream.write(basePayload, 0, embeddedStartOfBlockIndex);
        payloadStream.write(START_OF_BLOCK);
        payloadStream.write(basePayload, embeddedStartOfBlockIndex, basePayload.length - embeddedStartOfBlockIndex);

        String expected = "HL7 payload contains an embedded START_OF_BLOCK {0xb, ASCII <VT>} at index " + embeddedStartOfBlockIndex;

        assertEquals(expected, Hl7Util.generateInvalidPayloadExceptionMessage(payloadStream.toByteArray()));
    }

    @Test
    public void testGenerateInvalidPayloadExceptionMessageWithEmbeddedEndOfBlock() throws Exception {
        byte[] basePayload = TEST_MESSAGE.getBytes();

        ByteArrayOutputStream payloadStream = new ByteArrayOutputStream(basePayload.length + 1);

        int embeddedEndOfBlockIndex = basePayload.length / 2;
        payloadStream.write(basePayload, 0, embeddedEndOfBlockIndex);
        payloadStream.write(END_OF_BLOCK);
        payloadStream.write(basePayload, embeddedEndOfBlockIndex, basePayload.length - embeddedEndOfBlockIndex);

        String expected = "HL7 payload contains an embedded END_OF_BLOCK {0x1c, ASCII <FS>} at index " + embeddedEndOfBlockIndex;

        assertEquals(expected, Hl7Util.generateInvalidPayloadExceptionMessage(payloadStream.toByteArray()));
    }
}
