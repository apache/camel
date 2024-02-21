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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the class.
 */
public class MllpAcknowledgementTimeoutExceptionTest extends MllpExceptionTestSupport {
    static final String ALTERNATE_EXCEPTION_MESSAGE = "Test Message";

    MllpAcknowledgementTimeoutException instance;

    /**
     * Description of test.
     *
     */
    @Test
    public void testConstructorOne() {
        instance = new MllpAcknowledgementTimeoutException(HL7_MESSAGE_BYTES, LOG_PHI_TRUE);

        assertTrue(instance.getMessage().startsWith(MllpAcknowledgementTimeoutException.EXCEPTION_MESSAGE));
        assertNull(instance.getCause());
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.hl7MessageBytes);
        assertNull(instance.hl7AcknowledgementBytes);
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testConstructorTwo() {
        instance = new MllpAcknowledgementTimeoutException(HL7_MESSAGE_BYTES, HL7_ACKNOWLEDGEMENT_BYTES, LOG_PHI_TRUE);

        assertTrue(instance.getMessage().startsWith(MllpAcknowledgementTimeoutException.EXCEPTION_MESSAGE));
        assertNull(instance.getCause());
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.hl7MessageBytes);
        assertArrayEquals(HL7_ACKNOWLEDGEMENT_BYTES, instance.hl7AcknowledgementBytes);
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testConstructorThree() {
        instance = new MllpAcknowledgementTimeoutException(HL7_MESSAGE_BYTES, CAUSE, LOG_PHI_TRUE);

        assertTrue(instance.getMessage().startsWith(MllpAcknowledgementTimeoutException.EXCEPTION_MESSAGE));
        assertSame(CAUSE, instance.getCause());
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.hl7MessageBytes);
        assertNull(instance.hl7AcknowledgementBytes);
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testConstructorFour() {
        instance = new MllpAcknowledgementTimeoutException(HL7_MESSAGE_BYTES, HL7_ACKNOWLEDGEMENT_BYTES, CAUSE, LOG_PHI_TRUE);

        assertTrue(instance.getMessage().startsWith(MllpAcknowledgementTimeoutException.EXCEPTION_MESSAGE));
        assertSame(CAUSE, instance.getCause());
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.hl7MessageBytes);
        assertArrayEquals(HL7_ACKNOWLEDGEMENT_BYTES, instance.hl7AcknowledgementBytes);
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testConstructorFive() {
        instance = new MllpAcknowledgementTimeoutException(ALTERNATE_EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, LOG_PHI_TRUE);

        assertTrue(instance.getMessage().startsWith(ALTERNATE_EXCEPTION_MESSAGE));
        assertNull(instance.getCause());
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.hl7MessageBytes);
        assertNull(instance.hl7AcknowledgementBytes);
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testConstructorSix() {
        instance = new MllpAcknowledgementTimeoutException(
                ALTERNATE_EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, HL7_ACKNOWLEDGEMENT_BYTES, LOG_PHI_TRUE);

        assertTrue(instance.getMessage().startsWith(ALTERNATE_EXCEPTION_MESSAGE));
        assertNull(instance.getCause());
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.hl7MessageBytes);
        assertArrayEquals(HL7_ACKNOWLEDGEMENT_BYTES, instance.hl7AcknowledgementBytes);
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testConstructorSeven() {
        instance = new MllpAcknowledgementTimeoutException(ALTERNATE_EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, CAUSE, LOG_PHI_TRUE);

        assertTrue(instance.getMessage().startsWith(ALTERNATE_EXCEPTION_MESSAGE));
        assertSame(CAUSE, instance.getCause());
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.hl7MessageBytes);
        assertNull(instance.hl7AcknowledgementBytes);
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testConstructorEight() {
        instance = new MllpAcknowledgementTimeoutException(
                ALTERNATE_EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, HL7_ACKNOWLEDGEMENT_BYTES, CAUSE, LOG_PHI_TRUE);

        assertTrue(instance.getMessage().startsWith(ALTERNATE_EXCEPTION_MESSAGE));
        assertSame(CAUSE, instance.getCause());
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.hl7MessageBytes);
        assertArrayEquals(HL7_ACKNOWLEDGEMENT_BYTES, instance.hl7AcknowledgementBytes);
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testGetHl7Acknowledgement() {
        instance = new MllpAcknowledgementTimeoutException(
                ALTERNATE_EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, HL7_ACKNOWLEDGEMENT_BYTES, CAUSE, LOG_PHI_TRUE);
        assertArrayEquals(HL7_ACKNOWLEDGEMENT_BYTES, instance.getHl7Acknowledgement());

        instance = new MllpAcknowledgementTimeoutException(
                ALTERNATE_EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, new byte[0], CAUSE, LOG_PHI_TRUE);
        assertNull(instance.getHl7Acknowledgement());

        instance = new MllpAcknowledgementTimeoutException(ALTERNATE_EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, CAUSE, LOG_PHI_TRUE);
        assertNull(instance.getHl7Acknowledgement());
    }

}
