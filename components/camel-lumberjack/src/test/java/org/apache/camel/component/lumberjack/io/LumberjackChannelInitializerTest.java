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
package org.apache.camel.component.lumberjack.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import static io.netty.buffer.Unpooled.buffer;
import static org.apache.camel.component.lumberjack.io.LumberjackConstants.TYPE_ACKNOWLEDGE;
import static org.apache.camel.component.lumberjack.io.LumberjackConstants.VERSION_V2;
import static org.junit.Assert.assertEquals;

public class LumberjackChannelInitializerTest {
    @Test
    public void shouldDecodeTwoWindowsWithCompressedMessages() throws Exception {
        // Given a properly configured netty channel
        List<Object> messages = new ArrayList<>();
        EmbeddedChannel channel = new EmbeddedChannel(new LumberjackChannelInitializer(null, null, (payload, callback) -> {
            messages.add(payload);
            callback.onComplete(true);
        }));

        // When writing the stream byte per byte in order to ensure that we support splits everywhere
        // It contains 2 windows with compressed messages
        writeResourceBytePerByte(channel, "window10");
        writeResourceBytePerByte(channel, "window15");

        // Then we must have 25 messages with only maps
        assertEquals(25, messages.size());

        // And the first map should contains valid data (we're assuming it's also valid for the other ones)
        Map first = (Map) messages.get(0);
        assertEquals("log", first.get("type"));
        assertEquals("/home/qatest/collectNetwork/log/data-integration/00000000-f000-0000-1541-8da26f200001/absorption.log", first.get("source"));

        // And we should have replied twice (one per window)
        assertEquals(2, channel.outboundMessages().size());
        checkAck((ByteBuf) channel.outboundMessages().poll(), 10);
        checkAck((ByteBuf) channel.outboundMessages().poll(), 15);
    }

    private void writeResourceBytePerByte(EmbeddedChannel channel, String resource) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            int input;
            while ((input = stream.read()) != -1) {
                ByteBuf buffer = buffer(1, 1);
                buffer.writeByte(input);
                channel.writeInbound(buffer);
            }
        }
    }

    private void checkAck(ByteBuf buf, int sequence) {
        assertEquals("version", (short) VERSION_V2, buf.readUnsignedByte());
        assertEquals("frame", (short) TYPE_ACKNOWLEDGE, buf.readUnsignedByte());
        assertEquals("sequence", sequence, buf.readInt());
        assertEquals("remaining", 0, buf.readableBytes());
    }
}
