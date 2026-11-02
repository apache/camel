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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

        // First packet contains only partial length digit: "1"
        assertFalse(channel.writeInbound(Unpooled.copiedBuffer("1", StandardCharsets.UTF_8)));
        assertNull(channel.readInbound());

        // Second packet contains remaining length digit, space, and body: "1 hello world"
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer("1 hello world", StandardCharsets.UTF_8)));

        ByteBuf frame = channel.readInbound();
        assertNotNull(frame);
        assertEquals(" hello world", frame.toString(StandardCharsets.UTF_8));
        frame.release();

        assertNull(channel.readInbound());
        assertFalse(channel.finish());
    }
}
