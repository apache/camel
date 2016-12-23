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
package org.apache.camel.component.smpp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Splitter for messages use National Language Lock Table
 * <p/>
 * @see 3GPP 23.038 Reference
 */
public class SmppNLSTSplitter extends SmppSplitter {
    /**
     * The length of the UDH for single short message in bytes.
     * 0x25 - UDHIE_NLI_IDENTIFIER
     * 0x01 - Length of the header
     * 0xXX - Locking shift table indicator the Language
     */
    protected static final int UDHIE_NLI_SINGLE_MSG_HEADER_LENGTH = 0x03; // header length for single message
    /**
     * The real length of the UDH for single short message
     */
    protected static final int UDHIE_NLI_SINGLE_MSG_HEADER_REAL_LENGTH = UDHIE_NLI_SINGLE_MSG_HEADER_LENGTH + 1;

    /**
     * The length of the UDH for splitted short messages, in bytes.
     * 0x08 Overall header length
     * 0x00 The value that identifier length of the SAR fragment. (8bit reference number only)
     * 0x03 The length of the SAR fragment
     * 0xXX The reference number for SAR
     * 0xXX Total number of splitted / segmented messages
     * 0xXX Segment number
     * 0x25 National language locking shift element identifier
     * 0x01 Length of the header
     * 0xXX Locking shift table indicator for the Language (ie: 0x01 for Turkish)
     */
    protected static final int UDHIE_NLI_MULTI_MSG_HEADER_LENGTH = 0x08;

    /**
     * The real length of the UDH for segmentet short messages
     */
    protected static final int UDHIE_NLI_MULTI_MSG_HEADER_REAL_LENGTH = UDHIE_NLI_MULTI_MSG_HEADER_LENGTH + 1;

    /**
     * The element identifier value for the National Language Locking Table
     */
    protected static final int UDHIE_NLI_IDENTIFIER = 0x25;
    /**
     * The length of the NLI header
     */
    protected static final int UDHIE_NLI_HEADER_LENGTH = 0x01;

    /**
     * The maximum length in chars of the NLI messages.
     * <p/>
     * Each letter will be represented as 7 bit (like GSM8)
     */
    public static final int MAX_MSG_CHAR_SIZE = (MAX_MSG_BYTE_LENGTH * 8 / 7) - (UDHIE_NLI_SINGLE_MSG_HEADER_REAL_LENGTH + 1);

    public static final int MAX_SEG_BYTE_SIZE = (MAX_MSG_BYTE_LENGTH - UDHIE_NLI_MULTI_MSG_HEADER_REAL_LENGTH) * 8 / 7;

    /**
     * Locking shift table indicator for the Language, single byte
     */
    private byte languageIdentifier;
    private final Logger logger = LoggerFactory.getLogger(SmppNLSTSplitter.class);

    public SmppNLSTSplitter(int currentLength, byte languageIdentifier) {
        super(MAX_MSG_CHAR_SIZE, MAX_SEG_BYTE_SIZE, currentLength);
        this.languageIdentifier = languageIdentifier;
    }

    public byte[][] split(byte[] message) {
        if (!isSplitRequired()) {
            byte[] nliMessage = new byte[UDHIE_NLI_SINGLE_MSG_HEADER_REAL_LENGTH + message.length];
            nliMessage[0] = (byte) UDHIE_NLI_SINGLE_MSG_HEADER_LENGTH;
            nliMessage[1] = (byte) UDHIE_NLI_IDENTIFIER;
            nliMessage[2] = (byte) UDHIE_NLI_HEADER_LENGTH;
            nliMessage[3] = this.languageIdentifier;
            System.arraycopy(message, 0, nliMessage, 4, message.length);
            return new byte[][]{nliMessage};
        }

        int segmentLength = getSegmentLength();

        // determine how many messages
        int segmentNum = message.length / segmentLength;
        int messageLength = message.length;
        if (segmentNum > MAX_SEG_COUNT) {
            // this is too long, can't fit, so chop
            segmentNum = MAX_SEG_COUNT;
            messageLength = segmentNum * segmentLength;
        }
        if ((messageLength % segmentLength) > 0) {
            segmentNum++;
        }

        byte[][] segments = new byte[segmentNum][];

        int lengthOfData;
        byte refNum = getReferenceNumber();
        for (int i = 0; i < segmentNum; i++) {
            logger.debug("segment number = {}", i);
            if (segmentNum - i == 1) {
                lengthOfData = messageLength - i * segmentLength;
            } else {
                lengthOfData = segmentLength;
            }
            logger.debug("Length of data = {}", lengthOfData);

            segments[i] = new byte[UDHIE_NLI_MULTI_MSG_HEADER_REAL_LENGTH + lengthOfData];
            logger.debug("segments[{}].length = {}", i, segments[i].length);

            segments[i][0] = UDHIE_NLI_MULTI_MSG_HEADER_LENGTH; // doesn't include itself, is header length
            // SAR identifier
            segments[i][1] = UDHIE_IDENTIFIER_SAR;
            // SAR length
            segments[i][2] = UDHIE_SAR_LENGTH;
            // DATAGRAM REFERENCE NUMBER
            segments[i][3] = refNum;
            // total number of segments
            segments[i][4] = (byte) segmentNum;
            // segment #
            segments[i][5] = (byte) (i + 1);

            // national language locking shift table, element identifier
            segments[i][6] = (byte) UDHIE_NLI_IDENTIFIER;
            segments[i][7] = (byte) UDHIE_NLI_HEADER_LENGTH;
            segments[i][8] = this.languageIdentifier;

            // now copy the data
            System.arraycopy(message, i * segmentLength, segments[i], UDHIE_NLI_MULTI_MSG_HEADER_REAL_LENGTH, lengthOfData);
        }
        return segments;
    }
}