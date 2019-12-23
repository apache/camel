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

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class Smpp8BitSplitterTest {

    @Test
    public void splitShortMessageWith160Character() {
        String message = "12345678901234567890123456789012345678901234567890123456789012345678901234567890"
                + "123456789012345678901234567890123456789012345678901234567890";

        Smpp8BitSplitter splitter = new Smpp8BitSplitter(message.length());
        SmppSplitter.resetCurrentReferenceNumber();
        byte[][] result = splitter.split(message.getBytes());
        
        assertEquals(1, result.length);
        assertArrayEquals(new byte[]{49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48,
            49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53,
            54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48,
            49, 50, 51, 52, 53, 54, 55, 56, 57, 48}, result[0]);

        assertEquals(message, new String(result[0]));
    }

    @Test
    public void splitShortMessageWith161Character() {
        String message = "12345678901234567890123456789012345678901234567890123456789012345678901234567890"
                + "12345678901234567890123456789012345678901234567890123456789012345678901";

        Smpp8BitSplitter splitter = new Smpp8BitSplitter(message.length());
        SmppSplitter.resetCurrentReferenceNumber();
        byte[][] result = splitter.split(message.getBytes());
        
        assertEquals(2, result.length);
        assertArrayEquals(new byte[]{SmppSplitter.UDHIE_HEADER_LENGTH, SmppSplitter.UDHIE_IDENTIFIER_SAR, SmppSplitter.UDHIE_SAR_LENGTH, 1, 2, 1, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51,
            52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56,
            57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51,
            52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52}, result[0]);
        assertArrayEquals(new byte[]{SmppSplitter.UDHIE_HEADER_LENGTH, SmppSplitter.UDHIE_IDENTIFIER_SAR, SmppSplitter.UDHIE_SAR_LENGTH, 1, 2, 2, 53, 54, 55, 56, 57, 48, 49, 50, 51, 52, 53, 54,
            55, 56, 57, 48, 49}, result[1]);

        String firstShortMessage = new String(result[0], SmppSplitter.UDHIE_HEADER_REAL_LENGTH, result[0].length - SmppSplitter.UDHIE_HEADER_REAL_LENGTH);
        String secondShortMessage = new String(result[1], SmppSplitter.UDHIE_HEADER_REAL_LENGTH, result[1].length - SmppSplitter.UDHIE_HEADER_REAL_LENGTH);

        assertEquals(message, firstShortMessage + secondShortMessage);
    }
}
