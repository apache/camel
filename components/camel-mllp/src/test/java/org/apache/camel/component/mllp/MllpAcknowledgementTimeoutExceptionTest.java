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

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the  class.
 */
public class MllpAcknowledgementTimeoutExceptionTest extends MllpExceptionTestSupport {
    static final String ALTERNATE_EXCEPTION_MESSAGE = "Test Message";

    MllpAcknowledgementTimeoutException instance;

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testConstructorOne() throws Exception {
        instance = new MllpAcknowledgementTimeoutException(HL7_MESSAGE_BYTES);

        assertTrue(instance.getMessage().startsWith(MllpAcknowledgementTimeoutException.EXCEPTION_MESSAGE));
        assertNull(instance.getCause());
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.hl7MessageBytes);
        assertNull(instance.hl7AcknowledgementBytes);
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testConstructorTwo() throws Exception {
        instance = new MllpAcknowledgementTimeoutException(HL7_MESSAGE_BYTES, HL7_ACKNOWLEDGEMENT_BYTES);

        assertTrue(instance.getMessage().startsWith(MllpAcknowledgementTimeoutException.EXCEPTION_MESSAGE));
        assertNull(instance.getCause());
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.hl7MessageBytes);
        assertArrayEquals(HL7_ACKNOWLEDGEMENT_BYTES, instance.hl7AcknowledgementBytes);
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testConstructorThree() throws Exception {
        instance = new MllpAcknowledgementTimeoutException(HL7_MESSAGE_BYTES, CAUSE);

        assertTrue(instance.getMessage().startsWith(MllpAcknowledgementTimeoutException.EXCEPTION_MESSAGE));
        assertSame(CAUSE, instance.getCause());
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.hl7MessageBytes);
        assertNull(instance.hl7AcknowledgementBytes);
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testConstructorFour() throws Exception {
        instance = new MllpAcknowledgementTimeoutException(HL7_MESSAGE_BYTES, HL7_ACKNOWLEDGEMENT_BYTES, CAUSE);

        assertTrue(instance.getMessage().startsWith(MllpAcknowledgementTimeoutException.EXCEPTION_MESSAGE));
        assertSame(CAUSE, instance.getCause());
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.hl7MessageBytes);
        assertArrayEquals(HL7_ACKNOWLEDGEMENT_BYTES, instance.hl7AcknowledgementBytes);
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testConstructorFive() throws Exception {
        instance = new MllpAcknowledgementTimeoutException(ALTERNATE_EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES);

        assertTrue(instance.getMessage().startsWith(ALTERNATE_EXCEPTION_MESSAGE));
        assertNull(instance.getCause());
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.hl7MessageBytes);
        assertNull(instance.hl7AcknowledgementBytes);
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testConstructorSix() throws Exception {
        instance = new MllpAcknowledgementTimeoutException(ALTERNATE_EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, HL7_ACKNOWLEDGEMENT_BYTES);

        assertTrue(instance.getMessage().startsWith(ALTERNATE_EXCEPTION_MESSAGE));
        assertNull(instance.getCause());
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.hl7MessageBytes);
        assertArrayEquals(HL7_ACKNOWLEDGEMENT_BYTES, instance.hl7AcknowledgementBytes);
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testConstructorSeven() throws Exception {
        instance = new MllpAcknowledgementTimeoutException(ALTERNATE_EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, CAUSE);

        assertTrue(instance.getMessage().startsWith(ALTERNATE_EXCEPTION_MESSAGE));
        assertSame(CAUSE, instance.getCause());
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.hl7MessageBytes);
        assertNull(instance.hl7AcknowledgementBytes);
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testConstructorEight() throws Exception {
        instance = new MllpAcknowledgementTimeoutException(ALTERNATE_EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, HL7_ACKNOWLEDGEMENT_BYTES, CAUSE);

        assertTrue(instance.getMessage().startsWith(ALTERNATE_EXCEPTION_MESSAGE));
        assertSame(CAUSE, instance.getCause());
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.hl7MessageBytes);
        assertArrayEquals(HL7_ACKNOWLEDGEMENT_BYTES, instance.hl7AcknowledgementBytes);
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testGetHl7Acknowledgement() throws Exception {
        instance = new MllpAcknowledgementTimeoutException(ALTERNATE_EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, HL7_ACKNOWLEDGEMENT_BYTES, CAUSE);
        assertArrayEquals(HL7_ACKNOWLEDGEMENT_BYTES, instance.getHl7Acknowledgement());

        instance = new MllpAcknowledgementTimeoutException(ALTERNATE_EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, new byte[0], CAUSE);
        assertNull(instance.getHl7Acknowledgement());

        instance = new MllpAcknowledgementTimeoutException(ALTERNATE_EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, CAUSE);
        assertNull(instance.getHl7Acknowledgement());
    }

}