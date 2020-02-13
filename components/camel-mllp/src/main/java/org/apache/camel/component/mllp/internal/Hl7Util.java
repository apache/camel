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
package org.apache.camel.component.mllp.internal;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.mllp.MllpAcknowledgementGenerationException;
import org.apache.camel.component.mllp.MllpComponent;
import org.apache.camel.component.mllp.MllpProtocolConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Hl7Util {
    public static final String NULL_REPLACEMENT_VALUE = "<null>";
    public static final String EMPTY_REPLACEMENT_VALUE = "<>";

    public static final Map<Character, String> CHARACTER_REPLACEMENTS;

    public static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMddHHmmssSSSZZZZ");

    static final int STRING_BUFFER_PAD_SIZE = 100;

    static final Logger LOG = LoggerFactory.getLogger(Hl7Util.class);

    static {
        CHARACTER_REPLACEMENTS = new HashMap<>();
        CHARACTER_REPLACEMENTS.put((char)0x00, "<0x00 NUL>");
        CHARACTER_REPLACEMENTS.put((char)0x01, "<0x01 SOH>");
        CHARACTER_REPLACEMENTS.put((char)0x02, "<0x02 STX>");
        CHARACTER_REPLACEMENTS.put((char)0x03, "<0x03 ETX>");
        CHARACTER_REPLACEMENTS.put((char)0x04, "<0x04 EOT>");
        CHARACTER_REPLACEMENTS.put((char)0x05, "<0x05 ENQ>");
        CHARACTER_REPLACEMENTS.put((char)0x06, "<0x06 ACK>");
        CHARACTER_REPLACEMENTS.put((char)0x07, "<0x07 BEL>");
        CHARACTER_REPLACEMENTS.put((char)0x08, "<0x08 BS>");
        CHARACTER_REPLACEMENTS.put((char)0x09, "<0x09 TAB>");
        CHARACTER_REPLACEMENTS.put((char)0x0A, "<0x0A LF>");
        CHARACTER_REPLACEMENTS.put((char)0x0B, "<0x0B VT>");
        CHARACTER_REPLACEMENTS.put((char)0x0C, "<0x0C FF>");
        CHARACTER_REPLACEMENTS.put((char)0x0D, "<0x0D CR>");
        CHARACTER_REPLACEMENTS.put((char)0x0E, "<0x0E SO>");
        CHARACTER_REPLACEMENTS.put((char)0x0F, "<0x0F SI>");
        CHARACTER_REPLACEMENTS.put((char)0x10, "<0x10 DLE>");
        CHARACTER_REPLACEMENTS.put((char)0x11, "<0x11 DC1>");
        CHARACTER_REPLACEMENTS.put((char)0x12, "<0x12 DC2>");
        CHARACTER_REPLACEMENTS.put((char)0x13, "<0x13 DC3>");
        CHARACTER_REPLACEMENTS.put((char)0x14, "<0x14 DC4>");
        CHARACTER_REPLACEMENTS.put((char)0x15, "<0x15 NAK>");
        CHARACTER_REPLACEMENTS.put((char)0x16, "<0x16 SYN>");
        CHARACTER_REPLACEMENTS.put((char)0x17, "<0x17 ETB>");
        CHARACTER_REPLACEMENTS.put((char)0x18, "<0x18 CAN>");
        CHARACTER_REPLACEMENTS.put((char)0x19, "<0x19 EM>");
        CHARACTER_REPLACEMENTS.put((char)0x1A, "<0x1A SUB>");
        CHARACTER_REPLACEMENTS.put((char)0x1B, "<0x1B ESC>");
        CHARACTER_REPLACEMENTS.put((char)0x1C, "<0x1C FS>");
        CHARACTER_REPLACEMENTS.put((char)0x1D, "<0x1D GS>");
        CHARACTER_REPLACEMENTS.put((char)0x1E, "<0x1E RS>");
        CHARACTER_REPLACEMENTS.put((char)0x1F, "<0x1F US>");
        CHARACTER_REPLACEMENTS.put((char)0x7F, "<0x7F DEL>");
    }

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

        if (hl7Bytes[validationLength - 1] != MllpProtocolConstants.SEGMENT_DELIMITER  && hl7Bytes[validationLength - 1] != MllpProtocolConstants.MESSAGE_TERMINATOR) {
            String format = "The HL7 payload terminating byte [%#x] is incorrect - expected [%#x]  {ASCII [<CR>]}";
            return String.format(format, hl7Bytes[validationLength - 2], (byte) MllpProtocolConstants.SEGMENT_DELIMITER);
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

    /**
     * Find the field separator indices in the Segment.
     *
     * NOTE:  The last element of the list will be the index of the end of the segment.
     *
     * @param hl7MessageBytes the HL7 binary message
     * @param startingIndex index of the beginning of the HL7 Segment
     *
     * @return List of the field separator indices, which may be empty.
     */
    public static List<Integer> findFieldSeparatorIndicesInSegment(byte[] hl7MessageBytes, int startingIndex) {
        List<Integer> fieldSeparatorIndices = new LinkedList<>();

        if (hl7MessageBytes != null && hl7MessageBytes.length > startingIndex && hl7MessageBytes.length > 3) {
            final byte fieldSeparator = hl7MessageBytes[3];

            for (int i = startingIndex; i < hl7MessageBytes.length; ++i) {
                if (fieldSeparator == hl7MessageBytes[i]) {
                    fieldSeparatorIndices.add(i);
                } else if (MllpProtocolConstants.SEGMENT_DELIMITER == hl7MessageBytes[i]) {
                    fieldSeparatorIndices.add(i);
                    break;
                }
            }
        }

        return fieldSeparatorIndices;
    }

    /**
     * Find the String value of MSH-18 (Character set).
     *
     * @param hl7Message the HL7 binary data to search
     *
     * @return the String value of MSH-18, or an empty String if not found.
     */
    public static String findMsh18(byte[] hl7Message) {
        String answer = "";

        if (hl7Message != null && hl7Message.length > 0) {

            List<Integer> fieldSeparatorIndexes = findFieldSeparatorIndicesInSegment(hl7Message, 0);

            if (fieldSeparatorIndexes.size() > 17) {
                int startOfMsh19 = fieldSeparatorIndexes.get(16) + 1;
                int length = fieldSeparatorIndexes.get(17) - fieldSeparatorIndexes.get(16) - 1;

                if (length > 0) {
                    answer = new String(hl7Message, startOfMsh19, length, MllpComponent.getDefaultCharset());
                }
            }
        }

        return answer;
    }


    public static void generateAcknowledgementPayload(MllpSocketBuffer mllpSocketBuffer, byte[] hl7MessageBytes, String acknowledgementCode)
            throws MllpAcknowledgementGenerationException {
        generateAcknowledgementPayload(mllpSocketBuffer, hl7MessageBytes, acknowledgementCode, null);
    }

    public static void generateAcknowledgementPayload(MllpSocketBuffer mllpSocketBuffer, byte[] hl7MessageBytes, String acknowledgementCode, String msa3)
            throws MllpAcknowledgementGenerationException {
        if (hl7MessageBytes == null) {
            throw new MllpAcknowledgementGenerationException("Null HL7 message received for parsing operation");
        }

        List<Integer> fieldSeparatorIndexes = findFieldSeparatorIndicesInSegment(hl7MessageBytes, 0);

        if (fieldSeparatorIndexes.isEmpty()) {
            throw new MllpAcknowledgementGenerationException("Failed to find the end of the MSH Segment while attempting to generate response", hl7MessageBytes);
        }

        if (fieldSeparatorIndexes.size() < 8) {
            String exceptionMessage = String.format("Insufficient number of fields found in MSH to generate a response - 10 are required but %d were found", fieldSeparatorIndexes.size() - 1);

            throw new MllpAcknowledgementGenerationException(exceptionMessage, hl7MessageBytes);
        }

        final byte fieldSeparator = hl7MessageBytes[3];

        // Start building the MLLP Envelope
        mllpSocketBuffer.openMllpEnvelope();

        // Build the MSH Segment
        mllpSocketBuffer.write(hl7MessageBytes, 0, fieldSeparatorIndexes.get(1)); // through MSH-2 (without trailing field separator)
        writeFieldToBuffer(3, mllpSocketBuffer, hl7MessageBytes, fieldSeparatorIndexes); // MSH-5
        writeFieldToBuffer(4, mllpSocketBuffer, hl7MessageBytes, fieldSeparatorIndexes); // MSH-6
        writeFieldToBuffer(1, mllpSocketBuffer, hl7MessageBytes, fieldSeparatorIndexes); // MSH-3
        writeFieldToBuffer(2, mllpSocketBuffer, hl7MessageBytes, fieldSeparatorIndexes); // MSH-4

        // MSH-7
        mllpSocketBuffer.write(fieldSeparator);
        mllpSocketBuffer.write(TIMESTAMP_FORMAT.format(new Date()).getBytes());

        // Don't copy MSH-8
        mllpSocketBuffer.write(fieldSeparator);

        // Need to generate the correct MSH-9
        mllpSocketBuffer.write(fieldSeparator);
        mllpSocketBuffer.write("ACK".getBytes()); // MSH-9.1
        int msh92start = -1;
        for (int j = fieldSeparatorIndexes.get(7) + 1; j < fieldSeparatorIndexes.get(8); ++j) {
            final byte componentSeparator = hl7MessageBytes[4];
            if (componentSeparator == hl7MessageBytes[j]) {
                msh92start = j;
                break;
            }
        }

        // MSH-9.2
        if (-1 == msh92start) {
            LOG.warn("Didn't find component separator for MSH-9.2 - sending ACK in MSH-9");
        } else {
            mllpSocketBuffer.write(hl7MessageBytes, msh92start, fieldSeparatorIndexes.get(8) - msh92start);
        }

        // MSH-10 - use the original control ID, but add an "A" as a suffix
        writeFieldToBuffer(8, mllpSocketBuffer, hl7MessageBytes, fieldSeparatorIndexes);
        if (fieldSeparatorIndexes.get(9) - fieldSeparatorIndexes.get(8) > 1) {
            mllpSocketBuffer.write('A');
        }

        // MSH-10 through the end of the MSH
        mllpSocketBuffer.write(hl7MessageBytes, fieldSeparatorIndexes.get(9), fieldSeparatorIndexes.get(fieldSeparatorIndexes.size() - 1) - fieldSeparatorIndexes.get(9));

        mllpSocketBuffer.write(MllpProtocolConstants.SEGMENT_DELIMITER);

        // Build the MSA Segment
        mllpSocketBuffer.write("MSA".getBytes(), 0, 3);
        mllpSocketBuffer.write(fieldSeparator);
        mllpSocketBuffer.write(acknowledgementCode.getBytes(), 0, 2);
        writeFieldToBuffer(8, mllpSocketBuffer, hl7MessageBytes, fieldSeparatorIndexes); // MSH-10 => MSA-2

        if (msa3 != null && !msa3.isEmpty()) {
            mllpSocketBuffer.write(fieldSeparator);
            mllpSocketBuffer.write(msa3.getBytes());
        }

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
        if (CHARACTER_REPLACEMENTS.containsKey(c)) {
            builder.append(CHARACTER_REPLACEMENTS.get(c));
        } else {
            builder.append(c);
        }
    }

    public static String getCharacterAsPrintFriendlyString(char c) {
        if (CHARACTER_REPLACEMENTS.containsKey(c)) {
            return CHARACTER_REPLACEMENTS.get(c);
        }

        return String.valueOf(c);
    }

    /**
     * Copy a field from the HL7 Message Bytes to the supplied MllpSocketBuffer.
     *
     * NOTE:  Internal function - no error checking
     *
     * @param mllpSocketBuffer the destination for the field
     * @param hl7MessageBytes the HL7 message bytes
     * @param fieldSeparatorIndexes the list of the indices of the field separators
     */
    private static void writeFieldToBuffer(int fieldNumber, MllpSocketBuffer mllpSocketBuffer, byte[] hl7MessageBytes, List<Integer> fieldSeparatorIndexes) {
        mllpSocketBuffer.write(hl7MessageBytes, fieldSeparatorIndexes.get(fieldNumber), fieldSeparatorIndexes.get(fieldNumber + 1) - fieldSeparatorIndexes.get(fieldNumber));
    }
}
