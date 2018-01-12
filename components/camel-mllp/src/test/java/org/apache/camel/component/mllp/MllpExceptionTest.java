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

import java.lang.ref.WeakReference;

import org.apache.camel.component.mllp.internal.Hl7Util;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MllpExceptionTest extends MllpExceptionTestSupport {
    static final String EXCEPTION_MESSAGE = "Test MllpException";
    static final byte[] NULL_BYTE_ARRAY = null;
    static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    MllpException instance;

    @Before
    public void setUp() throws Exception {
        instance = new MllpException(EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, HL7_ACKNOWLEDGEMENT_BYTES);
    }

    @Test
    public void testGetHl7MessageBytes() throws Exception {
        instance = new MllpException(EXCEPTION_MESSAGE);
        assertNull(instance.getHl7MessageBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, NULL_BYTE_ARRAY);
        assertNull(instance.getHl7MessageBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, NULL_BYTE_ARRAY, NULL_BYTE_ARRAY);
        assertNull(instance.getHl7MessageBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, NULL_BYTE_ARRAY, EMPTY_BYTE_ARRAY);
        assertNull(instance.getHl7MessageBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, EMPTY_BYTE_ARRAY);
        assertNull(instance.getHl7MessageBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, EMPTY_BYTE_ARRAY, NULL_BYTE_ARRAY);
        assertNull(instance.getHl7MessageBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY);
        assertNull(instance.getHl7MessageBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES);
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.getHl7MessageBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, NULL_BYTE_ARRAY);
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.getHl7MessageBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, EMPTY_BYTE_ARRAY);
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.getHl7MessageBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, HL7_ACKNOWLEDGEMENT_BYTES);
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.getHl7MessageBytes());
    }

    @Test
    public void testGetHl7AcknowledgementBytes() throws Exception {
        instance = new MllpException(EXCEPTION_MESSAGE);
        assertNull(instance.getHl7AcknowledgementBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, NULL_BYTE_ARRAY);
        assertNull(instance.getHl7AcknowledgementBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, NULL_BYTE_ARRAY, NULL_BYTE_ARRAY);
        assertNull(instance.getHl7AcknowledgementBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, NULL_BYTE_ARRAY, EMPTY_BYTE_ARRAY);
        assertNull(instance.getHl7AcknowledgementBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, EMPTY_BYTE_ARRAY);
        assertNull(instance.getHl7AcknowledgementBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, EMPTY_BYTE_ARRAY, NULL_BYTE_ARRAY);
        assertNull(instance.getHl7AcknowledgementBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY);
        assertNull(instance.getHl7AcknowledgementBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES);
        assertNull(instance.getHl7AcknowledgementBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, NULL_BYTE_ARRAY);
        assertNull(instance.getHl7AcknowledgementBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, EMPTY_BYTE_ARRAY);
        assertNull(instance.getHl7AcknowledgementBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, HL7_ACKNOWLEDGEMENT_BYTES);
        assertArrayEquals(HL7_ACKNOWLEDGEMENT_BYTES, instance.getHl7AcknowledgementBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, null, HL7_ACKNOWLEDGEMENT_BYTES);
        assertArrayEquals(HL7_ACKNOWLEDGEMENT_BYTES, instance.getHl7AcknowledgementBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, EMPTY_BYTE_ARRAY, HL7_ACKNOWLEDGEMENT_BYTES);
        assertArrayEquals(HL7_ACKNOWLEDGEMENT_BYTES, instance.getHl7AcknowledgementBytes());

        instance = new MllpException(EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, HL7_ACKNOWLEDGEMENT_BYTES);
        assertArrayEquals(HL7_ACKNOWLEDGEMENT_BYTES, instance.getHl7AcknowledgementBytes());
    }

    @Test
    public void testNullHl7Message() throws Exception {
        System.setProperty(MllpComponent.MLLP_LOG_PHI_PROPERTY, "true");

        instance = new MllpException(EXCEPTION_MESSAGE, null, HL7_ACKNOWLEDGEMENT_BYTES);

        assertEquals(expectedMessage(null, HL7_ACKNOWLEDGEMENT), instance.getMessage());
    }

    @Test
    public void testEmptyHl7Message() throws Exception {
        System.setProperty(MllpComponent.MLLP_LOG_PHI_PROPERTY, "true");

        instance = new MllpException(EXCEPTION_MESSAGE, EMPTY_BYTE_ARRAY, HL7_ACKNOWLEDGEMENT_BYTES);

        assertEquals(expectedMessage(null, HL7_ACKNOWLEDGEMENT), instance.getMessage());
    }

    @Test
    public void testNullHl7Acknowledgement() throws Exception {
        System.setProperty(MllpComponent.MLLP_LOG_PHI_PROPERTY, "true");

        instance = new MllpException(EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, NULL_BYTE_ARRAY);

        assertEquals(expectedMessage(HL7_MESSAGE, null), instance.getMessage());
    }

    @Test
    public void testEmptyAcknowledgement() throws Exception {
        System.setProperty(MllpComponent.MLLP_LOG_PHI_PROPERTY, "true");

        instance = new MllpException(EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, EMPTY_BYTE_ARRAY);

        assertEquals(expectedMessage(HL7_MESSAGE, null), instance.getMessage());
    }

    @Test
    public void testNullHl7Payloads() throws Exception {
        System.setProperty(MllpComponent.MLLP_LOG_PHI_PROPERTY, "true");

        instance = new MllpException(EXCEPTION_MESSAGE, NULL_BYTE_ARRAY, NULL_BYTE_ARRAY);

        assertEquals(expectedMessage(null, null), instance.getMessage());
    }


    private String expectedMessage(String hl7Message, String hl7Acknowledgment) {
        StringBuilder expectedMessageBuilder = new StringBuilder();

        expectedMessageBuilder.append(EXCEPTION_MESSAGE);

        if (hl7Message != null) {
            expectedMessageBuilder.append("\n\t{hl7Message [")
                .append(hl7Message.length())
                .append("] = ")
                .append(hl7Message.replaceAll("\r", "<CR>").replaceAll("\n", "<LF>"))
                .append("}");
        }

        if (hl7Acknowledgment != null) {
            expectedMessageBuilder.append("\n\t{hl7Acknowledgement [")
                .append(hl7Acknowledgment.length())
                .append("] = ")
                .append(hl7Acknowledgment.replaceAll("\r", "<CR>").replaceAll("\n", "<LF>"))
                .append("}");
        }

        return expectedMessageBuilder.toString();
    }
}