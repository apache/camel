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
package org.apache.camel.component.syslog.netty;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Rfc5425FrameDecoderTest {

    @Test
    void testFragmentedLengthFieldDoesNotDuplicateFrames() {
        EmbeddedChannel channel = new EmbeddedChannel(new Rfc5425FrameDecoder());

        // Write first message and partial length of next message: "3 one5"
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer("3 one5", StandardCharsets.UTF_8)));

        // Exactly one frame (" one") should be produced
        ByteBuf frame1 = channel.readInbound();
        assertNotNull(frame1);
        assertEquals(" one", frame1.toString(StandardCharsets.UTF_8));
        frame1.release();

        // No duplicate frame should be produced
        assertNull(channel.readInbound());

        // Write remainder of second message: " three"
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(" three", StandardCharsets.UTF_8)));

        // Second frame (" three") should be produced
        ByteBuf frame2 = channel.readInbound();
        assertNotNull(frame2);
        assertEquals(" three", frame2.toString(StandardCharsets.UTF_8));
        frame2.release();

        assertNull(channel.readInbound());
        assertFalse(channel.finish());
    }

    @Test
    void testFragmentedLengthDigitsAcrossPackets() {
        EmbeddedChannel channel = new EmbeddedChannel(new Rfc5425FrameDecoder());

        // A complete frame followed by the first digit of the next length field: "3 one1". Before the fix the
        // decoder reset the reader index when the space had not arrived, replaying " one" on the next packet.
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer("3 one1", StandardCharsets.UTF_8)));
        ByteBuf frame1 = channel.readInbound();
        assertNotNull(frame1);
        assertEquals(" one", frame1.toString(StandardCharsets.UTF_8));
        frame1.release();
        assertNull(channel.readInbound());

        // The second digit, the space and the body arrive in the next packet: "1 hello world"
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer("1 hello world", StandardCharsets.UTF_8)));
        ByteBuf frame2 = channel.readInbound();
        assertNotNull(frame2);
        assertEquals(" hello world", frame2.toString(StandardCharsets.UTF_8));
        frame2.release();

        assertNull(channel.readInbound());
        assertFalse(channel.finish());
    }

    @Test
    void testEmptyLengthFieldIsRejected() {
        assertRejected(" hello", "empty length field");
    }

    @Test
    void testNonDigitLengthFieldIsRejected() {
        assertRejected("1a hello", "non-digit character in length field");
    }

    @Test
    void testLengthFieldWrappingAnIntIsRejected() {
        // 4294967296 wraps a 32-bit int to 0, which would make the decoder emit one-byte frames
        assertRejected("4294967296 hello", "length field does not fit an int");
    }

    @Test
    void testLengthFieldAtIntegerMaxIsRejected() {
        // the frame is read as length + 1 bytes, so Integer.MAX_VALUE itself does not fit
        assertRejected("2147483647 hello", "length field does not fit an int");
    }

    @Test
    void testTenDigitLengthFieldThatFitsIsAccepted() {
        EmbeddedChannel channel = new EmbeddedChannel(new Rfc5425FrameDecoder());

        // the largest field this decoder accepts; the frame itself never arrives, so nothing is produced
        assertFalse(channel.writeInbound(Unpooled.copiedBuffer("2147483646 x", StandardCharsets.UTF_8)));
        assertNull(channel.readInbound());
        channel.finishAndReleaseAll();
    }

    private static void assertRejected(String input, String reason) {
        EmbeddedChannel channel = new EmbeddedChannel(new Rfc5425FrameDecoder());

        // ByteToMessageDecoder wraps the decoder's exception, and EmbeddedChannel rethrows it from writeInbound
        DecoderException e = assertThrows(DecoderException.class,
                () -> channel.writeInbound(Unpooled.copiedBuffer(input, StandardCharsets.UTF_8)));
        assertTrue(e.getMessage().contains(reason), e.getMessage());
        channel.finishAndReleaseAll();
    }
}
