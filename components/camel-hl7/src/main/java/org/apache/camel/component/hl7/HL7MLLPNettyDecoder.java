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
package org.apache.camel.component.hl7;

import java.nio.charset.Charset;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HL7 MLLP Decoder for Netty4
 */
class HL7MLLPNettyDecoder extends DelimiterBasedFrameDecoder {

    private static final Logger LOG = LoggerFactory.getLogger(HL7MLLPNettyDecoder.class);
    private static final int MAX_FRAME_LENGTH = Integer.MAX_VALUE;
    private final HL7MLLPConfig config;

    /**
     * Creates a decoder instance using a default HL7MLLPConfig
     */
    HL7MLLPNettyDecoder() {
        this(new HL7MLLPConfig());
    }

    /**
     * Creates a decoder instance
     *
     * @param config HL7MLLPConfig to be used for decoding
     * @throws java.lang.NullPointerException is config is null
     */
    HL7MLLPNettyDecoder(HL7MLLPConfig config) {
        super(MAX_FRAME_LENGTH, true, Unpooled.copiedBuffer(
                new char[]{config.getEndByte1(), config.getEndByte2()},
                Charset.defaultCharset()));
        this.config = config;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        ByteBuf buf = (ByteBuf) super.decode(ctx, buffer);
        if (buf != null) {
            try {
                int pos = buf.bytesBefore((byte) config.getStartByte());
                if (pos >= 0) {
                    ByteBuf msg = buf.readerIndex(pos + 1).slice();
                    LOG.debug("Message ends with length {}", msg.readableBytes());
                    return config.isProduceString() ? asString(msg) : asByteArray(msg);
                } else {
                    throw new DecoderException("Did not find start byte " + (int) config.getStartByte());
                }
            } finally {
                // We need to release the buf here to avoid the memory leak
                buf.release();
            }
        }
        // Message not complete yet - return null to be called again
        LOG.debug("No complete messages yet at position {}", buffer.readableBytes());
        return null;
    }

    private byte[] asByteArray(ByteBuf msg) {
        byte[] bytes = new byte[msg.readableBytes()];
        msg.getBytes(0, bytes);
        if (config.isConvertLFtoCR()) {
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] == (byte) '\n') {
                    bytes[i] = (byte) '\r';
                }
            }
        }
        return bytes;
    }

    private String asString(ByteBuf msg) {
        String s = msg.toString(config.getCharset());
        if (config.isConvertLFtoCR()) {
            return s.replace('\n', '\r');
        }
        return s;
    }
}
