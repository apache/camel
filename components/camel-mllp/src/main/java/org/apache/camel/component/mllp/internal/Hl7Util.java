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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.component.mllp.MllpComponent;
import org.apache.camel.component.mllp.MllpProtocolConstants;
import org.apache.camel.processor.mllp.Hl7AcknowledgementGenerationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Hl7Util {
    public static final String START_OF_BLOCK_REPLACEMENT_VALUE = "<VT>";      // VT (vertical tab)        - decimal 11, octal 013
    public static final String END_OF_BLOCK_REPLACEMENT_VALUE = "<FS>";        // FS (file separator)      - decimal 28, octal 034
    public static final String SEGMENT_DELIMITER_REPLACEMENT_VALUE = "<CR>";   // CR (carriage return)     - decimal 13, octal 015
    public static final String MESSAGE_TERMINATOR_REPLACEMENT_VALUE = "<LF>";  // LF (line feed, new line) - decimal 10, octal 012
    // Some other non-printable characters
    public static final String TAB_REPLACEMENT_VALUE = "<TAB>";      // TAB (tab)        - decimal 9, octal 011
    public static final String BACKSPACE_REPLACEMENT_VALUE = "<BS>";      // BS (backspace)        - decimal 8, octal 010
    public static final String FORMFEED_REPLACEMENT_VALUE = "<FF>";      // FF (tab)        - decimal 12, octal 014

    public static final String NULL_REPLACEMENT_VALUE = "<null>";
    public static final String EMPTY_REPLACEMENT_VALUE = "<>";

    static final int STRING_BUFFER_PAD_SIZE = 100;

    static final Logger LOG = LoggerFactory.getLogger(Hl7Util.class);

    private Hl7Util() {
        //utility class, never constructed
    }

    public static String generateInvalidPayloadExceptionMessage(final byte[] hl7Bytes) {
        if (hl7Bytes == null) {
            return "HL7 payload is null";
        }

        return generateInvalidPayloadExceptionMessage(hl7Bytes, hl7Bytes.length);
    }

    /**
     * Verifies that the HL7 payload array <p> The MLLP protocol does not allow embedded START_OF_BLOCK or END_OF_BLOCK characters.  The END_OF_DATA character is allowed (and expected) because it is
     * also the segment delimiter for an HL7 message
     *
     * @param hl7Bytes the HL7 payload to validate
     *
     * @return If the payload is invalid, an error message suitable for inclusion in an exception is returned.  If the payload is valid, null is returned;
     */
    public static String generateInvalidPayloadExceptionMessage(final byte[] hl7Bytes, final int length) {
        if (hl7Bytes == null) {
            return "HL7 payload is null";
        }

        if (hl7Bytes.length <= 0) {
            return "HL7 payload is empty";
        }

        if (length > hl7Bytes.length) {
            LOG.warn("The length specified for the HL7 payload array <{}> is greater than the actual length of the array <{}> - only validating {} bytes", length, hl7Bytes.length, hl7Bytes.length);
        }

        if (hl7Bytes.length < 3 || hl7Bytes[0] != 'M' || hl7Bytes[1] != 'S' || hl7Bytes[2] != 'H') {
            return String.format("The first segment of the HL7 payload {%s} is not an MSH segment", new String(hl7Bytes, 0, Math.min(3, hl7Bytes.length)));
        }

        int validationLength = Math.min(length, hl7Bytes.length);

        if (hl7Bytes[validationLength - 2] != MllpProtocolConstants.SEGMENT_DELIMITER || hl7Bytes[validationLength - 1] != MllpProtocolConstants.MESSAGE_TERMINATOR) {
            String format = "The HL7 payload terminating bytes [%#x, %#x] are incorrect - expected [%#x, %#x]  {ASCII [<CR>, <LF>]}";
            return String.format(format, hl7Bytes[validationLength - 2], hl7Bytes[validationLength - 1],
                (byte) MllpProtocolConstants.SEGMENT_DELIMITER, (byte) MllpProtocolConstants.MESSAGE_TERMINATOR);
        }

        for (int i = 0; i < validationLength; ++i) {
            switch (hl7Bytes[i]) {
            case MllpProtocolConstants.START_OF_BLOCK:
                return String.format("HL7 payload contains an embedded START_OF_BLOCK {%#x, ASCII <VT>} at index %d", hl7Bytes[i], i);
            case MllpProtocolConstants.END_OF_BLOCK:
                return String.format("HL7 payload contains an embedded END_OF_BLOCK {%#x, ASCII <FS>} at index %d", hl7Bytes[i], i);
            default:
                // continue on
            }
        }

        return null;
    }

    public static void generateAcknowledgementPayload(MllpSocketBuffer mllpSocketBuffer, byte[] hl7MessageBytes, String acknowledgementCode) throws Hl7AcknowledgementGenerationException {
        if (hl7MessageBytes == null) {
            throw new Hl7AcknowledgementGenerationException("Null HL7 message received for parsing operation");
        }

        final byte fieldSeparator = hl7MessageBytes[3];
        final byte componentSeparator = hl7MessageBytes[4];

        List<Integer> fieldSeparatorIndexes = new ArrayList<>(10);  // We need at least 10 fields to create the acknowledgment

        // Find the end of the MSH and indexes of the fields in the MSH
        int endOfMSH = -1;
        for (int i = 0; i < hl7MessageBytes.length; ++i) {
            if (fieldSeparator == hl7MessageBytes[i]) {
                fieldSeparatorIndexes.add(i);
            } else if (MllpProtocolConstants.SEGMENT_DELIMITER == hl7MessageBytes[i]) {
                endOfMSH = i;
                break;
            }
        }

        if (-1 == endOfMSH) {
            throw new Hl7AcknowledgementGenerationException("Failed to find the end of the  MSH Segment while attempting to generate response", hl7MessageBytes);
        }

        if (8 > fieldSeparatorIndexes.size()) {
            throw new Hl7AcknowledgementGenerationException("Insufficient number of fields in after MSH-2 in MSH to generate a response - 8 are required but "
                + fieldSeparatorIndexes.size() + " " + "were found", hl7MessageBytes);
        }

        // Start building the MLLP Envelope
        mllpSocketBuffer.openMllpEnvelope();

        // Build the MSH Segment
        mllpSocketBuffer.write(hl7MessageBytes, 0, fieldSeparatorIndexes.get(1)); // through MSH-2 (without trailing field separator)
        mllpSocketBuffer.write(hl7MessageBytes, fieldSeparatorIndexes.get(3), fieldSeparatorIndexes.get(4) - fieldSeparatorIndexes.get(3)); // MSH-5
        mllpSocketBuffer.write(hl7MessageBytes, fieldSeparatorIndexes.get(4), fieldSeparatorIndexes.get(5) - fieldSeparatorIndexes.get(4)); // MSH-6
        mllpSocketBuffer.write(hl7MessageBytes, fieldSeparatorIndexes.get(1), fieldSeparatorIndexes.get(2) - fieldSeparatorIndexes.get(1)); // MSH-3
        mllpSocketBuffer.write(hl7MessageBytes, fieldSeparatorIndexes.get(2), fieldSeparatorIndexes.get(3) - fieldSeparatorIndexes.get(2)); // MSH-4
        mllpSocketBuffer.write(hl7MessageBytes, fieldSeparatorIndexes.get(5), fieldSeparatorIndexes.get(7) - fieldSeparatorIndexes.get(5)); // MSH-7 and MSH-8
        // Need to generate the correct MSH-9
        mllpSocketBuffer.write(fieldSeparator);
        mllpSocketBuffer.write("ACK".getBytes(), 0, 3); // MSH-9.1
        int msh92start = -1;
        for (int j = fieldSeparatorIndexes.get(7) + 1; j < fieldSeparatorIndexes.get(8); ++j) {
            if (componentSeparator == hl7MessageBytes[j]) {
                msh92start = j;
                break;
            }
        }

        if (-1 == msh92start) {
            LOG.warn("Didn't find component separator for MSH-9.2 - sending ACK in MSH-9");
        } else {
            mllpSocketBuffer.write(hl7MessageBytes, msh92start, fieldSeparatorIndexes.get(8) - msh92start); // MSH-9.2
        }

        mllpSocketBuffer.write(hl7MessageBytes, fieldSeparatorIndexes.get(8), endOfMSH - fieldSeparatorIndexes.get(8)); // MSH-10 through the end of the MSH
        mllpSocketBuffer.write(MllpProtocolConstants.SEGMENT_DELIMITER);

        // Build the MSA Segment
        mllpSocketBuffer.write("MSA".getBytes(), 0, 3);
        mllpSocketBuffer.write(fieldSeparator);
        mllpSocketBuffer.write(acknowledgementCode.getBytes(), 0, 2);
        mllpSocketBuffer.write(hl7MessageBytes, fieldSeparatorIndexes.get(8), fieldSeparatorIndexes.get(9) - fieldSeparatorIndexes.get(8)); // MSH-10 end
        mllpSocketBuffer.write(MllpProtocolConstants.SEGMENT_DELIMITER);

        // Close the MLLP Envelope
        mllpSocketBuffer.write(MllpProtocolConstants.PAYLOAD_TERMINATOR);

        return;
    }

    public static String convertToPrintFriendlyString(String phiString) {
        if (null == phiString) {
            return NULL_REPLACEMENT_VALUE;
        } else if (phiString.length() == 0) {
            return EMPTY_REPLACEMENT_VALUE;
        }

        int logPhiMaxBytes = MllpComponent.getLogPhiMaxBytes();
        int conversionLength = (logPhiMaxBytes > 0) ? Integer.min(phiString.length(), logPhiMaxBytes) : phiString.length();

        StringBuilder builder = new StringBuilder(conversionLength + STRING_BUFFER_PAD_SIZE);

        for (int i = 0; i < conversionLength; ++i) {
            appendCharacterAsPrintFriendlyString(builder, phiString.charAt(i));
        }

        return builder.toString();
    }

    public static String convertToPrintFriendlyString(byte[] phiBytes) {
        return bytesToPrintFriendlyStringBuilder(phiBytes).toString();
    }

    /**
     * Convert a PHI byte[] to a String, replacing specific non-printable characters with readable strings.
     *
     * NOTE: this conversion uses the default character set, so not all characters my convert correctly.
     *
     * @param phiBytes      the PHI byte[] to log
     * @param startPosition the starting position/index of the data
     * @param endPosition   the ending position/index of the data - will not be included in String
     *
     * @return a String representation of the byte[]
     */
    public static String convertToPrintFriendlyString(byte[] phiBytes, int startPosition, int endPosition) {
        return bytesToPrintFriendlyStringBuilder(phiBytes, startPosition, endPosition).toString();
    }

    /**
     * Convert a PHI byte[] to a StringBuilder, replacing specific non-printable characters with readable strings.
     *
     * NOTE: this conversion uses the default character set, so not all characters my convert correctly.
     *
     * @param phiBytes the PHI byte[] to log
     *
     * @return
     */
    public static StringBuilder bytesToPrintFriendlyStringBuilder(byte[] phiBytes) {
        return bytesToPrintFriendlyStringBuilder(phiBytes, 0, phiBytes != null ? phiBytes.length : -1);
    }

    /**
     * Convert a PHI byte[] to a StringBuilder, replacing specific non-printable characters with readable strings.
     *
     * NOTE: this conversion uses the default character set, so not all characters my convert correctly.
     *
     * @param phiBytes      the PHI byte[] to log
     * @param startPosition the starting position/index of the data
     * @param endPosition   the ending position/index of the data - will not be included in StringBuilder
     *
     * @return a String representation of the byte[]
     */
    public static StringBuilder bytesToPrintFriendlyStringBuilder(byte[] phiBytes, int startPosition, int endPosition) {
        StringBuilder answer = new StringBuilder();

        appendBytesAsPrintFriendlyString(answer, phiBytes, startPosition, endPosition);

        return answer;
    }

    public static void appendBytesAsPrintFriendlyString(StringBuilder builder, byte[] phiBytes) {
        appendBytesAsPrintFriendlyString(builder, phiBytes, 0, phiBytes != null ? phiBytes.length : 0);
    }

    /**
     * Append a PHI byte[] to a StringBuilder, replacing specific non-printable characters with readable strings.
     *
     * NOTE: this conversion uses the default character set, so not all characters my convert correctly.
     *
     * @param phiBytes      the PHI byte[] to log
     * @param startPosition the starting position/index of the data
     * @param endPosition   the ending position/index of the data - will not be included in String
     */
    public static void appendBytesAsPrintFriendlyString(StringBuilder builder, byte[] phiBytes, int startPosition, int endPosition) {
        if (builder == null) {
            throw new IllegalArgumentException("StringBuilder cannot be null");
        }

        if (null == phiBytes) {
            builder.append(NULL_REPLACEMENT_VALUE);
        } else if (phiBytes.length == 0) {
            builder.append(EMPTY_REPLACEMENT_VALUE);
        } else if (startPosition <= endPosition) {
            if (startPosition < 0) {
                startPosition = 0;
            }

            if (startPosition < phiBytes.length) {
                if (endPosition >= -1) {
                    if (endPosition == -1 || endPosition >= phiBytes.length) {
                        endPosition = phiBytes.length;
                    }

                    int length = endPosition - startPosition;
                    if (length > 0) {
                        int logPhiMaxBytes = MllpComponent.getLogPhiMaxBytes();
                        int conversionLength = (logPhiMaxBytes > 0) ? Integer.min(length, logPhiMaxBytes) : length;
                        if (builder.capacity() - builder.length() < conversionLength + STRING_BUFFER_PAD_SIZE) {
                            builder.ensureCapacity(builder.length() + conversionLength + STRING_BUFFER_PAD_SIZE);
                        }
                        for (int i = 0; i < conversionLength; ++i) {
                            appendCharacterAsPrintFriendlyString(builder, (char) phiBytes[startPosition + i]);
                        }
                    }
                }
            }
        }
    }

    static void appendCharacterAsPrintFriendlyString(StringBuilder builder, char c) {
        switch (c) {
        case MllpProtocolConstants.START_OF_BLOCK:
            builder.append(START_OF_BLOCK_REPLACEMENT_VALUE);
            break;
        case MllpProtocolConstants.END_OF_BLOCK:
            builder.append(END_OF_BLOCK_REPLACEMENT_VALUE);
            break;
        case MllpProtocolConstants.SEGMENT_DELIMITER:
            builder.append(SEGMENT_DELIMITER_REPLACEMENT_VALUE);
            break;
        case MllpProtocolConstants.MESSAGE_TERMINATOR:
            builder.append(MESSAGE_TERMINATOR_REPLACEMENT_VALUE);
            break;
        default:
            builder.append(c);
        }
    }

    public static String getCharacterAsPrintFriendlyString(char c) {
        switch (c) {
        case MllpProtocolConstants.START_OF_BLOCK:
            return START_OF_BLOCK_REPLACEMENT_VALUE;
        case MllpProtocolConstants.END_OF_BLOCK:
            return END_OF_BLOCK_REPLACEMENT_VALUE;
        case MllpProtocolConstants.SEGMENT_DELIMITER:
            return SEGMENT_DELIMITER_REPLACEMENT_VALUE;
        case MllpProtocolConstants.MESSAGE_TERMINATOR:
            return MESSAGE_TERMINATOR_REPLACEMENT_VALUE;
        case '\t':
            return TAB_REPLACEMENT_VALUE;
        case '\b':
            return BACKSPACE_REPLACEMENT_VALUE;
        case '\f':
            return FORMFEED_REPLACEMENT_VALUE;
        default:
            return String.valueOf(c);
        }
    }
}
