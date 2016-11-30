package org.apache.camel.component.smpp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by engin on 30/11/2016.
 */
class SmppNLSTSplitter extends SmppSplitter{

    protected static final int UDHIE_NLI_SINGLE_MSG_HEADER_LENGTH = 0x03; // header length for single message
    protected static final int UDHIE_NLI_SINGLE_MSG_HEADER_REAL_LENGTH = UDHIE_NLI_SINGLE_MSG_HEADER_LENGTH + 1;

    protected static final int UDHIE_NLI_MULTI_MSG_HEADER_LENGTH = 0x08; // header length for multi message
    protected static final int UDHIE_NLI_MULTI_MSG_HEADER_REAL_LENGTH = UDHIE_NLI_MULTI_MSG_HEADER_LENGTH + 1;


    protected static final int UDHIE_SAR_REF_NUM_LENGTH = 1;
//        protected static final byte UDHIE_IDENTIFIER_SAR = 0x00;
//        protected static final byte UDHIE_SAR_LENGTH = 0x03;
//        protected static final int MAX_SEG_COUNT = 255;

    protected static final int UDHIE_NLI_IDENTIFIER = 0x25;
    protected static final int UDHIE_NLI_HEADER_LENGTH = 0x01;

    public static final int MAX_MSG_CHAR_SIZE = (MAX_MSG_BYTE_LENGTH * 8 / 7) - (UDHIE_NLI_SINGLE_MSG_HEADER_REAL_LENGTH + 1); // 155 for NLST
    public static final int MAX_SEG_BYTE_SIZE = (MAX_MSG_BYTE_LENGTH - UDHIE_NLI_MULTI_MSG_HEADER_REAL_LENGTH) * 8 / 7;

    private byte languageIdentifier;
    private final Logger logger = LoggerFactory.getLogger(SmppNLSTSplitter.class);

    SmppNLSTSplitter(int currentLength, byte languageIdentifier) {
        super(MAX_MSG_CHAR_SIZE, MAX_SEG_BYTE_SIZE, currentLength);
        this.languageIdentifier = languageIdentifier;
    }

    public byte[][] split(byte[] message) {
        if (!isSplitRequired()) {
            byte[] nli_message = new byte[UDHIE_NLI_SINGLE_MSG_HEADER_REAL_LENGTH + message.length];
            nli_message[0] = (byte) UDHIE_NLI_SINGLE_MSG_HEADER_LENGTH;
            nli_message[1] = (byte) UDHIE_NLI_IDENTIFIER;
            nli_message[2] = (byte) UDHIE_NLI_HEADER_LENGTH;
            nli_message[3] = this.languageIdentifier;
            System.arraycopy(message, 0, nli_message, 4, message.length);
            return new byte[][]{nli_message};
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
            logger.info("segment number = {}", i);
            if (segmentNum - i == 1) {
                lengthOfData = messageLength - i * segmentLength;
            } else {
                lengthOfData = segmentLength;
            }
            logger.info("Length of data = {}", lengthOfData);

            segments[i] = new byte[UDHIE_NLI_MULTI_MSG_HEADER_REAL_LENGTH + lengthOfData];
            logger.info("segments[{}].length = {}", i, segments[i].length);

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

            // language stuff
            segments[i][6] = (byte) UDHIE_NLI_IDENTIFIER;
            segments[i][7] = (byte) UDHIE_NLI_HEADER_LENGTH;
            segments[i][8] = this.languageIdentifier;

            // now copy the data
            System.arraycopy(message, i * segmentLength, segments[i], UDHIE_NLI_MULTI_MSG_HEADER_REAL_LENGTH, lengthOfData);
        }
        return segments;
    }
}