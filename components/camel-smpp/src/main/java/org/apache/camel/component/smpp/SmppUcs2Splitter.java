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

public class SmppUcs2Splitter extends SmppSplitter {

    /**
     * The maximum length in chars of the unicode messages.
     * <p/>
     * Each letter requires 2 bytes.
     */
    public static final int MAX_MSG_CHAR_SIZE = MAX_MSG_BYTE_LENGTH / 2;

    // ( / 2 * 2) is required because UDHIE_HEADER_REAL_LENGTH might be equal to 0x07 so the length of the segment
    // is 133 = (70 * 2 - 7)and the last letter in the unicode will be damaged.
    public static final int MAX_SEG_BYTE_SIZE = (MAX_MSG_CHAR_SIZE * 2 - UDHIE_HEADER_REAL_LENGTH) / 2 * 2;

    public SmppUcs2Splitter(int segmentLength) {
        super(MAX_MSG_CHAR_SIZE, MAX_SEG_BYTE_SIZE, segmentLength);
    }
}