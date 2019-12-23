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
package org.apache.camel.component.netty.http.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.apache.camel.component.netty.http.OutboundStreamHttpRequest;
import org.apache.camel.component.netty.http.OutboundStreamHttpResponse;

public class HttpOutboundStreamHandler extends ChunkedWriteHandler {
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        boolean needNewPromise = false;

        if (msg instanceof OutboundStreamHttpRequest) {
            super.write(ctx, ((OutboundStreamHttpRequest) msg).getRequest(), promise);
            needNewPromise = true;
        } else if (msg instanceof OutboundStreamHttpResponse) {
            super.write(ctx, ((OutboundStreamHttpResponse)msg).getResponse(), promise);
            needNewPromise = true;
        }

        if (needNewPromise) {
            promise = new DefaultChannelPromise(ctx.channel());
        }

        super.write(ctx, msg, promise);
    }
}
