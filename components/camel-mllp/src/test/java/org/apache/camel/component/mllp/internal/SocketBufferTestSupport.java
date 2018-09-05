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

package org.apache.camel.component.mllp.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.camel.component.mllp.MllpComponent;
import org.apache.camel.component.mllp.MllpConfiguration;
import org.apache.camel.component.mllp.MllpEndpoint;
import org.apache.camel.component.mllp.MllpProtocolConstants;
import org.junit.Before;

public class SocketBufferTestSupport {
    static final String TEST_HL7_MESSAGE =
        "MSH|^~\\&|JCAPS|CC|ADT|EPIC|20161206193919|RISTECH|ACK^A08|00001|D|2.3^^|||||||" + '\r'
            + "MSA|AA|00001|" + '\r';

    MllpEndpoint endpoint;
    MllpSocketBuffer instance;

    @Before
    public void setUp() throws Exception {
        endpoint = new MllpEndpoint("mllp://dummy", new MllpComponent(), new MllpConfiguration());
        instance = new MllpSocketBuffer(endpoint);
    }

    byte[] buildTestBytes(boolean includeStartOfBlock, boolean includeEndOfBlock, boolean includeEndOfData) throws IOException {
        return buildTestBytes(TEST_HL7_MESSAGE, includeStartOfBlock, includeEndOfBlock, includeEndOfData);
    }

    byte[] buildTestBytes(String message, boolean includeStartOfBlock, boolean includeEndOfBlock, boolean includeEndOfData) throws IOException {
        ByteArrayOutputStream payloadBuilder = new ByteArrayOutputStream();

        if (includeStartOfBlock) {
            payloadBuilder.write(MllpProtocolConstants.START_OF_BLOCK);
        }

        if (message != null && !message.isEmpty()) {
            payloadBuilder.write(message.getBytes());
        }

        if (includeEndOfBlock) {
            payloadBuilder.write(MllpProtocolConstants.END_OF_BLOCK);
        }

        if (includeEndOfData) {
            payloadBuilder.write(MllpProtocolConstants.END_OF_DATA);
        }

        return payloadBuilder.toByteArray();
    }
}
