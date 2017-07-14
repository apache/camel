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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This handler connects the Netty pipeline to the Camel endpoint.
 */
final class LumberjackMessageHandler extends SimpleChannelInboundHandler<LumberjackMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(LumberjackMessageHandler.class);

    private final LumberjackSessionHandler sessionHandler;
    private final LumberjackMessageProcessor messageProcessor;

    private volatile boolean process = true;

    LumberjackMessageHandler(LumberjackSessionHandler sessionHandler, LumberjackMessageProcessor messageProcessor) {
        this.sessionHandler = sessionHandler;
        this.messageProcessor = messageProcessor;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof IOException) {
            LOG.debug("IO exception (client connection closed ?)", cause);
        } else {
            LOG.warn("Caught an exception while reading, closing channel.", cause);
        }
        ctx.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, LumberjackMessage msg) throws Exception {
        if (process) {
            messageProcessor.onMessageReceived(msg.getPayload(), success -> {
                if (success) {
                    sessionHandler.notifyMessageProcessed(ctx, msg.getSequenceNumber());
                } else {
                    ctx.close();

                    // Mark that we shouldn't process the next messages that are already decoded and are waiting in netty queues
                    process = false;
                }
            });
        }
    }
}
