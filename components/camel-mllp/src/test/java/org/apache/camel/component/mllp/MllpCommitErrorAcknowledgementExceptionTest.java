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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the  class.
 */
public class MllpCommitErrorAcknowledgementExceptionTest extends MllpExceptionTestSupport {
    MllpCommitErrorAcknowledgementException instance;

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testConstructorOne() throws Exception {
        instance = new MllpCommitErrorAcknowledgementException(HL7_MESSAGE_BYTES, HL7_ACKNOWLEDGEMENT_BYTES);

        assertTrue(instance.getMessage().startsWith(MllpCommitErrorAcknowledgementException.EXCEPTION_MESSAGE));
        assertNull(instance.getCause());
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.getHl7MessageBytes());
        assertArrayEquals(HL7_ACKNOWLEDGEMENT_BYTES, instance.getHl7AcknowledgementBytes());
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testConstructorTwo() throws Exception {
        instance = new MllpCommitErrorAcknowledgementException(HL7_MESSAGE_BYTES, HL7_ACKNOWLEDGEMENT_BYTES, CAUSE);

        assertTrue(instance.getMessage().startsWith(MllpCommitErrorAcknowledgementException.EXCEPTION_MESSAGE));
        assertSame(CAUSE, instance.getCause());
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.getHl7MessageBytes());
        assertArrayEquals(HL7_ACKNOWLEDGEMENT_BYTES, instance.getHl7AcknowledgementBytes());
    }

    /**
     * Description of test.
     *
     * @throws Exception in the event of a test error.
     */
    @Test
    public void testGetAcknowledgmentType() throws Exception {
        instance = new MllpCommitErrorAcknowledgementException(HL7_MESSAGE_BYTES, HL7_ACKNOWLEDGEMENT_BYTES);

        assertEquals("CE", instance.getAcknowledgmentType());
    }

}