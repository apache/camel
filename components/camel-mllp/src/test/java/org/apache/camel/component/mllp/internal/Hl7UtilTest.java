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

import org.apache.camel.component.mllp.MllpAcknowledgementGenerationException;
import org.apache.camel.component.mllp.MllpProtocolConstants;
import org.apache.camel.test.stub.camel.MllpEndpointStub;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class Hl7UtilTest {
    // @formatter:off
    static final String TEST_MESSAGE =
        "MSH|^~\\&|REQUESTING|ICE|INHOUSE|RTH00|20161206193919||ORM^O01|00001|D|2.3|||||||" + '\r'
            + "PID|1||ICE999999^^^ICE^ICE||Testpatient^Testy^^^Mr||19740401|M|||123 Barrel Drive^^^^SW18 4RT|||||2||||||||||||||" + '\r'
            + "NTE|1||Free text for entering clinical details|" + '\r'
            + "PV1|1||^^^^^^^^Admin Location|||||||||||||||NHS|" + '\r'
            + "ORC|NW|213||175|REQ||||20080808093202|ahsl^^Administrator||G999999^TestDoctor^GPtests^^^^^^NAT|^^^^^^^^Admin Location | 819600|200808080932||RTH00||ahsl^^Administrator||" + '\r'
            + "OBR|1|213||CCOR^Serum Cortisol ^ JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + '\r'
            + "OBR|2|213||GCU^Serum Copper ^ JRH06 |||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + '\r'
            + "OBR|3|213||THYG^Serum Thyroglobulin ^JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + '\r';

    static final String EXPECTED_ACKNOWLEDGEMENT_PAYLOAD_START = MllpProtocolConstants.START_OF_BLOCK + "MSH|^~\\&|INHOUSE|RTH00|REQUESTING|ICE|";

    static final String EXPECTED_ACKNOWLEDGEMENT_PAYLOAD_END = "||ACK^O01|00001A|D|2.3|||||||" + '\r'
        + "MSA|AA|00001" + '\r'
        + MllpProtocolConstants.END_OF_BLOCK + MllpProtocolConstants.END_OF_DATA;

    static final String EXPECTED_ACKNOWLEDGEMENT_PAYLOAD =
        MllpProtocolConstants.START_OF_BLOCK
        + "MSH|^~\\&|INHOUSE|RTH00|REQUESTING|ICE|20161206193919||ACK^O01|00001A|D|2.3|||||||" + '\r'
        + "MSA|AA|00001" + '\r'
        + MllpProtocolConstants.END_OF_BLOCK + MllpProtocolConstants.END_OF_DATA;

    static final String EXPECTED_MESSAGE =
        "MSH|^~\\&|REQUESTING|ICE|INHOUSE|RTH00|20161206193919||ORM^O01|00001|D|2.3|||||||" + "<0x0D CR>"
            + "PID|1||ICE999999^^^ICE^ICE||Testpatient^Testy^^^Mr||19740401|M|||123 Barrel Drive^^^^SW18 4RT|||||2||||||||||||||" + "<0x0D CR>"
            + "NTE|1||Free text for entering clinical details|" + "<0x0D CR>"
            + "PV1|1||^^^^^^^^Admin Location|||||||||||||||NHS|" + "<0x0D CR>"
            + "ORC|NW|213||175|REQ||||20080808093202|ahsl^^Administrator||G999999^TestDoctor^GPtests^^^^^^NAT|^^^^^^^^Admin Location | 819600|200808080932||RTH00||ahsl^^Administrator||" + "<0x0D CR>"
            + "OBR|1|213||CCOR^Serum Cortisol ^ JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + "<0x0D CR>"
            + "OBR|2|213||GCU^Serum Copper ^ JRH06 |||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + "<0x0D CR>"
            + "OBR|3|213||THYG^Serum Thyroglobulin ^JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + "<0x0D CR>";
    // @formatter:on

    static final String MSH_SEGMENT = "MSH|^~\\&|0|90100053675|INHOUSE|RTH00|20131125122938||ORM|28785|D|2.3";

    // @formatter:off
    static final String REMAINING_SEGMENTS =
        "PID|1||ICE999999^^^ICE^ICE||Testpatient^Testy^^^Mr||19740401|M|||123 Barrel Drive^^^^SW18 4RT|||||2||||||||||||||" + '\r'
            + "NTE|1||Free text for entering clinical details|" + '\r'
            + "PV1|1||^^^^^^^^Admin Location|||||||||||||||NHS|" + '\r'
            + "ORC|NW|213||175|REQ||||20080808093202|ahsl^^Administrator||G999999^TestDoctor^GPtests^^^^^^NAT|^^^^^^^^Admin Location | 819600|200808080932||RTH00||ahsl^^Administrator||" + '\r'
            + "OBR|1|213||CCOR^Serum Cortisol ^ JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + '\r'
            + "OBR|2|213||GCU^Serum Copper ^ JRH06 |||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + '\r'
            + "OBR|3|213||THYG^Serum Thyroglobulin ^JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + '\r';
    // @formatter:on

    static final byte[] TEST_MESSAGE_BYTES = TEST_MESSAGE.getBytes();
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

        assertEquals("The HL7 payload terminating byte [0x7c] is incorrect - expected [0xd]  {ASCII [<CR>]}", message);
    }

    @Test
    public void testGenerateInvalidPayloadExceptionMessageWithNullPayload() throws Exception {
        assertEquals("HL7 payload is null", Hl7Util.generateInvalidPayloadExceptionMessage(null));

        assertEquals("HL7 payload is null", Hl7Util.generateInvalidPayloadExceptionMessage(null, 1234));
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
        payloadStream.write(MllpProtocolConstants.START_OF_BLOCK);
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
        payloadStream.write(MllpProtocolConstants.END_OF_BLOCK);
        payloadStream.write(basePayload, embeddedEndOfBlockIndex, basePayload.length - embeddedEndOfBlockIndex);

        String expected = "HL7 payload contains an embedded END_OF_BLOCK {0x1c, ASCII <FS>} at index " + embeddedEndOfBlockIndex;

        assertEquals(expected, Hl7Util.generateInvalidPayloadExceptionMessage(payloadStream.toByteArray()));
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testGenerateAcknowledgementPayload() throws Exception {
        MllpSocketBuffer mllpSocketBuffer = new MllpSocketBuffer(new MllpEndpointStub());
        Hl7Util.generateAcknowledgementPayload(mllpSocketBuffer, TEST_MESSAGE.getBytes(), "AA");

        String actual = mllpSocketBuffer.toString();

        assertThat(actual, startsWith(EXPECTED_ACKNOWLEDGEMENT_PAYLOAD_START));
        assertThat(actual, endsWith(EXPECTED_ACKNOWLEDGEMENT_PAYLOAD_END));
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test(expected = MllpAcknowledgementGenerationException.class)
    public void testGenerateAcknowledgementPayloadFromNullMessage() throws Exception {
        MllpSocketBuffer mllpSocketBuffer = new MllpSocketBuffer(new MllpEndpointStub());
        Hl7Util.generateAcknowledgementPayload(mllpSocketBuffer, null, "AA");

        assertEquals(EXPECTED_ACKNOWLEDGEMENT_PAYLOAD, mllpSocketBuffer.toString());
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test(expected = MllpAcknowledgementGenerationException.class)
    public void testGenerateAcknowledgementPayloadFromEmptyMessage() throws Exception {
        MllpSocketBuffer mllpSocketBuffer = new MllpSocketBuffer(new MllpEndpointStub());
        Hl7Util.generateAcknowledgementPayload(mllpSocketBuffer, new byte[0], "AA");

        assertEquals(EXPECTED_ACKNOWLEDGEMENT_PAYLOAD, mllpSocketBuffer.toString());
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test(expected = MllpAcknowledgementGenerationException.class)
    public void testGenerateAcknowledgementPayloadWithoutEnoughFields() throws Exception {
        final byte[] testMessage = TEST_MESSAGE.replace("||ORM^O01|00001|D|2.3|||||||", "").getBytes();

        MllpSocketBuffer mllpSocketBuffer = new MllpSocketBuffer(new MllpEndpointStub());
        Hl7Util.generateAcknowledgementPayload(mllpSocketBuffer, testMessage, "AA");
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
        Hl7Util.generateAcknowledgementPayload(mllpSocketBuffer, junkMessage.getBytes(), "AA");

        String actual = mllpSocketBuffer.toString();

        assertThat(actual, startsWith(EXPECTED_ACKNOWLEDGEMENT_PAYLOAD_START));
        assertThat(actual, endsWith("PID|1||ICE999999^^^ICE^ICE||Testpatient^Testy^^^Mr||19740401|M|||123 Barrel Drive^^^^SW18 4RT|||||2|||||||||||||\r"
                                    + "MSA|AA|00001\r"
                                    + MllpProtocolConstants.END_OF_BLOCK + MllpProtocolConstants.END_OF_DATA));
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testConvertStringToPrintFriendlyString() throws Exception {
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString((String) null));
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString(""));
        assertEquals(EXPECTED_MESSAGE, Hl7Util.convertToPrintFriendlyString(TEST_MESSAGE));
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testConvertBytesToPrintFriendlyString() throws Exception {
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString((byte[]) null));
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString(new byte[0]));
        assertEquals(EXPECTED_MESSAGE, Hl7Util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES));
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testConvertBytesToPrintFriendlyStringWithStartAndEndPositions() throws Exception {
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString((byte[]) null, 0, 1000));
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString((byte[]) null, 200, 1000));
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString((byte[]) null, -200, 1000));

        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString((byte[]) null, 0, 0));
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString((byte[]) null, 200, 0));
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString((byte[]) null, -200, 0));

        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString((byte[]) null, 0, -1000));
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString((byte[]) null, 200, -1000));
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString((byte[]) null, -200, -1000));

        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString(new byte[0], 0, 1000));
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString(new byte[0], 200, 1000));
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString(new byte[0], -200, 1000));

        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString(new byte[0], 0, 0));
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString(new byte[0], 200, 0));
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString(new byte[0], -200, 0));

        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString(new byte[0], 0, -1000));
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString(new byte[0], 200, -1000));
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.convertToPrintFriendlyString(new byte[0], -200, -1000));

        assertEquals(EXPECTED_MESSAGE, Hl7Util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, 0, TEST_MESSAGE_BYTES.length));
        assertEquals("", Hl7Util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, 0, 0));


        assertEquals(EXPECTED_MESSAGE, Hl7Util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, -14, TEST_MESSAGE_BYTES.length));

        assertEquals("", Hl7Util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, -14, 0));
        assertEquals("", Hl7Util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, -14, -14));
        assertEquals(EXPECTED_MESSAGE, Hl7Util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, -14, 1000000));

        assertEquals("", Hl7Util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, 0, -14));
        assertEquals(EXPECTED_MESSAGE, Hl7Util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, 0, 1000000));

        assertEquals("", Hl7Util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, 1000000, TEST_MESSAGE_BYTES.length));
        assertEquals("", Hl7Util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, 1000000, 0));
        assertEquals("", Hl7Util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, 1000000, -14));
        assertEquals("", Hl7Util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, 1000000, 1000000));

        assertEquals("", Hl7Util.convertToPrintFriendlyString(TEST_MESSAGE_BYTES, 50, 50));
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testBytesToPrintFriendlyStringBuilder() throws Exception {
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.bytesToPrintFriendlyStringBuilder((byte[]) null).toString());
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.bytesToPrintFriendlyStringBuilder(new byte[0]).toString());
        assertEquals(EXPECTED_MESSAGE, Hl7Util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES).toString());
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testBytesToPrintFriendlyStringBuilderWithStartAndEndPositions() throws Exception {
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.bytesToPrintFriendlyStringBuilder((byte[]) null, 0, 1000).toString());
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.bytesToPrintFriendlyStringBuilder((byte[]) null, 200, 1000).toString());
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.bytesToPrintFriendlyStringBuilder((byte[]) null, -200, 1000).toString());

        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.bytesToPrintFriendlyStringBuilder((byte[]) null, 0, 0).toString());
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.bytesToPrintFriendlyStringBuilder((byte[]) null, 200, 0).toString());
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.bytesToPrintFriendlyStringBuilder((byte[]) null, -200, 0).toString());

        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.bytesToPrintFriendlyStringBuilder((byte[]) null, 0, -1000).toString());
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.bytesToPrintFriendlyStringBuilder((byte[]) null, 200, -1000).toString());
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, Hl7Util.bytesToPrintFriendlyStringBuilder((byte[]) null, -200, -1000).toString());

        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.bytesToPrintFriendlyStringBuilder(new byte[0], 0, 1000).toString());
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.bytesToPrintFriendlyStringBuilder(new byte[0], 200, 1000).toString());
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.bytesToPrintFriendlyStringBuilder(new byte[0], -200, 1000).toString());

        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.bytesToPrintFriendlyStringBuilder(new byte[0], 0, 0).toString());
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.bytesToPrintFriendlyStringBuilder(new byte[0], 200, 0).toString());
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.bytesToPrintFriendlyStringBuilder(new byte[0], -200, 0).toString());

        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.bytesToPrintFriendlyStringBuilder(new byte[0], 0, -1000).toString());
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.bytesToPrintFriendlyStringBuilder(new byte[0], 200, -1000).toString());
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, Hl7Util.bytesToPrintFriendlyStringBuilder(new byte[0], -200, -1000).toString());

        assertEquals(EXPECTED_MESSAGE, Hl7Util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, 0, TEST_MESSAGE_BYTES.length).toString());
        assertEquals("", Hl7Util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, 0, 0).toString());


        assertEquals(EXPECTED_MESSAGE, Hl7Util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, -14, TEST_MESSAGE_BYTES.length).toString());

        assertEquals("", Hl7Util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, -14, 0).toString());
        assertEquals("", Hl7Util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, -14, -14).toString());
        assertEquals(EXPECTED_MESSAGE, Hl7Util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, -14, 1000000).toString());

        assertEquals("", Hl7Util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, 0, -14).toString());
        assertEquals(EXPECTED_MESSAGE, Hl7Util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, 0, 1000000).toString());

        assertEquals("", Hl7Util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, 1000000, TEST_MESSAGE_BYTES.length).toString());
        assertEquals("", Hl7Util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, 1000000, 0).toString());
        assertEquals("", Hl7Util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, 1000000, -14).toString());
        assertEquals("", Hl7Util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, 1000000, 1000000).toString());

        assertEquals("ORM^O01|00001|D|2.3|||||||<0x0D CR>PID|1||ICE999999^^^", Hl7Util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, 54, 100).toString());
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testAppendBytesAsPrintFriendlyString() throws Exception {
        StringBuilder builder = null;

        try {
            Hl7Util.appendBytesAsPrintFriendlyString(builder, null);
            fail("Exception should be raised with null StringBuilder argument");
        } catch (IllegalArgumentException ignoredEx) {
            // Eat this
        }

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, (byte[]) null);
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, new byte[0]);
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES);
        assertEquals(EXPECTED_MESSAGE, builder.toString());
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testAppendBytesAsPrintFriendlyStringWithStartAndEndPositions() throws Exception {
        StringBuilder builder = null;

        try {
            Hl7Util.appendBytesAsPrintFriendlyString(builder, null);
            fail("Exception should be raised with null StringBuilder argument");
        } catch (IllegalArgumentException ignoredEx) {
            // Eat this
        }

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, null, 0, 1000);
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, null, 200, 1000);
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, null, -200, 1000);
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, builder.toString());


        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, null, 0, 0);
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, null, 200, 0);
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, null, -200, 0);
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, builder.toString());


        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, null, 0, -1000);
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, null, 200, -1000);
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, null, -200, -1000);
        assertEquals(Hl7Util.NULL_REPLACEMENT_VALUE, builder.toString());


        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, new byte[0], 0, 1000);
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, new byte[0], 200, 1000);
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, new byte[0], -200, 1000);
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, builder.toString());


        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, new byte[0], 0, 0);
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, new byte[0], 200, 0);
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, new byte[0], -200, 0);
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, builder.toString());


        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, new byte[0], 0, -1000);
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, new byte[0], 200, -1000);
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, new byte[0], -200, -1000);
        assertEquals(Hl7Util.EMPTY_REPLACEMENT_VALUE, builder.toString());


        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, 0, TEST_MESSAGE_BYTES.length);
        assertEquals(EXPECTED_MESSAGE, builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, 0, 0);
        assertEquals("", builder.toString());


        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, -14, TEST_MESSAGE_BYTES.length);
        assertEquals(EXPECTED_MESSAGE, builder.toString());


        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, -14, 0);
        assertEquals("", builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, -14, -14);
        assertEquals("", builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, -14, 1000000);
        assertEquals(EXPECTED_MESSAGE, builder.toString());


        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, 0, -14);
        assertEquals("", builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, 0, 1000000);
        assertEquals(EXPECTED_MESSAGE, builder.toString());


        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, 1000000, TEST_MESSAGE_BYTES.length);
        assertEquals("", builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, 1000000, 0);
        assertEquals("", builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, 1000000, -14);
        assertEquals("", builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, 1000000, 1000000);
        assertEquals("", builder.toString());


        builder = new StringBuilder();
        Hl7Util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, 54, 100);
        assertEquals("ORM^O01|00001|D|2.3|||||||<0x0D CR>PID|1||ICE999999^^^", builder.toString());
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testAppendCharacterAsPrintFriendlyString() throws Exception {
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
    public void testGetCharacterAsPrintFriendlyString() throws Exception {
        assertEquals("<0x0B VT>", Hl7Util.getCharacterAsPrintFriendlyString(MllpProtocolConstants.START_OF_BLOCK));
        assertEquals("<0x1C FS>", Hl7Util.getCharacterAsPrintFriendlyString(MllpProtocolConstants.END_OF_BLOCK));
        assertEquals("<0x0D CR>", Hl7Util.getCharacterAsPrintFriendlyString(MllpProtocolConstants.SEGMENT_DELIMITER));
        assertEquals("<0x0A LF>", Hl7Util.getCharacterAsPrintFriendlyString(MllpProtocolConstants.MESSAGE_TERMINATOR));
        assertEquals("<0x09 TAB>", Hl7Util.getCharacterAsPrintFriendlyString('\t'));
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testFindMsh18WhenExistsWithoutTrailingPipe() throws Exception {
        final String testMessage = MSH_SEGMENT + "||||||8859/1" + '\r' + REMAINING_SEGMENTS;

        assertEquals("8859/1", Hl7Util.findMsh18(testMessage.getBytes()));
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testFindMsh18WhenExistsWithTrailingPipe() throws Exception {
        final String testMessage = MSH_SEGMENT + "||||||8859/1|" + '\r' + REMAINING_SEGMENTS;

        assertEquals("8859/1", Hl7Util.findMsh18(testMessage.getBytes()));
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testFindMsh18WhenMissingWithoutTrailingPipe() throws Exception {
        final String testMessage = MSH_SEGMENT + "||||||" + '\r' + REMAINING_SEGMENTS;

        assertEquals("", Hl7Util.findMsh18(testMessage.getBytes()));
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testFindMsh18WhenMissingWithTrailingPipe() throws Exception {
        final String testMessage = MSH_SEGMENT + "|||||||" + '\r' + REMAINING_SEGMENTS;

        assertEquals("", Hl7Util.findMsh18(testMessage.getBytes()));
    }
}
