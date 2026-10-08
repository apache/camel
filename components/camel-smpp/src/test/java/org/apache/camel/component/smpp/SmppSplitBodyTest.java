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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.jsmpp.bean.Alphabet;
import org.jsmpp.session.SMPPSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * An 8-bit short message holds 140 octets: the body must be split by its length in bytes, into at most 255 segments.
 */
class SmppSplitBodyTest {

    private SmppSubmitSmCommand command;

    @BeforeEach
    void setUp() {
        command = new SmppSubmitSmCommand(mock(SMPPSession.class), new SmppConfiguration());
    }

    @Test
    void eightBitBinaryBodyOfMoreThan140BytesIsSplit() throws Exception {
        // 150 bytes, which happen to be 50 valid UTF-8 sequences (50 characters when read as a String)
        byte[] body = new byte[150];
        for (int i = 0; i < body.length; i += 3) {
            body[i] = (byte) 0xE2;
            body[i + 1] = (byte) 0x82;
            body[i + 2] = (byte) 0xAC;
        }

        byte[][] segments = splitEightBit(body);

        assertEquals(2, segments.length, "a body of 150 bytes needs two segments");
        assertSegments(body, segments);
    }

    @Test
    void eightBitTextBodyOfMoreThan140BytesIsSplit() throws Exception {
        // 100 characters, 200 bytes in UTF-8 (the charset of the exchange)
        String text = "é".repeat(100);

        byte[][] segments = splitEightBit(text);

        assertEquals(2, segments.length, "a body of 200 bytes needs two segments");
        assertSegments(text.getBytes(StandardCharsets.UTF_8), segments);
    }

    @Test
    void eightBitBodyIsNotSplitIntoMoreThan255Segments() throws Exception {
        // one byte more than 255 full segments
        byte[] body = new byte[255 * Smpp8BitSplitter.MAX_SEG_BYTE_SIZE + 1];
        Arrays.fill(body, (byte) 'a');

        byte[][] segments = splitEightBit(body);

        assertEquals(255, segments.length, "a long message is truncated to 255 segments");
        for (int i = 0; i < segments.length; i++) {
            assertEquals((byte) 255, segments[i][4], "total number of segments in the UDH of segment " + (i + 1));
            assertEquals((byte) (i + 1), segments[i][5], "number of segment " + (i + 1) + " in its UDH");
        }
    }

    @Test
    void eightBitBodyOf140BytesIsNotSplit() throws Exception {
        byte[] body = new byte[140];
        Arrays.fill(body, (byte) 'a');

        byte[][] segments = splitEightBit(body);

        assertEquals(1, segments.length);
        assertArrayEquals(body, segments[0]);
    }

    private byte[][] splitEightBit(Object body) throws Exception {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext(), ExchangePattern.InOut);
        // the charset that a String body is converted with
        exchange.setProperty(Exchange.CHARSET_NAME, StandardCharsets.UTF_8.name());
        exchange.getIn().setHeader(SmppConstants.ALPHABET, Alphabet.ALPHA_8_BIT.value());
        exchange.getIn().setBody(body);
        return command.splitBody(exchange.getIn());
    }

    private static void assertSegments(byte[] body, byte[][] segments) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (byte[] segment : segments) {
            assertTrue(segment.length <= 140, "a segment holds at most 140 bytes, was " + segment.length);
            data.write(segment, SmppSplitter.UDHIE_HEADER_REAL_LENGTH, segment.length - SmppSplitter.UDHIE_HEADER_REAL_LENGTH);
        }
        assertArrayEquals(body, data.toByteArray());
    }
}
