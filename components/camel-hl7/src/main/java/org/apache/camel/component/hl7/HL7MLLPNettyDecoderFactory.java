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

import io.netty.channel.ChannelHandler;

/**
 * Netty MLLP Decoder factory
 */
public class HL7MLLPNettyDecoderFactory extends HL7MLLPConfigAwareChannelHandlerFactory {

    public int getMaxFrameLength() {
        return config.getMaxFrameLength();
    }

    /**
     * The maximum length in bytes of a message that the decoder reads, counted from the start of the data up to the end
     * bytes (the start byte included, the end bytes not). A longer message is discarded and fails with a
     * {@code TooLongFrameException}; a netty consumer reports it to its exception handler and closes the connection.
     * The decoder fails as soon as more bytes than the limit have arrived without both end bytes, so leave some margin
     * over the largest expected message. The default is {@link Integer#MAX_VALUE} (no limit).
     */
    public void setMaxFrameLength(int maxFrameLength) {
        config.setMaxFrameLength(maxFrameLength);
    }

    @Override
    public ChannelHandler newChannelHandler() {
        return new HL7MLLPNettyDecoder(config);
    }
}
