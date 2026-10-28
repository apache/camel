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
package org.apache.camel.component.smpp;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SmppNLSTSplitterTest {

    @Test
    public void splitTurkishShortMessageWith155Character() {
        String message = "12345678901234567890123456789012345678901234567890123456789012345678901234567890"
                         + "123456789012345678901234567890123456789012345678901234567890123456789012345"; // 155 single message

        byte turkishLanguageIdentifier = 0x01;
        SmppSplitter splitter = new SmppNLSTSplitter(message.length(), turkishLanguageIdentifier);
        SmppSplitter.resetCurrentReferenceNumber();
        byte[][] result = splitter.split(message.getBytes());

        assertEquals(1, result.length);
        assertArrayEquals(
                new byte[] {
                        SmppNLSTSplitter.UDHIE_NLI_SINGLE_MSG_HEADER_LENGTH, SmppNLSTSplitter.UDHIE_NLI_IDENTIFIER,
                        SmppNLSTSplitter.UDHIE_NLI_HEADER_LENGTH, turkishLanguageIdentifier,
                        49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54,
                        55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48,
                        49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54,
                        55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53,
                        54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49,
                        50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48,
                        49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53 },
                result[0]);
    }

    @Test
    public void splitShortMessageWith156Character() {
        String message
                = "12345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789"
                  + // first part 149
                  "0123456"; // second part 7

        byte turkishLanguageIdentifier = 0x01;
        SmppSplitter splitter = new SmppNLSTSplitter(message.length(), turkishLanguageIdentifier);
        SmppSplitter.resetCurrentReferenceNumber();
        byte[][] result = splitter.split(message.getBytes());

        assertEquals(2, result.length);
        assertArrayEquals(new byte[] {
                SmppNLSTSplitter.UDHIE_NLI_MULTI_MSG_HEADER_LENGTH, SmppSplitter.UDHIE_IDENTIFIER_SAR,
                SmppSplitter.UDHIE_SAR_LENGTH, 1, 2, 1,
                SmppNLSTSplitter.UDHIE_NLI_IDENTIFIER, SmppNLSTSplitter.UDHIE_NLI_HEADER_LENGTH, turkishLanguageIdentifier,
                49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56,
                57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48,
                49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56,
                57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53,
                54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51,
                52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48,
                49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57 }, result[0]);

        assertArrayEquals(new byte[] {
                SmppNLSTSplitter.UDHIE_NLI_MULTI_MSG_HEADER_LENGTH, SmppSplitter.UDHIE_IDENTIFIER_SAR,
                SmppSplitter.UDHIE_SAR_LENGTH, 1, 2, 2,
                SmppNLSTSplitter.UDHIE_NLI_IDENTIFIER, SmppNLSTSplitter.UDHIE_NLI_HEADER_LENGTH, turkishLanguageIdentifier,
                48, 49, 50, 51, 52, 53, 54 }, result[1]);

        String firstShortMessage = new String(
                result[0], SmppNLSTSplitter.UDHIE_NLI_MULTI_MSG_HEADER_REAL_LENGTH,
                result[0].length - SmppNLSTSplitter.UDHIE_NLI_MULTI_MSG_HEADER_REAL_LENGTH);
        String secondShortMessage = new String(
                result[1], SmppNLSTSplitter.UDHIE_NLI_MULTI_MSG_HEADER_REAL_LENGTH,
                result[1].length - SmppNLSTSplitter.UDHIE_NLI_MULTI_MSG_HEADER_REAL_LENGTH);
        assertEquals(message, firstShortMessage + secondShortMessage);
    }

    @Test
    public void splitIntoAtMost255Segments() {
        // one byte more than 255 full segments
        byte[] message = new byte[255 * SmppNLSTSplitter.MAX_SEG_BYTE_SIZE + 1];
        Arrays.fill(message, (byte) '1');

        SmppSplitter splitter = new SmppNLSTSplitter(message.length, (byte) 0x01);
        byte[][] result = splitter.split(message);

        assertEquals(255, result.length, "a long message is truncated to 255 segments");
        for (int i = 0; i < result.length; i++) {
            assertEquals((byte) 255, result[i][4], "total number of segments in the UDH of segment " + (i + 1));
            assertEquals((byte) (i + 1), result[i][5], "number of segment " + (i + 1) + " in its UDH");
            assertEquals(SmppNLSTSplitter.UDHIE_NLI_MULTI_MSG_HEADER_REAL_LENGTH + SmppNLSTSplitter.MAX_SEG_BYTE_SIZE,
                    result[i].length, "length of segment " + (i + 1));
        }
    }
}
