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

package org.apache.camel.component.netty.http;

import java.io.InputStream;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.stream.ChunkedInput;
import io.netty.handler.stream.ChunkedStream;

public class OutboundStreamHttpResponse extends DefaultHttpResponse implements ChunkedInput<HttpContent> {
    private final HttpChunkedInput input;

    public OutboundStreamHttpResponse(InputStream in, DefaultHttpResponse response) {
        super(response.protocolVersion(), response.status());
        this.input = new HttpChunkedInput(new ChunkedStream(in));
    }

    public DefaultHttpResponse getResponse() {
        return new DefaultHttpResponse(this.protocolVersion(), this.status(), this.headers());
    }

    @Override
    public boolean isEndOfInput() throws Exception {
        return input.isEndOfInput();
    }

    @Override
    public void close() throws Exception {
        input.close();
    }

    @Override
    @Deprecated
    public HttpContent readChunk(ChannelHandlerContext channelHandlerContext) throws Exception {
        return input.readChunk(channelHandlerContext);
    }

    @Override
    public HttpContent readChunk(ByteBufAllocator byteBufAllocator) throws Exception {
        return input.readChunk(byteBufAllocator);
    }

    @Override
    public long length() {
        return input.length();
    }

    @Override
    public long progress() {
        return input.progress();
    }
}
