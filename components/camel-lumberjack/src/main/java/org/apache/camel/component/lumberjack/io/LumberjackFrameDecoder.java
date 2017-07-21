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
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
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

/**
 * Decode lumberjack protocol frames. Support protocol V1 and V2 and frame types D, J, W and C.<br/>
 * <p>
 * For more info, see:
 * <ul>
 * <li><a href="https://github.com/elastic/beats">https://github.com/elastic/beats</a></li>
 * <li><a href="https://github.com/logstash-plugins/logstash-input-beats/blob/master/PROTOCOL.md">https://github.com/logstash-plugins/logstash-input-beats/blob/master/PROTOCOL.md</a></li>
 * <li><a href="https://github.com/elastic/logstash-forwarder/blob/master/PROTOCOL.md">https://github.com/elastic/logstash-forwarder/blob/master/PROTOCOL.md</a></li>
 * <li><a href="https://github.com/elastic/libbeat/issues/279">https://github.com/elastic/libbeat/issues/279</a></li>
 * </ul>
 */
final class LumberjackFrameDecoder extends ByteToMessageDecoder {
    private static final Logger LOG = LoggerFactory.getLogger(LumberjackFrameDecoder.class);

    private final LumberjackSessionHandler sessionHandler;
    private final ObjectMapper jackson = new ObjectMapper();

    LumberjackFrameDecoder(LumberjackSessionHandler sessionHandler) {
        this.sessionHandler = sessionHandler;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // mark the reader index to be able to start decoding from the same position if there is not enough data to finish the frame decoding
        in.markReaderIndex();

        boolean frameDecoded = false;

        try {
            if (!in.isReadable(FRAME_HEADER_LENGTH)) {
                return;
            }

            int frameVersion = in.readUnsignedByte();
            sessionHandler.versionRead(frameVersion);

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
                frameDecoded = handleWindowFrame(in);
                break;
            case TYPE_COMPRESS:
                frameDecoded = handleCompressedFrame(ctx, in, out);
                break;
            default:
                throw new RuntimeException("Unsupported frame type=" + frameType);
            }
        } finally {
            if (!frameDecoded) {
                LOG.debug("Not enough data to decode a complete frame, retry when more data is available. Reader index was {}", in.readerIndex());
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

        // put message in the pipeline
        out.add(new LumberjackMessage(sequenceNumber, jsonMessage));
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

        out.add(new LumberjackMessage(sequenceNumber, dataMessage));
        return true;
    }

    private boolean handleWindowFrame(ByteBuf in) {
        if (!in.isReadable(FRAME_WINDOW_HEADER_LENGTH)) {
            return false;
        }

        // update window size
        sessionHandler.windowSizeRead(in.readInt());
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
        Inflater inflater = new Inflater();
        if (in.hasArray()) {
            inflater.setInput(in.array(), in.arrayOffset() + in.readerIndex(), compressedPayloadLength);
            in.skipBytes(compressedPayloadLength);
        } else {
            byte[] array = new byte[compressedPayloadLength];
            in.readBytes(array);
            inflater.setInput(array);
        }

        while (!inflater.finished()) {
            ByteBuf decompressed = ctx.alloc().heapBuffer(1024, 1024);
            byte[] outArray = decompressed.array();
            int count = inflater.inflate(outArray, decompressed.arrayOffset(), decompressed.writableBytes());
            decompressed.writerIndex(count);
            // put data in the pipeline
            out.add(decompressed);
        }

        return true;
    }

    /**
     * Read a string that is prefixed by its length encoded by a 4 bytes integer.
     *
     * @param in the buffer to consume
     * @return the read string or {@code null} if not enough data available to read the whole string
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
}
