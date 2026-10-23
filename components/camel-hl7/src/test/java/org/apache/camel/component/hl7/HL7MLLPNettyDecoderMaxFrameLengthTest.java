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

import java.nio.charset.StandardCharsets;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the maxFrameLength option of the HL7 MLLP Netty decoder.
 */
class HL7MLLPNettyDecoderMaxFrameLengthTest {

    private static final String START = "\u000b";
    private static final String END = "\u001c\r";

    @Test
    void messageAtTheLimitIsDecoded() {
        // the start byte and 49 bytes of message: 50 bytes before the end bytes
        String message = "A".repeat(49);
        EmbeddedChannel channel = new EmbeddedChannel(decoder(50));

        channel.writeInbound(Unpooled.copiedBuffer(START + message + END, StandardCharsets.ISO_8859_1));

        assertEquals(message, channel.readInbound());
        assertFalse(channel.finish());
    }

    @Test
    void messageOverTheLimitFails() {
        String message = "A".repeat(50);
        EmbeddedChannel channel = new EmbeddedChannel(decoder(50));

        TooLongFrameException e = assertThrows(TooLongFrameException.class,
                () -> channel.writeInbound(Unpooled.copiedBuffer(START + message + END, StandardCharsets.ISO_8859_1)));

        assertTrue(e.getMessage().startsWith("frame length exceeds 50"), e.getMessage());
        assertNull(channel.readInbound());
        assertFalse(channel.finish());
    }

    @Test
    void dataWithoutEndBytesFailsOnceOverTheLimitAndTheNextMessageIsDecoded() {
        EmbeddedChannel channel = new EmbeddedChannel(decoder(50));

        // a peer that sends more than the limit without the end bytes: the decoder does not wait for them
        TooLongFrameException e = assertThrows(TooLongFrameException.class,
                () -> channel.writeInbound(Unpooled.copiedBuffer(START + "B".repeat(60), StandardCharsets.ISO_8859_1)));
        assertTrue(e.getMessage().startsWith("frame length exceeds 50"), e.getMessage());
        assertNull(channel.readInbound());

        // the rest of the long message is discarded up to its end bytes, and the next message is decoded
        channel.writeInbound(Unpooled.copiedBuffer("B".repeat(20) + END + START + "next" + END,
                StandardCharsets.ISO_8859_1));
        assertEquals("next", channel.readInbound());
        assertNull(channel.readInbound());
        assertFalse(channel.finish());
    }

    @Test
    void factoryAppliesTheLimitToItsDecoders() {
        HL7MLLPNettyDecoderFactory factory = new HL7MLLPNettyDecoderFactory();
        factory.setMaxFrameLength(50);
        assertEquals(50, factory.getMaxFrameLength());

        EmbeddedChannel channel = new EmbeddedChannel(factory.newChannelHandler());
        channel.writeInbound(Unpooled.copiedBuffer(START + "A".repeat(49) + END, StandardCharsets.ISO_8859_1));
        assertEquals("A".repeat(49), channel.readInbound());

        assertThrows(TooLongFrameException.class,
                () -> channel.writeInbound(Unpooled.copiedBuffer(START + "A".repeat(50) + END, StandardCharsets.ISO_8859_1)));
        assertNull(channel.readInbound());
        assertFalse(channel.finish());
    }

    @Test
    void defaultIsNoLimit() {
        assertEquals(Integer.MAX_VALUE, new HL7MLLPConfig().getMaxFrameLength());
        assertEquals(Integer.MAX_VALUE, new HL7MLLPNettyDecoderFactory().getMaxFrameLength());

        String message = "C".repeat(1024 * 1024);
        EmbeddedChannel channel = new EmbeddedChannel(new HL7MLLPNettyDecoderFactory().newChannelHandler());
        channel.writeInbound(Unpooled.copiedBuffer(START + message + END, StandardCharsets.ISO_8859_1));

        assertEquals(message, channel.readInbound());
        assertFalse(channel.finish());
    }

    @Test
    void maxFrameLengthMustBePositive() {
        HL7MLLPConfig config = new HL7MLLPConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setMaxFrameLength(0));
        assertThrows(IllegalArgumentException.class, () -> config.setMaxFrameLength(-1));
        assertEquals(Integer.MAX_VALUE, config.getMaxFrameLength());
    }

    private static ChannelHandler decoder(int maxFrameLength) {
        HL7MLLPConfig config = new HL7MLLPConfig();
        config.setCharset(StandardCharsets.ISO_8859_1);
        config.setMaxFrameLength(maxFrameLength);
        return new HL7MLLPNettyDecoder(config);
    }
}
