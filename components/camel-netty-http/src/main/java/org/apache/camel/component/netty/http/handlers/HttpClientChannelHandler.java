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
package org.apache.camel.component.netty.http.handlers;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.netty.NettyConstants;
import org.apache.camel.component.netty.handlers.ClientChannelHandler;
import org.apache.camel.component.netty.http.NettyHttpProducer;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty HTTP {@link org.apache.camel.component.netty.handlers.ClientChannelHandler} that handles the response combing
 * back from the HTTP server, called by this client.
 *
 */
public class HttpClientChannelHandler extends ClientChannelHandler {

    // use NettyHttpProducer as logger to make it easier to read the logs as this is part of the producer
    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpProducer.class);
    private final NettyHttpProducer producer;
    private HttpResponse response;
    private ChannelBuffer buffer;

    public HttpClientChannelHandler(NettyHttpProducer producer) {
        super(producer);
        this.producer = producer;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent messageEvent) throws Exception {
        // store response, as this channel handler is created per pipeline
        Object msg = messageEvent.getMessage();

        // it may be a chunked message
        if (msg instanceof HttpChunk) {
            HttpChunk chunk = (HttpChunk) msg;
            if (LOG.isTraceEnabled()) {
                LOG.trace("HttpChunk received: {} isLast: {}", chunk, chunk.isLast());
            }

            if (msg instanceof HttpChunkTrailer) {
                // chunk trailer only has headers
                HttpChunkTrailer trailer = (HttpChunkTrailer) msg;
                for (Map.Entry<String, String> entry : trailer.trailingHeaders()) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Adding trailing header {}={}", entry.getKey(), entry.getValue());
                    }
                    response.headers().add(entry.getKey(), entry.getValue());
                }
            } else {
                // append chunked content
                buffer.writeBytes(chunk.getContent());
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Wrote {} bytes to chunk buffer", buffer.writerIndex());
                }
            }
            if (chunk.isLast()) {
                // the content is a copy of the buffer with the actual data we wrote to it
                int end = buffer.writerIndex();
                ChannelBuffer copy = buffer.copy(0, end);
                // the copy must not be readable when the content was chunked, so set the index to the end
                copy.setIndex(end, end);
                response.setContent(copy);
                // we get the all the content now, so call super to process the received message
                super.messageReceived(ctx, messageEvent);
            }
        } else if (msg instanceof HttpResponse) {
            response = (HttpResponse) msg;
            Exchange exchange = super.getExchange(ctx);
            if (!HttpHeaders.isKeepAlive(response)) {
                // just want to make sure we close the channel if the keepAlive is not true
                exchange.setProperty(NettyConstants.NETTY_CLOSE_CHANNEL_WHEN_COMPLETE, true);
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("HttpResponse received: {} chunked:", response, response.isChunked());
            }
            if (response.getStatus().getCode() == HttpResponseStatus.CONTINUE.getCode()) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("HttpResponse received: {}: {}", response, response.getStatus());
                }
            } else if (!response.isChunked()) {
                // the response is not chunked so we have all the content
                super.messageReceived(ctx, messageEvent);
            } else {
                // the response is chunkced so use a dynamic buffer to receive the content in chunks
                buffer = ChannelBuffers.dynamicBuffer();
            }
        } else {
            // ignore not supported message
            if (LOG.isTraceEnabled() && msg != null) {
                LOG.trace("Ignoring non supported response message of type {} -> {}", msg.getClass(), msg);
            }
        }

    }

    @Override
    protected Message getResponseMessage(Exchange exchange, MessageEvent messageEvent) throws Exception {
        // use the binding
        return producer.getEndpoint().getNettyHttpBinding().toCamelMessage(response, exchange, producer.getConfiguration());
    }
}
