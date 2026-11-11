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
package org.apache.camel.component.hl7;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.ProtocolCodecSession;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the maxFrameLength option of the HL7 MLLP MINA decoder. The decoder keeps the data that has no complete message
 * yet between two calls, on a TCP session and also on a session without fragmentation, such as the test session.
 */
class HL7MLLPDecoderMaxFrameLengthTest {

    private static final String START = "\u000b";
    private static final String END = "\u001c\r";

    private final ProtocolCodecSession session = new ProtocolCodecSession();

    @Test
    void messageAtTheLimitIsDecoded() throws Exception {
        // the start byte and 49 bytes of message: 50 bytes before the end bytes
        String message = "A".repeat(49);
        ProtocolDecoder decoder = decoder(50);

        decode(decoder, START + message + END);

        assertEquals(message, session.getDecoderOutputQueue().poll());
        assertNull(session.getDecoderOutputQueue().poll());
    }

    @Test
    void messageAtTheLimitIsDecodedWhenItsEndBytesArriveApart() throws Exception {
        // unlike the Netty decoder, an end byte 1 that arrives without the end byte 2 is not counted yet
        String message = "A".repeat(49);
        ProtocolDecoder decoder = decoder(50);

        decode(decoder, START + message + "\u001c");
        decode(decoder, "\r");

        assertEquals(message, session.getDecoderOutputQueue().poll());
    }

    @Test
    void messageOverTheLimitFails() throws Exception {
        ProtocolDecoder decoder = decoder(50);

        ProtocolDecoderException e = assertThrows(ProtocolDecoderException.class,
                () -> decode(decoder, START + "A".repeat(50) + END));

        assertTrue(e.getMessage().startsWith("HL7 MLLP message is longer than maxFrameLength of 50 bytes"),
                e.getMessage());
        // MINA adds a hex dump of the received data (the message) to a decoder exception that has none, and the
        // consumer logs the exception: this one has an empty dump
        assertEquals("", e.getHexdump());
        assertFalse(e.getMessage().contains("Hexdump"), e.getMessage());
        assertNull(session.getDecoderOutputQueue().poll());

        // its end bytes came with it: the next message is decoded
        decode(decoder, START + "next" + END);
        assertEquals("next", session.getDecoderOutputQueue().poll());
    }

    @Test
    void messageOverTheLimitOverTwoReadsFailsAndTheNextMessageIsDecoded() throws Exception {
        ProtocolDecoder decoder = decoder(50);

        // the first part is kept for the next read; the second part goes over the limit and has the end bytes
        decode(decoder, START + "B".repeat(30));
        assertThrows(ProtocolDecoderException.class, () -> decode(decoder, "B".repeat(30) + END));
        assertNull(session.getDecoderOutputQueue().poll());

        // nothing of the long message is read again: the next message is decoded
        decode(decoder, START + "next" + END);
        assertEquals("next", session.getDecoderOutputQueue().poll());
        assertNull(session.getDecoderOutputQueue().poll());
    }

    @Test
    void dataWithoutEndBytesFailsOnceOverTheLimitAndTheNextMessageIsDecoded() throws Exception {
        ProtocolDecoder decoder = decoder(50);

        // a peer that sends more than the limit without the end bytes: the decoder does not wait for them, in one read
        // or over several reads
        decode(decoder, START + "B".repeat(30));
        assertThrows(ProtocolDecoderException.class, () -> decode(decoder, "B".repeat(30)));
        assertNull(session.getDecoderOutputQueue().poll());

        // the rest of the long message is discarded up to its end bytes (over several reads too), and the next message
        // is decoded
        decode(decoder, "B".repeat(100));
        decode(decoder, "B".repeat(20) + "\u001c");
        decode(decoder, "\r" + START + "next" + END);
        assertEquals("next", session.getDecoderOutputQueue().poll());
        assertNull(session.getDecoderOutputQueue().poll());
    }

    @Test
    void dataBeforeTheStartByteCounts() throws Exception {
        // the limit counts from the start of the data, as the Netty decoder does: bytes before the start byte count too
        ProtocolDecoder decoder = decoder(50);

        assertThrows(ProtocolDecoderException.class, () -> decode(decoder, "x".repeat(45) + START + "A".repeat(10) + END));
        decode(decoder, START + "next" + END);
        assertEquals("next", session.getDecoderOutputQueue().poll());
    }

    @Test
    void eachMessageOfOneReadHasTheLimit() throws Exception {
        // the limit counts from the end bytes of the previous message, also when several messages come in one read
        ProtocolDecoder decoder = decoder(50);
        String message = "A".repeat(49);

        decode(decoder, START + message + END + START + message + END + START + message.substring(10));
        decode(decoder, "A".repeat(10) + END);

        assertEquals(message, session.getDecoderOutputQueue().poll());
        assertEquals(message, session.getDecoderOutputQueue().poll());
        assertEquals(message, session.getDecoderOutputQueue().poll());
        assertNull(session.getDecoderOutputQueue().poll());
    }

    @Test
    void theDataOfDecodedMessagesIsNotKept() throws Exception {
        // reads that do not end with a message end: only the data of the current message is kept for the next read
        ProtocolDecoder decoder = decoder(50);
        for (int i = 0; i < 1000; i++) {
            decode(decoder, "A" + END + START + "message-" + i);
            decode(decoder, END + START + "next-" + i);
        }
        assertTrue(sessionBufferSize() <= 50, "the session buffer has " + sessionBufferSize() + " bytes");
        // each read ends the message of the previous one (the first "A" has no start byte), the last one stays open
        assertEquals(1999, session.getDecoderOutputQueue().size());
        assertEquals("message-0", session.getDecoderOutputQueue().poll());
        assertEquals("next-0A", session.getDecoderOutputQueue().poll());
    }

    @Test
    void endBytesWithoutAStartAreNotKept() throws Exception {
        // end bytes without a start byte are ignored (with a WARN), and the data before them is not kept
        ProtocolDecoder decoder = decoder(50);
        for (int i = 0; i < 100; i++) {
            decode(decoder, "x" + END + "x");
        }
        assertTrue(sessionBufferSize() <= 50, "the session buffer has " + sessionBufferSize() + " bytes");
        decode(decoder, END + START + "next" + END);
        assertEquals("next", session.getDecoderOutputQueue().poll());
    }

    @Test
    // without the data kept, MINA's codec filter loops on the read for good: fail instead
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void aTransportWithoutFragmentationKeepsTheDataOfAnIncompleteMessage() throws Exception {
        // the test session reports no fragmentation, as a mina:udp or mina:vm session: through the codec filter of
        // MINA, a read with a message and the start of the next one gives each message once
        ProtocolCodecFilter filter = new ProtocolCodecFilter(new HL7MLLPCodec());
        List<Object> received = new ArrayList<>();
        IoFilter.NextFilter next = (IoFilter.NextFilter) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { IoFilter.NextFilter.class }, (proxy, method, args) -> {
                    if ("messageReceived".equals(method.getName()) || "exceptionCaught".equals(method.getName())) {
                        received.add(args[1]);
                        if (received.size() > 2) {
                            // the same message again: stop the loop of the filter instead of filling the memory
                            throw new IllegalStateException("received " + received);
                        }
                    }
                    return null;
                });

        filter.messageReceived(next, session,
                IoBuffer.wrap((START + "A" + END + START + "B").getBytes(StandardCharsets.ISO_8859_1)));
        filter.messageReceived(next, session, IoBuffer.wrap(END.getBytes(StandardCharsets.ISO_8859_1)));

        assertEquals(List.of("A", "B"), received);
    }

    @Test
    void startAndEndBytesAbove0x7F() throws Exception {
        // the decoder compares the bytes it reads with the configured start and end bytes unsigned
        HL7MLLPConfig config = new HL7MLLPConfig();
        config.setCharset(StandardCharsets.ISO_8859_1);
        config.setStartByte((char) 0x8b);
        config.setEndByte1((char) 0x9c);
        config.setEndByte2((char) 0x8d);
        config.setMaxFrameLength(50);
        ProtocolDecoder decoder = new HL7MLLPDecoder(config);
        String start = "\u008b";
        String end = "\u009c\u008d";

        decode(decoder, start + "A".repeat(49) + end);
        assertEquals("A".repeat(49), session.getDecoderOutputQueue().poll());

        // an end byte 1 that arrives without the end byte 2 is not counted yet
        decode(decoder, start + "B".repeat(49) + "\u009c");
        decode(decoder, "\u008d");
        assertEquals("B".repeat(49), session.getDecoderOutputQueue().poll());

        // a message over the limit is discarded up to its end bytes, and the next message is decoded
        assertThrows(ProtocolDecoderException.class, () -> decode(decoder, start + "C".repeat(60)));
        decode(decoder, "C".repeat(10) + end + start + "next" + end);
        assertEquals("next", session.getDecoderOutputQueue().poll());
        assertNull(session.getDecoderOutputQueue().poll());
    }

    @Test
    void codecAppliesTheLimitToItsDecoders() throws Exception {
        HL7MLLPCodec codec = new HL7MLLPCodec();
        codec.setCharset(StandardCharsets.ISO_8859_1);
        codec.setMaxFrameLength(50);
        assertEquals(50, codec.getMaxFrameLength());
        ProtocolDecoder decoder = codec.getDecoder(session);

        decode(decoder, START + "A".repeat(49) + END);
        assertEquals("A".repeat(49), session.getDecoderOutputQueue().poll());

        assertThrows(ProtocolDecoderException.class, () -> decode(decoder, START + "A".repeat(50) + END));
        assertNull(session.getDecoderOutputQueue().poll());
    }

    @Test
    void defaultIsNoLimit() throws Exception {
        assertEquals(Integer.MAX_VALUE, new HL7MLLPCodec().getMaxFrameLength());

        String message = "C".repeat(1024 * 1024);
        HL7MLLPCodec codec = new HL7MLLPCodec();
        codec.setCharset(StandardCharsets.ISO_8859_1);
        ProtocolDecoder decoder = codec.getDecoder(session);
        decode(decoder, START + message.substring(1000));
        decode(decoder, message.substring(0, 1000) + END);

        assertEquals(message, session.getDecoderOutputQueue().poll());
    }

    @Test
    void maxFrameLengthMustBePositive() {
        HL7MLLPCodec codec = new HL7MLLPCodec();
        assertThrows(IllegalArgumentException.class, () -> codec.setMaxFrameLength(0));
        assertThrows(IllegalArgumentException.class, () -> codec.setMaxFrameLength(-1));
        assertEquals(Integer.MAX_VALUE, codec.getMaxFrameLength());
    }

    /**
     * The bytes that MINA keeps for the session between two reads (0 when it keeps none).
     */
    private int sessionBufferSize() {
        return session.getAttributeKeys().stream().map(session::getAttribute).filter(IoBuffer.class::isInstance)
                .mapToInt(b -> ((IoBuffer) b).position()).sum();
    }

    private void decode(ProtocolDecoder decoder, String data) throws Exception {
        decoder.decode(session, IoBuffer.wrap(data.getBytes(StandardCharsets.ISO_8859_1)), session.getDecoderOutput());
    }

    private static ProtocolDecoder decoder(int maxFrameLength) {
        HL7MLLPConfig config = new HL7MLLPConfig();
        config.setCharset(StandardCharsets.ISO_8859_1);
        config.setMaxFrameLength(maxFrameLength);
        return new HL7MLLPDecoder(config);
    }
}
