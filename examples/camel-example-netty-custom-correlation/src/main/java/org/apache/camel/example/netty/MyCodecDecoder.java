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
package org.apache.camel.example.netty;

import java.nio.charset.Charset;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;

/**
 * Netty decoder that assembles a complete messages from the frames received by Netty.
 * The decoder uses delimter based on start and end byte markers, to know when all
 * data for a complete message has been received.
 */
public class MyCodecDecoder extends DelimiterBasedFrameDecoder {

    private static final int MAX_FRAME_LENGTH = 4096;

    private static char startByte = 0x0b; // 11 decimal
    private static char endByte1 = 0x1c; // 28 decimal
    private static char endByte2 = 0x0d; // 13 decimal

    public MyCodecDecoder() {
        super(MAX_FRAME_LENGTH, true, Unpooled.copiedBuffer(
            new char[]{endByte1, endByte2}, Charset.defaultCharset()));
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        ByteBuf buf = (ByteBuf) super.decode(ctx, buffer);
        if (buf != null) {
            try {
                int pos = buf.bytesBefore((byte) startByte);
                if (pos >= 0) {
                    ByteBuf msg = buf.readerIndex(pos + 1).slice();
                    return asString(msg);
                } else {
                    throw new DecoderException("Did not find start byte " + (int) startByte);
                }
            } finally {
                // We need to release the buf here to avoid the memory leak
                buf.release();
            }
        }
        // Message not complete yet - return null to be called again
        return null;
    }

    private String asString(ByteBuf msg) {
        // convert the message to a String which Camel will then use
        String text = msg.toString(Charset.defaultCharset());
        return text;
    }

}
