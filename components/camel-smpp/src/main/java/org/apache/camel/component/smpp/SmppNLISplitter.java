package org.apache.camel.component.smpp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by engin on 22/11/2016.
 */


public class SmppNLISplitter extends SmppSplitter {

    /**
     * The length of the UDH in bytes.
     * <p/>
     * The real length of the header must be 6 bytes, but the first byte that
     * contains the length of the header must not be counted.
     */
    protected static final int UDHIE_HEADER_LENGTH = 0x08;
    /**
     * The real length of the UDH header.
     * <p/>
     * The real length of the UDH header is {@link #UDHIE_HEADER_LENGTH}
     * {@code + 1}.
     *
     * @see #UDHIE_HEADER_LENGTH
     */
    protected static final int UDHIE_HEADER_REAL_LENGTH = UDHIE_HEADER_LENGTH + 1;

    /**
     * The length of the reference number of the SAR fragmet of the UDH header.
     * <p/>
     * The length can be 1 or 2 bytes and is considered to be 1 byte.
     */
    protected static final int UDHIE_SAR_REF_NUM_LENGTH = 1;

    /**
     * The value that identifier length of the SAR fragment.
     * <p/>
     * {@code 0x00} value must be used if the legnth of the reference number is
     * 1 byte.<br/>
     * {@code 0x08} value must be used if the legnth of the reference number is
     * 2 bytes.
     */
    protected static final byte UDHIE_IDENTIFIER_SAR = 0x00;

    /**
     * The length of the SAR fragment.
     * <p/>
     * {@code 0x03} value must be used if the legnth of the reference number is
     * 1 byte.<br/>
     * {@code 0x04} value must be used if the legnth of the reference number is
     * 2 bytes.
     */
    protected static final byte UDHIE_SAR_LENGTH = 0x03;

    /**
     * The maximum length of the message in bytes.
     */
    protected static final int MAX_MSG_BYTE_LENGTH = 140;

    /**
     * The maximum amount of segments in the multipart message.
     */
    protected static final int MAX_SEG_COUNT = 255;

    private static final Logger LOG = LoggerFactory.getLogger(SmppNLISplitter.class);



    private byte languageIdentifier;

    SmppNLISplitter(int messageLength, int segmentLength, int currentLength, byte languageIdentifier) {

        super(messageLength, segmentLength, currentLength);

        this.languageIdentifier = languageIdentifier;
    }



    public byte[][] split(byte[] message) {
        if (!isSplitRequired()) {
            if (message.length > 0){

                byte[] nli_message = new byte[4 + message.length];
                nli_message[0] = (byte) 0x03;
                nli_message[1] = (byte) 0x25;
                nli_message[2] = (byte) 0x01;
                nli_message[3] = this.languageIdentifier;
                System.arraycopy(message, 0, nli_message, 4, message.length);
                return new byte[][]{nli_message};

            }else {
                return new byte[][]{message};
            }
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
            LOG.trace("segment number = {}", i);
            if (segmentNum - i == 1) {
                lengthOfData = messageLength - i * segmentLength;
            } else {
                lengthOfData = segmentLength;
            }
            LOG.trace("Length of data = {}", lengthOfData);

            segments[i] = new byte[UDHIE_HEADER_REAL_LENGTH + lengthOfData];
            LOG.trace("segments[{}].length = {}", i, segments[i].length);

            segments[i][0] = UDHIE_HEADER_LENGTH; // doesn't include itself, is header length
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
            segments[i][6] = (byte) 0x25;
            segments[i][7] = (byte) 0x01;
            segments[i][8] = this.languageIdentifier;


            // now copy the data
            System.arraycopy(message, i * segmentLength, segments[i], UDHIE_HEADER_REAL_LENGTH, lengthOfData);
        }

        return segments;
    }

    protected boolean isSplitRequired() {
        return getCurrentLength() > getMessageLength();
    }

    /**
     * Gets maximum message length.
     *
     * @return maximum message length
     */
    public int getMessageLength() {
        return super.getMessageLength();
    }

    /**
     * Gets maximum segment length.
     *
     * @return maximum segment length
     */
    public int getSegmentLength() {
        return super.getSegmentLength();
    }

    /**
     * Gets length of the message to split.
     *
     * @return length of the message to split
     */
    public int getCurrentLength() {
        return super.getCurrentLength();
    }
}