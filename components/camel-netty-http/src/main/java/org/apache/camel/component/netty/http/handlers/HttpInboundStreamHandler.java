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

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import org.apache.camel.component.netty.http.InboundStreamHttpRequest;
import org.apache.camel.component.netty.http.InboundStreamHttpResponse;

import static io.netty.handler.codec.http.HttpHeaderNames.TRANSFER_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderValues.CHUNKED;

public class HttpInboundStreamHandler extends MessageToMessageDecoder<HttpObject> {
    private PipedInputStream is;
    private PipedOutputStream os;
    private boolean isChunked;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        os = new PipedOutputStream();
        is = new PipedInputStream(os);
        isChunked = false;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        os.close();
        is.close();
    }

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        if (!super.acceptInboundMessage(msg)) {
            return false;
        }

        if (msg instanceof HttpMessage) {
            HttpHeaders headers = ((HttpMessage) msg).headers();
            return isChunked = headers.contains(TRANSFER_ENCODING, CHUNKED, true);
        } else {
            return (msg instanceof HttpContent) && isChunked;
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, HttpObject msg, List<Object> out) throws Exception {
        if (msg instanceof HttpRequest) {
            InboundStreamHttpRequest request = new InboundStreamHttpRequest((HttpRequest)msg, is);
            out.add(request);
        }

        if (msg instanceof HttpResponse) {
            InboundStreamHttpResponse response = new InboundStreamHttpResponse((HttpResponse)msg, is);
            out.add(response);
        }

        if (msg instanceof HttpContent) {
            ByteBuf body = ((HttpContent) msg).content();
            if (body.readableBytes() > 0) {
                body.readBytes(os, body.readableBytes());
            }

            if (msg instanceof LastHttpContent) {
                os.close();
            }
        }
    }
}
