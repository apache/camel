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
public class MllpInvalidMessageExceptionTest extends MllpExceptionTestSupport {
    static final String EXCEPTION_MESSAGE = "Invalid Message Exception Message";

    MllpInvalidMessageException instance;

    /**
     * Description of test.
     *
     */
    @Test
    public void testConstructorOne() {
        instance = new MllpInvalidMessageException(EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, LOG_PHI_TRUE);

        assertTrue(instance.getMessage().startsWith(EXCEPTION_MESSAGE));
        assertNull(instance.getCause());
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.getHl7MessageBytes());
        assertNull(instance.getHl7AcknowledgementBytes());
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testConstructorTwo() {
        instance = new MllpInvalidMessageException(EXCEPTION_MESSAGE, HL7_MESSAGE_BYTES, CAUSE, LOG_PHI_TRUE);

        assertTrue(instance.getMessage().startsWith(EXCEPTION_MESSAGE));
        assertSame(CAUSE, instance.getCause());
        assertArrayEquals(HL7_MESSAGE_BYTES, instance.getHl7MessageBytes());
        assertNull(instance.getHl7AcknowledgementBytes());
    }
}
