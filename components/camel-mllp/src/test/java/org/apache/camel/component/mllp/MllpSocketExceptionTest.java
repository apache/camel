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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for the class.
 */
public class MllpSocketExceptionTest extends MllpExceptionTestSupport {
    static final String TEST_EXCEPTION_MESSAGE = "MLLP Socket Exception Message";

    MllpSocketException instance;

    /**
     * Description of test.
     *
     */
    @Test
    public void testConstructorOne() {
        instance = new MllpSocketException(TEST_EXCEPTION_MESSAGE);

        assertNull(instance.getCause());
        assertEquals(TEST_EXCEPTION_MESSAGE, instance.getMessage());
    }

    /**
     * Description of test.
     *
     */
    @Test
    public void testConstructorTwo() {
        instance = new MllpSocketException(TEST_EXCEPTION_MESSAGE, CAUSE);

        assertNotNull(instance.getCause());
        assertEquals(TEST_EXCEPTION_MESSAGE, instance.getMessage());
    }

}
