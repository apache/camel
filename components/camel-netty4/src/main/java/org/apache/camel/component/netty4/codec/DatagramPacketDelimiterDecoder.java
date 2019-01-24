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
package org.apache.camel.component.netty4.codec;

import java.net.InetSocketAddress;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatagramPacketDelimiterDecoder extends MessageToMessageDecoder<AddressedEnvelope<Object, InetSocketAddress>> {
    
    private static final Logger LOG = LoggerFactory.getLogger(DatagramPacketDelimiterDecoder.class);
    
    private final DelimiterBasedFrameDecoder delegateDecoder;
    
    public DatagramPacketDelimiterDecoder(int maxFrameLength, ByteBuf[] delimiters) {
        this(maxFrameLength, true, delimiters);
    }
    
    public DatagramPacketDelimiterDecoder(int maxFrameLength, boolean stripDelimiter,  ByteBuf[] delimiters) {
        delegateDecoder = new DelimiterBasedFrameDecoder(maxFrameLength, stripDelimiter, delimiters);
    }
    
    @Override
    protected void decode(ChannelHandlerContext ctx, AddressedEnvelope<Object, InetSocketAddress> msg,
                          List<Object> out) throws Exception {
        if (msg.content() instanceof ByteBuf) {
            ByteBuf payload = (ByteBuf)msg.content();
            Object result = delegateDecoder.decode(ctx, payload);
            AddressedEnvelope<Object, InetSocketAddress> addressEvelop = 
                new DefaultAddressedEnvelope<>(result, msg.recipient(), msg.sender());
            out.add(addressEvelop);
        } else {
            LOG.debug("Ignoring message content as it is not an io.netty.buffer.ByteBuf instance.");
        }
    }

}
