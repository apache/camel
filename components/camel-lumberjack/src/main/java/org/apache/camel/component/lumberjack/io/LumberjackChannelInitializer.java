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
package org.apache.camel.component.lumberjack.io;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.EventExecutorGroup;

final class LumberjackChannelInitializer extends ChannelInitializer<Channel> {
    private final SSLContext sslContext;
    private final EventExecutorGroup messageExecutorService;
    private final LumberjackMessageProcessor messageProcessor;

    LumberjackChannelInitializer(SSLContext sslContext, EventExecutorGroup messageExecutorService,
                                 LumberjackMessageProcessor messageProcessor) {
        this.sslContext = sslContext;
        this.messageExecutorService = messageExecutorService;
        this.messageProcessor = messageProcessor;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        // Add SSL support if configured
        if (sslContext != null) {
            SSLEngine sslEngine = sslContext.createSSLEngine();
            sslEngine.setUseClientMode(false);
            pipeline.addLast(new SslHandler(sslEngine));
        }

        LumberjackSessionHandler sessionHandler = new LumberjackSessionHandler();

        // Add the primary lumberjack frame decoder
        pipeline.addLast(new LumberjackFrameDecoder(sessionHandler));

        // Add the secondary lumberjack frame decoder, used when the first one is processing compressed frames
        pipeline.addLast(new LumberjackFrameDecoder(sessionHandler));

        // Add the bridge to Camel
        pipeline.addLast(messageExecutorService, new LumberjackMessageHandler(sessionHandler, messageProcessor));
    }
}
