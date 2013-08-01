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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.netty.handlers.ClientChannelHandler;
import org.apache.camel.component.netty.http.NettyHttpProducer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty HTTP {@link org.apache.camel.component.netty.handlers.ClientChannelHandler} that handles the response combing
 * back from thhe HTTP server, called by this client.
 *
 */
public class HttpClientChannelHandler extends ClientChannelHandler {

    // use NettyHttpProducer as logger to make it easier to read the logs as this is part of the producer
    private static final transient Logger LOG = LoggerFactory.getLogger(NettyHttpProducer.class);
    private final NettyHttpProducer producer;
    private HttpResponse response;

    public HttpClientChannelHandler(NettyHttpProducer producer) {
        super(producer);
        this.producer = producer;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent messageEvent) throws Exception {
        // store response, as this channel handler is created per pipeline
        Object msg = messageEvent.getMessage();
        if (msg instanceof HttpResponse) {
            response = (HttpResponse) msg;
            super.messageReceived(ctx, messageEvent);
        } else {
            // ignore not supported message
            if (msg != null) {
                LOG.trace("Ignoring non HttpResponse message of type {} -> {}", msg.getClass(), msg);
            }
        }

    }

    @Override
    protected Message getResponseMessage(Exchange exchange, MessageEvent messageEvent) throws Exception {
        // use the binding
        return producer.getEndpoint().getNettyHttpBinding().toCamelMessage(response, exchange, producer.getConfiguration());
    }
}
