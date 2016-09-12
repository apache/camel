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
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MllpFrameExceptionTest {
    static final String EXCEPTION_MESSAGE = "Test Frame Exception";

    static final String HL7_MESSAGE =
            "MSH|^~\\&|APP_A|FAC_A|^org^sys||||ADT^A04^ADT_A04|||2.6" + '\r'
            + "PID|1||1100832^^^^PI||TEST^FIG||98765432|U||R|435 MAIN STREET^^LONGMONT^CO^80503||123-456-7890|||S" + '\r'
            + '\r' + '\n';

    static final String EXCEPTION_MESSAGE_WITH_LOG_PHI_DISABLED = EXCEPTION_MESSAGE;
    static final String EXCEPTION_MESSAGE_WITH_LOG_PHI_ENABLED =
            String.format(String.format("%s:\n\tMLLP Payload: %s",
                    EXCEPTION_MESSAGE,
                    new String(HL7_MESSAGE).replaceAll("\r", "<CR>").replaceAll("\n", "<LF>"))
            );

    Exception exception;

    @Before
    public void setUp() throws Exception {
        exception = new MllpFrameException(EXCEPTION_MESSAGE, HL7_MESSAGE.getBytes());
    }

    @After
    public void tearDown() throws Exception {
        System.clearProperty(MllpComponent.MLLP_LOG_PHI_PROPERTY);
    }

    @Test
    public void testLogPhiDefault() throws Exception {
        String exceptionMessage = exception.getMessage();

        assertEquals(EXCEPTION_MESSAGE_WITH_LOG_PHI_ENABLED, exceptionMessage);
    }

    @Test
    public void testLogPhiDisabled() throws Exception {
        System.setProperty(MllpComponent.MLLP_LOG_PHI_PROPERTY, "false");

        String exceptionMessage = exception.getMessage();

        assertEquals(EXCEPTION_MESSAGE_WITH_LOG_PHI_DISABLED, exceptionMessage);
    }

    @Test
    public void testLogPhiEnabled() throws Exception {
        System.setProperty(MllpComponent.MLLP_LOG_PHI_PROPERTY, "true");

        String exceptionMessage = exception.getMessage();

        assertEquals(EXCEPTION_MESSAGE_WITH_LOG_PHI_ENABLED, exceptionMessage);
    }

    @Test
    public void testNullPayload() throws Exception {
        final String expectedMessage = String.format("%s:\n\tMLLP Payload: null", EXCEPTION_MESSAGE);

        exception = new MllpFrameException(EXCEPTION_MESSAGE, null);

        System.setProperty(MllpComponent.MLLP_LOG_PHI_PROPERTY, "true");
        String exceptionMessage = exception.getMessage();

        assertEquals(expectedMessage, exceptionMessage);
    }
    @Test
    public void testToString() throws Exception {
        final String expectedString =
                "org.apache.camel.component.mllp.MllpFrameException: "
                        + "{mllpPayload="
                        +      "MSH|^~\\&|APP_A|FAC_A|^org^sys||||ADT^A04^ADT_A04|||2.6<CR>"
                        +      "PID|1||1100832^^^^PI||TEST^FIG||98765432|U||R|435 MAIN STREET^^LONGMONT^CO^80503||123-456-7890|||S<CR><CR><LF>"
                        + "}";

        assertEquals(expectedString, exception.toString());
    }


}