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

import java.nio.charset.Charset;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class SmppUcs2SplitterTest {

    @Test
    public void splitShortMessageWith70Character() {
        String message = "1234567890123456789012345678901234567890123456789012345678901234567890";
        Charset charset = Charset.forName("UTF-16BE");
        
        SmppUcs2Splitter splitter = new SmppUcs2Splitter(message.length());
        SmppSplitter.resetCurrentReferenceNumber();
        byte[][] result = splitter.split(message.getBytes(charset));
        
        assertEquals(1, result.length);
        assertArrayEquals(new byte[]{0, 49, 0, 50, 0, 51, 0, 52, 0, 53, 0, 54, 0, 55, 0, 56, 0, 57, 0, 48, 0, 49, 0, 50, 0, 51, 0, 52, 0, 53, 0, 54, 0, 55, 0, 56, 0, 57, 0, 48, 0, 49, 0, 50, 0, 51,
            0, 52, 0, 53, 0, 54, 0, 55, 0, 56, 0, 57, 0, 48, 0, 49, 0, 50, 0, 51, 0, 52, 0, 53, 0, 54, 0, 55, 0, 56, 0, 57, 0, 48, 0, 49, 0, 50, 0, 51, 0, 52, 0, 53, 0, 54, 0, 55, 0, 56, 0, 57,
            0, 48, 0, 49, 0, 50, 0, 51, 0, 52, 0, 53, 0, 54, 0, 55, 0, 56, 0, 57, 0, 48, 0, 49, 0, 50, 0, 51, 0, 52, 0, 53, 0, 54, 0, 55, 0, 56, 0, 57, 0, 48}, result[0]);

        assertEquals(message, new String(result[0], charset));
    }

    @Test
    public void splitShortMessageWith71Character() {
        String message = "12345678901234567890123456789012345678901234567890123456789012345678901";
        Charset charset = Charset.forName("UTF-16BE");
        
        SmppUcs2Splitter splitter = new SmppUcs2Splitter(message.length());
        SmppSplitter.resetCurrentReferenceNumber();
        byte[][] result = splitter.split(message.getBytes(charset));
        
        assertEquals(2, result.length);
        assertArrayEquals(new byte[]{SmppSplitter.UDHIE_HEADER_LENGTH, SmppSplitter.UDHIE_IDENTIFIER_SAR, SmppSplitter.UDHIE_SAR_LENGTH, 1, 2, 1, 0, 49, 0, 50, 0, 51, 0, 52, 0, 53, 0, 54, 0, 55, 0,
            56, 0, 57, 0, 48, 0, 49, 0, 50, 0, 51, 0, 52, 0, 53, 0, 54, 0, 55, 0, 56, 0, 57, 0, 48, 0, 49, 0, 50, 0, 51, 0, 52, 0, 53, 0, 54, 0, 55, 0, 56, 0, 57, 0, 48, 0, 49, 0, 50, 0, 51, 0,
            52, 0, 53, 0, 54, 0, 55, 0, 56, 0, 57, 0, 48, 0, 49, 0, 50, 0, 51, 0, 52, 0, 53, 0, 54, 0, 55, 0, 56, 0, 57, 0, 48, 0, 49, 0, 50, 0, 51, 0, 52, 0, 53, 0, 54, 0, 55, 0, 56, 0, 57, 0,
            48, 0, 49, 0, 50, 0, 51, 0, 52, 0, 53, 0, 54, 0, 55}, result[0]);
        assertArrayEquals(new byte[]{SmppSplitter.UDHIE_HEADER_LENGTH, SmppSplitter.UDHIE_IDENTIFIER_SAR, SmppSplitter.UDHIE_SAR_LENGTH, 1, 2, 2, 0, 56, 0, 57, 0, 48, 0, 49}, result[1]);

        String firstShortMessage = new String(result[0], SmppSplitter.UDHIE_HEADER_REAL_LENGTH, result[0].length - SmppSplitter.UDHIE_HEADER_REAL_LENGTH, charset);
        String secondShortMessage = new String(result[1], SmppSplitter.UDHIE_HEADER_REAL_LENGTH, result[1].length - SmppSplitter.UDHIE_HEADER_REAL_LENGTH, charset);

        assertEquals(message, firstShortMessage + secondShortMessage);
    }
}
