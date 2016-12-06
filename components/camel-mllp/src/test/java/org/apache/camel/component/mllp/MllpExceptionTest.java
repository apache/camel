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
package org.apache.camel.component.mllp;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MllpExceptionTest {
    static final String EXCEPTION_MESSAGE = "Test Frame Exception";

    static final String HL7_MESSAGE =
            "MSH|^~\\&|APP_A|FAC_A|^org^sys||20161206193919||ADT^A04|00001||2.6" + '\r'
                    + "PID|1||1100832^^^^PI||TEST^FIG||98765432|U||R|435 MAIN STREET^^LONGMONT^CO^80503||123-456-7890|||S" + '\r'
                    + '\r' + '\n';

    static final String HL7_ACK =
            "MSH|^~\\&|APP_A|FAC_A|^org^sys||20161206193919||ACK^A04|00002||2.6" + '\r'
                    + "MSA|AA|00001" + '\r'
                    + '\r' + '\n';

    @After
    public void tearDown() throws Exception {
        System.clearProperty(MllpComponent.MLLP_LOG_PHI_PROPERTY);
    }

    @Test
    public void testLogPhiDefault() throws Exception {
        assertEquals(expectedMessage(HL7_MESSAGE, HL7_ACK), createException(HL7_MESSAGE, HL7_ACK).getMessage());
    }

    @Test
    public void testLogPhiDisabled() throws Exception {
        System.setProperty(MllpComponent.MLLP_LOG_PHI_PROPERTY, "false");

        assertEquals(EXCEPTION_MESSAGE, createException(HL7_MESSAGE, HL7_ACK).getMessage());
    }

    @Test
    public void testLogPhiEnabled() throws Exception {
        System.setProperty(MllpComponent.MLLP_LOG_PHI_PROPERTY, "true");

        assertEquals(expectedMessage(HL7_MESSAGE, HL7_ACK), createException(HL7_MESSAGE, HL7_ACK).getMessage());
    }

    @Test
    public void testNullHl7Message() throws Exception {
        System.setProperty(MllpComponent.MLLP_LOG_PHI_PROPERTY, "true");

        assertEquals(expectedMessage(null, HL7_ACK), createException(null, HL7_ACK).getMessage());
    }

    @Test
    public void testNullHl7Acknowledgement() throws Exception {
        System.setProperty(MllpComponent.MLLP_LOG_PHI_PROPERTY, "true");

        assertEquals(expectedMessage(HL7_MESSAGE, null), createException(HL7_MESSAGE, null).getMessage());
    }

    @Test
    public void testNullHl7Payloads() throws Exception {
        System.setProperty(MllpComponent.MLLP_LOG_PHI_PROPERTY, "true");

        assertEquals(expectedMessage(null, null), createException(null, null).getMessage());
    }


    // Utility methods
    private Exception createException(String hl7Message, String hl7Acknowledgment) {
        byte[] hl7MessageBytes = null;
        byte[] hl7AcknowledgementBytes = null;

        if (hl7Message != null) {
            hl7MessageBytes = hl7Message.getBytes();
        }

        if (hl7Acknowledgment != null) {
            hl7AcknowledgementBytes = hl7Acknowledgment.getBytes();
        }
        return new MllpException(EXCEPTION_MESSAGE, hl7MessageBytes, hl7AcknowledgementBytes);
    }

    private String expectedMessage(String hl7Message, String hl7Acknowledgment) {
        final String exceptionMessageFormat = EXCEPTION_MESSAGE + " \n\t{hl7Message= %s} \n\t{hl7Acknowledgement= %s}";

        String formattedHl7Message = null;
        String formattedHl7Acknowledgement = null;

        if (hl7Message != null) {
            formattedHl7Message = hl7Message.replaceAll("\r", "<CR>").replaceAll("\n", "<LF>");
        }

        if (hl7Acknowledgment != null) {
            formattedHl7Acknowledgement = hl7Acknowledgment.replaceAll("\r", "<CR>").replaceAll("\n", "<LF>");
        }

        return String.format(exceptionMessageFormat, formattedHl7Message, formattedHl7Acknowledgement);
    }
}