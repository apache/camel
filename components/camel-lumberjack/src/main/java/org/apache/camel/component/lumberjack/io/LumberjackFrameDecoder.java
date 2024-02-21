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
package org.apache.camel.component.lumberjack.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.apache.camel.RuntimeCamelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.lumberjack.io.LumberjackConstants.FRAME_COMPRESS_HEADER_LENGTH;
import static org.apache.camel.component.lumberjack.io.LumberjackConstants.FRAME_DATA_HEADER_LENGTH;
import static org.apache.camel.component.lumberjack.io.LumberjackConstants.FRAME_HEADER_LENGTH;
import static org.apache.camel.component.lumberjack.io.LumberjackConstants.FRAME_JSON_HEADER_LENGTH;
import static org.apache.camel.component.lumberjack.io.LumberjackConstants.FRAME_WINDOW_HEADER_LENGTH;
import static org.apache.camel.component.lumberjack.io.LumberjackConstants.INT_LENGTH;
import static org.apache.camel.component.lumberjack.io.LumberjackConstants.TYPE_COMPRESS;
import static org.apache.camel.component.lumberjack.io.LumberjackConstants.TYPE_DATA;
import static org.apache.camel.component.lumberjack.io.LumberjackConstants.TYPE_JSON;
import static org.apache.camel.component.lumberjack.io.LumberjackConstants.TYPE_WINDOW;
import static org.apache.camel.component.lumberjack.io.LumberjackConstants.VERSION_V1;
import static org.apache.camel.component.lumberjack.io.LumberjackConstants.VERSION_V2;

/**
 * Decode lumberjack protocol frames. Support protocol V1 and V2 and frame types D, J, W and C.<br/>
 * <p>
 * For more info, see:
 * <ul>
 * <li><a href="https://github.com/elastic/beats">https://github.com/elastic/beats</a></li>
 * <li><a href=
 * "https://github.com/logstash-plugins/logstash-input-beats/blob/master/PROTOCOL.md">https://github.com/logstash-plugins/logstash-input-beats/blob/master/PROTOCOL.md</a></li>
 * <li><a href=
 * "https://github.com/elastic/logstash-forwarder/blob/master/PROTOCOL.md">https://github.com/elastic/logstash-forwarder/blob/master/PROTOCOL.md</a></li>
 * <li><a href="https://github.com/elastic/libbeat/issues/279">https://github.com/elastic/libbeat/issues/279</a></li>
 * </ul>
 */
final class LumberjackFrameDecoder extends ByteToMessageDecoder {
    private static final Logger LOG = LoggerFactory.getLogger(LumberjackFrameDecoder.class);

    private final ObjectMapper jackson = new ObjectMapper();
    private LumberjackWindow window;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // mark the reader index to be able to start decoding from the same position if there is not enough data to finish the frame decoding
        in.markReaderIndex();

        boolean frameDecoded = false;

        try {
            if (!in.isReadable(FRAME_HEADER_LENGTH)) {
                return;
            }

            byte frameVersion = in.readByte();

            // make sure we get the right version
            verifyVersion(frameVersion);

            int frameType = in.readUnsignedByte();
            LOG.debug("Received a lumberjack frame of type {}", (char) frameType);

            switch (frameType) {
                case TYPE_JSON:
                    frameDecoded = handleJsonFrame(in, out);
                    break;
                case TYPE_DATA:
                    frameDecoded = handleDataFrame(in, out);
                    break;
                case TYPE_WINDOW:
                    frameDecoded = handleWindowFrame(in, frameVersion, out);
                    break;
                case TYPE_COMPRESS:
                    frameDecoded = handleCompressedFrame(ctx, in, out);
                    break;
                default:
                    throw new RuntimeCamelException("Unsupported frame type=" + frameType);
            }
        } finally {
            if (!frameDecoded) {
                LOG.debug("Not enough data to decode a complete frame, retry when more data is available. Reader index was {}",
                        in.readerIndex());
                in.resetReaderIndex();
            }
        }
    }

    private boolean handleJsonFrame(ByteBuf in, List<Object> out) throws IOException {
        if (!in.isReadable(FRAME_JSON_HEADER_LENGTH)) {
            return false;
        }

        int sequenceNumber = in.readInt();

        // read message string and then decode it as JSON
        String jsonStr = readLengthPrefixedString(in);
        if (jsonStr == null) {
            return false;
        }

        Object jsonMessage = jackson.readValue(jsonStr, Object.class);

        window.addMessage(new LumberjackMessage(sequenceNumber, jsonMessage));
        sendWindowIfComplete(out);
        return true;
    }

    private boolean handleDataFrame(ByteBuf in, List<Object> out) {
        if (!in.isReadable(FRAME_DATA_HEADER_LENGTH)) {
            return false;
        }

        int sequenceNumber = in.readInt();
        int entriesCount = in.readInt();

        Map<String, String> dataMessage = new LinkedHashMap<>();

        while (entriesCount-- > 0) {
            String key = readLengthPrefixedString(in);
            if (key == null) {
                return false;
            }

            String value = readLengthPrefixedString(in);
            if (value == null) {
                return false;
            }

            dataMessage.put(key, value);
        }

        window.addMessage(new LumberjackMessage(sequenceNumber, dataMessage));
        sendWindowIfComplete(out);
        return true;
    }

    private boolean handleWindowFrame(ByteBuf in, byte frameVersion, List<Object> out) {
        if (!in.isReadable(FRAME_WINDOW_HEADER_LENGTH)) {
            return false;
        }

        // receive a new window in the pipeline while another one is still processing (should not happen)
        // inspired from logstash-input-beats : https://github.com/logstash-plugins/logstash-input-beats
        if (window != null) {
            LOG.warn("New window size received but the current window was not complete, sending the current window");
            out.add(window);
            // init window for this pipeline
            window = null;
        }

        int windowSize = in.readInt();
        window = new LumberjackWindow(frameVersion, windowSize);
        return true;
    }

    private boolean handleCompressedFrame(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (!in.isReadable(FRAME_COMPRESS_HEADER_LENGTH)) {
            return false;
        }

        int compressedPayloadLength = in.readInt();
        if (!in.isReadable(compressedPayloadLength)) {
            return false;
        }

        // decompress payload
        // inspired from logstash-input-beats : https://github.com/logstash-plugins/logstash-input-beats
        Inflater inflater = new Inflater();
        ByteBuf decompressed = ctx.alloc().buffer(compressedPayloadLength);
        try (
             ByteBufOutputStream buffOutput = new ByteBufOutputStream(decompressed);
             InflaterOutputStream inflaterStream = new InflaterOutputStream(buffOutput, inflater)) {
            in.readBytes(inflaterStream, compressedPayloadLength);
        } finally {
            inflater.end();
        }

        try {
            while (decompressed.readableBytes() > 0) {
                decode(ctx, decompressed, out);
            }
        } finally {
            decompressed.release();
        }
        return true;
    }

    /**
     * Read a string that is prefixed by its length encoded by a 4 bytes integer.
     *
     * @param  in the buffer to consume
     * @return    the read string or {@code null} if not enough data available to read the whole string
     */
    private String readLengthPrefixedString(ByteBuf in) {
        if (!in.isReadable(INT_LENGTH)) {
            return null;
        }
        int length = in.readInt();
        if (!in.isReadable(length)) {
            return null;
        }

        String str = in.toString(in.readerIndex(), length, StandardCharsets.UTF_8);
        in.skipBytes(length);
        return str;
    }

    /**
     * reads version
     *
     * @param version
     */
    private void verifyVersion(byte version) {
        if (this.window == null || this.window.getVersion() == -1) {
            if (version != VERSION_V1 && version != VERSION_V2) {
                throw new RuntimeCamelException("Unsupported frame version=" + version);
            }
            LOG.debug("Lumberjack protocol version is {}", (char) version);
        } else if (window.getVersion() != version) {
            throw new IllegalStateException(
                    "Protocol version changed during session from " + window.getVersion() + " to " + version);
        }
    }

    public void sendWindowIfComplete(List<Object> out) {
        if (window.isComplete()) {
            out.add(window);
            // init window to handle other windows
            window = null;
        }
    }
}
