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

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
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

/**
 * Passes a chunked HTTP message on as soon as its headers are received, with an {@link InputStream} of its body, which
 * gets the chunks as they are received.
 * <p/>
 * The I/O thread of the connection never waits for the reader of the body: when {@link #HIGH_WATER_MARK} bytes of the
 * body are waiting to be read, the handler stops reading from the connection, and reads again when the reader has
 * caught up. The body must therefore be read by another thread than the I/O thread of the connection; reading it on
 * that thread fails rather than waiting for data that cannot be received. Closing the stream discards the rest of the
 * body. Each message gets its own stream.
 */
public class HttpInboundStreamHandler extends MessageToMessageDecoder<HttpObject> {

    /**
     * The number of bytes of the body that can wait to be read before the handler stops reading from the connection.
     */
    static final int HIGH_WATER_MARK = 64 * 1024;

    /**
     * The number of bytes of the body waiting to be read below which the handler reads from the connection again.
     */
    static final int LOW_WATER_MARK = HIGH_WATER_MARK / 2;

    private InboundStream stream;
    private boolean isChunked;

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        failStream();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        failStream();
        super.channelInactive(ctx);
    }

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        if (!super.acceptInboundMessage(msg)) {
            return false;
        }

        if (msg instanceof HttpMessage httpMessage) {
            HttpHeaders headers = httpMessage.headers();
            return isChunked = headers.contains(TRANSFER_ENCODING, CHUNKED, true);
        } else {
            return msg instanceof HttpContent && isChunked;
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, HttpObject msg, List<Object> out) throws Exception {
        if (msg instanceof HttpMessage) {
            failStream();
            stream = new InboundStream(ctx.channel());
        }

        if (msg instanceof HttpRequest httpRequest) {
            out.add(new InboundStreamHttpRequest(httpRequest, stream));
        }

        if (msg instanceof HttpResponse httpResponse) {
            out.add(new InboundStreamHttpResponse(httpResponse, stream));
        }

        if (msg instanceof HttpContent httpContent && stream != null) {
            if (httpContent.decoderResult().isFailure()) {
                stream.fail(new IOException(
                        "Invalid chunked body: " + httpContent.decoderResult().cause().getMessage(),
                        httpContent.decoderResult().cause()));
                stream = null;
                return;
            }

            stream.offer(httpContent.content());

            if (msg instanceof LastHttpContent) {
                stream.end();
                stream = null;
            }
        }
    }

    private void failStream() {
        if (stream != null) {
            stream.fail(new IOException("The connection was closed before the whole body was received"));
            stream = null;
        }
    }

    /**
     * The body of a chunked message: the I/O thread adds the chunks, and another thread reads them.
     */
    private static final class InboundStream extends InputStream {
        private final Channel channel;
        private final Deque<byte[]> chunks = new ArrayDeque<>();
        // the position in the first chunk
        private int position;
        // the number of bytes waiting to be read
        private int waiting;
        private boolean ended;
        private boolean closed;
        private boolean suspended;
        private IOException failure;

        InboundStream(Channel channel) {
            this.channel = channel;
        }

        synchronized void offer(ByteBuf content) {
            if (closed || !content.isReadable()) {
                return;
            }
            byte[] chunk = ByteBufUtil.getBytes(content);
            chunks.add(chunk);
            waiting += chunk.length;
            if (waiting >= HIGH_WATER_MARK && !suspended) {
                suspended = true;
                channel.config().setAutoRead(false);
            }
            notifyAll();
        }

        synchronized void end() {
            ended = true;
            notifyAll();
        }

        synchronized void fail(IOException cause) {
            if (!ended) {
                failure = cause;
                ended = true;
                resume();
                notifyAll();
            }
        }

        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            int n = read(b, 0, 1);
            return n == -1 ? -1 : b[0] & 0xff;
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) throws IOException {
            Objects.checkFromIndexSize(off, len, b.length);
            if (len == 0) {
                return 0;
            }
            while (!closed && chunks.isEmpty() && !ended) {
                if (channel.eventLoop().inEventLoop()) {
                    throw new IOException(
                            "Cannot wait for the body on the I/O thread of the connection that receives it");
                }
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Interrupted while waiting for the body");
                }
            }
            if (closed) {
                throw new IOException("Stream closed");
            }
            if (chunks.isEmpty()) {
                if (failure != null) {
                    throw new IOException(failure.getMessage(), failure);
                }
                return -1;
            }

            int n = 0;
            while (n < len && !chunks.isEmpty()) {
                byte[] chunk = chunks.peek();
                int count = Math.min(len - n, chunk.length - position);
                System.arraycopy(chunk, position, b, off + n, count);
                n += count;
                position += count;
                if (position == chunk.length) {
                    chunks.poll();
                    position = 0;
                }
            }
            waiting -= n;
            if (waiting <= LOW_WATER_MARK) {
                resume();
            }
            return n;
        }

        @Override
        public synchronized int available() {
            return waiting;
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                closed = true;
                chunks.clear();
                waiting = 0;
                resume();
                notifyAll();
            }
        }

        private void resume() {
            if (suspended) {
                suspended = false;
                channel.config().setAutoRead(true);
            }
        }
    }
}
