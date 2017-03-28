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