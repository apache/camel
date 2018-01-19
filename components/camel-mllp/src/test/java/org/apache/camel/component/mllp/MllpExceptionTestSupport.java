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

public class MllpExceptionTestSupport {
    public static final String HL7_MESSAGE =
        "MSH|^~\\&|APP_A|FAC_A|^org^sys||20161206193919||ADT^A04|00001||2.6" + '\r'
            + "PID|1||1100832^^^^PI||TEST^FIG||98765432|U||R|435 MAIN STREET^^LONGMONT^CO^80503||123-456-7890|||S" + '\r'
            + '\n';

    public static final String HL7_ACKNOWLEDGEMENT =
        "MSH|^~\\&|APP_A|FAC_A|^org^sys||20161206193919||ACK^A04|00002||2.6" + '\r'
            + "MSA|AA|00001" + '\r'
            + '\n';

    public static final byte[] HL7_MESSAGE_BYTES = HL7_MESSAGE.getBytes();
    public static final byte[] HL7_ACKNOWLEDGEMENT_BYTES = HL7_ACKNOWLEDGEMENT.getBytes();

    public static final Exception CAUSE = new Exception("Dummy Exception");

    @After
    public void tearDown() throws Exception {
        System.clearProperty(MllpComponent.MLLP_LOG_PHI_PROPERTY);
        System.clearProperty(MllpComponent.MLLP_LOG_PHI_MAX_BYTES_PROPERTY);
    }

}