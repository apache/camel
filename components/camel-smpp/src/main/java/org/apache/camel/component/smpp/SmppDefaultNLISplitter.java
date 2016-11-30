package org.apache.camel.component.smpp;

/**
 * Created by engin on 23/11/2016.
 */

public class SmppDefaultNLISplitter extends SmppNLISplitter {

    public static final int MAX_MSG_CHAR_SIZE = (MAX_MSG_BYTE_LENGTH * 8 / 7) - 5; // 155 for Turkish
    public static final int MAX_SEG_BYTE_SIZE = (MAX_MSG_BYTE_LENGTH - UDHIE_HEADER_REAL_LENGTH) * 8 / 7;

    public SmppDefaultNLISplitter(int currentLength, byte languageIndentifier) {
        super(MAX_MSG_CHAR_SIZE, MAX_SEG_BYTE_SIZE, currentLength, languageIndentifier);
    }
}