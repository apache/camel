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
package org.apache.camel.component.mllp.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.mllp.MllpEndpoint.END_OF_BLOCK;
import static org.apache.camel.component.mllp.MllpEndpoint.MESSAGE_TERMINATOR;
import static org.apache.camel.component.mllp.MllpEndpoint.SEGMENT_DELIMITER;
import static org.apache.camel.component.mllp.MllpEndpoint.START_OF_BLOCK;

public final class Hl7Util {

    static final Logger LOG = LoggerFactory.getLogger(Hl7Util.class);

    private Hl7Util() {
    }

    public static String generateInvalidPayloadExceptionMessage(final byte[] hl7Bytes) {
        if (hl7Bytes == null) {
            return "HL7 payload is null";
        }

        return generateInvalidPayloadExceptionMessage(hl7Bytes, hl7Bytes.length);
    }

    /**
     * Verifies that the HL7 payload array
     * <p>
     * The MLLP protocol does not allow embedded START_OF_BLOCK or END_OF_BLOCK characters.  The END_OF_DATA character
     * is allowed (and expected) because it is also the segment delimiter for an HL7 message
     *
     * @param hl7Bytes the HL7 payload to validate
     * @return If the payload is invalid, an error message suitable for inclusion in an exception is returned.  If
     * the payload is valid, null is returned;
     */
    public static String generateInvalidPayloadExceptionMessage(final byte[] hl7Bytes, final int length) {
        if (hl7Bytes == null) {
            return "HL7 payload is null";
        }

        if (hl7Bytes.length <= 0) {
            return "HL7 payload is empty";
        }

        if (length > hl7Bytes.length) {
            LOG.warn("The length specified for the HL7 payload array <{}> is greater than the actual length of the array <{}> - only validating {} bytes", length, hl7Bytes.length, length);
        }

        if (hl7Bytes.length < 3 || hl7Bytes[0] != 'M' || hl7Bytes[1] != 'S' || hl7Bytes[2] != 'H') {
            return String.format("The first segment of the HL7 payload {%s} is not an MSH segment", new String(hl7Bytes, 0, Math.min(3, hl7Bytes.length)));
        }

        int validationLength = Math.min(length, hl7Bytes.length);

        if (hl7Bytes[validationLength - 2] != SEGMENT_DELIMITER || hl7Bytes[validationLength - 1] != MESSAGE_TERMINATOR) {
            String format = "The HL7 payload terminating bytes [%#x, %#x] are incorrect - expected [%#x, %#x]  {ASCII [<CR>, <LF>]}";
            return String.format(format, hl7Bytes[validationLength - 2], hl7Bytes[validationLength - 1], (byte) SEGMENT_DELIMITER, (byte) MESSAGE_TERMINATOR);
        }

        for (int i = 0; i < validationLength; ++i) {
            switch (hl7Bytes[i]) {
            case START_OF_BLOCK:
                return String.format("HL7 payload contains an embedded START_OF_BLOCK {%#x, ASCII <VT>} at index %d", hl7Bytes[i], i);
            case END_OF_BLOCK:
                return String.format("HL7 payload contains an embedded END_OF_BLOCK {%#x, ASCII <FS>} at index %d", hl7Bytes[i], i);
            default:
                // continue on
            }
        }

        return null;
    }


}
