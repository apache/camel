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

package org.apache.camel.component.mllp.internal;

import java.io.ByteArrayOutputStream;

import org.apache.camel.component.mllp.MllpProtocolConstants;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class Hl7UtilTest {
    // @formatter:off
    static final String TEST_MESSAGE =
        "MSH|^~\\&|ADT|EPIC|JCAPS|CC|20161206193919|RISTECH|ADT^A08|00001|D|2.3^^|||||||" + '\r'
            + "EVN|A08|20150107161440||REG_UPDATE_SEND_VISIT_MESSAGES_ON_PATIENT_CHANGES|RISTECH^RADIOLOGY^TECHNOLOGIST^^^^^^UCLA^^^^^RRMC||" + '\r'
            + "PID|1|2100355^^^MRN^MRN|2100355^^^MRN^MRN||MDCLS9^MC9||19700109|F||U|111 HOVER STREET^^LOS ANGELES^CA^90032^USA^P^^LOS ANGELE|"
            + "LOS ANGELE|(310)725-6952^P^PH^^^310^7256952||ENGLISH|U||60000013647|565-33-2222|||U||||||||N||" + '\r'
            + "PD1|||UCLA HEALTH SYSTEM^^10|10002116^ADAMS^JOHN^D^^^^^EPIC^^^^PROVID||||||||||||||" + '\r'
            + "NK1|1|DOE^MC9^^|OTH|^^^^^USA|(310)888-9999^^^^^310^8889999|(310)999-2222^^^^^310^9992222|Emergency Contact 1|||||||||||||||||||||||||||" + '\r'
            + "PV1|1|OUTPATIENT|RR CT^^^1000^^^^^^^DEPID|EL|||017511^TOBIAS^JONATHAN^^^^^^EPIC^^^^PROVID|017511^TOBIAS^JONATHAN^^^^^^EPIC^^^^PROVID||||||"
            + "CLR|||||60000013647|SELF|||||||||||||||||||||HOV_CONF|^^^1000^^^^^^^||20150107161438||||||||||" + '\r'
            + "PV2||||||||20150107161438||||CT BRAIN W WO CONTRAST||||||||||N|||||||||||||||||||||||||||" + '\r'
            + "ZPV||||||||||||20150107161438|||||||||" + '\r'
            + "AL1|1||33361^NO KNOWN ALLERGIES^^NOTCOMPUTRITION^NO KNOWN ALLERGIES^EXTELG||||||" + '\r'
            + "DG1|1|DX|784.0^Headache^DX|Headache||VISIT" + '\r'
            + "GT1|1|1000235129|MDCLS9^MC9^^||111 HOVER STREET^^LOS ANGELES^CA^90032^USA^^^LOS ANGELE|(310)725-6952^^^^^310^7256952||19700109|F|P/F|SLF|"
            + "565-33-2222|||||^^^^^USA|||UNKNOWN|||||||||||||||||||||||||||||" + '\r'
            + "UB2||||||||" + '\r'
            + '\n';

    static final String EXPECTED_MESSAGE =
        "MSH|^~\\&|ADT|EPIC|JCAPS|CC|20161206193919|RISTECH|ADT^A08|00001|D|2.3^^|||||||" + "<CR>"
            + "EVN|A08|20150107161440||REG_UPDATE_SEND_VISIT_MESSAGES_ON_PATIENT_CHANGES|RISTECH^RADIOLOGY^TECHNOLOGIST^^^^^^UCLA^^^^^RRMC||" + "<CR>"
            + "PID|1|2100355^^^MRN^MRN|2100355^^^MRN^MRN||MDCLS9^MC9||19700109|F||U|111 HOVER STREET^^LOS ANGELES^CA^90032^USA^P^^LOS ANGELE|LOS ANGELE|(310)725-6952^P^PH^^^310^7256952"
            +     "||ENGLISH|U||60000013647|565-33-2222|||U||||||||N||" + "<CR>"
            + "PD1|||UCLA HEALTH SYSTEM^^10|10002116^ADAMS^JOHN^D^^^^^EPIC^^^^PROVID||||||||||||||" + "<CR>"
            + "NK1|1|DOE^MC9^^|OTH|^^^^^USA|(310)888-9999^^^^^310^8889999|(310)999-2222^^^^^310^9992222|Emergency Contact 1|||||||||||||||||||||||||||" + "<CR>"
            + "PV1|1|OUTPATIENT|RR CT^^^1000^^^^^^^DEPID|EL|||017511^TOBIAS^JONATHAN^^^^^^EPIC^^^^PROVID|017511^TOBIAS^JONATHAN^^^^^^EPIC^^^^PROVID||||||CLR|||||60000013647|SELF"
            +     "|||||||||||||||||||||HOV_CONF|^^^1000^^^^^^^||20150107161438||||||||||" + "<CR>"
            + "PV2||||||||20150107161438||||CT BRAIN W WO CONTRAST||||||||||N|||||||||||||||||||||||||||" + "<CR>"
            + "ZPV||||||||||||20150107161438|||||||||" + "<CR>"
            + "AL1|1||33361^NO KNOWN ALLERGIES^^NOTCOMPUTRITION^NO KNOWN ALLERGIES^EXTELG||||||" + "<CR>"
            + "DG1|1|DX|784.0^Headache^DX|Headache||VISIT" + "<CR>"
            + "GT1|1|1000235129|MDCLS9^MC9^^||111 HOVER STREET^^LOS ANGELES^CA^90032^USA^^^LOS ANGELE|(310)725-6952^^^^^310^7256952||19700109|F|P/F|SLF|565-33-2222|||||^^^^^USA|||UNKNOWN"
            +     "|||||||||||||||||||||||||||||" + "<CR>"
            + "UB2||||||||" + "<CR>"
            + "<LF>";
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

        assertEquals("The HL7 payload terminating bytes [0x7c, 0x41] are incorrect - expected [0xd, 0xa]  {ASCII [<CR>, <LF>]}", message);
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

        assertEquals("ADT^A08|00001|D|2.3^^|||||||<CR>EVN|A08|2015010716144", Hl7Util.bytesToPrintFriendlyStringBuilder(TEST_MESSAGE_BYTES, 50, 100).toString());
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
        Hl7Util.appendBytesAsPrintFriendlyString(builder, TEST_MESSAGE_BYTES, 50, 100);
        assertEquals("ADT^A08|00001|D|2.3^^|||||||<CR>EVN|A08|2015010716144", builder.toString());
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
        assertEquals(Hl7Util.START_OF_BLOCK_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendCharacterAsPrintFriendlyString(builder, MllpProtocolConstants.END_OF_BLOCK);
        assertEquals(Hl7Util.END_OF_BLOCK_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendCharacterAsPrintFriendlyString(builder, MllpProtocolConstants.SEGMENT_DELIMITER);
        assertEquals(Hl7Util.SEGMENT_DELIMITER_REPLACEMENT_VALUE, builder.toString());

        builder = new StringBuilder();
        Hl7Util.appendCharacterAsPrintFriendlyString(builder, MllpProtocolConstants.MESSAGE_TERMINATOR);
        assertEquals(Hl7Util.MESSAGE_TERMINATOR_REPLACEMENT_VALUE, builder.toString());
    }

    @Test
    public void testGetCharacterAsPrintFriendlyString() throws Exception {
        assertEquals(Hl7Util.START_OF_BLOCK_REPLACEMENT_VALUE, Hl7Util.getCharacterAsPrintFriendlyString(MllpProtocolConstants.START_OF_BLOCK));
        assertEquals(Hl7Util.END_OF_BLOCK_REPLACEMENT_VALUE, Hl7Util.getCharacterAsPrintFriendlyString(MllpProtocolConstants.END_OF_BLOCK));
        assertEquals(Hl7Util.SEGMENT_DELIMITER_REPLACEMENT_VALUE, Hl7Util.getCharacterAsPrintFriendlyString(MllpProtocolConstants.SEGMENT_DELIMITER));
        assertEquals(Hl7Util.MESSAGE_TERMINATOR_REPLACEMENT_VALUE, Hl7Util.getCharacterAsPrintFriendlyString(MllpProtocolConstants.MESSAGE_TERMINATOR));
        assertEquals(Hl7Util.TAB_REPLACEMENT_VALUE, Hl7Util.getCharacterAsPrintFriendlyString('\t'));
    }
}