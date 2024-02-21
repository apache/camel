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
package org.apache.camel.component.mllp.internal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.camel.component.mllp.MllpAcknowledgementGenerationException;
import org.apache.camel.component.mllp.MllpProtocolConstants;
import org.apache.camel.test.stub.camel.MllpEndpointStub;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.mllp.MllpExceptionTestSupport.LOG_PHI_TRUE;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class Hl7UtilTest {

    // @formatter:off
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
              + '\r';

    static final String EXPECTED_ACKNOWLEDGEMENT_PAYLOAD_START
            = MllpProtocolConstants.START_OF_BLOCK + "MSH|^~\\&|INHOUSE|RTH00|REQUESTING|ICE|";

    static final String EXPECTED_ACKNOWLEDGEMENT_PAYLOAD_END = "||ACK^O01|00001A|D|2.3|||||||" + '\r'
                                                               + "MSA|AA|00001" + '\r'
                                                               + MllpProtocolConstants.END_OF_BLOCK
                                                               + MllpProtocolConstants.END_OF_DATA;

    static final String EXPECTED_ACKNOWLEDGEMENT_PAYLOAD = MllpProtocolConstants.START_OF_BLOCK
                                                           + "MSH|^~\\&|INHOUSE|RTH00|REQUESTING|ICE|20161206193919||ACK^O01|00001A|D|2.3|||||||"
                                                           + '\r'
                                                           + "MSA|AA|00001" + '\r'
                                                           + MllpProtocolConstants.END_OF_BLOCK
                                                           + MllpProtocolConstants.END_OF_DATA;

    static final String EXPECTED_MESSAGE
            = "MSH|^~\\&|REQUESTING|ICE|INHOUSE|RTH00|20161206193919||ORM^O01|00001|D|2.3|||||||" + "<0x0D CR>"
              + "PID|1||ICE999999^^^ICE^ICE||Testpatient^Testy^^^Mr||19740401|M|||123 Barrel Drive^^^^SW18 4RT|||||2||||||||||||||"
              + "<0x0D CR>"
              + "NTE|1||Free text for entering clinical details|" + "<0x0D CR>"
              + "PV1|1||^^^^^^^^Admin Location|||||||||||||||NHS|" + "<0x0D CR>"
              + "ORC|NW|213||175|REQ||||20080808093202|ahsl^^Administrator||G999999^TestDoctor^GPtests^^^^^^NAT|^^^^^^^^Admin Location | 819600|200808080932||RTH00||ahsl^^Administrator||"
              + "<0x0D CR>"
              + "OBR|1|213||CCOR^Serum Cortisol ^ JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||"
              + "<0x0D CR>"
              + "OBR|2|213||GCU^Serum Copper ^ JRH06 |||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||"
              + "<0x0D CR>"
              + "OBR|3|213||THYG^Serum Thyroglobulin ^JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||"
              + "<0x0D CR>";
    // @formatter:on

    static final String MSH_SEGMENT = "MSH|^~\\&|0|90100053675|INHOUSE|RTH00|20131125122938||ORM|28785|D|2.3";

    // @formatter:off
    static final String REMAINING_SEGMENTS
            = "PID|1||ICE999999^^^ICE^ICE||Testpatient^Testy^^^Mr||19740401|M|||123 Barrel Drive^^^^SW18 4RT|||||2||||||||||||||"
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
              + '\r';
    // @formatter:on

    static final byte[] TEST_MESSAGE_BYTES = TEST_MESSAGE.getBytes();

    private final Hl7Util hl7util = new Hl7Util(5120, LOG_PHI_TRUE);

    private final Charset charset = StandardCharsets.ISO_8859_1;

    @Test
    public void testGenerateInvalidPayloadExceptionMessage() {
        String message = hl7util.generateInvalidPayloadExceptionMessage(TEST_MESSAGE.getBytes());

        assertNull(message, "Valid payload should result in a null message");
    }

    @Test
    public void testGenerateInvalidPayloadExceptionMessageWithLengthLargerThanArraySize() {
        byte[] payload = TEST_MESSAGE.getBytes();
        String message = hl7util.generateInvalidPayloadExceptionMessage(payload, payload.length * 2);

        assertNull(message, "Valid payload should result in a null message");
    }

    @Test
    public void testGenerateInvalidPayloadExceptionMessageWithLengthSmallerThanArraySize() {
        byte[] payload = TEST_MESSAGE.getBytes();
        String message = hl7util.generateInvalidPayloadExceptionMessage(payload, 10);

        assertEquals("The HL7 payload terminating byte [0x7c] is incorrect - expected [0xd]  {ASCII [<CR>]}", message);
    }

    @Test
    public void testGenerateInvalidPayloadExceptionMessageWithNullPayload() {
        assertEquals("HL7 payload is null", hl7util.generateInvalidPayloadExceptionMessage(null));

        assertEquals("HL7 payload is null", hl7util.generateInvalidPayloadExceptionMessage(null, 1234));
    }

    @Test
    public void testGenerateInvalidPayloadExceptionMessageWithInvalidStartingSegment() throws Exception {
        byte[] invalidStartingSegment = "MSA|AA|00001|\r".getBytes();
        byte[] basePayload = TEST_MESSAGE.getBytes();

        ByteArrayOutputStream payloadStream = new ByteArrayOutputStream(invalidStartingSegment.length + basePayload.length);
        payloadStream.write(invalidStartingSegment);
        payloadStream.write(basePayload.length);

        assertEquals("The first segment of the HL7 payload {MSA} is not an MSH segment",
                hl7util.generateInvalidPayloadExceptionMessage(payloadStream.toByteArray()));
    }

    @Test
    public void testGenerateInvalidPayloadExceptionMessageWithEmptyPayload() {
        byte[] payload = new byte[0];

        assertEquals("HL7 payload is empty", hl7util.generateInvalidPayloadExceptionMessage(payload));
        assertEquals("HL7 payload is empty", hl7util.generateInvalidPayloadExceptionMessage(payload, payload.length));
    }

    @Test
    public void testGenerateInvalidPayloadExceptionMessageWithEmbeddedStartOfBlock() {
        byte[] basePayload = TEST_MESSAGE.getBytes();

        ByteArrayOutputStream payloadStream = new ByteArrayOutputStream(basePayload.length + 1);

        int embeddedStartOfBlockIndex = basePayload.length / 2;
        payloadStream.write(basePayload, 0, embeddedStartOfBlockIndex);
        payloadStream.write(MllpProtocolConstants.START_OF_BLOCK);
        payloadStream.write(basePayload, embeddedStartOfBlockIndex, basePayload.length - embeddedStartOfBlockIndex);

        String expected
                = "HL7 payload contains an embedded START_OF_BLOCK {0xb, ASCII <VT>} at index " + embeddedStartOfBlockIndex;

        assertEquals(expected, hl7util.generateInvalidPayloadExceptionMessage(payloadStream.toByteArray()));
    }

    @Test
    public void testGenerateInvalidPayloadExceptionMessageWithEmbeddedEndOfBlock() {
        byte[] basePayload = TEST_MESSAGE.getBytes();

        ByteArrayOutputStream payloadStream = new ByteArrayOutputStream(basePayload.length + 1);

        int embeddedEndOfBlockIndex = basePayload.length / 2;
        payloadStream.write(basePayload, 0, embeddedEndOfBlockIndex);
        payloadStream.write(MllpProtocolConstants.END_OF_BLOCK);
        payloadStream.write(basePayload, embeddedEndOfBlockIndex, basePayload.length - embeddedEndOfBlockIndex);

        String expected
                = "HL7 payload contains an embedded END_OF_BLOCK {0x1c, ASCII <FS>} at index " + embeddedEndOfBlockIndex;

        assertEquals(expected, hl7util.generateInvalidPayloadExceptionMessage(payloadStream.toByteArray()));
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testGenerateAcknowledgementPayload() throws Exception {
        MllpSocketBuffer mllpSocketBuffer = new MllpSocketBuffer(new MllpEndpointStub());
        hl7util.generateAcknowledgementPayload(mllpSocketBuffer, TEST_MESSAGE.getBytes(), "AA");

        String actual = mllpSocketBuffer.toString();

        assertThat(actual, startsWith(EXPECTED_ACKNOWLEDGEMENT_PAYLOAD_START));
        assertThat(actual, endsWith(EXPECTED_ACKNOWLEDGEMENT_PAYLOAD_END));
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testGenerateAcknowledgementPayloadFromNullMessage() {
        MllpSocketBuffer mllpSocketBuffer = new MllpSocketBuffer(new MllpEndpointStub());
        assertThrows(MllpAcknowledgementGenerationException.class,
                () -> hl7util.generateAcknowledgementPayload(mllpSocketBuffer, null, "AA"));
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testGenerateAcknowledgementPayloadFromEmptyMessage() {
        MllpSocketBuffer mllpSocketBuffer = new MllpSocketBuffer(new MllpEndpointStub());
        assertThrows(MllpAcknowledgementGenerationException.class,
                () -> hl7util.generateAcknowledgementPayload(mllpSocketBuffer, new byte[0], "AA"));
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testGenerateAcknowledgementPayloadWithoutEnoughFields() {
        final byte[] testMessage = TEST_MESSAGE.replace("||ORM^O01|00001|D|2.3|||||||", "").getBytes();

        MllpSocketBuffer mllpSocketBuffer = new MllpSocketBuffer(new MllpEndpointStub());
        assertThrows(MllpAcknowledgementGenerationException.class,
                () -> hl7util.generateAcknowledgementPayload(mllpSocketBuffer, testMessage, "AA"));
    }

    /**
     * If the MSH isn't terminated correctly, we'll get Junk for the acknowledgement.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testGenerateAcknowledgementPayloadWithoutEndOfSegment() throws Exception {
        String junkMessage = "MSH|^~\\&|REQUESTING|ICE|INHOUSE|RTH00|20161206193919||ORM^O01|00001|D|2.3|||||||"
                             + "PID|1||ICE999999^^^ICE^ICE||Testpatient^Testy^^^Mr||19740401|M|||123 Barrel Drive^^^^SW18 4RT|||||2||||||||||||||";

        MllpSocketBuffer mllpSocketBuffer = new MllpSocketBuffer(new MllpEndpointStub());
        hl7util.generateAcknowledgementPayload(mllpSocketBuffer, junkMessage.getBytes(), "AA");

        String actual = mllpSocketBuffer.toString();

        assertThat(actual, startsWith(EXPECTED_ACKNOWLEDGEMENT_PAYLOAD_START));
        assertThat(actual, endsWith(
                "PID|1||ICE999999^^^ICE^ICE||Testpatient^Testy^^^Mr||19740401|M|||123 Barrel Drive^^^^SW18 4RT|||||2|||||||||||||\r"
                                    + "MSA|AA|00001\r"
                                    + MllpProtocolConstants.END_OF_BLOCK + MllpProtocolConstants.END_OF_DATA));
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testConvertStringToPrintFriendlyString() {
        assertEquals(hl7util.NULL_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString((String) null));
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString(""));
        assertEquals(EXPECTED_MESSAGE, hl7util.convertToPrintFriendlyString(TEST_MESSAGE));
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testConvertBytesToPrintFriendlyString() {
        assertEquals(hl7util.NULL_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString((byte[]) null));
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString(new byte[0]));
        assertEquals(EXPECTED_MESSAGE, hl7util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES));
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testConvertBytesToPrintFriendlyStringWithStartAndEndPositions() {
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString((byte[]) null, 0, 1000));
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString((byte[]) null, 200, 1000));
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString((byte[]) null, -200, 1000));

        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString((byte[]) null, 0, 0));
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString((byte[]) null, 200, 0));
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString((byte[]) null, -200, 0));

        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString((byte[]) null, 0, -1000));
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString((byte[]) null, 200, -1000));
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString((byte[]) null, -200, -1000));

        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString(new byte[0], 0, 1000));
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString(new byte[0], 200, 1000));
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString(new byte[0], -200, 1000));

        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString(new byte[0], 0, 0));
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString(new byte[0], 200, 0));
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString(new byte[0], -200, 0));

        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString(new byte[0], 0, -1000));
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString(new byte[0], 200, -1000));
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE, hl7util.convertToPrintFriendlyString(new byte[0], -200, -1000));

        assertEquals(EXPECTED_MESSAGE, hl7util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, 0, TEST_MESSAGE_BYTES.length));
        assertEquals("", hl7util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, 0, 0));

        assertEquals(EXPECTED_MESSAGE,
                hl7util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, -14, TEST_MESSAGE_BYTES.length));

        assertEquals("", hl7util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, -14, 0));
        assertEquals("", hl7util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, -14, -14));
        assertEquals(EXPECTED_MESSAGE, hl7util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, -14, 1000000));

        assertEquals("", hl7util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, 0, -14));
        assertEquals(EXPECTED_MESSAGE, hl7util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, 0, 1000000));

        assertEquals("", hl7util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, 1000000, TEST_MESSAGE_BYTES.length));
        assertEquals("", hl7util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, 1000000, 0));
        assertEquals("", hl7util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, 1000000, -14));
        assertEquals("", hl7util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, 1000000, 1000000));

        assertEquals("", hl7util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, 50, 50));
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testBytesToPrintFriendlyStringBuilder() {
        assertEquals(hl7util.NULL_REPLACEMENT_VALUE, hl7util.bytesToPrintFriendlyStringBuilder((byte[]) null).toString());
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE, hl7util.bytesToPrintFriendlyStringBuilder(new byte[0]).toString());
        assertEquals(EXPECTED_MESSAGE, hl7util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES).toString());
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testBytesToPrintFriendlyStringBuilderWithStartAndEndPositions() {
        assertEquals(hl7util.NULL_REPLACEMENT_VALUE,
                hl7util.bytesToPrintFriendlyStringBuilder((byte[]) null, 0, 1000).toString());
        assertEquals(hl7util.NULL_REPLACEMENT_VALUE,
                hl7util.bytesToPrintFriendlyStringBuilder((byte[]) null, 200, 1000).toString());
        assertEquals(hl7util.NULL_REPLACEMENT_VALUE,
                hl7util.bytesToPrintFriendlyStringBuilder((byte[]) null, -200, 1000).toString());

        assertEquals(hl7util.NULL_REPLACEMENT_VALUE, hl7util.bytesToPrintFriendlyStringBuilder((byte[]) null, 0, 0).toString());
        assertEquals(hl7util.NULL_REPLACEMENT_VALUE,
                hl7util.bytesToPrintFriendlyStringBuilder((byte[]) null, 200, 0).toString());
        assertEquals(hl7util.NULL_REPLACEMENT_VALUE,
                hl7util.bytesToPrintFriendlyStringBuilder((byte[]) null, -200, 0).toString());

        assertEquals(hl7util.NULL_REPLACEMENT_VALUE,
                hl7util.bytesToPrintFriendlyStringBuilder((byte[]) null, 0, -1000).toString());
        assertEquals(hl7util.NULL_REPLACEMENT_VALUE,
                hl7util.bytesToPrintFriendlyStringBuilder((byte[]) null, 200, -1000).toString());
        assertEquals(hl7util.NULL_REPLACEMENT_VALUE,
                hl7util.bytesToPrintFriendlyStringBuilder((byte[]) null, -200, -1000).toString());

        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE,
                hl7util.bytesToPrintFriendlyStringBuilder(new byte[0], 0, 1000).toString());
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE,
                hl7util.bytesToPrintFriendlyStringBuilder(new byte[0], 200, 1000).toString());
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE,
                hl7util.bytesToPrintFriendlyStringBuilder(new byte[0], -200, 1000).toString());

        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE, hl7util.bytesToPrintFriendlyStringBuilder(new byte[0], 0, 0).toString());
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE,
                hl7util.bytesToPrintFriendlyStringBuilder(new byte[0], 200, 0).toString());
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE,
                hl7util.bytesToPrintFriendlyStringBuilder(new byte[0], -200, 0).toString());

        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE,
                hl7util.bytesToPrintFriendlyStringBuilder(new byte[0], 0, -1000).toString());
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE,
                hl7util.bytesToPrintFriendlyStringBuilder(new byte[0], 200, -1000).toString());
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE,
                hl7util.bytesToPrintFriendlyStringBuilder(new byte[0], -200, -1000).toString());

        assertEquals(EXPECTED_MESSAGE,
                hl7util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, 0, TEST_MESSAGE_BYTES.length).toString());
        assertEquals("", hl7util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, 0, 0).toString());

        assertEquals(EXPECTED_MESSAGE,
                hl7util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, -14, TEST_MESSAGE_BYTES.length).toString());

        assertEquals("", hl7util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, -14, 0).toString());
        assertEquals("", hl7util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, -14, -14).toString());
        assertEquals(EXPECTED_MESSAGE, hl7util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, -14, 1000000).toString());

        assertEquals("", hl7util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, 0, -14).toString());
        assertEquals(EXPECTED_MESSAGE, hl7util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, 0, 1000000).toString());

        assertEquals("",
                hl7util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, 1000000, TEST_MESSAGE_BYTES.length).toString());
        assertEquals("", hl7util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, 1000000, 0).toString());
        assertEquals("", hl7util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, 1000000, -14).toString());
        assertEquals("", hl7util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, 1000000, 1000000).toString());

        assertEquals("ORM^O01|00001|D|2.3|||||||<0x0D CR>PID|1||ICE999999^^^",
                hl7util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, 54, 100).toString());
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testAppendBytesAsPrintFriendlyString() {
        StringBuilder builder = null;

        try {
            hl7util.appendBytesAsPrintFriendlyString(builder, null);
            fail("Exception should be raised with null StringBuilder argument");
        } catch (IllegalArgumentException ignoredEx) {
            // Eat this
        }

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, (byte[]) null);
        assertEquals(hl7util.NULL_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, new byte[0]);
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES);
        assertEquals(EXPECTED_MESSAGE, builder.toString());
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testAppendBytesAsPrintFriendlyStringWithStartAndEndPositions() {
        StringBuilder builder = null;

        try {
            hl7util.appendBytesAsPrintFriendlyString(builder, null);
            fail("Exception should be raised with null StringBuilder argument");
        } catch (IllegalArgumentException ignoredEx) {
            // Eat this
        }

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, null, 0, 1000);
        assertEquals(hl7util.NULL_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, null, 200, 1000);
        assertEquals(hl7util.NULL_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, null, -200, 1000);
        assertEquals(hl7util.NULL_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, null, 0, 0);
        assertEquals(hl7util.NULL_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, null, 200, 0);
        assertEquals(hl7util.NULL_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, null, -200, 0);
        assertEquals(hl7util.NULL_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, null, 0, -1000);
        assertEquals(hl7util.NULL_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, null, 200, -1000);
        assertEquals(hl7util.NULL_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, null, -200, -1000);
        assertEquals(hl7util.NULL_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, new byte[0], 0, 1000);
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, new byte[0], 200, 1000);
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, new byte[0], -200, 1000);
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, new byte[0], 0, 0);
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, new byte[0], 200, 0);
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, new byte[0], -200, 0);
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, new byte[0], 0, -1000);
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, new byte[0], 200, -1000);
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, new byte[0], -200, -1000);
        assertEquals(hl7util.EMPTY_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, 0, TEST_MESSAGE_BYTES.length);
        assertEquals(EXPECTED_MESSAGE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, 0, 0);
        assertEquals("", builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, -14, TEST_MESSAGE_BYTES.length);
        assertEquals(EXPECTED_MESSAGE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, -14, 0);
        assertEquals("", builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, -14, -14);
        assertEquals("", builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, -14, 1000000);
        assertEquals(EXPECTED_MESSAGE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, 0, -14);
        assertEquals("", builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, 0, 1000000);
        assertEquals(EXPECTED_MESSAGE, builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, 1000000, TEST_MESSAGE_BYTES.length);
        assertEquals("", builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, 1000000, 0);
        assertEquals("", builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, 1000000, -14);
        assertEquals("", builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, 1000000, 1000000);
        assertEquals("", builder.toString());

        builder = new StringBuilder();
        hl7util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, 54, 100);
        assertEquals("ORM^O01|00001|D|2.3|||||||<0x0D CR>PID|1||ICE999999^^^", builder.toString());
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testAppendCharacterAsPrintFriendlyString() {
        StringBuilder builder = null;

        try {
            Hl7Util.appendCharacterAsPrintFriendlyString(builder, 'a');
            fail("Exception should be raised with null StringBuilder argument");
        } catch (NullPointerException ignoredEx) {
            // Eat this
        }

        builder = new StringBuilder();
        Hl7Util.appendCharacterAsPrintFriendlyString(builder, MllpProtocolConstants.START_OF_BLOCK);
        assertEquals("<0x0B VT>", builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendCharacterAsPrintFriendlyString(builder, MllpProtocolConstants.END_OF_BLOCK);
        assertEquals("<0x1C FS>", builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendCharacterAsPrintFriendlyString(builder, MllpProtocolConstants.SEGMENT_DELIMITER);
        assertEquals("<0x0D CR>", builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendCharacterAsPrintFriendlyString(builder, MllpProtocolConstants.MESSAGE_TERMINATOR);
        assertEquals("<0x0A LF>", builder.toString());
    }

    @Test
    public void testGetCharacterAsPrintFriendlyString() {
        assertEquals("<0x0B VT>", Hl7Util.getCharacterAsPrintFriendlyString(MllpProtocolConstants.START_OF_BLOCK));
        assertEquals("<0x1C FS>", Hl7Util.getCharacterAsPrintFriendlyString(MllpProtocolConstants.END_OF_BLOCK));
        assertEquals("<0x0D CR>", Hl7Util.getCharacterAsPrintFriendlyString(MllpProtocolConstants.SEGMENT_DELIMITER));
        assertEquals("<0x0A LF>", Hl7Util.getCharacterAsPrintFriendlyString(MllpProtocolConstants.MESSAGE_TERMINATOR));
        assertEquals("<0x09 TAB>", Hl7Util.getCharacterAsPrintFriendlyString('\t'));
    }

    @Test
    public void testFindMsh18WhenExistsWithoutTrailingPipe() {
        final String testMessage = MSH_SEGMENT + "||||||8859/1" + '\r' + REMAINING_SEGMENTS;

        assertEquals("8859/1", hl7util.findMsh18(testMessage.getBytes(), charset));
    }

    @Test
    public void testFindMsh18WhenExistsWithTrailingPipe() {
        final String testMessage = MSH_SEGMENT + "||||||8859/1|" + '\r' + REMAINING_SEGMENTS;

        assertEquals("8859/1", hl7util.findMsh18(testMessage.getBytes(), charset));
    }

    @Test
    public void testFindMsh18WhenMissingWithoutTrailingPipe() {
        final String testMessage = MSH_SEGMENT + "||||||" + '\r' + REMAINING_SEGMENTS;

        assertEquals("", hl7util.findMsh18(testMessage.getBytes(), charset));
    }

    @Test
    public void testFindMsh18WhenMissingWithTrailingPipe() {
        final String testMessage = MSH_SEGMENT + "|||||||" + '\r' + REMAINING_SEGMENTS;

        assertEquals("", hl7util.findMsh18(testMessage.getBytes(), charset));
    }

    @Test
    public void testConvertToPrintFriendlyStringWithPhiMaxBytes() {
        Hl7Util local = new Hl7Util(3, LOG_PHI_TRUE);
        String result = local.convertToPrintFriendlyString(TEST_MESSAGE);
        assertEquals("MSH", result);
    }
}
