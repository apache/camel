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
package org.apache.camel.component.netty.codec;

import java.net.InetSocketAddress;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.serialization.ClassResolver;

public class DatagramPacketObjectDecoder extends MessageToMessageDecoder<AddressedEnvelope<Object, InetSocketAddress>> {

    private final ObjectDecoder delegateDecoder;

    public DatagramPacketObjectDecoder(ClassResolver resolver) {
        delegateDecoder = new ObjectDecoder(resolver);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, AddressedEnvelope<Object, InetSocketAddress> msg,
                          List<Object> out) throws Exception {
        if (msg.content() instanceof ByteBuf) {
            ByteBuf payload = (ByteBuf) msg.content();
            Object result = delegateDecoder.decode(ctx, payload);
            AddressedEnvelope<Object, InetSocketAddress> addressedEnvelop =
                new DefaultAddressedEnvelope<>(result, msg.recipient(), msg.sender());
            out.add(addressedEnvelop);
        }
    }

}
